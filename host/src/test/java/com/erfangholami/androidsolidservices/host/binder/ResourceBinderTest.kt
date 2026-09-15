package com.erfangholami.androidsolidservices.host.binder

import android.os.ParcelFileDescriptor
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.host.access.AccessGuard
import com.erfangholami.androidsolidservices.host.access.VerbTarget
import com.erfangholami.androidsolidservices.host.testing.ALICE
import com.erfangholami.androidsolidservices.host.testing.CALLER
import com.erfangholami.androidsolidservices.host.testing.CapturingCallback
import com.erfangholami.androidsolidservices.host.testing.CapturingListCallback
import com.erfangholami.androidsolidservices.host.testing.FakeHostSession
import com.erfangholami.androidsolidservices.host.testing.Outcome
import com.erfangholami.androidsolidservices.host.testing.Recorder
import com.erfangholami.androidsolidservices.host.testing.RecordingPolicy
import com.erfangholami.androidsolidservices.host.testing.VerbTable
import com.erfangholami.androidsolidservices.host.testing.recording
import com.erfangholami.androidsolidservices.host.testing.verb
import com.erfangholami.androidsolidservices.shared.ipc.parcelable
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Pins the resource half of the plan's verb table: every verb names its target and level to
 * the policy, reaches the manager only when allowed, and answers a denial with the policy's
 * own code. The manager is a recording proxy, so what is proven is the guard, not the pod.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ResourceBinderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val session = FakeHostSession()
    private val policy = RecordingPolicy()
    private val recorder = Recorder()
    private val scope = CoroutineScope(SupervisorJob())
    private val resourceManager = recording<SolidResourceManager>(
        recorder,
        overrides = mapOf("readStream" to SolidResult.Failure(SolidError.fromHttp(404, "gone"))),
    )
    private val guard = AccessGuard(session, policy) { CALLER }
    private val binder by lazy {
        ResourceBinder(resourceManager, session, guard, scope, folder.root, Dispatchers.Default)
    }

    private val file = "https://alice.pod/notes/a.txt"
    private val container = "https://alice.pod/notes/"
    private val other = "https://alice.pod/archive/a.txt"

    private fun read(uri: String) = VerbTarget.Resource(uri) to AccessLevel.VIEW
    private fun append(uri: String) = VerbTarget.Resource(uri) to AccessLevel.ADD
    private fun write(uri: String) = VerbTarget.Resource(uri) to AccessLevel.EDIT

    private fun nonRdf() = SolidNonRDFResource(file, "text/plain", ByteArrayInputStream(byteArrayOf()))
    private fun rdf() = SolidRDFResource("https://alice.pod/notes/a.ttl")

    private fun single(call: (CapturingCallback) -> Unit): Outcome = CapturingCallback().also(call).await()
    private fun list(call: (CapturingListCallback) -> Unit): Outcome = CapturingListCallback().also(call).await()

    private fun upload(): ParcelFileDescriptor =
        ParcelFileDescriptor.open(folder.newFile(), ParcelFileDescriptor.MODE_READ_ONLY)

    private val verbs = listOf(
        verb("getWebId", VerbTarget.AnyEntry to AccessLevel.VIEW, managerVerb = null) {
            single { binder.getWebId(ALICE, it) }
        },
        verb("head", read(file)) { single { binder.head(ALICE, file, it) } },
        verb("read", read(file)) { single { binder.read(ALICE, file, it) } },
        verb("readRdf", read(file), managerVerb = "read") { single { binder.readRdf(ALICE, file, it) } },
        verb("readContainer", read(container), managerVerb = "read") { single { binder.readContainer(ALICE, container, it) } },
        verb("exists", read(file)) { single { binder.exists(ALICE, file, it) } },
        verb("probeAccess", read(file)) { single { binder.probeAccess(ALICE, file, it) } },
        verb("listContainer", read(container)) { list { binder.listContainer(ALICE, container, false, it) } },
        verb("readStream", read(file), deliversResult = false) { single { binder.readStream(ALICE, file, it) } },
        verb("create", write(file)) { single { binder.create(ALICE, nonRdf(), it) } },
        verb("createRdf", write(rdf().getIdentifier()), managerVerb = "create") { single { binder.createRdf(ALICE, rdf(), it) } },
        verb("update", write(file)) { single { binder.update(ALICE, nonRdf(), null, it) } },
        verb("updateRdf", write(rdf().getIdentifier()), managerVerb = "update") { single { binder.updateRdf(ALICE, rdf(), null, it) } },
        verb("patch", write(file), managerVerb = "patchRaw") { single { binder.patch(ALICE, file, "", it) } },
        verb("delete", write(file)) { single { binder.delete(ALICE, nonRdf(), it) } },
        verb("deleteRdf", write(rdf().getIdentifier()), managerVerb = "delete") { single { binder.deleteRdf(ALICE, rdf(), it) } },
        verb("deleteContainer", write(container), managerVerb = "delete") { single { binder.deleteContainer(ALICE, container, it) } },
        verb("ensureContainer", write(container)) { single { binder.ensureContainer(ALICE, container, it) } },
        verb("putRaw", write(file)) { single { binder.putRaw(ALICE, file, "text/plain", byteArrayOf(), null, null, it) } },
        verb("writeStream", write(file)) { single { binder.writeStream(ALICE, file, "text/plain", -1, upload(), null, it) } },
        verb("rename", write(file)) { single { binder.rename(ALICE, file, "b.txt", it) } },
        verb("copy", read(file), write(other)) { single { binder.copy(ALICE, file, other, it) } },
        verb("move", write(file), write(other)) { single { binder.move(ALICE, file, other, it) } },
        verb("post", append(container)) { single { binder.post(ALICE, container, "text/plain", byteArrayOf(), null, it) } },
        verb("createInContainer", append(container)) { single { binder.createInContainer(ALICE, container, nonRdf(), it) } },
        verb("createInContainerRdf", append(container), managerVerb = "createInContainer") {
            single { binder.createInContainerRdf(ALICE, container, rdf(), it) }
        },
        verb("readPublic", managerVerb = "readPublic") { single { binder.readPublic(file, it) } },
        verb("readPublicRdf", managerVerb = "readPublic") { single { binder.readPublicRdf(file, it) } },
        verb("headPublic") { single { binder.headPublic(file, it) } },
    )

    private val table = VerbTable(policy, recorder, verbs)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `every verb asks for its target and level, then reaches the manager`() {
        session.profiles[ALICE] = WebId(ALICE, emptyList())

        table.assertEveryVerbIsGuardedAndReachesItsManager()
    }

    @Test
    fun `a denied verb answers with the policy's code and never reaches the manager`() {
        table.assertEveryDeniedVerbStopsBeforeItsManager()
    }

    @Test
    fun `the profile read answers with the account's WebID document`() {
        session.profiles[ALICE] = WebId(ALICE, emptyList())

        val outcome = single { binder.getWebId(ALICE, it) } as Outcome.Result

        assertEquals(ALICE, outcome.bundle.parcelable(WebId::class.java)?.getIdentifier())
    }

    @Test
    fun `the profile read of an account without a document is a typed error`() {
        val outcome = single { binder.getWebId(ALICE, it) }

        assertEquals(ExceptionsErrorCode.NULL_WEBID, (outcome as Outcome.Error).code)
    }

    @Test
    fun `an account with no session is refused before the policy is asked`() {
        session.sessions.clear()

        val outcome = single { binder.head(ALICE, file, it) }

        assertEquals(ExceptionsErrorCode.SOLID_NOT_LOGGED_IN, (outcome as Outcome.Error).code)
        assertTrue(policy.checks.isEmpty())
        assertTrue(recorder.calls.isEmpty())
    }

    @Test
    fun `a stream read the manager refuses reaches the caller as that refusal`() {
        val outcome = single { binder.readStream(ALICE, file, it) }

        assertEquals(404, (outcome as Outcome.Error).code)
        assertEquals(listOf("readStream"), recorder.calls)
    }
}
