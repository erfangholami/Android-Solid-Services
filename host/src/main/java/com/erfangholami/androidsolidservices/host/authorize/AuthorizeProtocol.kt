package com.erfangholami.androidsolidservices.host.authorize

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import com.erfangholami.androidsolidservices.shared.model.grant.AccessRequest
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import com.erfangholami.androidsolidservices.shared.model.grant.GrantEntry
import com.erfangholami.androidsolidservices.shared.model.grant.GrantTarget
import com.erfangholami.androidsolidservices.shared.model.grant.RequestedTarget

/** What a consent activity finishes with: the result code and the data Intent, if any. */
public class AuthorizeResult(
    public val resultCode: Int,
    public val data: Intent?,
)

/**
 * The host's half of the [SolidAuthorization] Intent protocol.
 *
 * A consent activity reads the request with [requestFrom], lets the user decide, resolves what
 * they approved into entries with [entriesFor], and finishes with one of the three results. The
 * SDK's `AuthorizeWithSolid` contract is the other half.
 */
public object AuthorizeProtocol {

    /** The request the calling app sent, or [AccessRequest.DEFAULT] when it sent none. */
    public fun requestFrom(intent: Intent?): AccessRequest {
        intent ?: return AccessRequest.DEFAULT
        intent.setExtrasClassLoader(AccessRequest::class.java.classLoader)
        val request = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(SolidAuthorization.EXTRA_ACCESS_REQUEST, AccessRequest::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(SolidAuthorization.EXTRA_ACCESS_REQUEST)
            }
        }.getOrNull()
        return request ?: AccessRequest.DEFAULT
    }

    /**
     * The entries a request becomes once the account is known: the whole pod, each path
     * resolved against every storage root in [storageRoots], and each module. Paths that cannot
     * be placed are dropped, and duplicates collapse.
     */
    public fun entriesFor(request: AccessRequest, storageRoots: List<String>): List<GrantEntry> =
        request.targets
            .flatMap { target ->
                when (target) {
                    RequestedTarget.Pod -> listOf(GrantTarget.Pod)
                    is RequestedTarget.Path ->
                        storageRoots.mapNotNull { root -> target.resolveAgainst(root)?.let(GrantTarget::Resource) }

                    is RequestedTarget.Module -> listOf(GrantTarget.Module(target.id))
                }
            }
            .distinct()
            .map { GrantEntry(it, request.level) }

    /** `RESULT_OK` with the chosen WebID and the grant the user approved. */
    public fun grantedResult(webId: String, grant: AppGrant): AuthorizeResult = AuthorizeResult(
        Activity.RESULT_OK,
        Intent()
            .putExtra(SolidAuthorization.EXTRA_WEB_ID, webId)
            .putExtra(SolidAuthorization.EXTRA_GRANT, grant),
    )

    /** `RESULT_CANCELED`: the user dismissed without granting. */
    public fun cancelledResult(): AuthorizeResult = AuthorizeResult(Activity.RESULT_CANCELED, null)

    /** `RESULT_ERROR` with an `ExceptionsErrorCode` value and a message. */
    public fun errorResult(code: Int, message: String): AuthorizeResult = AuthorizeResult(
        SolidAuthorization.RESULT_ERROR,
        Intent()
            .putExtra(SolidAuthorization.EXTRA_ERROR_CODE, code)
            .putExtra(SolidAuthorization.EXTRA_ERROR_MESSAGE, message),
    )
}
