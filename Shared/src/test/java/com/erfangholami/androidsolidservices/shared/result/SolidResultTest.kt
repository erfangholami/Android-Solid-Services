package com.erfangholami.androidsolidservices.shared.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SolidResultTest {

    private val ok: SolidResult<Int> = SolidResult.Success(42)
    private val bad: SolidResult<Int> = SolidResult.Failure(SolidError.Forbidden())

    @Test
    fun `success accessors`() {
        assertTrue(ok.isSuccess)
        assertEquals(42, ok.getOrNull())
        assertNull(ok.errorOrNull())
        assertEquals(42, ok.getOrThrow())
        assertEquals(42, ok.getOrDefault(0))
    }

    @Test
    fun `failure accessors`() {
        assertTrue(bad.isFailure)
        assertNull(bad.getOrNull())
        assertEquals(SolidErrorCode.FORBIDDEN, bad.errorOrNull()!!.code)
        assertEquals(0, bad.getOrDefault(0))
        assertEquals(-1, bad.getOrElse { -1 })
    }

    @Test
    fun `getOrThrow on a failure throws a SolidResultException carrying the error`() {
        val ex = assertThrows(SolidResultException::class.java) { bad.getOrThrow() }
        assertEquals(SolidErrorCode.FORBIDDEN, ex.error.code)
    }

    @Test
    fun `map transforms success and passes failure through`() {
        assertEquals(SolidResult.Success(84), ok.map { it * 2 })
        assertSame(bad, bad.map { it * 2 })
    }

    @Test
    fun `flatMap chains success and short-circuits failure`() {
        assertEquals(SolidResult.Success("42"), ok.flatMap { SolidResult.Success(it.toString()) })
        assertSame(bad, bad.flatMap { SolidResult.Success(it.toString()) })
    }

    @Test
    fun `fold collapses both variants`() {
        assertEquals("v=42", ok.fold({ "v=$it" }, { "e=${it.code}" }))
        assertEquals("e=FORBIDDEN", bad.fold({ "v=$it" }, { "e=${it.code}" }))
    }

    @Test
    fun `recover rescues a failure`() {
        assertEquals(7, bad.recover { 7 }.getOrNull())
        assertEquals(42, ok.recover { 7 }.getOrNull())
    }

    @Test
    fun `onSuccess and onFailure fire on the matching branch only`() {
        var successes = 0
        var failures = 0
        ok.onSuccess { successes++ }.onFailure { failures++ }
        bad.onSuccess { successes++ }.onFailure { failures++ }
        assertEquals(1, successes)
        assertEquals(1, failures)
    }

    @Test
    fun `solidCatching wraps a value and maps a thrown exception`() {
        assertEquals(SolidResult.Success(5), solidCatching { 5 })
        val failure = solidCatching { throw IOException("down") }
        assertEquals(SolidErrorCode.NETWORK, failure.errorOrNull()!!.code)
    }

    @Test
    fun `solidCatching maps a JDK cancellation to Cancelled`() {
        assertEquals(
            SolidError.Cancelled,
            solidCatching { throw java.util.concurrent.CancellationException() }.errorOrNull(),
        )
    }
}
