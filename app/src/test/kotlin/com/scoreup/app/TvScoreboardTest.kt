package com.scoreup.app

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scoreup.app.data.ThemeMode
import com.scoreup.app.ui.theme.ScoreUpTheme
import com.scoreup.app.ui.tv.TvScoreboard
import com.scoreup.core.InMemorySessionStore
import com.scoreup.core.ScoreEngine
import com.scoreup.core.Side
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The TV board on a 16:9 screen, with real text measurement (native graphics). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land")
class TvScoreboardTest {
    @get:Rule val rule = createComposeRule()

    private fun show(home: Int, away: Int, heartRate: Int? = null) {
        val e = ScoreEngine("p", InMemorySessionStore())
        e.addPoints(Side.HOME, home); e.addPoints(Side.AWAY, away)
        rule.setContent { ScoreUpTheme(ThemeMode.DARK) { TvScoreboard(e.state.value, heartRate = heartRate) } }
        rule.waitForIdle()
    }

    @Test fun showsScoresTimeAndWatchHeartRate() {
        show(12, 7, heartRate = 131)
        rule.onNodeWithTag("tv_score_home", useUnmergedTree = true).assertTextEquals("12")
        rule.onNodeWithTag("tv_score_away", useUnmergedTree = true).assertTextEquals("7")
        rule.onNodeWithTag("tv_heart", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("131", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("tv_time", useUnmergedTree = true).assertExists()
    }

    @Test fun noHeartRateWithoutWatch() {
        show(1, 0)
        rule.onNodeWithTag("tv_heart", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun scoresFillTheirPanels() {
        show(88, 100)
        val board = rule.onNodeWithTag("tv_scoreboard").fetchSemanticsNode().size
        val home = rule.onNodeWithTag("tv_score_home", useUnmergedTree = true).fetchSemanticsNode().size
        val away = rule.onNodeWithTag("tv_score_away", useUnmergedTree = true).fetchSemanticsNode().size
        // Two-digit scores take well over half the screen height; three digits shrink to stay inside the panel.
        assertThat(home.height.toFloat()).isGreaterThan(board.height * 0.5f)
        assertThat(home.width.toFloat()).isLessThan(board.width * 0.5f)
        assertThat(away.width.toFloat()).isLessThan(board.width * 0.5f)
        assertThat(away.height).isLessThan(home.height)
    }
}
