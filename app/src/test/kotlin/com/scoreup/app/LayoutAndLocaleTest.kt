package com.scoreup.app

import android.content.Context
import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scoreup.shared.ScoreRuntime
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land")
class LandscapeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @After fun tearDown() { ScoreRuntime.resetForTests() }

    @Test fun landscapeShowsBothTeamsAndCenterControls() {
        rule.dismissWelcome()
        rule.onNodeWithTag("team_home").assertIsDisplayed()
        rule.onNodeWithTag("team_away").assertIsDisplayed()
        rule.onNodeWithTag("play").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
class TabletTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @After fun tearDown() { ScoreRuntime.resetForTests() }

    @Test fun tabletShowsRecentPlays() {
        rule.dismissWelcome()
        rule.onNodeWithText("Recent plays").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "iw-w411dp-h891dp-port")
class HebrewRtlTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @After fun tearDown() { ScoreRuntime.resetForTests() }

    @Test fun hebrewStringsAndRtl() {
        rule.dismissWelcome()
        rule.runOnUiThread { (rule.activity.application as ScoreUpApp).runtime.engine.newMatch(com.scoreup.core.MatchConfig.default()) }
        rule.waitForIdle()
        rule.onNodeWithText("בית").assertIsDisplayed()
        rule.onNodeWithText("חוץ").assertIsDisplayed()
        assertThat(rule.activity.window.decorView.layoutDirection).isEqualTo(View.LAYOUT_DIRECTION_RTL)
    }
}

/** The first-run welcome dialog would cover the board; tests dismiss it like a user would. */
internal fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.dismissWelcome() {
    waitForIdle()
    if (onAllNodesWithTag("welcome_go").fetchSemanticsNodes().isNotEmpty()) onNodeWithTag("welcome_go").performClick()
    waitForIdle()
}
