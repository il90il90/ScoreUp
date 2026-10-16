package com.scoreup.app

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import com.scoreup.core.MatchConfig
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Full-app E2E on a real device/emulator: scoring, rotation, persistence, TV mode. */
@RunWith(AndroidJUnit4::class)
class PhoneE2ETest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val app get() = rule.activity.application as ScoreUpApp

    @Before fun setUp() {
        rule.waitForIdle()
        if (rule.onAllNodesWithTag("welcome_go").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag("welcome_go").performClick()
        rule.runOnUiThread { app.runtime.engine.newMatch(MatchConfig.default()) }
        rule.waitForIdle()
    }

    @After fun tearDown() { device.setOrientationNatural() }

    @Test fun scoreSurvivesRotationAndRecreation() {
        rule.onNodeWithTag("team_home").performClick()
        rule.onNodeWithTag("team_away").performClick()
        rule.onNodeWithTag("team_away").performClick()
        device.setOrientationLeft()
        rule.waitForIdle()
        rule.onNodeWithTag("score_home").assertTextEquals("2")
        rule.onNodeWithTag("score_away").assertTextEquals("4")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag("score_away").assertTextEquals("4")
    }

    @Test fun rapidTapsCountExactly() {
        repeat(40) { rule.onNodeWithTag("team_home").performClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("score_home").assertTextEquals("80")
        assertThat(app.runtime.engine.activeLog.events).hasSize(40)
    }

    @Test fun tvModeMirrorsScore() {
        rule.onNodeWithTag("team_home").performClick()
        rule.onNodeWithTag("nav_tv").performClick()
        rule.onNodeWithTag("open_tv_mode").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tv_score_home").assertTextEquals("2")
    }
}
