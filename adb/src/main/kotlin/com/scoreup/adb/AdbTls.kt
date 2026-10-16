package com.scoreup.adb

import java.net.Socket
import java.security.KeyStore
import java.security.Provider
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Exports TLS keying material (RFC 5705). On Android this is `Conscrypt.exportKeyingMaterial`. */
fun interface KeyingMaterialExporter {
    fun export(socket: SSLSocket, label: String, length: Int): ByteArray
}

/**
 * TLS 1.3 client used for adb pairing and the post-STLS connection. adbd uses self-signed
 * certificates; identity is established by the SPAKE2 pairing code, not by a CA.
 */
class AdbTls(private val key: AdbKey, private val provider: Provider? = null) {
    private val context: SSLContext by lazy {
        val ks = KeyStore.getInstance("PKCS12").apply { load(null, null); setKeyEntry("adb", key.privateKey, CharArray(0), arrayOf(key.certificate)) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }
        val ctx = if (provider != null) SSLContext.getInstance("TLSv1.3", provider) else SSLContext.getInstance("TLSv1.3")
        ctx.init(kmf.keyManagers, arrayOf<TrustManager>(TrustAll), null)
        ctx
    }

    fun wrap(raw: Socket, host: String, port: Int): SSLSocket {
        val s = context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        s.useClientMode = true
        s.enabledProtocols = arrayOf("TLSv1.3")
        s.startHandshake()
        return s
    }

    @Suppress("CustomX509TrustManager")
    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
