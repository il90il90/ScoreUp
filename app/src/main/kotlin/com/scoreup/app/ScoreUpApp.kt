package com.scoreup.app

import android.app.Application
import com.scoreup.app.data.AppSettings
import com.scoreup.core.MatchSummary
import com.scoreup.shared.HistoryRepository
import com.scoreup.shared.ScoreRuntime
import org.conscrypt.Conscrypt
import java.security.Security

class ScoreUpApp : Application() {
    lateinit var settings: AppSettings private set
    lateinit var history: HistoryRepository private set
    lateinit var runtime: ScoreRuntime private set
    lateinit var feedback: Feedback private set

    override fun onCreate() {
        super.onCreate()
        // Conscrypt provides TLS 1.3 + keying-material export needed for Wireless ADB pairing.
        runCatching { Security.insertProviderAt(Conscrypt.newProvider(), 1) }
        settings = AppSettings(this)
        history = HistoryRepository(filesDir)
        runtime = ScoreRuntime.init(this, "phone", BuildConfig.VERSION_CODE.toLong()) { history.add(MatchSummary.of(it, System.currentTimeMillis())) }
        feedback = Feedback(this, settings)
    }

    /** Saves the current score to history now (saving the same match again updates its entry). */
    fun saveCurrentScore(): Boolean {
        val s = runtime.engine.state.value
        if (!s.hasActivity) return false
        history.add(MatchSummary.of(s, System.currentTimeMillis()))
        return true
    }
}
