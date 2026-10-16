package com.scoreup.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReducerTest {
    private var now = 1_000L
    private fun engine(sport: Sport = Sport.BASKETBALL, id: String = "phone") =
        ScoreEngine(id, InMemorySessionStore(), clock = { now }, defaultConfig = MatchConfig.default(sport))

    @Test fun addsAndUndoes() {
        val e = engine()
        e.addPoints(Side.HOME, 2); e.addPoints(Side.HOME, 3); e.addPoints(Side.AWAY, 1)
        assertThat(e.state.value.home).isEqualTo(5)
        assertThat(e.state.value.away).isEqualTo(1)
        assertThat(e.undo()).isTrue()
        assertThat(e.state.value.away).isEqualTo(0)
        assertThat(e.undo()).isTrue()
        assertThat(e.state.value.home).isEqualTo(2)
        assertThat(e.state.value.feed).hasSize(1)
    }

    @Test fun undoOnEmptyMatchIsNoop() {
        val e = engine()
        assertThat(e.undo()).isFalse()
        assertThat(e.state.value.eventCount).isEqualTo(0)
    }

    @Test fun undoLastForSide() {
        val e = engine()
        e.addPoints(Side.HOME, 2); e.addPoints(Side.AWAY, 3)
        assertThat(e.undoLast(Side.HOME)).isTrue()
        assertThat(e.state.value.home).isEqualTo(0)
        assertThat(e.state.value.away).isEqualTo(3)
    }

    @Test fun manualSetScoreThenAddsContinueFromIt() {
        val e = engine()
        e.addPoints(Side.HOME, 2)
        e.setScore(Side.HOME, 40)
        e.addPoints(Side.HOME, 3)
        assertThat(e.state.value.home).isEqualTo(43)
        e.undo() // undo +3
        e.undo() // undo set -> back to 2
        assertThat(e.state.value.home).isEqualTo(2)
    }

    @Test fun scoreNeverNegative() {
        val e = engine()
        e.addPoints(Side.AWAY, -5)
        assertThat(e.state.value.away).isEqualTo(0)
    }

    @Test fun usesConfiguredTapPoints() {
        val e = engine()
        e.setTapPoints(3)
        e.addPoints(Side.HOME)
        assertThat(e.state.value.home).isEqualTo(3)
    }

    @Test fun volleyballSetsAndMatch() {
        val e = engine(Sport.VOLLEYBALL)
        repeat(25) { e.addPoints(Side.HOME, 1) }
        var s = e.state.value
        assertThat(s.sets).containsExactly(SetResult(25, 0))
        assertThat(s.home).isEqualTo(0)
        assertThat(s.period).isEqualTo(2)
        // Win-by-two: 25-24 does not close the set.
        repeat(24) { e.addPoints(Side.AWAY, 1) }
        repeat(24) { e.addPoints(Side.HOME, 1) }
        e.addPoints(Side.AWAY, 1)
        assertThat(e.state.value.sets).hasSize(1)
        e.addPoints(Side.AWAY, 1) // 24-26
        s = e.state.value
        assertThat(s.sets.last()).isEqualTo(SetResult(24, 26))
        assertThat(s.setsWon(Side.AWAY)).isEqualTo(1)
        repeat(25) { e.addPoints(Side.HOME, 1) }
        repeat(25) { e.addPoints(Side.HOME, 1) }
        s = e.state.value
        assertThat(s.finished).isTrue()
        assertThat(s.setsWon(Side.HOME)).isEqualTo(3)
        assertThat(s.leader).isEqualTo(Side.HOME)
        // Points after the match ended are ignored.
        e.addPoints(Side.AWAY, 1)
        assertThat(e.state.value.away).isEqualTo(0)
    }

    @Test fun undoReopensSet() {
        val e = engine(Sport.VOLLEYBALL)
        repeat(25) { e.addPoints(Side.HOME, 1) }
        e.undo()
        val s = e.state.value
        assertThat(s.sets).isEmpty()
        assertThat(s.home).isEqualTo(24)
        assertThat(s.period).isEqualTo(1)
    }

    @Test fun decidingSetUsesFinalTarget() {
        val e = engine(Sport.VOLLEYBALL)
        repeat(2) { repeat(25) { e.addPoints(Side.HOME, 1) }; repeat(25) { e.addPoints(Side.AWAY, 1) } }
        repeat(15) { e.addPoints(Side.AWAY, 1) }
        assertThat(e.state.value.finished).isTrue()
        assertThat(e.state.value.sets.last()).isEqualTo(SetResult(0, 15))
    }

    @Test fun clockStartPauseAccumulates() {
        val e = engine(Sport.SOCCER)
        e.startClock(); now += 5_000
        assertThat(e.state.value.displayClockMs(now)).isEqualTo(5_000)
        e.pauseClock(); now += 10_000
        assertThat(e.state.value.clock.elapsedMs(now)).isEqualTo(5_000)
        e.startClock(); now += 1_000
        assertThat(e.state.value.clock.elapsedMs(now)).isEqualTo(6_000)
        e.toggleClock()
        assertThat(e.state.value.clock.running).isFalse()
        e.resetClock()
        assertThat(e.state.value.clock.elapsedMs(now)).isEqualTo(0)
    }

    @Test fun basketballClockCountsDownAndPeriodsAdvance() {
        val e = engine(Sport.BASKETBALL)
        e.startClock(); now += 60_000
        assertThat(e.state.value.displayClockMs(now)).isEqualTo(9 * 60_000L)
        now += 10 * 60_000
        assertThat(e.state.value.displayClockMs(now)).isEqualTo(0)
        assertThat(e.state.value.periodExpired(now)).isTrue()
        e.nextPeriod(); e.nextPeriod(); e.nextPeriod()
        assertThat(e.state.value.period).isEqualTo(4)
        assertThat(e.state.value.clock.running).isFalse()
        e.nextPeriod()
        assertThat(e.state.value.finished).isTrue()
        e.undo()
        assertThat(e.state.value.finished).isFalse()
    }

    @Test fun teamUpdatesAndConfig() {
        val e = engine()
        e.renameTeam(Side.HOME, "  Lions  ")
        e.setTeamColor(Side.AWAY, 0xFF00FF00)
        assertThat(e.state.value.config.home.name).isEqualTo("Lions")
        assertThat(e.state.value.config.away.color).isEqualTo(0xFF00FF00)
        e.renameTeam(Side.AWAY, "x".repeat(100))
        assertThat(e.state.value.config.away.name).hasLength(TeamInfo.MAX_NAME)
    }

    @Test fun newMatchArchivesOnlyActiveMatches() {
        val archived = mutableListOf<MatchState>()
        val e = engine()
        e.onArchive = { archived += it }
        e.newMatch() // empty match: not archived
        assertThat(archived).isEmpty()
        e.addPoints(Side.HOME, 2)
        val config = e.state.value.config.copy(home = TeamInfo("Reds", 1))
        e.newMatch(config)
        assertThat(archived).hasSize(1)
        assertThat(archived[0].home).isEqualTo(2)
        assertThat(e.state.value.home).isEqualTo(0)
        assertThat(e.state.value.config.home.name).isEqualTo("Reds")
    }

    @Test fun persistsAndRestores() {
        val store = InMemorySessionStore()
        val e = ScoreEngine("phone", store, clock = { now })
        e.addPoints(Side.HOME, 3); e.renameTeam(Side.AWAY, "Blue")
        val restored = ScoreEngine("phone", store, clock = { now })
        assertThat(restored.state.value.home).isEqualTo(3)
        assertThat(restored.state.value.config.away.name).isEqualTo("Blue")
        restored.addPoints(Side.HOME, 1)
        // Sequence numbers continue after restore (no id reuse => no dedupe collisions).
        assertThat(restored.activeLog.events.values.map { it.id.seq }.sorted()).containsExactly(1L, 2L, 3L)
    }

    @Test fun corruptStoreFallsBackToFreshMatch() {
        val bad = object : SessionStore {
            override fun load(): SessionSnapshot = error("corrupt")
            override fun save(snapshot: SessionSnapshot) {}
        }
        val e = ScoreEngine("phone", bad)
        assertThat(e.state.value.home).isEqualTo(0)
    }

    @Test fun rulesValidation() {
        val r = runCatching { Rules(emptyList(), 0, 0, false) }
        assertThat(r.isFailure).isTrue()
    }

    @Test fun historyUpsertDedupes() {
        val e = engine(); e.addPoints(Side.HOME, 2)
        val a = MatchSummary.of(e.state.value, now)
        e.addPoints(Side.HOME, 2)
        val b = MatchSummary.of(e.state.value, now + 1)
        val h = HistoryMerge.upsert(HistoryMerge.upsert(emptyList(), a), b)
        assertThat(h).hasSize(1)
        assertThat(h[0].homeScore).isEqualTo(4)
        assertThat(h[0].winner).isEqualTo(Side.HOME)
    }
}
