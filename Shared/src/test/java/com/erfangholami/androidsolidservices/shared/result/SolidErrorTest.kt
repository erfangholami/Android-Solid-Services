package com.erfangholami.androidsolidservices.shared.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class SolidErrorTest {

    @Test
    fun `fromHttp maps each status to its typed error, code, and http status`() {
        assertEquals(SolidErrorCode.UNAUTHORIZED, SolidError.fromHttp(401).code)
        assertEquals(SolidErrorCode.FORBIDDEN, SolidError.fromHttp(403).code)
        assertEquals(SolidErrorCode.NOT_FOUND, SolidError.fromHttp(404).code)
        assertEquals(SolidErrorCode.METHOD_NOT_ALLOWED, SolidError.fromHttp(405).code)
        assertEquals(SolidErrorCode.CONFLICT, SolidError.fromHttp(409).code)
        assertEquals(SolidErrorCode.PRECONDITION_FAILED, SolidError.fromHttp(412).code)
        assertEquals(SolidErrorCode.RATE_LIMITED, SolidError.fromHttp(429).code)
        assertEquals(SolidErrorCode.SERVER_ERROR, SolidError.fromHttp(503).code)
        assertEquals(SolidErrorCode.UNEXPECTED_RESPONSE, SolidError.fromHttp(418).code)

        assertEquals(412, SolidError.fromHttp(412).httpStatus)
        assertEquals(503, SolidError.fromHttp(503).httpStatus)
    }

    @Test
    fun `410 Gone maps to NotFound preserving the status`() {
        val error = SolidError.fromHttp(410)
        assertEquals(SolidErrorCode.NOT_FOUND, error.code)
        assertEquals(410, error.httpStatus)
    }

    @Test
    fun `only transient statuses are retryable`() {
        assertTrue(SolidError.fromHttp(429).retryable)
        assertTrue(SolidError.fromHttp(500).retryable)
        assertTrue(SolidError.fromHttp(502).retryable)
        assertFalse(SolidError.fromHttp(403).retryable)
        assertFalse(SolidError.fromHttp(404).retryable)
        assertFalse(SolidError.fromHttp(412).retryable)
        assertFalse(SolidError.fromHttp(418).retryable)
    }

    @Test
    fun `fromHttp keeps a server-provided detail message`() {
        assertEquals("quota exceeded", SolidError.fromHttp(403, "quota exceeded").message)
    }

    @Test
    fun `fromThrowable classifies transport failures`() {
        assertEquals(SolidErrorCode.TIMEOUT, SolidError.fromThrowable(SocketTimeoutException()).code)
        assertEquals(SolidErrorCode.TLS, SolidError.fromThrowable(SSLException("bad cert")).code)
        assertEquals(SolidErrorCode.NETWORK, SolidError.fromThrowable(UnknownHostException()).code)
        assertEquals(SolidErrorCode.NETWORK, SolidError.fromThrowable(IOException()).code)
        assertEquals(SolidErrorCode.UNKNOWN, SolidError.fromThrowable(IllegalStateException("x")).code)
    }

    @Test
    fun `a JDK CancellationException maps to Cancelled`() {
        assertSame(
            SolidError.Cancelled,
            SolidError.fromThrowable(java.util.concurrent.CancellationException()),
        )
    }

    @Test
    fun `timeout and network are retryable, tls is not`() {
        assertTrue(SolidError.fromThrowable(SocketTimeoutException()).retryable)
        assertTrue(SolidError.fromThrowable(IOException()).retryable)
        assertFalse(SolidError.fromThrowable(SSLException("x")).retryable)
    }

    @Test
    fun `asException carries the error and preserves the cause`() {
        val cause = IOException("boom")
        val error = SolidError.Network(cause)
        val ex = error.asException()
        assertSame(error, ex.error)
        assertSame(cause, ex.cause)
    }
}
