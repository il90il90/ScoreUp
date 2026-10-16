package com.scoreup.app.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.scoreup.app.BuildConfig
import com.scoreup.app.data.AppSettings
import com.scoreup.core.MatchState
import com.scoreup.core.Side
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Google Cast configuration: the custom ScoreUp web receiver (cast-receiver/index.html). */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder().setReceiverApplicationId(receiverAppId(context) ?: BuildConfig.CAST_RECEIVER_APP_ID).build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null

    companion object {
        /** Native Cast is only offered when a custom receiver App ID is configured (DMR can't render a scoreboard). */
        fun receiverAppId(context: Context): String? =
            AppSettings.castAppId(context).takeIf { it.isNotBlank() }
                ?: BuildConfig.CAST_RECEIVER_APP_ID.takeIf { it.isNotBlank() && it != DEFAULT_MEDIA_RECEIVER }

        const val DEFAULT_MEDIA_RECEIVER = "CC1AD845"
    }
}

/**
 * Pushes live match state to the ScoreUp Cast receiver over a custom message channel. The
 * receiver renders the scoreboard and runs the clock locally from (running, displayMs, sentAt).
 */
class CastSender(private val context: Context) {
    private val _connectedTo = MutableStateFlow<String?>(null)
    val connectedTo: StateFlow<String?> = _connectedTo.asStateFlow()
    private var session: CastSession? = null
    private var last: MatchState? = null

    /** True when a receiver App ID is configured and Google Play services' Cast framework loaded. */
    val available: Boolean get() = CastOptionsProvider.receiverAppId(context) != null && castContext() != null

    @Suppress("DEPRECATION") // synchronous accessor; always called on the main thread
    private fun castContext(): CastContext? = runCatching { CastContext.getSharedInstance(context) }.getOrNull()

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, id: String) = attach(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = attach(s)
        override fun onSessionEnded(s: CastSession, error: Int) = detach()
        override fun onSessionSuspended(s: CastSession, reason: Int) = detach()
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionStartFailed(s: CastSession, error: Int) = detach()
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, id: String) {}
        override fun onSessionResumeFailed(s: CastSession, error: Int) = detach()
    }

    fun start() {
        if (CastOptionsProvider.receiverAppId(context) == null) return
        runCatching {
            val sm = castContext()!!.sessionManager
            sm.addSessionManagerListener(listener, CastSession::class.java)
            sm.currentCastSession?.let { attach(it) }
        }
    }

    fun stop() {
        runCatching { castContext()!!.sessionManager.removeSessionManagerListener(listener, CastSession::class.java) }
    }

    private fun attach(s: CastSession) {
        session = s
        _connectedTo.value = s.castDevice?.friendlyName ?: "TV"
        last?.let { publish(it, force = true) }
    }

    private fun detach() { session = null; _connectedTo.value = null }

    fun publish(state: MatchState, force: Boolean = false, labels: (MatchState) -> Pair<String, String> = { "" to "" }) {
        if (!force && state == last) return
        last = state
        val s = session ?: return
        runCatching { s.sendMessage(NAMESPACE, toJson(state, labels(state)).toString()) }
    }

    companion object {
        const val NAMESPACE = "urn:x-cast:com.scoreup.live"

        fun toJson(s: MatchState, names: Pair<String, String> = "" to "", now: Long = System.currentTimeMillis()): JSONObject = JSONObject().apply {
            put("v", 1)
            put("sport", s.config.sport.name)
            put("period", s.period)
            put("periods", s.config.rules.periods)
            put("setBased", s.config.rules.setBased)
            put("finished", s.finished)
            put("clock", JSONObject().apply {
                put("running", s.clock.running)
                put("displayMs", s.displayClockMs(now))
                put("countsDown", s.config.rules.clockCountsDown && s.config.rules.periodMinutes > 0)
                put("sentAt", now)
            })
            for (side in Side.entries) {
                val t = s.config.team(side)
                put(side.name.lowercase(), JSONObject().apply {
                    put("name", (if (side == Side.HOME) names.first else names.second).ifEmpty { t.name })
                    put("color", String.format("#%06X", t.color and 0xFFFFFF))
                    put("score", s.score(side))
                    put("sets", s.setsWon(side))
                })
            }
            put("sets", JSONArray().apply { s.sets.forEach { put(JSONArray(listOf(it.home, it.away))) } })
        }
    }
}
