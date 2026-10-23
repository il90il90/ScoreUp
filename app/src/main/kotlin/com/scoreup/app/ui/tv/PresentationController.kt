package com.scoreup.app.ui.tv

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.scoreup.app.ui.common.rememberWatchHeartRate
import com.scoreup.app.ui.theme.ScoreUpTheme
import com.scoreup.app.data.ThemeMode
import com.scoreup.core.MatchState
import com.scoreup.shared.WearSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shows [TvScoreboard] on any presentation display (HDMI/USB-C, Miracast/wireless display, or an
 * emulator's simulated secondary display) while the phone keeps the controls. Bound to the
 * activity lifecycle: start in onStart, stop in onStop.
 */
class PresentationController(
    private val activity: ComponentActivity,
    private val state: StateFlow<MatchState>,
    private val heart: StateFlow<WearSync.HeartReading?>,
) {
    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private var presentation: Presentation? = null
    private val _activeDisplay = MutableStateFlow<String?>(null)
    val activeDisplay: StateFlow<String?> = _activeDisplay.asStateFlow()

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        displayManager.registerDisplayListener(listener, null)
        refresh()
    }

    fun stop() {
        displayManager.unregisterDisplayListener(listener)
        dismiss()
    }

    private fun refresh() {
        val display = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        val current = presentation
        if (current != null && current.display.displayId == display?.displayId) return
        dismiss()
        if (display != null) show(display)
    }

    private fun show(display: Display) {
        val p = ScorePresentation(activity, display, state, heart)
        runCatching { p.show() }.onSuccess {
            presentation = p
            _activeDisplay.value = display.name
        }.onFailure { _activeDisplay.value = null } // e.g. WindowManager.InvalidDisplayException
    }

    private fun dismiss() {
        presentation?.let { runCatching { it.dismiss() } }
        presentation = null
        _activeDisplay.value = null
    }
}

private class ScorePresentation(
    private val owner: ComponentActivity,
    display: Display,
    private val state: StateFlow<MatchState>,
    private val heart: StateFlow<WearSync.HeartReading?>,
) : Presentation(owner as Context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = ComposeView(context).apply {
            setContent {
                val s by state.collectAsState()
                val bpm = rememberWatchHeartRate(heart)
                ScoreUpTheme(ThemeMode.DARK) { TvScoreboard(s, heartRate = bpm) }
            }
        }
        // Compose needs lifecycle owners on the window's view tree (a Presentation is a Dialog).
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(owner)
            it.setViewTreeViewModelStoreOwner(owner)
            it.setViewTreeSavedStateRegistryOwner(owner)
        }
        setContentView(view)
    }
}
