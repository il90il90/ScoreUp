package com.scoreup.shared

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.scoreup.core.ScoreEngine
import com.scoreup.core.Side
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileSessionStoreTest {
    private val dir = ApplicationProvider.getApplicationContext<Context>().filesDir

    @Test fun survivesProcessRestart() {
        val f = File(dir, "s1.json")
        val e1 = ScoreEngine("phone-1", FileSessionStore(f))
        e1.addPoints(Side.HOME, 3); e1.renameTeam(Side.AWAY, "Owls")
        val e2 = ScoreEngine("phone-1", FileSessionStore(f))
        assertThat(e2.state.value.home).isEqualTo(3)
        assertThat(e2.state.value.config.away.name).isEqualTo("Owls")
    }

    @Test fun corruptFileStartsFresh() {
        val f = File(dir, "s2.json").apply { writeText("{not json") }
        val e = ScoreEngine("phone-1", FileSessionStore(f))
        assertThat(e.state.value.home).isEqualTo(0)
        e.addPoints(Side.HOME, 1)
        assertThat(ScoreEngine("phone-1", FileSessionStore(f)).state.value.home).isEqualTo(1)
    }

    @Test fun deviceIdIsStable() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val a = DeviceIdentity.get(ctx, "phone")
        assertThat(DeviceIdentity.get(ctx, "phone")).isEqualTo(a)
        assertThat(a).startsWith("phone-")
    }
}
