package com.morse.master.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.SdkSuppress
import com.morse.master.MainActivity
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun showsActiveTrainingCharacters() {
        rule.onNodeWithText("K").assertIsDisplayed()
        rule.onNodeWithText("M").assertIsDisplayed()
    }

    @SdkSuppress(maxSdkVersion = 35)
    @Test
    fun keepsPracticeActionsPinnedWhenTrainingCharactersGrow() {
        val initialBottom = rule.onNodeWithText("Play Training Set")
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom

        repeat(4) {
            rule.onNodeWithText("Add Next Character", substring = true)
                .assertIsDisplayed()
                .performClick()
            rule.waitForIdle()
        }

        val updatedBottom = rule.onNodeWithText("Play Training Set")
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom

        assertTrue(
            "Expected Play Training Set button to stay pinned; initial=$initialBottom updated=$updatedBottom",
            abs(updatedBottom - initialBottom) <= 1f
        )
    }
}
