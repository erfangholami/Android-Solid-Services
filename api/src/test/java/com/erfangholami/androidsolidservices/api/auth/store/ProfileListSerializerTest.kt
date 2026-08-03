package com.erfangholami.androidsolidservices.api.auth.store

import androidx.datastore.core.CorruptionException
import com.erfangholami.androidsolidservices.api.auth.store.UserRepositoryImplementation.Companion.ProfileListSerializer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProfileListSerializerTest {

    private lateinit var marker: File

    @Before
    fun setUp() {
        marker = File.createTempFile("profile_store_failures", null).apply { delete() }
        ProfileListSerializer.failureMarker = marker
    }

    @After
    fun tearDown() {
        marker.delete()
        ProfileListSerializer.failureMarker = null
    }

    @Test
    fun `empty file reads as an empty profile list`() = runBlocking {
        val result = ProfileListSerializer.readFrom(ByteArrayInputStream(ByteArray(0)))
        assertEquals(0, result.profiles.size)
    }

    @Test
    fun `legacy plaintext store still parses when decryption is unavailable`() = runBlocking {
        val plaintext = """{"profiles":{}}""".encodeToByteArray()
        val result = ProfileListSerializer.readFrom(ByteArrayInputStream(plaintext))
        assertEquals(0, result.profiles.size)
    }

    @Test
    fun `undecryptable bytes fail transiently before the strike limit and keep counting`() {
        val ciphertextLike = ByteArray(64) { (it * 7 + 5).toByte() }
        repeat(2) { attempt ->
            try {
                runBlocking { ProfileListSerializer.readFrom(ByteArrayInputStream(ciphertextLike)) }
                fail("read $attempt should have thrown")
            } catch (e: IOException) {
                assertTrue(
                    "strike ${attempt + 1} must not be a CorruptionException",
                    e !is CorruptionException,
                )
            }
        }
        assertEquals("2", marker.readText().trim())
    }

    @Test
    fun `undecryptable bytes concede corruption once the strike limit is reached`() {
        marker.writeText("2")
        val ciphertextLike = ByteArray(64) { (it * 7 + 5).toByte() }
        try {
            runBlocking { ProfileListSerializer.readFrom(ByteArrayInputStream(ciphertextLike)) }
            fail("third strike should have thrown")
        } catch (e: CorruptionException) {
            assertTrue(
                "the corruption reason names the strike count: ${e.message}",
                e.message.orEmpty().contains("3 reads"),
            )
            assertTrue("strike marker must reset after conceding", !marker.exists())
        }
    }
}
