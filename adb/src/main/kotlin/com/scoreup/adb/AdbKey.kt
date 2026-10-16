package com.scoreup.adb

import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The phone's ADB identity: an RSA-2048 key plus a self-signed X.509 certificate (needed for the
 * TLS used by Wireless debugging). Generated once and persisted; the watch remembers it after
 * pairing, so later installs/updates need no pairing code.
 */
class AdbKey(val privateKey: PrivateKey, val publicKey: RSAPublicKey, val certificate: X509Certificate, val name: String) {

    /** adbd "user key" text: base64(android RSA struct) + " " + name. */
    fun adbPublicKeyString(): String = Base64.getEncoder().encodeToString(androidPubKey(publicKey)) + " " + name

    fun save(dir: File) {
        dir.mkdirs()
        File(dir, "adbkey.pk8").writeBytes(privateKey.encoded)
        File(dir, "adbkey.crt").writeBytes(certificate.encoded)
    }

    companion object {
        fun loadOrCreate(dir: File, name: String): AdbKey = runCatching { load(dir, name) }.getOrNull()
            ?: generate(name).also { it.save(dir) }

        fun load(dir: File, name: String): AdbKey {
            val pk = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(File(dir, "adbkey.pk8").readBytes()))
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(File(dir, "adbkey.crt").inputStream()) as X509Certificate
            return AdbKey(pk, cert.publicKey as RSAPublicKey, cert, name)
        }

        fun generate(name: String): AdbKey {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            return AdbKey(kp.private, kp.public as RSAPublicKey, selfSignedCert(kp, "CN=$name"), name)
        }

        /** Android's `RSAPublicKey` struct (mincrypt) used by adb for key identity: 524 bytes. */
        fun androidPubKey(key: RSAPublicKey): ByteArray {
            val words = 64
            val n = key.modulus
            val r32 = BigInteger.ONE.shiftLeft(32)
            val n0inv = r32 - n.mod(r32).modInverse(r32)
            val rr = BigInteger.ONE.shiftLeft(4096).mod(n)
            val out = ByteArray(4 + 4 + 256 + 256 + 4)
            putLe32(out, 0, words.toLong())
            putLe32(out, 4, n0inv.toLong())
            Ed25519.toLittleEndian(n, 256).copyInto(out, 8)
            Ed25519.toLittleEndian(rr, 256).copyInto(out, 264)
            putLe32(out, 520, key.publicExponent.toLong())
            return out
        }

        private fun putLe32(b: ByteArray, off: Int, v: Long) { for (i in 0 until 4) b[off + i] = (v shr (8 * i) and 0xff).toByte() }

        // ---- Minimal DER encoder for a self-signed X.509 v3 certificate (SHA256withRSA).
        internal fun selfSignedCert(kp: KeyPair, dn: String): X509Certificate {
            val sigAlg = seq(oid("1.2.840.113549.1.1.11"), byteArrayOf(0x05, 0x00))
            val name = seq(set(seq(oid("2.5.4.3"), utf8(dn.removePrefix("CN=")))))
            val now = System.currentTimeMillis()
            // 20 years: UTCTime (YYMMDD) is only valid until 2049.
            val validity = seq(time(Date(now - 86_400_000L)), time(Date(now + 20L * 365 * 86_400_000L)))
            val tbs = seq(
                tlv(0xA0, int(BigInteger.TWO)), // v3
                int(BigInteger(63, java.security.SecureRandom())),
                sigAlg, name, validity, name,
                kp.public.encoded, // SubjectPublicKeyInfo
            )
            val sig = Signature.getInstance("SHA256withRSA").run { initSign(kp.private); update(tbs); sign() }
            val der = seq(tbs, sigAlg, tlv(0x03, byteArrayOf(0) + sig))
            return CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
        }

        private fun len(n: Int): ByteArray = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
            else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
        }
        private fun tlv(tag: Int, content: ByteArray) = byteArrayOf(tag.toByte()) + len(content.size) + content
        private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
        private fun set(vararg parts: ByteArray) = tlv(0x31, parts.fold(ByteArray(0)) { a, b -> a + b })
        private fun int(v: BigInteger) = tlv(0x02, v.toByteArray())
        private fun utf8(s: String) = tlv(0x0C, s.toByteArray())
        private fun time(d: Date) = tlv(0x17, SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(d).toByteArray())
        private fun oid(dotted: String): ByteArray {
            val parts = dotted.split('.').map { it.toLong() }
            val out = ByteArrayOutputStream()
            out.write((parts[0] * 40 + parts[1]).toInt())
            for (p in parts.drop(2)) {
                var v = p; val stack = ArrayList<Int>()
                do { stack += (v and 0x7f).toInt(); v = v shr 7 } while (v > 0)
                for (i in stack.indices.reversed()) out.write(stack[i] or if (i > 0) 0x80 else 0)
            }
            return tlv(0x06, out.toByteArray())
        }
    }
}
