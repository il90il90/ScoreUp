package com.scoreup.wear

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scoreup.shared.ScoreRuntime
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a Wear OS emulator: launches the real activity and scores with the big buttons. */
@RunWith(AndroidJUnit4::class)
class WearE2ETest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun fresh() { rule.runOnUiThread { ScoreRuntime.get().engine.resetMatch() }; rule.waitForIdle() }

    @Test fun scoreBothTeamsAndUndo() {
        rule.onNodeWithTag("watch_home").performClick()
        rule.onNodeWithTag("watch_away").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("watch_score_home").assertTextEquals("2")
        rule.onNodeWithTag("watch_score_away").assertTextEquals("2")
        rule.onNodeWithTag("watch_undo").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("watch_score_away").assertTextEquals("0")
    }
}
