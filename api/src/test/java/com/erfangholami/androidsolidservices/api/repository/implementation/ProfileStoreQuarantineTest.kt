package com.erfangholami.androidsolidservices.api.repository.implementation

import android.app.Application
import com.erfangholami.androidsolidservices.api.auth.ProfileList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProfileStoreQuarantineTest {

    @Test
    fun `an unreadable store is quarantined beside the original, never silently wiped`() {
        KeystoreCipher.installKeyForTest(KeyGenerator.getInstance("AES").generateKey())
        val context: Application = RuntimeEnvironment.getApplication()
        val storeDir = File(context.filesDir, "datastore").apply { mkdirs() }
        val original = File(storeDir, "profiles.json")
        val garbage = "{ this is not a profile list".toByteArray()
        original.writeBytes(garbage)

        val repository = UserRepositoryImplementation.getInstance(context)
        val profiles = runBlocking { repository.readAllProfiles().first() }

        assertEquals(
            "the app starts empty and asks for a fresh sign-in",
            ProfileList(),
            profiles,
        )
        val quarantined = storeDir.listFiles()
            .orEmpty()
            .filter { it.name.startsWith("profiles.json.quarantined.") }
        assertEquals(
            "exactly one quarantined copy: ${storeDir.listFiles()?.map { it.name }}",
            1,
            quarantined.size,
        )
        assertArrayEquals(
            "the unreadable bytes are preserved for later recovery",
            garbage,
            quarantined.single().readBytes(),
        )
        assertTrue(
            "the live store must have been replaced with a readable one",
            original.exists(),
        )
    }
}
