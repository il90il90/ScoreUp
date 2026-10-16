package com.scoreup.app.ui.scoreboard

import com.scoreup.app.Feedback
import com.scoreup.core.MatchConfig
import com.scoreup.core.ScoreEngine
import com.scoreup.core.Side

/** UI-facing actions: engine mutation + sound/haptic feedback in one place. */
class ScoreController(val engine: ScoreEngine, private val feedback: Feedback) {
    fun add(side: Side, points: Int = engine.state.value.config.tapPoints) {
        if (engine.state.value.finished) return
        engine.addPoints(side, points)
        if (engine.state.value.finished) feedback.win() else feedback.score(points)
    }
    fun undo() { if (engine.undo()) feedback.undo() }
    fun undoSide(side: Side) { if (engine.undoLast(side)) feedback.undo() }
    fun setScore(side: Side, v: Int) { engine.setScore(side, v); feedback.tick() }
    fun toggleClock() { engine.toggleClock(); feedback.tick() }
    fun resetClock() { engine.resetClock(); feedback.tick() }
    fun nextPeriod() { engine.nextPeriod(); feedback.tick() }
    fun setTapPoints(p: Int) { engine.setTapPoints(p); feedback.tick() }
    fun reset() { engine.resetMatch(); feedback.tick() }
    fun newMatch(config: MatchConfig) { engine.newMatch(config); feedback.tick() }
    fun finish() { engine.finish(); feedback.win() }
    fun reopen() { engine.reopen(); feedback.tick() }
    fun buzzer() = feedback.buzzer()
}
