package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import com.erfangholami.androidsolidservices.client.internal.fakes.AlwaysSignedInSession
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.client.internal.fakes.InMemoryGrantStore
import com.erfangholami.androidsolidservices.client.internal.fakes.StubResourceManager
import com.erfangholami.androidsolidservices.client.internal.fakes.grantOf
import com.erfangholami.androidsolidservices.host.HostService
import com.erfangholami.androidsolidservices.host.access.AccessGuard
import com.erfangholami.androidsolidservices.host.access.ModuleRootResolver
import com.erfangholami.androidsolidservices.host.access.ScopedAccessPolicy
import com.erfangholami.androidsolidservices.host.binder.ResourceBinder
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import kotlinx.coroutines.runBlocking

/**
 * The **real** `ResourceBinder`, guarded by the **real** policy, in `:fakeass`.
 *
 * The other services in this package are hand-written fakes: they prove that what the SDK sends
 * arrives with the arguments it sent. They cannot prove what the host decides, because they have
 * no policy in them. This one carries the production binder and guard, so a refusal here is the
 * refusal Solid Share would give.
 *
 * The caller's package is read from the binder transaction, so the grant is seeded for whoever
 * binds. The level is chosen by the action the test binds with, which is how a single service
 * covers both the allowed and the refused case without cross-process configuration.
 */
class RealHostResourceService : HostService() {

    private val grants = InMemoryGrantStore()
    private val manager = StubResourceManager()

    override fun onBind(intent: Intent?): IBinder {
        val level = if (intent?.action == ACTION_VIEW_ONLY) AccessLevel.VIEW else AccessLevel.EDIT
        runBlocking {
            grants.revokeAll(Fixtures.WEB_ID)
            if (intent?.action != ACTION_NO_GRANT) {
                grants.put(grantOf(packageName, Fixtures.WEB_ID, level))
            }
        }
        manager.forget()
        return super.onBind(intent)
    }

    override fun createBinder(): IBinder = ResourceBinder(
        resourceManager = manager,
        session = AlwaysSignedInSession(Fixtures.WEB_ID),
        guard = AccessGuard(
            session = AlwaysSignedInSession(Fixtures.WEB_ID),
            policy = ScopedAccessPolicy(grants, ModuleRootResolver.NONE),
        ),
        scope = serviceScope,
        spoolDirectory = cacheDir,
    )

    companion object {
        const val ACTION_VIEW_ONLY: String = "com.erfangholami.androidsolidservices.client.test.GUARD_VIEW"
        const val ACTION_EDIT: String = "com.erfangholami.androidsolidservices.client.test.GUARD_EDIT"
        const val ACTION_NO_GRANT: String = "com.erfangholami.androidsolidservices.client.test.GUARD_NONE"
    }
}
