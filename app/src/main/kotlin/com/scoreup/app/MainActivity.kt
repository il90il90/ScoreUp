package com.scoreup.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.scoreup.app.cast.CastSender
import com.scoreup.app.install.WatchInstaller
import com.scoreup.app.update.AppUpdater
import com.scoreup.app.ui.history.HistoryScreen
import com.scoreup.app.ui.scoreboard.Nav
import com.scoreup.app.ui.scoreboard.ScoreController
import com.scoreup.app.ui.scoreboard.ScoreboardScreen
import com.scoreup.app.ui.scoreboard.WatchStatus
import com.scoreup.app.ui.settings.SettingsScreen
import com.scoreup.app.ui.setup.MatchSetupScreen
import com.scoreup.app.ui.theme.ScoreUpTheme
import com.scoreup.app.ui.tv.CastScreen
import com.scoreup.app.ui.tv.PresentationController
import com.scoreup.app.ui.tv.TvScoreboard
import com.scoreup.app.ui.watch.WatchScreen
import com.scoreup.core.MatchConfig
import com.scoreup.core.Side
import kotlinx.coroutines.launch
import java.util.Locale

enum class Screen { BOARD, SETUP, HISTORY, WATCH, TV, TV_MODE, SETTINGS }

/** FragmentActivity (not just ComponentActivity) because the Cast MediaRouteButton needs it. */
class MainActivity : FragmentActivity() {
    private val app get() = application as ScoreUpApp
    private lateinit var presentation: PresentationController
    private lateinit var castSender: CastSender
    private lateinit var installer: WatchInstaller
    private lateinit var updater: AppUpdater
    private val availableUpdate = kotlinx.coroutines.flow.MutableStateFlow<AppUpdater.Release?>(null)
    private var appliedLanguage: String = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appliedLanguage = app.settings.state.value.language
        val engine = app.runtime.engine
        val controller = ScoreController(engine, app.feedback)
        presentation = PresentationController(this, engine.state)
        castSender = CastSender(applicationContext)
        installer = WatchInstaller(applicationContext)
        updater = AppUpdater(applicationContext)
        lifecycleScope.launch { availableUpdate.value = updater.check() }

        lifecycleScope.launch {
            engine.state.collect { s ->
                castSender.publish(s) { st ->
                    fun n(side: Side) = st.config.team(side).name.let { if (it == "HOME" || it == "AWAY" || it.isBlank()) getString(if (side == Side.HOME) R.string.home else R.string.away) else it }
                    n(Side.HOME) to n(Side.AWAY)
                }
            }
        }
        lifecycleScope.launch {
            app.settings.state.collect { s ->
                if (s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (s.language != appliedLanguage) { appliedLanguage = s.language; applyLanguage(s.language) }
            }
        }

        setContent {
            val settings by app.settings.state.collectAsState()
            ScoreUpTheme(settings.theme) {
                AppContent(controller)
            }
        }
    }

    @Composable
    private fun AppContent(controller: ScoreController) {
        val state by app.runtime.engine.state.collectAsState()
        val settings by app.settings.state.collectAsState()
        val peers by app.runtime.sync.peers.collectAsState()
        val connected by app.runtime.sync.connectedDevices.collectAsState()
        val history by app.history.items.collectAsState()
        val presentationDisplay by presentation.activeDisplay.collectAsState()
        val castTo by castSender.connectedTo.collectAsState()
        val installerState by installer.state.collectAsState()
        var screen by rememberSaveable { mutableStateOf(Screen.BOARD) }
        var showLanguage by rememberSaveable { mutableStateOf(false) }
        val update by availableUpdate.collectAsState()
        val watchUpdate = peers.any { (it.appVersionCode ?: 0) in 1 until installerState.bundledVersionCode }

        BackHandler(enabled = screen != Screen.BOARD) { screen = if (screen == Screen.TV_MODE) Screen.TV else Screen.BOARD }
        LaunchedEffect(screen) { setTvMode(screen == Screen.TV_MODE) }

        AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { s ->
            when (s) {
                Screen.BOARD -> ScoreboardScreen(
                    state, controller,
                    WatchStatus(peers.isNotEmpty(), peers.firstOrNull()?.name),
                    tvActive = presentationDisplay != null || castTo != null,
                    watchUpdateAvailable = watchUpdate,
                ) { nav ->
                    screen = when (nav) {
                        Nav.SETUP -> Screen.SETUP; Nav.HISTORY -> Screen.HISTORY; Nav.WATCH -> Screen.WATCH
                        Nav.TV -> Screen.TV; Nav.SETTINGS -> Screen.SETTINGS
                        Nav.LANGUAGE -> { showLanguage = true; Screen.BOARD }
                    }
                }
                Screen.SETUP -> MatchSetupScreen(
                    state.config, state.hasActivity, onBack = { screen = Screen.BOARD },
                    onStart = { controller.newMatch(it); screen = Screen.BOARD },
                    onApply = { controller.engine.updateConfig(it); screen = Screen.BOARD },
                )
                Screen.HISTORY -> HistoryScreen(
                    history, onBack = { screen = Screen.BOARD },
                    onRematch = { m ->
                        controller.newMatch(MatchConfig(m.sport, state.config.rules.takeIf { state.config.sport == m.sport } ?: com.scoreup.core.Rules.defaultsFor(m.sport), m.home, m.away, state.config.tapPoints))
                        screen = Screen.BOARD
                    },
                    onDelete = { app.history.delete(it) }, onClear = { app.history.clear() },
                )
                Screen.WATCH -> WatchScreen(
                    installerState, peers, connected, installer.lastHost(),
                    onStartDiscovery = installer::startDiscovery, onStopDiscovery = installer::stopDiscovery,
                    onPair = { h, p, c -> installer.pair(h, p, c) }, onInstall = { h, p -> installer.install(h, p) },
                    onReset = installer::reset, onBack = { screen = Screen.BOARD },
                )
                Screen.TV -> CastScreen(presentationDisplay, castSender.available, castTo, onBack = { screen = Screen.BOARD }, onOpenTvMode = { screen = Screen.TV_MODE })
                Screen.TV_MODE -> TvScoreboard(state, Modifier.testTag("tv_mode").clickable { screen = Screen.TV })
                Screen.SETTINGS -> SettingsScreen(settings, onBack = { screen = Screen.BOARD }, onChange = app.settings::update)
            }
        }

        if (showLanguage) com.scoreup.app.ui.common.LanguageDialog(settings.language, onPick = { tag -> app.settings.update { it.copy(language = tag) } }) { showLanguage = false }
        update?.let { r ->
            AlertDialog(
                onDismissRequest = { availableUpdate.value = null },
                title = { Text(stringResource(R.string.update_available_title)) },
                text = { Text(stringResource(R.string.update_available_body, r.versionName)) },
                confirmButton = { TextButton(onClick = { updater.downloadAndInstall(r); availableUpdate.value = null }) { Text(stringResource(R.string.update_now)) } },
                dismissButton = { TextButton(onClick = { availableUpdate.value = null }) { Text(stringResource(R.string.later)) } },
            )
        }
        if (!settings.onboarded) WelcomeDialog(onWatch = { app.settings.update { it.copy(onboarded = true) }; screen = Screen.WATCH }) {
            app.settings.update { it.copy(onboarded = true) }
        }
    }

    @Composable
    private fun WelcomeDialog(onWatch: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.welcome_title)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.welcome_body))
                    Text(stringResource(R.string.language_menu), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    com.scoreup.app.ui.common.LanguageOptions(app.settings.state.collectAsState().value.language) { tag -> app.settings.update { it.copy(language = tag) } }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("welcome_go")) { Text(stringResource(R.string.lets_go)) } },
            dismissButton = { TextButton(onClick = onWatch) { Text(stringResource(R.string.install_watch_cta)) } },
        )
    }

    private fun setTvMode(on: Boolean) {
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onStart() {
        super.onStart()
        presentation.start()
        castSender.start()
        app.runtime.sync.handshake()
    }

    override fun onStop() {
        presentation.stop()
        castSender.stop()
        super.onStop()
    }

    private fun applyLanguage(tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(android.app.LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
        recreate()
    }

    companion object {
        /** Applies the in-app language choice (also on Android < 13, where per-app locales don't exist). */
        fun localized(base: Context): Context {
            val tag = com.scoreup.app.data.AppSettings.language(base)
            if (tag.isEmpty()) return base
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }
    }
}
