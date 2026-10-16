package com.scoreup.adb

import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PairingException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Android 11+ "Wireless debugging → Pair device with pairing code" client
 * (protocol of adb's `pairing_connection` / `pairing_auth`):
 *  1. TLS 1.3 to the pairing port; export 64 bytes of keying material ("adb-label\0").
 *  2. SPAKE2 (we are Alice) with password = pairing code + exported material.
 *  3. Exchange PeerInfo (our adb public key / device GUID) encrypted with AES-128-GCM keyed by
 *     HKDF-SHA256(spake2 key, info="adb pairing_auth aes-128-gcm key").
 */
class PairingClient(private val key: AdbKey, private val tls: AdbTls, private val exporter: KeyingMaterialExporter) {

    /** Pairs and returns the device GUID. Throws [PairingException] (wrong code, refused, timeout). */
    fun pair(host: String, port: Int, code: String, timeoutMs: Int = 15_000): String {
        val raw = Socket().apply { soTimeout = timeoutMs; connect(InetSocketAddress(host, port), timeoutMs) }
        try {
            val ssl = runCatching { tls.wrap(raw, host, port) }.getOrElse { throw PairingException("TLS handshake failed", it) }
            ssl.use { s ->
                val ekm = exporter.export(s, EXPORTED_KEY_LABEL, EXPORTED_KEY_SIZE)
                val input = DataInputStream(s.inputStream)
                val out = s.outputStream

                val spake = Spake2(Spake2.Role.ALICE, CLIENT_NAME, SERVER_NAME)
                val msg = spake.generateMessage(code.trim().toByteArray() + ekm)
                writePacket(out, TYPE_SPAKE2_MSG, msg)
                val theirMsg = readPacket(input, TYPE_SPAKE2_MSG)
                val sharedKey = runCatching { spake.processMessage(theirMsg) }.getOrElse { throw PairingException("Invalid pairing response", it) }
                val cipher = PairingCipher(sharedKey)

                val info = ByteArray(PEER_INFO_SIZE)
                info[0] = PEER_RSA_PUB_KEY
                val pub = key.adbPublicKeyString().toByteArray()
                pub.copyInto(info, 1, 0, minOf(pub.size, PEER_INFO_SIZE - 2))
                writePacket(out, TYPE_PEER_INFO, cipher.encrypt(info))

                // A wrong code makes the device fail to decrypt and drop the connection here.
                val theirInfo = runCatching { cipher.decrypt(readPacket(input, TYPE_PEER_INFO)) }
                    .getOrElse { throw PairingException("Wrong pairing code", it) }
                if (theirInfo.size != PEER_INFO_SIZE) throw PairingException("Bad peer info")
                val guid = theirInfo.copyOfRange(1, theirInfo.size).takeWhile { it != 0.toByte() }.toByteArray()
                return String(guid)
            }
        } finally {
            runCatching { raw.close() }
        }
    }

    companion object {
        const val EXPORTED_KEY_LABEL = "adb-label\u0000"
        const val EXPORTED_KEY_SIZE = 64
        val CLIENT_NAME = "adb pair client\u0000".toByteArray()
        val SERVER_NAME = "adb pair server\u0000".toByteArray()
        const val HEADER_VERSION: Byte = 1
        const val TYPE_SPAKE2_MSG: Byte = 0
        const val TYPE_PEER_INFO: Byte = 1
        const val PEER_RSA_PUB_KEY: Byte = 0
        const val PEER_DEVICE_GUID: Byte = 1
        const val PEER_INFO_SIZE = 8192
        private const val MAX_PAYLOAD = 2 * PEER_INFO_SIZE

        fun writePacket(out: OutputStream, type: Byte, payload: ByteArray) {
            val h = ByteArray(6)
            h[0] = HEADER_VERSION; h[1] = type
            val n = payload.size
            h[2] = (n ushr 24).toByte(); h[3] = (n ushr 16).toByte(); h[4] = (n ushr 8).toByte(); h[5] = n.toByte()
            out.write(h + payload); out.flush()
        }

        fun readPacket(input: DataInputStream, expectType: Byte): ByteArray {
            val h = ByteArray(6)
            try { input.readFully(h) } catch (e: IOException) { throw PairingException("Connection closed by device", e) }
            if (h[0] != HEADER_VERSION) throw PairingException("Unsupported pairing version ${h[0]}")
            if (h[1] != expectType) throw PairingException("Unexpected packet type ${h[1]}")
            val n = ((h[2].toInt() and 0xff) shl 24) or ((h[3].toInt() and 0xff) shl 16) or ((h[4].toInt() and 0xff) shl 8) or (h[5].toInt() and 0xff)
            if (n <= 0 || n > MAX_PAYLOAD) throw PairingException("Bad payload size $n")
            return ByteArray(n).also { input.readFully(it) }
        }
    }
}

/** AES-128-GCM with independent 64-bit little-endian sequence-number nonces per direction. */
class PairingCipher(sharedKey: ByteArray) {
    private val key = SecretKeySpec(hkdfSha256(sharedKey, "adb pairing_auth aes-128-gcm key".toByteArray(), 16), "AES")
    private var encSeq = 0L
    private var decSeq = 0L

    fun encrypt(plain: ByteArray): ByteArray = run(Cipher.ENCRYPT_MODE, encSeq++, plain)
    fun decrypt(sealed: ByteArray): ByteArray = run(Cipher.DECRYPT_MODE, decSeq++, sealed)

    private fun run(mode: Int, seq: Long, data: ByteArray): ByteArray {
        val nonce = ByteArray(12)
        for (i in 0 until 8) nonce[i] = (seq ushr (8 * i)).toByte()
        return Cipher.getInstance("AES/GCM/NoPadding").run { init(mode, key, GCMParameterSpec(128, nonce)); doFinal(data) }
    }

    companion object {
        /** RFC 5869 with an empty salt (= HashLen zero bytes). */
        fun hkdfSha256(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
            val prk = mac.doFinal(ikm)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val out = java.io.ByteArrayOutputStream()
            var t = ByteArray(0); var i = 1
            while (out.size() < length) { mac.update(t); mac.update(info); mac.update(i.toByte()); t = mac.doFinal(); out.write(t); i++ }
            return out.toByteArray().copyOf(length)
        }
    }
}
