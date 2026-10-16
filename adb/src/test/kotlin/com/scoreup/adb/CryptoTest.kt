package com.scoreup.adb

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigInteger
import java.security.Signature

class CryptoTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test fun spakePointsMatchBoringSsl() {
        // Encoded values published in BoringSSL crypto/curve25519/spake25519.c.
        assertThat(hex(Spake2.M.encode())).isEqualTo("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e")
        assertThat(hex(Spake2.N.encode())).isEqualTo("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778")
    }

    @Test fun basePointAndOrder() {
        assertThat(hex(Ed25519.BASE.encode())).isEqualTo("5866666666666666666666666666666666666666666666666666666666666666")
        assertThat(hex(Ed25519.BASE.times(Ed25519.L).encode())).isEqualTo(hex(Ed25519.IDENTITY.encode()))
        val p = Ed25519.BASE.times(BigInteger.valueOf(12345))
        assertThat(hex(Ed25519.decode(p.encode())!!.encode())).isEqualTo(hex(p.encode()))
    }

    @Test fun rfc8032PublicKeyVector() {
        // RFC 8032 test 1: secret 9d61b1...; clamped SHA-512 scalar * B = d75a98...
        val seed = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val h = java.security.MessageDigest.getInstance("SHA-512").digest(seed).copyOf(32)
        h[0] = (h[0].toInt() and 248).toByte(); h[31] = (h[31].toInt() and 127 or 64).toByte()
        val a = Ed25519.fromLittleEndian(h)
        assertThat(hex(Ed25519.BASE.times(a).encode())).isEqualTo("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
    }

    @Test fun spakeAgreesWithSamePassword() {
        repeat(3) {
            val alice = Spake2(Spake2.Role.ALICE, PairingClient.CLIENT_NAME, PairingClient.SERVER_NAME)
            val bob = Spake2(Spake2.Role.BOB, PairingClient.SERVER_NAME, PairingClient.CLIENT_NAME)
            val pw = "123456".toByteArray() + ByteArray(64) { it.toByte() }
            val a = alice.generateMessage(pw); val b = bob.generateMessage(pw)
            assertThat(hex(alice.processMessage(b))).isEqualTo(hex(bob.processMessage(a)))
        }
    }

    @Test fun spakeDisagreesWithWrongPassword() {
        val alice = Spake2(Spake2.Role.ALICE, PairingClient.CLIENT_NAME, PairingClient.SERVER_NAME)
        val bob = Spake2(Spake2.Role.BOB, PairingClient.SERVER_NAME, PairingClient.CLIENT_NAME)
        val a = alice.generateMessage("123456".toByteArray()); val b = bob.generateMessage("654321".toByteArray())
        assertThat(hex(alice.processMessage(b))).isNotEqualTo(hex(bob.processMessage(a)))
    }

    @Test fun spakeRejectsInvalidPoint() {
        val alice = Spake2(Spake2.Role.ALICE, PairingClient.CLIENT_NAME, PairingClient.SERVER_NAME)
        alice.generateMessage("1".toByteArray())
        val bad = ByteArray(32) { 0xff.toByte() }
        assertThat(runCatching { alice.processMessage(bad) }.isFailure).isTrue()
    }

    @Test fun hkdfRfc5869Case3() {
        // RFC 5869 A.3: zero-length salt and info.
        val ikm = ByteArray(22) { 0x0b }
        val okm = PairingCipher.hkdfSha256(ikm, ByteArray(0), 42)
        assertThat(hex(okm)).isEqualTo("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8")
    }

    @Test fun pairingCipherRoundTripAndSequence() {
        val k = ByteArray(64) { it.toByte() }
        val a = PairingCipher(k); val b = PairingCipher(k)
        val m1 = a.encrypt("hello".toByteArray()); val m2 = a.encrypt("world".toByteArray())
        assertThat(String(b.decrypt(m1))).isEqualTo("hello")
        assertThat(String(b.decrypt(m2))).isEqualTo("world")
        assertThat(m1.size).isEqualTo(5 + 16)
    }

    @Test fun adbKeyCertAndAndroidPubKey() {
        val key = AdbKey.generate("scoreup@phone")
        key.certificate.checkValidity()
        key.certificate.verify(key.publicKey)
        val s = AdbKey.androidPubKey(key.publicKey)
        assertThat(s.size).isEqualTo(524)
        val n = key.publicKey.modulus
        val n0inv = BigInteger(1, s.copyOfRange(4, 8).reversedArray())
        val r32 = BigInteger.ONE.shiftLeft(32)
        assertThat((n0inv * n).mod(r32)).isEqualTo(r32 - BigInteger.ONE)
        assertThat(Ed25519.fromLittleEndian(s.copyOfRange(8, 264))).isEqualTo(n)
        assertThat(key.adbPublicKeyString()).endsWith(" scoreup@phone")
    }

    @Test fun keyPersistsAcrossLoads() {
        val dir = createTempDir()
        val k1 = AdbKey.loadOrCreate(dir, "x")
        val k2 = AdbKey.loadOrCreate(dir, "x")
        assertThat(k2.publicKey.modulus).isEqualTo(k1.publicKey.modulus)
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(k2.privateKey); update(byteArrayOf(1)); sign() }
        assertThat(Signature.getInstance("SHA256withRSA").run { initVerify(k1.certificate); update(byteArrayOf(1)); verify(sig) }).isTrue()
    }

    @Test fun messageEncodeDecode() {
        val m = AdbMessage(AdbMessage.WRTE, 3, 7, "abc".toByteArray())
        val back = AdbMessage.read(java.io.DataInputStream(m.encode().inputStream()))
        assertThat(back).isEqualTo(m)
    }
}
