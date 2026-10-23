package com.scoreup.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.scoreup.shared.ScoreRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val releaseScreen = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    private val app get() = application as WearApp
    private val prefs by lazy { getSharedPreferences("scoreup_watch", MODE_PRIVATE) }
    private lateinit var heart: HeartRateMonitor
    private val heartEnabled = MutableStateFlow(true)
    private val heartPermitted = MutableStateFlow(false)
    private val askHeartPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        heartPermitted.value = granted
        if (!granted) prefs.edit().putBoolean(KEY_HEART_DENIED, true).apply()
        // Usually delivered just before onResume, which starts the sensor.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startHeart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val runtime = ScoreRuntime.get()
        val haptics = Haptics(this)
        heart = HeartRateMonitor(this)
        heartEnabled.value = prefs.getBoolean(KEY_HEART, true)
        heartPermitted.value = hasHeartPermission()
        // Ask once on first launch; afterwards only when the user turns heart rate on.
        if (heart.available && heartEnabled.value && !heartPermitted.value && !prefs.getBoolean(KEY_HEART_ASKED, false)) {
            prefs.edit().putBoolean(KEY_HEART_ASKED, true).apply()
            askHeartPermission.launch(Manifest.permission.BODY_SENSORS)
        }
        lifecycleScope.launch {
            heart.readings.filterNotNull().collect { r ->
                runtime.sync.sendHeartRate(r.bpm)
                app.heartTracker.record(runtime.engine.state.value.matchId, r.bpm)
            }
        }
        setContent {
            val state by runtime.engine.state.collectAsState()
            val peers by runtime.sync.peers.collectAsState()
            val diag by runtime.sync.diagnostics.collectAsState()
            val saved by app.history.items.collectAsState()
            val reading by heart.readings.collectAsState()
            val enabled by heartEnabled.collectAsState()
            val permitted by heartPermitted.collectAsState()
            val phoneVersion = peers.maxOfOrNull { it.appVersionCode ?: 0 } ?: 0
            WearScoreApp(state, runtime.engine, haptics, phoneConnected = peers.isNotEmpty(), onInteraction = ::keepAwake,
                updateAvailable = phoneVersion > BuildConfig.VERSION_CODE, sync = diag, onSyncNow = runtime.sync::handshake,
                heart = HeartUi(reading, available = heart.available, enabled = enabled, permitted = permitted), onHeartToggle = ::setHeartEnabled,
                saved = saved, onSaveScore = app::saveCurrentScore, onClearSaved = app.history::clear)
        }
    }

    private fun hasHeartPermission() = checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED

    private fun startHeart() { if (heartEnabled.value && hasHeartPermission()) heart.start() }

    private fun setHeartEnabled(on: Boolean) {
        heartEnabled.value = on
        prefs.edit().putBoolean(KEY_HEART, on).apply()
        when {
            !on -> heart.stop()
            hasHeartPermission() -> startHeart()
            // Denied twice, Android no longer shows the prompt: the switch lives in the app's settings.
            prefs.getBoolean(KEY_HEART_DENIED, false) && !shouldShowRequestPermissionRationale(Manifest.permission.BODY_SENSORS) ->
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) }
            else -> askHeartPermission.launch(Manifest.permission.BODY_SENSORS)
        }
    }

    /** Keeps the screen on during active scoring; lets it sleep after 5 idle minutes to save battery. */
    private fun keepAwake() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.removeCallbacks(releaseScreen)
        handler.postDelayed(releaseScreen, 5 * 60_000L)
    }

    override fun onResume() {
        super.onResume()
        keepAwake()
        ScoreRuntime.get().sync.handshake()
        heartPermitted.value = hasHeartPermission()
        startHeart()
    }

    override fun onPause() {
        heart.stop()
        handler.removeCallbacks(releaseScreen)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    private companion object {
        const val KEY_HEART = "heart_rate"
        const val KEY_HEART_ASKED = "heart_rate_asked"
        const val KEY_HEART_DENIED = "heart_rate_denied"
    }
}
