package com.scoreup.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun launches() { rule.onNodeWithText("ScoreUp").assertExists() }
}
