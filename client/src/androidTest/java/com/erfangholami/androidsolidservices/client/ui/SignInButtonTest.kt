package com.erfangholami.androidsolidservices.client.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one composable the SDK publishes. Third-party apps put it on their sign-in screen, so a
 * regression here is visible in every app that uses it.
 */
@RunWith(AndroidJUnit4::class)
class SignInButtonTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComposeHostActivity>()

    @Test
    fun renders_the_default_label_and_is_clickable() {
        compose.setContent { SignInButton(onClick = {}) }

        compose.onNodeWithText("Sign in with Solid").assertHasClickAction()
    }

    @Test
    fun a_tap_reaches_the_caller() {
        var clicks = 0
        compose.setContent { SignInButton(onClick = { clicks++ }) }

        compose.onNodeWithText("Sign in with Solid").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun a_disabled_button_swallows_the_tap() {
        var clicks = 0
        compose.setContent { SignInButton(onClick = { clicks++ }, enabled = false) }

        compose.onNodeWithText("Sign in with Solid").performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun a_custom_label_replaces_the_default() {
        compose.setContent { SignInButton(onClick = {}, text = "Continue with your pod") }

        compose.onNodeWithText("Continue with your pod").assertHasClickAction()
    }
}
