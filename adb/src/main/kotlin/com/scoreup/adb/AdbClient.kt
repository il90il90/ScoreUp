package com.scoreup.adb

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.Signature

class AdbException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** One ADB transport message (24-byte little-endian header + payload). */
data class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray = ByteArray(0)) {
    fun encode(): ByteArray {
        val h = ByteArray(24)
        fun put(off: Int, v: Int) { for (i in 0 until 4) h[off + i] = (v ushr (8 * i)).toByte() }
        put(0, command); put(4, arg0); put(8, arg1); put(12, payload.size)
        put(16, payload.sumOf { it.toInt() and 0xff }); put(20, command.inv())
        return h + payload
    }

    override fun equals(other: Any?) = other is AdbMessage && command == other.command && arg0 == other.arg0 && arg1 == other.arg1 && payload.contentEquals(other.payload)
    override fun hashCode() = command * 31 + arg0

    companion object {
        const val CNXN = 0x4e584e43
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val WRTE = 0x45545257
        const val AUTH = 0x48545541
        const val STLS = 0x534c5453
        const val VERSION = 0x01000001
        const val STLS_VERSION = 0x01000000
        const val MAX_PAYLOAD = 1024 * 1024
        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        fun read(input: DataInputStream, maxPayload: Int = MAX_PAYLOAD): AdbMessage {
            val h = ByteArray(24)
            input.readFully(h)
            fun get(off: Int) = (h[off].toInt() and 0xff) or ((h[off + 1].toInt() and 0xff) shl 8) or ((h[off + 2].toInt() and 0xff) shl 16) or ((h[off + 3].toInt() and 0xff) shl 24)
            val cmd = get(0)
            if (get(20) != cmd.inv()) throw AdbException("Corrupt ADB header")
            val len = get(12)
            if (len < 0 || len > maxOf(maxPayload, MAX_PAYLOAD)) throw AdbException("ADB payload too large: $len")
            val payload = ByteArray(len).also { input.readFully(it) }
            return AdbMessage(cmd, get(4), get(8), payload)
        }
    }
}

/**
 * Minimal ADB host client: connects (plain, legacy RSA AUTH, or Wireless-debugging TLS via STLS),
 * runs `exec:` services and streams APK installs. Operations are sequential (one stream at a time).
 */
class AdbClient private constructor(private val raw: Socket, private val key: AdbKey, private val tls: AdbTls?) : Closeable {
    private var input = DataInputStream(raw.getInputStream().buffered())
    private var output: OutputStream = raw.getOutputStream()
    private var nextLocalId = 1
    var maxPayload = 4096; private set
    var banner = ""; private set
    /** True when the device showed (or may show) an "Allow debugging?" prompt (legacy AUTH). */
    var sentPublicKey = false; private set

    private fun send(m: AdbMessage) { output.write(m.encode()); output.flush() }
    private fun recv(): AdbMessage = try { AdbMessage.read(input, maxPayload) } catch (e: EOFException) { throw AdbException("Device closed the connection", e) }

    private fun handshake(host: String, port: Int) {
        val features = "host::features=shell_v2,cmd,stat_v2,ls_v2,fixed_push_mkdir,apex,abb,abb_exec"
        send(AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_PAYLOAD, features.toByteArray()))
        var signed = false
        var tlsActive = false
        while (true) {
            // With TLS 1.3 the device validates our certificate after the handshake, so an
            // unpaired phone only shows up as an alert on the first read.
            val m = try { recv() } catch (e: javax.net.ssl.SSLException) {
                if (tlsActive) throw AdbException("TLS rejected — pair this phone with the watch first", e) else throw e
            }
            when (m.command) {
                AdbMessage.STLS -> {
                    val t = tls ?: throw AdbException("Device requires TLS")
                    send(AdbMessage(AdbMessage.STLS, AdbMessage.STLS_VERSION, 0))
                    val ssl = try { t.wrap(raw, host, port) } catch (e: IOException) {
                        throw AdbException("TLS rejected — pair this phone with the watch first", e)
                    }
                    input = DataInputStream(ssl.inputStream.buffered()); output = ssl.outputStream
                    tlsActive = true
                }
                AdbMessage.AUTH -> {
                    if (m.arg0 != AdbMessage.AUTH_TOKEN) throw AdbException("Unexpected AUTH ${m.arg0}")
                    if (!signed) {
                        signed = true
                        send(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_SIGNATURE, 0, signToken(m.payload)))
                    } else {
                        sentPublicKey = true
                        send(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_RSAPUBLICKEY, 0, (key.adbPublicKeyString() + "\u0000").toByteArray()))
                        raw.soTimeout = 60_000 // user has to accept the prompt on the device
                    }
                }
                AdbMessage.CNXN -> {
                    maxPayload = m.arg1.coerceIn(4096, AdbMessage.MAX_PAYLOAD)
                    banner = String(m.payload)
                    raw.soTimeout = 30_000
                    return
                }
                else -> { /* ignore stray messages during handshake */ }
            }
        }
    }

    private fun signToken(token: ByteArray): ByteArray {
        // adbd verifies RSA_sign(NID_sha1, token): PKCS#1 v1.5 over a SHA-1 DigestInfo of the raw token.
        val prefix = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
        return Signature.getInstance("NONEwithRSA").run { initSign(key.privateKey); update(prefix + token); sign() }
    }

    /**
     * Runs an `exec:` service, optionally streaming [stdin] of [stdinLength] bytes to it, and
     * returns everything it printed. [onProgress] gets bytes sent so far.
     */
    fun exec(command: String, stdin: InputStream? = null, stdinLength: Long = 0, onProgress: (Long) -> Unit = {}): String {
        val localId = nextLocalId++
        send(AdbMessage(AdbMessage.OPEN, localId, 0, "exec:$command\u0000".toByteArray()))
        var remoteId = 0
        val out = ByteArrayOutputStream()
        // Wait for the stream to open.
        while (remoteId == 0) {
            val m = recv()
            when {
                m.command == AdbMessage.OKAY && m.arg1 == localId -> remoteId = m.arg0
                m.command == AdbMessage.CLSE && m.arg1 == localId -> throw AdbException("Device refused service '$command'")
                m.command == AdbMessage.WRTE -> send(AdbMessage(AdbMessage.OKAY, localId, m.arg0))
            }
        }
        var closed = false
        if (stdin != null) {
            val buf = ByteArray(maxPayload)
            var sent = 0L
            while (sent < stdinLength && !closed) {
                val n = stdin.read(buf, 0, minOf(buf.size.toLong(), stdinLength - sent).toInt())
                if (n < 0) throw AdbException("Input ended early")
                send(AdbMessage(AdbMessage.WRTE, localId, remoteId, buf.copyOf(n)))
                sent += n
                // Flow control: wait for the device to acknowledge this chunk.
                var acked = false
                while (!acked) {
                    val m = recv()
                    if (m.arg1 != localId) continue
                    when (m.command) {
                        AdbMessage.OKAY -> acked = true
                        AdbMessage.WRTE -> { out.write(m.payload); send(AdbMessage(AdbMessage.OKAY, localId, remoteId)) }
                        AdbMessage.CLSE -> { closed = true; acked = true }
                    }
                }
                onProgress(sent)
            }
        }
        while (!closed) {
            val m = recv()
            if (m.arg1 != localId) continue
            when (m.command) {
                AdbMessage.WRTE -> { out.write(m.payload); send(AdbMessage(AdbMessage.OKAY, localId, remoteId)) }
                AdbMessage.CLSE -> closed = true
            }
        }
        runCatching { send(AdbMessage(AdbMessage.CLSE, localId, remoteId)) }
        return out.toString(Charsets.UTF_8.name())
    }

    /** Streams an APK to `cmd package install`. Returns normally on "Success", throws otherwise. */
    fun install(apk: InputStream, size: Long, onProgress: (Long) -> Unit = {}): String {
        val result = exec("cmd package install -r -S $size", apk, size, onProgress).trim()
        if (!result.contains("Success")) throw AdbException(result.ifEmpty { "Unknown install error" })
        return result
    }

    fun shell(command: String): String = exec(command)

    override fun close() { runCatching { raw.close() } }

    companion object {
        fun connect(host: String, port: Int, key: AdbKey, tls: AdbTls?, timeoutMs: Int = 10_000): AdbClient {
            val s = Socket().apply { tcpNoDelay = true; soTimeout = timeoutMs; connect(InetSocketAddress(host, port), timeoutMs) }
            val c = AdbClient(s, key, tls)
            try { c.handshake(host, port) } catch (t: Throwable) { c.close(); throw t }
            return c
        }

        fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    }
}
