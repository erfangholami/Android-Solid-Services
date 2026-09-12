package com.erfangholami.androidsolidservices.shared.rdf.jsonld

import com.apicatalog.jsonld.JsonLdError
import com.apicatalog.jsonld.JsonLdErrorCode
import com.apicatalog.jsonld.document.Document
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.http.media.MediaType
import com.apicatalog.jsonld.loader.DocumentLoader
import com.apicatalog.jsonld.loader.LRUDocumentCache
import com.apicatalog.jsonld.loader.SchemeRouter
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

internal object JsonLdContexts {
    private const val ACTIVITY_STREAMS = "https://www.w3.org/ns/activitystreams"
    private const val ACTIVITY_STREAMS_FILE = "activitystreams.jsonld"
    private const val REMOTE_CACHE_SIZE = 16

    private val bundled: Map<String, String> = mapOf(
        ACTIVITY_STREAMS to ACTIVITY_STREAMS_FILE,
        "$ACTIVITY_STREAMS.jsonld" to ACTIVITY_STREAMS_FILE,
        "http://www.w3.org/ns/activitystreams" to ACTIVITY_STREAMS_FILE,
    )

    private val documents = ConcurrentHashMap<String, Document>()

    val loader: DocumentLoader = loader(LRUDocumentCache(SchemeRouter.defaultInstance(), REMOTE_CACHE_SIZE))

    fun loader(remote: DocumentLoader): DocumentLoader = DocumentLoader { url, options ->
        bundledDocument(url) ?: remote.loadDocument(url, options)
    }

    fun bundledDocument(url: URI): Document? {
        val key = url.toString().substringBefore('#').substringBefore('?')
        val file = bundled[key] ?: return null
        return documents.getOrPut(file) {
            val stream = JsonLdContexts::class.java.getResourceAsStream(file)
                ?: throw JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, "Bundled JSON-LD context $file is missing.")
            stream.use { JsonDocument.of(MediaType.JSON_LD, it) }.apply { documentUrl = URI.create(key) }
        }
    }
}
