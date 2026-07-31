package com.erfangholami.androidsolidservices.api.auth.implementation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IssuerUrlTest {

    @Test
    fun a_bare_domain_is_assumed_to_be_https() {
        assertEquals("https://solidcommunity.net", asHttpsIssuerUrl("solidcommunity.net"))
    }

    @Test
    fun an_https_url_is_left_alone() {
        assertEquals("https://login.inrupt.com", asHttpsIssuerUrl("https://login.inrupt.com"))
    }

    @Test
    fun a_path_survives_normalization() {
        assertEquals("https://example.org/oidc/", asHttpsIssuerUrl("example.org/oidc/"))
    }

    @Test
    fun surrounding_whitespace_is_trimmed() {
        assertEquals("https://example.org", asHttpsIssuerUrl("  https://example.org \n"))
    }

    @Test
    fun an_uppercase_scheme_is_lowercased_because_AppAuth_compares_it_literally() {
        assertEquals("https://example.org", asHttpsIssuerUrl("HTTPS://example.org"))
    }

    // Anything below reaches AppAuth as a fatal IllegalArgumentException thrown on its own
    // AsyncTask, so it has to be stopped here.

    @Test
    fun http_is_rejected_rather_than_upgraded() {
        assertNull(asHttpsIssuerUrl("http://example.org"))
    }

    @Test
    fun a_loopback_dev_server_is_rejected() {
        assertNull(asHttpsIssuerUrl("http://localhost:3000"))
    }

    @Test
    fun a_non_http_scheme_is_rejected() {
        assertNull(asHttpsIssuerUrl("ftp://example.org"))
    }

    @Test
    fun blank_input_is_rejected() {
        assertNull(asHttpsIssuerUrl(""))
        assertNull(asHttpsIssuerUrl("   "))
    }

    @Test
    fun a_url_without_a_host_is_rejected() {
        assertNull(asHttpsIssuerUrl("https://"))
    }

    @Test
    fun an_unparseable_url_is_rejected() {
        assertNull(asHttpsIssuerUrl("https://exa mple.org"))
    }
}
