package com.scoreup.wear

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scoreup.core.InMemorySessionStore
import com.scoreup.core.MatchConfig
import com.scoreup.core.ScoreEngine
import com.scoreup.core.SyncMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Watch scoring UI on a round 454px (~227dp) screen. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w227dp-h227dp-round-watch")
class WearUiTest {
    @get:Rule val rule = createComposeRule()
    private val engine = ScoreEngine("watch-t", InMemorySessionStore(), defaultConfig = MatchConfig.default())
    private val sent = mutableListOf<SyncMessage>()
    private var interactions = 0

    private fun launch() {
        engine.outbound = { sent += it }
        rule.setContent {
            val s by engine.state.collectAsState()
            WearScoreApp(s, engine, Haptics(ApplicationProvider.getApplicationContext()), phoneConnected = true, onInteraction = { interactions++ })
        }
    }

    @Test fun bigButtonsScoreAndBroadcast() {
        launch()
        rule.onNodeWithTag("watch_home").performClick()
        rule.onNodeWithTag("watch_away").performClick()
        rule.onNodeWithTag("watch_away").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("watch_score_home", useUnmergedTree = true).assertTextEquals("1")
        rule.onNodeWithTag("watch_score_away", useUnmergedTree = true).assertTextEquals("2")
        assertThat(sent.filterIsInstance<SyncMessage.Events>()).hasSize(3)
        assertThat(interactions).isAtLeast(3)
    }

    @Test fun longPressUndoesThatTeamAndUndoButtonWorks() {
        launch()
        rule.onNodeWithTag("watch_home").performClick()
        rule.onNodeWithTag("watch_away").performClick()
        rule.onNodeWithTag("watch_home").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("watch_score_home", useUnmergedTree = true).assertTextEquals("0")
        rule.onNodeWithTag("watch_score_away", useUnmergedTree = true).assertTextEquals("1")
        rule.onNodeWithTag("watch_undo").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("watch_score_away", useUnmergedTree = true).assertTextEquals("0")
    }

    @Test fun clockToggles() {
        launch()
        rule.onNodeWithTag("watch_clock").performClick()
        rule.waitForIdle()
        assertThat(engine.state.value.clock.running).isTrue()
    }

    @Test fun rapidTapsAreAllCountedExactlyOnce() {
        launch()
        repeat(25) { rule.onNodeWithTag("watch_home").performClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("watch_score_home", useUnmergedTree = true).assertTextEquals("25")
        assertThat(engine.activeLog.events).hasSize(25)
    }
}
