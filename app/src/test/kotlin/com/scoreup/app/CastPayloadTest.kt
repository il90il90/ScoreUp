package com.scoreup.app

import com.google.common.truth.Truth.assertThat
import com.scoreup.app.cast.CastSender
import com.scoreup.core.InMemorySessionStore
import com.scoreup.core.MatchConfig
import com.scoreup.core.ScoreEngine
import com.scoreup.core.Side
import com.scoreup.core.Sport
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastPayloadTest {
    @Test fun payloadCarriesScoresColorsAndClock() {
        var now = 0L
        val e = ScoreEngine("p", InMemorySessionStore(), clock = { now }, defaultConfig = MatchConfig.default(Sport.BASKETBALL))
        e.addPoints(Side.HOME, 3); e.startClock(); now = 30_000
        val j = CastSender.toJson(e.state.value, "Lions" to "Tigers", now = now)
        assertThat(j.getJSONObject("home").getInt("score")).isEqualTo(3)
        assertThat(j.getJSONObject("home").getString("name")).isEqualTo("Lions")
        assertThat(j.getJSONObject("home").getString("color")).isEqualTo("#FF3D57")
        val clock = j.getJSONObject("clock")
        assertThat(clock.getBoolean("running")).isTrue()
        assertThat(clock.getBoolean("countsDown")).isTrue()
        assertThat(clock.getLong("displayMs")).isEqualTo(10 * 60_000L - 30_000L)
    }
}
