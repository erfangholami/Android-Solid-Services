package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.ACP
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class InruptAcrJsonTest {

    private val base = URI.create("https://storage.inrupt.com/alice/photo.acr")

    private val inruptAcr = """
        {
          "@context": ["https://authorization.inrupt.com/authorization/v1"],
          "id": "https://storage.inrupt.com/alice/photo.acr",
          "type": "AccessControlResource",
          "resource": "https://storage.inrupt.com/alice/photo",
          "accessControl": [
            { "id": "#ac1", "type": "AccessControl", "apply": [
              { "id": "#policy1", "type": "Policy",
                "allow": ["http://www.w3.org/ns/auth/acl#Read"],
                "allOf": [ { "id": "#matcher1", "type": "Matcher",
                  "agent": ["https://bob.pod/profile/card#me"] } ] } ] }
          ]
        }
    """.trimIndent()

    private fun List<RdfQuad>.has(s: String, p: String, o: String) =
        any { it.subject == s && it.predicate == p && it.`object` == o }

    @Test
    fun `an Inrupt ACR maps to non-empty ACP quads`() {
        val quads = InruptAcrJson.parseOrNull(inruptAcr, base)!!
        assertTrue(quads.isNotEmpty())

        val acr = "https://storage.inrupt.com/alice/photo.acr"
        val ac = "$acr#ac1"
        val policy = "$acr#policy1"
        val matcher = "$acr#matcher1"

        assertTrue(quads.has(acr, RDF.TYPE, ACP.ACCESS_CONTROL_RESOURCE))
        assertTrue(quads.has(acr, ACP.RESOURCE, "https://storage.inrupt.com/alice/photo"))
        assertTrue(quads.has(acr, ACP.ACCESS_CONTROL, ac))
        assertTrue(quads.has(ac, ACP.APPLY, policy))
        assertTrue(quads.has(policy, ACP.ALLOW, ACL.READ))
        assertTrue(quads.has(policy, ACP.ALL_OF, matcher))
        assertTrue(quads.has(matcher, ACP.AGENT, "https://bob.pod/profile/card#me"))
    }

    @Test
    fun `a public agent matcher is mapped`() {
        val json = inruptAcr.replace("https://bob.pod/profile/card#me", ACP.PUBLIC_AGENT)
        val quads = InruptAcrJson.parseOrNull(json, base)!!
        val matcher = "https://storage.inrupt.com/alice/photo.acr#matcher1"
        assertTrue(quads.has(matcher, ACP.AGENT, ACP.PUBLIC_AGENT))
    }

    @Test
    fun `a non-Inrupt context is declined so the generic reader runs`() {
        val notInrupt = inruptAcr.replace(
            "https://authorization.inrupt.com/authorization/v1",
            "https://www.w3.org/ns/solid/acp",
        )
        assertNull(InruptAcrJson.parseOrNull(notInrupt, base))
    }

    @Test
    fun `a non-JSON body is declined`() {
        assertNull(InruptAcrJson.parseOrNull("<> a acp:AccessControlResource .", base))
    }

    @Test
    fun `an Inrupt context given as a bare string is recognised`() {
        val stringContext = inruptAcr.replace(
            """["https://authorization.inrupt.com/authorization/v1"]""",
            """"https://authorization.inrupt.com/authorization/v1"""",
        )
        assertFalse(InruptAcrJson.parseOrNull(stringContext, base).isNullOrEmpty())
    }
}
