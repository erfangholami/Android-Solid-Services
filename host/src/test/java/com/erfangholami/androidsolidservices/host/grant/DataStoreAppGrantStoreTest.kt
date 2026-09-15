package com.erfangholami.androidsolidservices.host.grant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.erfangholami.androidsolidservices.host.testing.ALICE
import com.erfangholami.androidsolidservices.host.testing.CALLER
import com.erfangholami.androidsolidservices.host.testing.entry
import com.erfangholami.androidsolidservices.host.testing.grant
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.grant.GrantTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DataStoreAppGrantStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val bob = "https://bob.pod/profile/card#me"
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreAppGrantStore

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(folder.root, "grants.preferences_pb")
        }
        store = DataStoreAppGrantStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a stored grant reads back whole`() = runBlocking {
        val stored = grant(
            entry(GrantTarget.Pod, AccessLevel.VIEW),
            entry(GrantTarget.Resource("https://alice.pod/notes/"), AccessLevel.EDIT),
            entry(GrantTarget.Module("contacts"), AccessLevel.FULL),
        )

        store.put(stored)

        assertEquals(stored, store.get(CALLER, ALICE))
        assertEquals(listOf(stored), store.grants().first())
    }

    @Test
    fun `putting a grant for the same app and account replaces the earlier one`() = runBlocking {
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW)))
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.FULL)))

        assertEquals(1, store.grants().first().size)
        assertEquals(AccessLevel.FULL, store.get(CALLER, ALICE)?.podLevel())
    }

    @Test
    fun `revoking removes one pair and leaves the others`() = runBlocking {
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW)))
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW), webId = bob))
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW), packageName = "com.other.app"))

        store.revoke(CALLER, ALICE)

        assertNull(store.get(CALLER, ALICE))
        assertEquals(2, store.grants().first().size)
    }

    @Test
    fun `revoking an account drops every grant of that account`() = runBlocking {
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW)))
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW), packageName = "com.other.app"))
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW), webId = bob))

        store.revokeAll(ALICE)

        assertEquals(listOf(bob), store.grants().first().map { it.webId })
    }

    @Test
    fun `revoking a package drops every grant of that package`() = runBlocking {
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW)))
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW), webId = bob))
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW), packageName = "com.other.app"))

        store.revokePackage(CALLER)

        assertEquals(listOf("com.other.app"), store.grants().first().map { it.packageName })
    }

    @Test
    fun `grantsFor narrows the live list to one account`() = runBlocking {
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW)))
        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW), webId = bob))

        assertEquals(listOf(bob), store.grantsFor(bob).first().map { it.webId })
    }

    @Test
    fun `a value that no longer parses reads as no grants and can be written over`() = runBlocking {
        dataStore.edit { it[DataStoreAppGrantStore.KEY] = "{not json" }

        assertTrue(store.grants().first().isEmpty())
        assertNull(store.get(CALLER, ALICE))

        store.put(grant(entry(GrantTarget.Pod, AccessLevel.VIEW)))

        assertEquals(1, store.grants().first().size)
    }
}
