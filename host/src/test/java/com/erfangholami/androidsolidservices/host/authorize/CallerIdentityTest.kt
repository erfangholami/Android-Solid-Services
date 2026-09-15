package com.erfangholami.androidsolidservices.host.authorize

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class CallerIdentityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `an activity not launched for a result has no caller`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        assertNull(CallerIdentity.of(activity))
    }

    @Test
    fun `the calling package is named even when it is unknown to the package manager`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        shadowOf(activity).setCallingPackage("com.example.unknown")

        val caller = CallerIdentity.of(activity)!!

        assertEquals("com.example.unknown", caller.packageName)
        assertEquals("com.example.unknown", caller.label)
        assertNull(caller.icon)
    }

    @Test
    fun `an installed package contributes its label`() {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = "com.example.notes"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.example.notes"
                    name = "Notes"
                    nonLocalizedLabel = "Notes"
                }
            },
        )

        val caller = CallerIdentity.of(context.packageManager, "com.example.notes")

        assertEquals("com.example.notes", caller.packageName)
        assertEquals("Notes", caller.label)
    }
}
