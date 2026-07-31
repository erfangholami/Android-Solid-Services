package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Each client owns a bound-service connection, so a second instance means a second binding to the
 * ASS app that nothing will ever release. The double-checked locking that prevents it is
 * hand-written in six places.
 */
@RunWith(RobolectricTestRunner::class)
class SingletonIdentityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        resetAll()
    }

    @Test
    fun `every entry point returns the same instance on repeated calls`() {
        assertSame(Solid.getSignInClient(context), Solid.getSignInClient(context))
        assertSame(Solid.getResourceClient(context), Solid.getResourceClient(context))
        assertSame(Solid.getContactsDataModule(context), Solid.getContactsDataModule(context))
        assertSame(Solid.getTicketsDataModule(context), Solid.getTicketsDataModule(context))
        assertSame(Solid.getSharingClient(context), Solid.getSharingClient(context))
        assertSame(Solid.getNotificationsClient(context), Solid.getNotificationsClient(context))
    }

    @Test
    fun `a race between threads still yields one instance`() {
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks = List(threads) { Callable { Solid.getResourceClient(context) } }
            val instances = pool.invokeAll(tasks).map { it.get(10, TimeUnit.SECONDS) }

            val first = instances.first()
            instances.forEach { assertSame("concurrent getInstance produced a second binding", first, it) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `an application context does not produce a different instance than an activity context`() {
        // The clients call applicationContext internally, so the caller's context flavour must not
        // matter — a per-Activity instance would leak the Activity for the process lifetime.
        val fromApp = Solid.getSharingClient(context.applicationContext)
        val fromOther = Solid.getSharingClient(context)
        assertSame(fromApp, fromOther)
    }

    private fun resetAll() {
        SolidSignInClient.resetForTests()
        SolidResourceClient.resetForTests()
        SolidContactsDataModule.resetForTests()
        SolidTicketsDataModule.resetForTests()
        SolidSharingClient.resetForTests()
        SolidNotificationsClient.resetForTests()
    }
}
