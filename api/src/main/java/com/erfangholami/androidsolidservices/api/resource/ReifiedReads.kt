package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import java.net.URI

/**
 * Reified conveniences for the `Class<T>`-token reads on [SolidResourceManager], so callers
 * write `rm.read<WebId>(webId, uri)` instead of `rm.read(webId, uri, WebId::class.java)`.
 * They forward verbatim to the token overloads — same behaviour, no Java-style class literal.
 */

/** Reified [SolidResourceManager.read]. */
public suspend inline fun <reified T : Resource> SolidResourceManager.read(
    webId: String,
    uri: URI,
): SolidResult<T> = read(webId, uri, T::class.java)

/** Reified [SolidResourceManager.readPublic]. */
public suspend inline fun <reified T : Resource> SolidResourceManager.readPublic(
    uri: URI,
): SolidResult<T> = readPublic(uri, T::class.java)
