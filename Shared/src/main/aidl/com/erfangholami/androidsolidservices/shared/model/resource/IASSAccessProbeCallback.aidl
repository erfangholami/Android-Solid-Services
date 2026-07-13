package com.erfangholami.androidsolidservices.shared.model.resource;

import com.erfangholami.androidsolidservices.shared.model.resource.AccessProbe;

/**
 * One-way callback delivering the outcome of an access probe.
 *
 * Note that an *indeterminate* probe (401 blip, 5xx, transport error) arrives on onError —
 * NOT as AccessProbe.Denied. A caller must not treat a failure as a denial.
 */
interface IASSAccessProbeCallback {
    oneway void onResult(in @nullable AccessProbe probe);
    oneway void onError(int errorCode, String errorMessage);
}
