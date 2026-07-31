package com.erfangholami.androidsolidservices.client.sdk

import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.access.SolidACLResource
import com.erfangholami.androidsolidservices.shared.model.access.SolidACR
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

/**
 * `SolidResourceClient` rebuilds resources over IPC with `Class.getConstructor(...)`, and
 * `Shared/consumer-rules.pro` keeps exactly those constructors. Neither side is checked by the
 * compiler, so a signature change silently breaks only minified builds — which is how the
 * `java.net.URI` to `java.lang.String` move went unnoticed until a release build failed.
 *
 * These tests pin both sides of that contract.
 */
class ReflectiveConstructionContractTest {

    private val rdfSignature = arrayOf(
        String::class.java,
        String::class.java,
        List::class.java,
        SolidHeaders::class.java,
    )

    private val nonRdfSignature = arrayOf(
        String::class.java,
        String::class.java,
        InputStream::class.java,
        SolidHeaders::class.java,
    )

    @Test
    fun `every RDF resource type exposes the constructor reconstructRdf looks up`() {
        listOf(
            SolidRDFResource::class.java,
            SolidContainer::class.java,
            WebId::class.java,
            SolidACLResource::class.java,
            SolidACR::class.java,
        ).forEach { type ->
            assertNotNull(
                "${type.name} is missing (String, String, List, SolidHeaders) — " +
                    "reconstructRdf would throw NoSuchMethodException over IPC",
                type.getConstructor(*rdfSignature),
            )
        }
    }

    @Test
    fun `non-RDF resources expose the constructor reconstructNonRdf looks up`() {
        assertNotNull(
            "SolidNonRDFResource is missing (String, String, InputStream, SolidHeaders)",
            SolidNonRDFResource::class.java.getConstructor(*nonRdfSignature),
        )
    }

    @Test
    fun `the identifier parameter is String, not URI`() {
        val first = SolidRDFResource::class.java.getConstructor(*rdfSignature).parameterTypes.first()
        assertTrue(
            "identifier parameter is ${first.name}, but the keep rule and call site expect String",
            first == String::class.java,
        )
    }

    @Test
    fun `the R8 keep rule mirrors the constructors the code looks up`() {
        val rules = consumerRules() ?: return
        val text = rules.readText().replace(Regex("\\s+"), " ")

        val rdfKeep = "<init>(java.lang.String, java.lang.String, java.util.List, " +
            "com.erfangholami.androidsolidservices.shared.http.SolidHeaders);"
        assertTrue(
            "Shared/consumer-rules.pro no longer keeps the RDF constructor reconstructRdf uses. " +
                "Minified consumers will fail with NoSuchMethodException.",
            text.contains(rdfKeep),
        )

        val nonRdfKeep = "<init>(java.lang.String, java.lang.String, java.io.InputStream, " +
            "com.erfangholami.androidsolidservices.shared.http.SolidHeaders);"
        assertTrue(
            "Shared/consumer-rules.pro no longer keeps the non-RDF constructor reconstructNonRdf uses.",
            text.contains(nonRdfKeep),
        )

        assertTrue(
            "Shared/consumer-rules.pro still names java.net.URI in a resource constructor; " +
                "the public API uses String IRIs, so that rule matches nothing.",
            !text.contains("<init>(java.net.URI"),
        )
    }

    /** Unit tests run with the module directory as working directory. */
    private fun consumerRules(): File? =
        listOf("../Shared/consumer-rules.pro", "Shared/consumer-rules.pro")
            .map(::File)
            .firstOrNull(File::exists)
}
