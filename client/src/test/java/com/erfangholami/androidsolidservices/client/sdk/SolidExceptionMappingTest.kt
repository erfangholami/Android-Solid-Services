package com.erfangholami.androidsolidservices.client.sdk

import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The IPC boundary carries only an `Int` and a message, so this mapping is the sole thing standing
 * between a service-side failure and the typed exception an SDK caller catches. An unmapped code
 * silently degrades to a generic error.
 */
class SolidExceptionMappingTest {

    private fun map(code: Int) = handleSolidException(code, "boom")

    @Test
    fun `each error code maps to its own exception type`() {
        val expected = mapOf(
            ExceptionsErrorCode.DRAW_OVERLAY_NOT_PERMITTED to
                SolidException.SolidServicesDrawPermissionDeniedException::class.java,
            ExceptionsErrorCode.SOLID_NOT_LOGGED_IN to
                SolidException.SolidNotLoggedInException::class.java,
            ExceptionsErrorCode.NOT_SUPPORTED_CLASS to
                SolidException.SolidResourceException.NotSupportedClassException::class.java,
            ExceptionsErrorCode.NOT_PERMISSION to
                SolidException.SolidResourceException.NotPermissionException::class.java,
            ExceptionsErrorCode.NULL_WEBID to
                SolidException.SolidResourceException.NullWebIdException::class.java,
            ExceptionsErrorCode.UNKNOWN to
                SolidException.SolidResourceException.UnknownException::class.java,
            ExceptionsErrorCode.ACCESS_DENIED to
                SolidException.SolidSharingException.AccessDeniedException::class.java,
            ExceptionsErrorCode.NO_INBOX to
                SolidException.SolidSharingException.NoInboxException::class.java,
            ExceptionsErrorCode.INBOX_UNAUTHORIZED to
                SolidException.SolidSharingException.InboxUnauthorizedException::class.java,
            ExceptionsErrorCode.INBOX_FORBIDDEN to
                SolidException.SolidSharingException.InboxForbiddenException::class.java,
            ExceptionsErrorCode.NOTIFICATION_DELIVERY_FAILED to
                SolidException.SolidSharingException.NotificationDeliveryException::class.java,
            ExceptionsErrorCode.IMPERSONATION_DETECTED to
                SolidException.SolidSharingException.ImpersonationDetectedException::class.java,
            ExceptionsErrorCode.STALE_ACL to
                SolidException.SolidSharingException.StaleAclException::class.java,
            ExceptionsErrorCode.UNSUPPORTED_AUTH_BACKEND to
                SolidException.SolidSharingException.UnsupportedAuthBackendException::class.java,
        )

        expected.forEach { (code, type) ->
            assertEquals("error code $code mapped to the wrong type", type, map(code).javaClass)
        }
    }

    @Test
    fun `no two codes collapse onto the same type`() {
        val codes = listOf(
            ExceptionsErrorCode.DRAW_OVERLAY_NOT_PERMITTED,
            ExceptionsErrorCode.SOLID_NOT_LOGGED_IN,
            ExceptionsErrorCode.NOT_SUPPORTED_CLASS,
            ExceptionsErrorCode.NOT_PERMISSION,
            ExceptionsErrorCode.NULL_WEBID,
            ExceptionsErrorCode.ACCESS_DENIED,
            ExceptionsErrorCode.NO_INBOX,
            ExceptionsErrorCode.INBOX_UNAUTHORIZED,
            ExceptionsErrorCode.INBOX_FORBIDDEN,
            ExceptionsErrorCode.NOTIFICATION_DELIVERY_FAILED,
            ExceptionsErrorCode.IMPERSONATION_DETECTED,
            ExceptionsErrorCode.STALE_ACL,
            ExceptionsErrorCode.UNSUPPORTED_AUTH_BACKEND,
        )
        assertEquals(
            "distinct error codes must not share an exception type",
            codes.size,
            codes.map { map(it).javaClass }.toSet().size,
        )
    }

    @Test
    fun `the service message survives the boundary`() {
        assertEquals("boom", map(ExceptionsErrorCode.ACCESS_DENIED).message)
    }

    @Test
    fun `an unrecognised code still yields a SolidException rather than crashing`() {
        val mapped = handleSolidException(9_999, "from the future")
        assertTrue("unknown codes must not escape the sealed hierarchy", mapped is SolidException)
    }
}
