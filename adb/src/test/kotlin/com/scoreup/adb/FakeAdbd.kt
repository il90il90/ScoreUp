package com.scoreup.adb

import org.conscrypt.Conscrypt
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * Test double of adbd implementing the device side of pairing (SPAKE2 Bob) and of the
 * transport (legacy AUTH or STLS + TLS with paired-key check), plus `cmd package install`.
 */
class FakeAdbd(private val pairingCode: String = "482915", private val useTls: Boolean = true) : AutoCloseable {
    val provider = Conscrypt.newProvider()
    private val serverKey = AdbKey.generate("adbd")
    val pairedKeys = mutableListOf<String>()
    val installedApks = mutableListOf<ByteArray>()
    val shellCommands = mutableListOf<String>()
    private val pairServer = ServerSocket(0)
    private val adbServer = ServerSocket(0)
    val pairPort get() = pairServer.localPort
    val adbPort get() = adbServer.localPort
    @Volatile var acceptLegacyKey: String? = null

    private val ctx: SSLContext = SSLContext.getInstance("TLSv1.3", provider).apply {
        val ks = KeyStore.getInstance("PKCS12").apply { load(null, null); setKeyEntry("k", serverKey.privateKey, CharArray(0), arrayOf(serverKey.certificate)) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?) {
                val pub = Base64.getEncoder().encodeToString(AdbKey.androidPubKey(chain[0].publicKey as java.security.interfaces.RSAPublicKey))
                if (pairedKeys.none { it.startsWith("$pub ") }) throw java.security.cert.CertificateException("unpaired")
            }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(serverKey.certificate)
        }
        init(kmf.keyManagers, arrayOf<TrustManager>(tm), null)
    }

    init {
        thread(isDaemon = true) { while (!pairServer.isClosed) runCatching { val s = pairServer.accept(); thread(isDaemon = true) { runCatching { pairSession(s) }; s.close() } } }
        thread(isDaemon = true) { while (!adbServer.isClosed) runCatching { val s = adbServer.accept(); thread(isDaemon = true) { runCatching { adbSession(s) }; s.close() } } }
    }

    private fun serverSocket(raw: Socket, requireAuth: Boolean): SSLSocket =
        (ctx.socketFactory.createSocket(raw, raw.inetAddress.hostAddress, raw.port, true) as SSLSocket).apply {
            useClientMode = false
            if (requireAuth) needClientAuth = true else wantClientAuth = true
            startHandshake()
        }

    private fun pairSession(raw: Socket) {
        // For pairing any client cert is accepted (identity comes from the code).
        val trustAll = SSLContext.getInstance("TLSv1.3", provider).apply {
            val ks = KeyStore.getInstance("PKCS12").apply { load(null, null); setKeyEntry("k", serverKey.privateKey, CharArray(0), arrayOf(serverKey.certificate)) }
            init(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }.keyManagers,
                arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
                    override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }), null)
        }
        val s = (trustAll.socketFactory.createSocket(raw, raw.inetAddress.hostAddress, raw.port, true) as SSLSocket).apply { useClientMode = false; wantClientAuth = true; startHandshake() }
        val ekm = Conscrypt.exportKeyingMaterial(s, PairingClient.EXPORTED_KEY_LABEL, null, 64)
        val input = DataInputStream(s.inputStream); val out = s.outputStream
        val bob = Spake2(Spake2.Role.BOB, PairingClient.SERVER_NAME, PairingClient.CLIENT_NAME)
        val msg = bob.generateMessage(pairingCode.toByteArray() + ekm)
        val theirs = PairingClient.readPacket(input, PairingClient.TYPE_SPAKE2_MSG)
        PairingClient.writePacket(out, PairingClient.TYPE_SPAKE2_MSG, msg)
        val cipher = PairingCipher(bob.processMessage(theirs))
        val info = cipher.decrypt(PairingClient.readPacket(input, PairingClient.TYPE_PEER_INFO)) // throws on wrong code
        val keyStr = String(info.copyOfRange(1, info.size).takeWhile { it != 0.toByte() }.toByteArray())
        pairedKeys += keyStr
        val reply = ByteArray(PairingClient.PEER_INFO_SIZE)
        reply[0] = PairingClient.PEER_DEVICE_GUID
        "adb-FAKE1234".toByteArray().copyInto(reply, 1)
        PairingClient.writePacket(out, PairingClient.TYPE_PEER_INFO, cipher.encrypt(reply))
    }

    private fun adbSession(raw: Socket) {
        var input = DataInputStream(raw.getInputStream())
        var out: OutputStream = raw.getOutputStream()
        fun send(m: AdbMessage) { out.write(m.encode()); out.flush() }
        val cnxn = AdbMessage.read(input)
        check(cnxn.command == AdbMessage.CNXN)
        if (useTls) {
            send(AdbMessage(AdbMessage.STLS, AdbMessage.STLS_VERSION, 0))
            check(AdbMessage.read(input).command == AdbMessage.STLS)
            val ssl = serverSocket(raw, requireAuth = true)
            input = DataInputStream(ssl.inputStream); out = ssl.outputStream
        } else {
            val token = ByteArray(20) { it.toByte() }
            send(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token))
            val sig = AdbMessage.read(input)
            val known = acceptLegacyKey
            val ok = known != null && runCatching {
                val pub = AdbKey.load(java.io.File(known), "x").publicKey
                val prefix = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
                Signature.getInstance("NONEwithRSA").run { initVerify(pub); update(prefix + token); verify(sig.payload) }
            }.getOrDefault(false)
            if (!ok) {
                send(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token))
                val pk = AdbMessage.read(input)
                check(pk.arg0 == AdbMessage.AUTH_RSAPUBLICKEY)
            }
        }
        send(AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, 256 * 1024, "device::ro.product.name=fakewatch;".toByteArray()))
        var nextRemote = 100
        while (true) {
            val m = runCatching { AdbMessage.read(input) }.getOrNull() ?: return
            if (m.command != AdbMessage.OPEN) continue
            val service = String(m.payload).trimEnd('\u0000')
            val local = m.arg0; val remote = nextRemote++
            send(AdbMessage(AdbMessage.OKAY, remote, local))
            val reply: String = if (service.startsWith("exec:cmd package install")) {
                val size = Regex("-S (\\d+)").find(service)!!.groupValues[1].toLong()
                val buf = ByteArrayOutputStream()
                while (buf.size() < size) {
                    val w = AdbMessage.read(input)
                    if (w.command == AdbMessage.WRTE) { buf.write(w.payload); send(AdbMessage(AdbMessage.OKAY, remote, local)) }
                }
                installedApks += buf.toByteArray()
                if (buf.toByteArray().take(2) == listOf('P'.code.toByte(), 'K'.code.toByte())) "Success\n" else "Failure [INSTALL_PARSE_FAILED_NOT_APK]\n"
            } else {
                shellCommands += service
                "ok:$service\n"
            }
            send(AdbMessage(AdbMessage.WRTE, remote, local, reply.toByteArray()))
            check(AdbMessage.read(input).command == AdbMessage.OKAY)
            send(AdbMessage(AdbMessage.CLSE, remote, local))
            runCatching { AdbMessage.read(input) } // client's CLSE
        }
    }

    override fun close() { pairServer.close(); adbServer.close() }
}
