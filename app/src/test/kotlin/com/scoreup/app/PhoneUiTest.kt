package com.scoreup.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scoreup.shared.ScoreRuntime
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Phone UI flows on the JVM (Robolectric + Compose test). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port")
class PhoneUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as ScoreUpApp

    @Before fun freshMatch() {
        rule.dismissWelcome()
        rule.runOnUiThread { app.runtime.engine.newMatch(com.scoreup.core.MatchConfig.default()) }
        rule.waitForIdle()
    }
    @After fun tearDown() { ScoreRuntime.resetForTests() }

    private fun score(side: String) = rule.onNodeWithTag("score_$side", useUnmergedTree = true)

    @Test fun tapAddsDefaultBasketballTwoPoints() {
        rule.onNodeWithTag("team_home").performClick()
        rule.waitForIdle()
        score("home").assertTextEquals("2")
        score("away").assertTextEquals("0")
    }

    @Test fun pointsPerTapAndUndo() {
        rule.onNodeWithTag("tap_3").performClick()
        rule.onNodeWithTag("team_away").performClick()
        rule.onNodeWithTag("team_away").performClick()
        rule.waitForIdle()
        score("away").assertTextEquals("6")
        rule.onNodeWithTag("undo").performClick()
        rule.waitForIdle()
        score("away").assertTextEquals("3")
        assertThat(app.runtime.engine.state.value.config.tapPoints).isEqualTo(3)
    }

    @Test fun longPressEditsScore() {
        rule.onNodeWithTag("team_home").performTouchInput { longClick() }
        rule.onNodeWithTag("number_input").performTextReplacement("42")
        rule.onNodeWithTag("number_save").performClick()
        rule.waitForIdle()
        score("home").assertTextEquals("42")
    }

    @Test fun resetSavesHistory() {
        rule.onNodeWithTag("team_home").performClick()
        rule.onNodeWithTag("reset").performClick()
        rule.onNodeWithTag("confirm_reset").performClick()
        rule.waitForIdle()
        score("home").assertTextEquals("0")
        assertThat(app.history.items.value.first().homeScore).isEqualTo(2)
        rule.onNodeWithTag("menu").performClick()
        rule.onNodeWithTag("menu_history").performClick()
        rule.onAllNodesWithTag("history_item")[0].assertIsDisplayed()
    }

    @Test fun setupStartsVolleyballWithCustomTeam() {
        rule.onNodeWithTag("menu").performClick()
        rule.onNodeWithTag("menu_setup").performClick()
        rule.onNodeWithTag("sport_volleyball").performClick()
        rule.onNodeWithTag("team_name_home").performScrollTo().performTextReplacement("Sharks")
        rule.onNodeWithTag("start_match").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("name_home", useUnmergedTree = true).assertTextEquals("SHARKS")
        val s = app.runtime.engine.state.value
        assertThat(s.config.rules.setBased).isTrue()
        assertThat(s.config.tapPoints).isEqualTo(1)
        rule.onNodeWithTag("period", useUnmergedTree = true).assertTextEquals("Set 1")
    }

    @Test fun clockStartsAndPauses() {
        rule.onNodeWithTag("play").performClick()
        rule.waitForIdle()
        assertThat(app.runtime.engine.state.value.clock.running).isTrue()
        rule.onNodeWithTag("clock").performClick()
        rule.waitForIdle()
        assertThat(app.runtime.engine.state.value.clock.running).isFalse()
    }

    @Test fun finishShowsWinner() {
        rule.onNodeWithTag("team_away").performClick()
        rule.onNodeWithTag("menu").performClick()
        rule.onNodeWithTag("menu_finish").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("winner_banner").assertIsDisplayed()
        rule.onNodeWithText("AWAY wins!").assertIsDisplayed()
    }

    @Test fun tvModeShowsLiveScore() {
        rule.onNodeWithTag("team_home").performClick()
        rule.onNodeWithTag("nav_tv").performClick()
        rule.onNodeWithTag("open_tv_mode").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tv_score_home", useUnmergedTree = true).assertTextEquals("2")
        rule.runOnUiThread { app.runtime.engine.addPoints(com.scoreup.core.Side.HOME, 3) }
        rule.waitForIdle()
        rule.onNodeWithTag("tv_score_home", useUnmergedTree = true).assertTextEquals("5")
    }

    @Test fun watchInstallerScreenOpens() {
        rule.onNodeWithTag("nav_watch").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("watch_status").assertIsDisplayed()
        // Guided wizard: two "I did it" steps lead to the pairing-code step.
        rule.onNodeWithTag("wiz_step_1").assertIsDisplayed()
        rule.onNodeWithTag("wiz_next_1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("wiz_next_2").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("wiz_step_3").assertIsDisplayed()
        rule.onNodeWithTag("pair_code").assertIsDisplayed()
    }
}
