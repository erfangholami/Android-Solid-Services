package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.vocab.ACP
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import org.json.JSONArray
import org.json.JSONObject

internal object InruptAcrJson {

    private const val INRUPT_CONTEXT_MARKER = "authorization.inrupt.com"

    fun parseOrNull(text: String, base: java.net.URI): List<RdfQuad>? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (!hasInruptContext(root)) return null
        return runCatching { parse(root, base) }.getOrDefault(emptyList())
    }

    private fun hasInruptContext(root: JSONObject): Boolean {
        when (val ctx = root.opt("@context")) {
            is String -> return ctx.contains(INRUPT_CONTEXT_MARKER)
            is JSONArray -> {
                for (i in 0 until ctx.length()) {
                    val v = ctx.optString(i, "")
                    if (v.contains(INRUPT_CONTEXT_MARKER)) return true
                }
            }
        }
        return false
    }

    private fun parse(root: JSONObject, base: java.net.URI): List<RdfQuad> {
        val quads = mutableListOf<RdfQuad>()
        val acrId = resolve(root.optString("id").ifEmpty { base.toString() }, base)

        quads += iri(acrId, RDF.TYPE, ACP.ACCESS_CONTROL_RESOURCE)
        root.optString("resource").takeIf { it.isNotEmpty() }?.let {
            quads += iri(acrId, ACP.RESOURCE, resolve(it, base))
        }

        addAccessControls(root.optJSONArray("accessControl"), acrId, ACP.ACCESS_CONTROL, base, quads)
        addAccessControls(
            root.optJSONArray("memberAccessControl"), acrId, ACP.MEMBER_ACCESS_CONTROL, base, quads,
        )
        return quads
    }

    private fun addAccessControls(
        acs: JSONArray?,
        acrId: String,
        linkPredicate: String,
        base: java.net.URI,
        quads: MutableList<RdfQuad>,
    ) {
        acs ?: return
        for (i in 0 until acs.length()) {
            val ac = acs.optJSONObject(i) ?: continue
            val acId = resolve(ac.optString("id"), base).ifEmpty { continue }
            quads += iri(acrId, linkPredicate, acId)
            quads += iri(acId, RDF.TYPE, ACP.ACCESS_CONTROL_TYPE)
            val policies = ac.optJSONArray("apply") ?: continue
            for (j in 0 until policies.length()) {
                val policy = policies.optJSONObject(j) ?: continue
                addPolicy(policy, acId, base, quads)
            }
        }
    }

    private fun addPolicy(
        policy: JSONObject,
        acId: String,
        base: java.net.URI,
        quads: MutableList<RdfQuad>,
    ) {
        val policyId = resolve(policy.optString("id"), base).ifEmpty { return }
        quads += iri(acId, ACP.APPLY, policyId)
        quads += iri(policyId, RDF.TYPE, ACP.POLICY)
        addIriValues(policy.optJSONArray("allow"), policyId, ACP.ALLOW, base, quads)
        addIriValues(policy.optJSONArray("deny"), policyId, ACP.DENY, base, quads)
        addMatchers(policy.optJSONArray("allOf"), policyId, ACP.ALL_OF, base, quads)
        addMatchers(policy.optJSONArray("anyOf"), policyId, ACP.ANY_OF, base, quads)
        addMatchers(policy.optJSONArray("noneOf"), policyId, ACP.NONE_OF, base, quads)
    }

    private fun addMatchers(
        matchers: JSONArray?,
        policyId: String,
        linkPredicate: String,
        base: java.net.URI,
        quads: MutableList<RdfQuad>,
    ) {
        matchers ?: return
        for (k in 0 until matchers.length()) {
            val matcher = matchers.optJSONObject(k) ?: continue
            val matcherId = resolve(matcher.optString("id"), base).ifEmpty { continue }
            quads += iri(policyId, linkPredicate, matcherId)
            quads += iri(matcherId, RDF.TYPE, ACP.MATCHER)
            addIriValues(matcher.optJSONArray("agent"), matcherId, ACP.AGENT, base, quads)
            addIriValues(matcher.optJSONArray("client"), matcherId, ACP.CLIENT, base, quads)
            addIriValues(matcher.optJSONArray("issuer"), matcherId, ACP.ISSUER, base, quads)
            addIriValues(matcher.optJSONArray("vc"), matcherId, ACP.VC, base, quads)
        }
    }

    private fun addIriValues(
        values: JSONArray?,
        subject: String,
        predicate: String,
        base: java.net.URI,
        quads: MutableList<RdfQuad>,
    ) {
        values ?: return
        for (i in 0 until values.length()) {
            val v = values.optString(i, "").ifEmpty { continue }
            quads += iri(subject, predicate, resolve(v, base))
        }
    }

    private fun iri(s: String, p: String, o: String) =
        RdfQuad(subject = s, predicate = p, `object` = o)

    private fun resolve(value: String, base: java.net.URI): String {
        if (value.isEmpty()) return value
        return runCatching { base.resolve(value).toString() }.getOrDefault(value)
    }
}
