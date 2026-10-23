package com.scoreup.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeartTest {
    @Test fun averagesAndPeaksPerMatch() {
        val t = HeartTracker()
        listOf(100, 120, 141).forEach { t.record("a", it) }
        t.record("b", 90)
        assertThat(t.statsFor("a")).isEqualTo(HeartStats(avg = 120, max = 141))
        assertThat(t.statsFor("b")).isEqualTo(HeartStats(avg = 90, max = 90))
        assertThat(t.statsFor("never")).isNull()
    }

    @Test fun ignoresSensorNoise() {
        val t = HeartTracker()
        t.record("a", 0); t.record("a", 400)
        assertThat(t.statsFor("a")).isNull()
        t.record("a", 75)
        assertThat(t.statsFor("a")).isEqualTo(HeartStats(75, 75))
    }

    @Test fun keepsOnlyRecentMatches() {
        val t = HeartTracker(keepMatches = 2)
        t.record("a", 80); t.record("b", 90); t.record("c", 100)
        assertThat(t.statsFor("a")).isNull()
        assertThat(t.statsFor("b")).isNotNull()
        assertThat(t.statsFor("c")).isNotNull()
    }

    @Test fun savedScoreKeepsHeartStatsAndOldEntriesStillLoad() {
        val e = ScoreEngine("watch", InMemorySessionStore(), clock = { 1_000L })
        e.addPoints(Side.HOME, 2)
        val json = SyncCodec.json.encodeToString(MatchSummary.serializer(), MatchSummary.of(e.state.value, 5_000, HeartStats(128, 171)))
        assertThat(SyncCodec.json.decodeFromString(MatchSummary.serializer(), json).heart).isEqualTo(HeartStats(128, 171))
        // History written by earlier versions has no heart field.
        val old = json.replace(Regex(""","heart":\{[^}]*\}"""), "")
        assertThat(old).doesNotContain("heart")
        val restored = SyncCodec.json.decodeFromString(MatchSummary.serializer(), old)
        assertThat(restored.heart).isNull()
        assertThat(restored.homeScore).isEqualTo(2)
    }
}
