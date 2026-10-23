package com.scoreup.wear

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scoreup.core.InMemorySessionStore
import com.scoreup.core.HeartStats
import com.scoreup.core.MatchConfig
import com.scoreup.core.MatchState
import com.scoreup.core.MatchSummary
import com.scoreup.core.ScoreEngine
import com.scoreup.core.Side
import com.scoreup.core.SyncMessage
import com.scoreup.shared.WearSync
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

    private fun launch(heart: HeartUi = HeartUi(), saved: List<MatchSummary> = emptyList(), page: Int = 0, onSave: () -> Unit = {}) {
        engine.outbound = { sent += it }
        rule.setContent {
            val s by engine.state.collectAsState()
            WearScoreApp(s, engine, Haptics(ApplicationProvider.getApplicationContext()), phoneConnected = true, onInteraction = { interactions++ },
                heart = heart, saved = saved, onSaveScore = onSave, initialPage = page)
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

    @Test fun restartButtonAsksThenSavesAndResets() {
        val archived = mutableListOf<MatchState>()
        engine.onArchive = { archived += it }
        launch()
        rule.onNodeWithTag("watch_home").performClick()
        rule.onNodeWithTag("watch_home").performClick()
        rule.onNodeWithTag("watch_restart").performClick()
        rule.onNodeWithTag("restart_confirm").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("watch_score_home", useUnmergedTree = true).assertTextEquals("0")
        assertThat(archived.single().home).isEqualTo(2)
    }

    @Test fun showsGameClockTimeOfDayAndHeartRate() {
        launch(heart = HeartUi(WearSync.HeartReading(128, System.currentTimeMillis()), available = true, enabled = true, permitted = true))
        rule.onNodeWithTag("watch_heart", useUnmergedTree = true).assertTextEquals("128")
        rule.onNodeWithTag("watch_game_clock", useUnmergedTree = true).assertTextEquals("00:00")
        rule.onNodeWithTag("watch_time", useUnmergedTree = true).assertExists()
    }

    @Test fun staleHeartRateIsHidden() {
        launch(heart = HeartUi(WearSync.HeartReading(128, System.currentTimeMillis() - 60_000), available = true, enabled = true, permitted = true))
        rule.onNodeWithTag("watch_heart", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun saveScoreFromControls() {
        var saves = 0
        engine.addPoints(Side.AWAY, 1)
        launch(page = 1, onSave = { saves++ })
        rule.onNodeWithTag("watch_save").performClick()
        rule.waitForIdle()
        assertThat(saves).isEqualTo(1)
    }

    @Test fun savedScoresPageListsResults() {
        engine.addPoints(Side.HOME, 21)
        val m = MatchSummary.of(engine.state.value, System.currentTimeMillis(), HeartStats(132, 171))
        launch(saved = listOf(m), page = 2)
        rule.onAllNodesWithTag("watch_saved_item").assertCountEquals(1)
        rule.onNodeWithText("21").assertExists()
        rule.onNodeWithText("avg 132 · max 171").assertExists()
    }
}
