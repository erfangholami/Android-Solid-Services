package com.erfangholami.androidsolidservices.shared.telemetry

/**
 * A timed unit of work reported to the installed [TelemetrySink].
 *
 * Obtain one from [Telemetry.startSpan] and always [stop] it — preferably from a `finally` block,
 * or by using [traced], which stops the span for you.
 *
 * Implementations must be safe to call from any thread and must never throw.
 */
public interface TelemetrySpan {
    /**
     * Attaches a low-cardinality label to this span, such as an outcome or a host name.
     *
     * Never pass user-identifying data (WebIDs, pod resource paths, tokens): sinks forward
     * attributes to third-party backends.
     */
    public fun putAttribute(
        name: String,
        value: String,
    )

    /** Attaches a numeric measurement to this span, such as a byte count or a retry count. */
    public fun putMetric(
        name: String,
        value: Long,
    )

    /** Ends the span and reports its duration. Calling this more than once has no effect. */
    public fun stop()
}

/**
 * A timed outbound HTTP request reported to the installed [TelemetrySink].
 *
 * Obtain one from [Telemetry.startNetworkSpan] and always [stop] it from a `finally` block, so
 * that transport failures are timed too.
 *
 * Implementations must be safe to call from any thread and must never throw.
 */
public interface TelemetryNetworkSpan {
    /** Records the HTTP status code of the response. */
    public fun setResponseCode(code: Int)

    /** Records the size of the request body in bytes. */
    public fun setRequestPayloadSize(bytes: Long)

    /** Records the size of the response body in bytes. */
    public fun setResponsePayloadSize(bytes: Long)

    /** Records the response `Content-Type`. */
    public fun setResponseContentType(contentType: String?)

    /** Attaches a low-cardinality label, subject to the same constraints as [TelemetrySpan.putAttribute]. */
    public fun putAttribute(
        name: String,
        value: String,
    )

    /** Ends the span and reports its duration. Calling this more than once has no effect. */
    public fun stop()
}

/**
 * Receives the performance and error signals emitted by Android Solid Services.
 *
 * The libraries are deliberately free of any monitoring dependency: they emit through [Telemetry],
 * and nothing is reported until a host application installs a sink. Implement this interface to
 * route those signals to your own backend — Firebase, Sentry, OpenTelemetry, or plain logs — then
 * call [Telemetry.install] from `Application.onCreate`.
 *
 * Implementations must be safe to call from any thread. They may throw: [Telemetry] isolates
 * failures so that a broken sink cannot break the caller.
 */
public interface TelemetrySink {
    /** Starts a custom span named [name]. */
    public fun startSpan(name: String): TelemetrySpan

    /**
     * Starts a span for an outbound HTTP request.
     *
     * @param url The request origin (`scheme://host[:port]`). Callers strip the path, query and
     *   fragment before reporting, because Solid pod URLs identify the user.
     * @param method The HTTP method, upper-case.
     */
    public fun startNetworkSpan(
        url: String,
        method: String,
    ): TelemetryNetworkSpan

    /** Reports a handled, non-fatal [throwable] together with contextual [attributes]. */
    public fun recordException(
        throwable: Throwable,
        attributes: Map<String, String>,
    )

    /** Records a breadcrumb to be attached to subsequent reports. */
    public fun log(message: String)

    /** Sets a key/value pair to be attached to subsequent reports. */
    public fun setKey(
        key: String,
        value: String,
    )
}

/**
 * The entry point for Android Solid Services telemetry.
 *
 * Every signal the SDK emits passes through here. Until a host application calls [install], all
 * calls are no-ops, so the libraries add no monitoring dependency and send nothing anywhere.
 *
 * ```kotlin
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Telemetry.install(MyTelemetrySink())
 *     }
 * }
 * ```
 *
 * All members are thread-safe, and no member ever throws: an exception raised by the installed
 * sink is swallowed rather than propagated to the calling SDK code.
 */
public object Telemetry {
    @Volatile
    private var sink: TelemetrySink = NoOpTelemetrySink

    /** Reports whether a sink other than the default no-op is installed. */
    public val isInstalled: Boolean
        get() = sink !== NoOpTelemetrySink

    /** Installs [sink] as the destination for all subsequent signals, replacing any previous one. */
    public fun install(sink: TelemetrySink) {
        this.sink = sink
    }

    /** Restores the default no-op sink, discarding signals again. */
    public fun uninstall() {
        sink = NoOpTelemetrySink
    }

    /**
     * Starts a custom span named [name].
     *
     * Returns a no-op span when no sink is installed, so callers never need a null check.
     */
    public fun startSpan(name: String): TelemetrySpan = try {
        sink.startSpan(name)
    } catch (_: Throwable) {
        NoOpTelemetrySpan
    }

    /**
     * Starts a span for an outbound HTTP request to [url] using [method].
     *
     * @param url The request origin only — see [TelemetrySink.startNetworkSpan].
     */
    public fun startNetworkSpan(
        url: String,
        method: String,
    ): TelemetryNetworkSpan = try {
        sink.startNetworkSpan(url, method)
    } catch (_: Throwable) {
        NoOpTelemetryNetworkSpan
    }

    /** Reports a handled, non-fatal [throwable] together with contextual [attributes]. */
    public fun recordException(
        throwable: Throwable,
        attributes: Map<String, String> = emptyMap(),
    ) {
        try {
            sink.recordException(throwable, attributes)
        } catch (_: Throwable) {
        }
    }

    /** Reports a handled, non-fatal [throwable] together with contextual [attributes]. */
    public fun recordException(
        throwable: Throwable,
        vararg attributes: Pair<String, String>,
    ) {
        recordException(throwable, attributes.toMap())
    }

    /** Records a breadcrumb to be attached to subsequent reports. */
    public fun log(message: String) {
        try {
            sink.log(message)
        } catch (_: Throwable) {
        }
    }

    /** Sets a key/value pair to be attached to subsequent reports. */
    public fun setKey(
        key: String,
        value: String,
    ) {
        try {
            sink.setKey(key, value)
        } catch (_: Throwable) {
        }
    }
}

/**
 * Runs [block] inside a span named [name], stopping the span even if [block] throws.
 *
 * A throwing [block] marks the span `outcome=error` and reports the throwable through
 * [Telemetry.recordException] before rethrowing it.
 */
public inline fun <T> traced(
    name: String,
    block: (TelemetrySpan) -> T,
): T {
    val span = Telemetry.startSpan(name)
    try {
        val result = block(span)
        span.putAttribute(TelemetryAttribute.OUTCOME, TelemetryAttribute.OUTCOME_SUCCESS)
        return result
    } catch (t: Throwable) {
        span.putAttribute(TelemetryAttribute.OUTCOME, TelemetryAttribute.OUTCOME_ERROR)
        Telemetry.recordException(t, TelemetryAttribute.SPAN to name)
        throw t
    } finally {
        span.stop()
    }
}

/** The attribute and metric names Android Solid Services reports on its own spans. */
public object TelemetryAttribute {
    /** Whether the observed operation succeeded: one of [OUTCOME_SUCCESS] or [OUTCOME_ERROR]. */
    public const val OUTCOME: String = "outcome"

    /** Value of [OUTCOME] for an operation that completed normally. */
    public const val OUTCOME_SUCCESS: String = "success"

    /** Value of [OUTCOME] for an operation that ended in a throwable. */
    public const val OUTCOME_ERROR: String = "error"

    /** The name of the span an exception was reported from. */
    public const val SPAN: String = "span"

    /** The origin (`scheme://host[:port]`) an HTTP request was sent to. */
    public const val HOST: String = "host"

    /** The HTTP method of the observed request. */
    public const val METHOD: String = "method"

    /** The logical SDK operation being performed, such as `resource.get`. */
    public const val OPERATION: String = "operation"

    /**
     * The package name of the application that requested the work over IPC, or `self` when it
     * originated in-process. Lets a report be traced back to the integrating app that triggered it.
     */
    public const val CALLING_APP: String = "calling_app"

    /** The simple class name of a reported throwable. */
    public const val ERROR_TYPE: String = "error_type"
}

private object NoOpTelemetrySink : TelemetrySink {
    override fun startSpan(name: String): TelemetrySpan = NoOpTelemetrySpan

    override fun startNetworkSpan(
        url: String,
        method: String,
    ): TelemetryNetworkSpan = NoOpTelemetryNetworkSpan

    override fun recordException(
        throwable: Throwable,
        attributes: Map<String, String>,
    ) = Unit

    override fun log(message: String) = Unit

    override fun setKey(
        key: String,
        value: String,
    ) = Unit
}

private object NoOpTelemetrySpan : TelemetrySpan {
    override fun putAttribute(
        name: String,
        value: String,
    ) = Unit

    override fun putMetric(
        name: String,
        value: Long,
    ) = Unit

    override fun stop() = Unit
}

private object NoOpTelemetryNetworkSpan : TelemetryNetworkSpan {
    override fun setResponseCode(code: Int) = Unit

    override fun setRequestPayloadSize(bytes: Long) = Unit

    override fun setResponsePayloadSize(bytes: Long) = Unit

    override fun setResponseContentType(contentType: String?) = Unit

    override fun putAttribute(
        name: String,
        value: String,
    ) = Unit

    override fun stop() = Unit
}
