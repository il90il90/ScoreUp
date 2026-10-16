package com.scoreup.app.install

import android.content.Context
import android.content.pm.PackageInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.scoreup.adb.AdbClient
import com.scoreup.adb.AdbKey
import com.scoreup.adb.AdbTls
import com.scoreup.adb.KeyingMaterialExporter
import com.scoreup.adb.PairingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.conscrypt.Conscrypt
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

data class Endpoint(val host: String, val port: Int, val name: String = "") {
    override fun toString() = "$host:$port"
}

enum class InstallPhase { IDLE, PAIRING, CONNECTING, INSTALLING, LAUNCHING, SUCCESS, ERROR }

data class InstallerState(
    val phase: InstallPhase = InstallPhase.IDLE,
    val pairingService: Endpoint? = null,
    /** Every device on the LAN advertising Wireless debugging (watch, but also TVs, PCs, phones). */
    val connectServices: List<Endpoint> = emptyList(),
    /** Address of the watch we last paired with / installed to; used to pick ITS connect service. */
    val pairedHost: String = "",
    val paired: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val errorKind: ErrorKind? = null,
    val bundledVersionName: String? = null,
    val bundledVersionCode: Long = 0,
    val discovering: Boolean = false,
) {
    val busy get() = phase in setOf(InstallPhase.PAIRING, InstallPhase.CONNECTING, InstallPhase.INSTALLING, InstallPhase.LAUNCHING)

    /**
     * The connect service of the watch: same IP as the watch we pair(ed) with; only when that is
     * unknown and exactly one device advertises, that one. Never another device's address.
     */
    fun connectServiceFor(host: String?): Endpoint? {
        val h = host?.takeIf { it.isNotBlank() } ?: pairedHost.takeIf { it.isNotBlank() } ?: pairingService?.host
        if (h != null) connectServices.firstOrNull { it.host == h }?.let { return it }
        return if (h == null) connectServices.singleOrNull() else null
    }

    val connectService: Endpoint? get() = connectServiceFor(null)
}

enum class ErrorKind { PAIR, CONNECT, INSTALL }

/**
 * Installs/updates the bundled Wear OS APK on a watch over Wireless debugging (adb protocol
 * implemented in :adb), with mDNS discovery of the watch's pairing and connect services.
 */
class WatchInstaller(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("scoreup_installer", Context.MODE_PRIVATE)
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val lock = Mutex()
    private val _state = MutableStateFlow(InstallerState(paired = prefs.getBoolean("paired", false), pairedHost = prefs.getString("host", "") ?: ""))
    val state: StateFlow<InstallerState> = _state.asStateFlow()

    private val key: AdbKey by lazy { AdbKey.loadOrCreate(File(context.filesDir, "adb"), "scoreup@${Build.MODEL.replace(' ', '_')}") }
    private val tls: AdbTls by lazy { AdbTls(key, runCatching { Conscrypt.newProvider() }.getOrNull()) }
    private val exporter = KeyingMaterialExporter { s, label, len -> Conscrypt.exportKeyingMaterial(s, label, null, len) }

    init { scope.launch { loadBundledInfo() } }

    // ------------------------------------------------------------------ bundled APK

    /** The wear APK packaged in this phone app's assets, extracted to cache once per version. */
    fun bundledApk(): File? = runCatching {
        val out = File(context.cacheDir, "scoreup-wear.apk")
        val assetSize = context.assets.openFd(ASSET).use { it.length }
        if (!out.exists() || out.length() != assetSize) {
            context.assets.open(ASSET).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        out
    }.getOrNull()

    private fun loadBundledInfo() {
        val apk = bundledApk() ?: return
        val info: PackageInfo = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return
        _state.update { it.copy(bundledVersionName = info.versionName, bundledVersionCode = PackageInfoCompat.getLongVersionCode(info)) }
    }

    // ------------------------------------------------------------------ discovery

    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    @Volatile private var resolving = false
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()

    fun startDiscovery() {
        if (listeners.isNotEmpty()) return
        _state.update { it.copy(discovering = true) }
        for (type in listOf(PAIRING_TYPE, CONNECT_TYPE)) {
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w(TAG, "discovery failed $errorCode") }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceFound(info: NsdServiceInfo) { resolveQueue += info; resolveNext() }
                override fun onServiceLost(info: NsdServiceInfo) {
                    _state.update {
                        if (info.serviceType.contains("pairing")) it.copy(pairingService = it.pairingService?.takeIf { e -> e.name != info.serviceName })
                        else it.copy(connectServices = it.connectServices.filter { e -> e.name != info.serviceName })
                    }
                }
            }
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l); listeners += l }
        }
    }

    fun stopDiscovery() {
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
        _state.update { it.copy(discovering = false) }
    }

    @Suppress("DEPRECATION") // resolveService is still the only API that works on every supported version
    private fun resolveNext() {
        synchronized(this) { if (resolving) return; resolving = true }
        val info = resolveQueue.poll() ?: run { resolving = false; return }
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { resolving = false; resolveNext() }
            override fun onServiceResolved(s: NsdServiceInfo) {
                val host = s.host?.hostAddress
                if (host != null && !host.contains(':')) { // prefer IPv4 (watch LAN address)
                    val ep = Endpoint(host, s.port, s.serviceName)
                    _state.update {
                        if (s.serviceType.contains("pairing")) it.copy(pairingService = ep)
                        else it.copy(connectServices = it.connectServices.filter { e -> e.name != ep.name && e.host != ep.host } + ep)
                    }
                }
                resolving = false; resolveNext()
            }
        })
    }

    // ------------------------------------------------------------------ actions

    fun pair(host: String, port: Int, code: String) = scope.launch {
        lock.withLock {
            _state.update { it.copy(phase = InstallPhase.PAIRING, error = null, errorKind = null) }
            runCatching { PairingClient(key, tls, exporter).pair(host.trim(), port, code) }
                .onSuccess {
                    prefs.edit().putBoolean("paired", true).putString("host", host.trim()).apply()
                    _state.update { s -> s.copy(phase = InstallPhase.IDLE, paired = true, pairedHost = host.trim()) }
                }
                .onFailure { e -> fail(ErrorKind.PAIR, e) }
        }
    }

    fun install(host: String, port: Int) = scope.launch {
        lock.withLock {
            val apk = bundledApk() ?: return@withLock fail(ErrorKind.INSTALL, IllegalStateException("Watch APK missing from this build"))
            _state.update { it.copy(phase = InstallPhase.CONNECTING, progress = 0f, error = null, errorKind = null) }
            val client = runCatching { AdbClient.connect(host.trim(), port, key, tls) }.getOrElse { return@withLock fail(ErrorKind.CONNECT, it) }
            client.use { c ->
                _state.update { it.copy(phase = InstallPhase.INSTALLING) }
                val size = apk.length()
                runCatching {
                    apk.inputStream().buffered().use { input -> c.install(input, size) { sent -> _state.update { it.copy(progress = sent.toFloat() / size) } } }
                }.onFailure { return@withLock fail(ErrorKind.INSTALL, it) }
                _state.update { it.copy(phase = InstallPhase.LAUNCHING, progress = 1f) }
                runCatching { c.shell("am start -n $WATCH_ACTIVITY") }
            }
            prefs.edit().putBoolean("paired", true).putString("host", host.trim()).apply()
            _state.update { it.copy(phase = InstallPhase.SUCCESS, paired = true, pairedHost = host.trim()) }
        }
    }

    fun lastHost(): String = prefs.getString("host", "") ?: ""

    fun reset() = _state.update { it.copy(phase = InstallPhase.IDLE, error = null, errorKind = null, progress = 0f) }

    private fun fail(kind: ErrorKind, e: Throwable) {
        Log.w(TAG, "$kind failed", e)
        _state.update { it.copy(phase = InstallPhase.ERROR, errorKind = kind, error = e.message ?: e.javaClass.simpleName) }
    }

    companion object {
        const val ASSET = "wear/scoreup-wear.apk"
        const val PAIRING_TYPE = "_adb-tls-pairing._tcp"
        const val CONNECT_TYPE = "_adb-tls-connect._tcp"
        const val WATCH_ACTIVITY = "com.scoreup.app/com.scoreup.wear.MainActivity"
        private const val TAG = "WatchInstaller"
    }
}
