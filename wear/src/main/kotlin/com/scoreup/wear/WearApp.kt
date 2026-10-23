package com.scoreup.wear

import android.app.Application
import com.scoreup.core.HeartTracker
import com.scoreup.core.MatchState
import com.scoreup.core.MatchSummary
import com.scoreup.shared.HistoryRepository
import com.scoreup.shared.ScoreRuntime

class WearApp : Application() {
    /** Saved scores on the watch (every restarted match, plus explicit saves). */
    lateinit var history: HistoryRepository private set
    val heartTracker = HeartTracker()

    override fun onCreate() {
        super.onCreate()
        history = HistoryRepository(filesDir)
        ScoreRuntime.init(this, "watch", BuildConfig.VERSION_CODE.toLong()) { history.add(summaryOf(it)) }
    }

    /** Saves the current score now; saving the same match again updates its entry. */
    fun saveCurrentScore() {
        val s = ScoreRuntime.get().engine.state.value
        if (s.hasActivity) history.add(summaryOf(s))
    }

    private fun summaryOf(s: MatchState) = MatchSummary.of(s, System.currentTimeMillis(), heartTracker.statsFor(s.matchId))
}
