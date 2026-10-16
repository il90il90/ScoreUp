package com.scoreup.adb

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.Provider
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
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
        val ctx = if (provider != null) SSLContext.getInstance("TLSv1.3", provider) else SSLContext.getInstance("TLSv1.3")
        ctx.init(arrayOf<KeyManager>(AlwaysOurKey()), arrayOf<TrustManager>(TrustAll), null)
        ctx
    }

    /**
     * adbd's CertificateRequest lists the paired keys' fingerprints as CA names, which never match
     * our self-signed issuer. A standard KeyManager then sends NO client certificate and adbd
     * rejects the handshake. Always offer our certificate, exactly like the adb host does.
     */
    private inner class AlwaysOurKey : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(key.certificate)
        override fun getPrivateKey(alias: String?): PrivateKey = key.privateKey
    }

    fun wrap(raw: Socket, host: String, port: Int): SSLSocket {
        val s = context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        s.useClientMode = true
        s.enabledProtocols = arrayOf("TLSv1.3")
        s.startHandshake()
        return s
    }

    private companion object { const val ALIAS = "adb" }

    @Suppress("CustomX509TrustManager")
    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
