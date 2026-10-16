package com.scoreup.adb

import com.google.common.truth.Truth.assertThat
import org.conscrypt.Conscrypt
import org.junit.Test

class PairAndInstallTest {
    private val provider = Conscrypt.newProvider()
    private val exporter = KeyingMaterialExporter { s, label, len -> Conscrypt.exportKeyingMaterial(s, label, null, len) }
    private val key = AdbKey.generate("scoreup@test")
    private val tls = AdbTls(key, provider)

    @Test fun pairWithCorrectCodeThenInstallOverTls() {
        FakeAdbd("482915").use { dev ->
            val guid = PairingClient(key, tls, exporter).pair("127.0.0.1", dev.pairPort, "482915")
            assertThat(guid).isEqualTo("adb-FAKE1234")
            assertThat(dev.pairedKeys.single()).isEqualTo(key.adbPublicKeyString())

            val apk = byteArrayOf('P'.code.toByte(), 'K'.code.toByte()) + ByteArray(700_000) { (it % 251).toByte() }
            val progress = mutableListOf<Long>()
            AdbClient.connect("127.0.0.1", dev.adbPort, key, tls).use { c ->
                assertThat(c.banner).contains("fakewatch")
                assertThat(c.install(apk.inputStream(), apk.size.toLong()) { progress += it }).isEqualTo("Success")
                assertThat(c.shell("am start -n com.scoreup.app/com.scoreup.wear.MainActivity")).contains("am start")
            }
            assertThat(dev.installedApks.single()).isEqualTo(apk)
            assertThat(progress.last()).isEqualTo(apk.size.toLong())
            assertThat(progress.size).isGreaterThan(1) // chunked by device max payload
        }
    }

    @Test fun wrongCodeIsReported() {
        FakeAdbd("482915").use { dev ->
            val r = runCatching { PairingClient(key, tls, exporter).pair("127.0.0.1", dev.pairPort, "000000") }
            assertThat(r.exceptionOrNull()).isInstanceOf(PairingException::class.java)
            assertThat(dev.pairedKeys).isEmpty()
        }
    }

    @Test fun unpairedPhoneIsRejectedOnConnect() {
        FakeAdbd().use { dev ->
            val r = runCatching { AdbClient.connect("127.0.0.1", dev.adbPort, key, tls).close() }
            assertThat(r.exceptionOrNull()).isInstanceOf(AdbException::class.java)
        }
    }

    @Test fun failedInstallSurfacesDeviceMessage() {
        FakeAdbd("1").use { dev ->
            PairingClient(key, tls, exporter).pair("127.0.0.1", dev.pairPort, "1")
            AdbClient.connect("127.0.0.1", dev.adbPort, key, tls).use { c ->
                val bad = ByteArray(10)
                val e = runCatching { c.install(bad.inputStream(), 10) }.exceptionOrNull()
                assertThat(e).hasMessageThat().contains("INSTALL_PARSE_FAILED_NOT_APK")
            }
        }
    }

    @Test fun legacyRsaAuthFlowSignsToken() {
        val dir = createTempDir(); key.save(dir)
        FakeAdbd(useTls = false).use { dev ->
            dev.acceptLegacyKey = dir.absolutePath
            AdbClient.connect("127.0.0.1", dev.adbPort, key, null).use { c ->
                assertThat(c.sentPublicKey).isFalse()
                assertThat(c.shell("echo hi")).contains("echo hi")
            }
        }
    }
}
