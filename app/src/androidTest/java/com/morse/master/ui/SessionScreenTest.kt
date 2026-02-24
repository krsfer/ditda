package com.morse.master.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.filters.SdkSuppress
import com.morse.master.MainActivity
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
}
