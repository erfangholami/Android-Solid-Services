package com.erfangholami.androidsolidservices.shared.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These strings are matched by the host's manifest and by the SDK's Intents; neither side is
 * compiler-checked against the other, so they are pinned here.
 */
class SolidHostContractTest {

    private val actions = listOf(
        SolidHostContract.ACTION_AUTHENTICATOR_SERVICE,
        SolidHostContract.ACTION_RESOURCE_SERVICE,
        SolidHostContract.ACTION_DATA_MODULES_SERVICE,
        SolidHostContract.ACTION_SHARING_SERVICE,
        SolidHostContract.ACTION_NOTIFICATIONS_SERVICE,
        SolidHostContract.ACTION_AUTHORIZE,
    )

    @Test
    fun `the host is Solid Share`() {
        assertEquals("com.erfangholami.solidshare", SolidHostContract.HOST_PACKAGE_NAME)
        assertEquals("com.erfangholami.solidshare", SolidHostContract.ACCOUNT_TYPE)
    }

    @Test
    fun `every action is namespaced to the SDK and distinct`() {
        actions.forEach { action ->
            assertTrue(action, action.startsWith("com.erfangholami.androidsolidservices.action."))
        }
        assertEquals(actions.size, actions.toSet().size)
    }
}
