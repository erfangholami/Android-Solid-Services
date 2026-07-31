package com.erfangholami.androidsolidservices.client.ui

import androidx.activity.ComponentActivity

/**
 * The activity the Compose tests render into.
 *
 * `createComposeRule()` launches androidx's stock `ComponentActivity`, which will not come up on a
 * dozing or locked device — the suite then fails with "no compose hierarchies found", and passes
 * again only because someone happened to touch the phone. Declaring our own host lets the manifest
 * carry `android:turnScreenOn` and `android:showWhenLocked`, so the result no longer depends on the
 * state the device was left in.
 */
class ComposeHostActivity : ComponentActivity()
