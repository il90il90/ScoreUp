package com.scoreup.wear

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.scoreup.shared.ScoreRuntime

class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val releaseScreen = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val runtime = ScoreRuntime.get()
        val haptics = Haptics(this)
        setContent {
            val state by runtime.engine.state.collectAsState()
            val peers by runtime.sync.peers.collectAsState()
            WearScoreApp(state, runtime.engine, haptics, phoneConnected = peers.isNotEmpty(), onInteraction = ::keepAwake)
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
    }

    override fun onPause() {
        handler.removeCallbacks(releaseScreen)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }
}
