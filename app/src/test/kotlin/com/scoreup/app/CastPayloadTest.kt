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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateManifestTest {
    @Test fun parsesReleaseManifest() {
        val json = """{"tag_name":"v1.0.42","body":"notes","assets":[
            {"name":"ScoreUp-watch-1.0.42.apk","browser_download_url":"https://x/w.apk"},
            {"name":"ScoreUp-phone-1.0.42.apk","browser_download_url":"https://x/p.apk"}]}"""
        val r = com.scoreup.app.update.AppUpdater.parse(json)!!
        assertThat(r.versionCode).isEqualTo(1042)
        assertThat(r.versionName).isEqualTo("1.0.42")
        assertThat(r.apkUrl).isEqualTo("https://x/p.apk")
        assertThat(com.scoreup.app.update.AppUpdater.parse("""{"tag_name":"v1.0.1","assets":[]}""")).isNull()
        assertThat(com.scoreup.app.update.AppUpdater.parse("nope")).isNull()
    }
}
