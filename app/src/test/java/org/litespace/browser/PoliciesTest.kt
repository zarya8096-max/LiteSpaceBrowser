package org.litespace.browser

import org.junit.Assert.*
import org.junit.Test
import org.litespace.browser.domain.*
import org.litespace.browser.util.readBounded

class PoliciesTest {
    @Test fun upgradePreservesEncodedPathQueryAndFragment() {
        assertEquals("https://example.com/a%2Fb?q=%26%3D%2F#x%23y",
            UrlPolicy.normalize("http://example.com:80/a%2Fb?q=%26%3D%2F#x%23y"))
    }
    @Test fun canonicalizesInternationalHostsAndDefaultPorts() {
        assertEquals("https://xn--bcher-kva.de/", UrlPolicy.normalize("HTTPS://BÜCHER.de/"))
        assertEquals("https://example.com", UrlPolicy.origin("HTTPS://EXAMPLE.com:443/a"))
        assertEquals("http://example.com", UrlPolicy.origin("http://example.com:80"))
        assertEquals("https://[::1]:8443", UrlPolicy.origin("https://[::1]:8443/test"))
    }
    @Test fun rejectsMalformedPortsAuthoritiesAndAmbiguousHosts() {
        listOf("https://example.com:65536", "https://example.com:-1", "https://example.com:",
            "https://user@example.com", "https://example.com\\evil", "https://exa%6dple.com").forEach {
            assertTrue("Must reject $it", runCatching { UrlPolicy.normalize(it) }.isFailure)
        }
    }
    @Test fun doesNotSilentlyMapDistinctInternationalDomains() {
        assertTrue(runCatching { UrlPolicy.normalize("https://faß.de") }.isFailure)
        assertEquals("https://xn--fa-hia.de", UrlPolicy.normalize("https://xn--fa-hia.de"))
        assertTrue(UrlPolicy.normalize("https://مثال.ایران/صفحه").startsWith("https://xn--"))
    }
    @Test fun normalizesWebsitesAndUpgradesHttp() {
        assertEquals("https://example.com", UrlPolicy.normalize(" example.com "))
        assertEquals("https://example.com/path?q=1#fragment", UrlPolicy.normalize("http://example.com:80/path?q=1#fragment"))
        assertEquals("http://example.com", UrlPolicy.normalize("http://example.com", false))
    }
    @Test fun rejectsNonWebAndEmbeddedCredentials() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "https://user:pass@example.com", "https://", "https://hello world.com").forEach { input ->
            assertTrue("Must reject $input", runCatching { UrlPolicy.normalize(input) }.isFailure)
        }
    }
    @Test fun comparesOriginsNotPrefixes() {
        assertEquals("https://example.com", UrlPolicy.origin("https://EXAMPLE.com/a"))
        assertNotEquals(UrlPolicy.origin("https://example.com"), UrlPolicy.origin("https://example.com.evil.test"))
        assertNotEquals(UrlPolicy.origin("http://example.com"), UrlPolicy.origin("https://example.com"))
        assertNull(UrlPolicy.origin("file:///tmp/a"))
    }
    @Test fun hostsMatchWholeDomainBoundaries() {
        val parsed = HostRules.parse("# comment\n0.0.0.0 ads.example.com\n127.0.0.1 tracker.test # note\nplain.test\n||unsupported.test^\n")
        assertEquals(setOf("ads.example.com", "tracker.test", "plain.test"), parsed)
        val rules = HostRules(parsed)
        assertTrue(rules.blocks("sub.ads.example.com"))
        assertTrue(rules.blocks("ADS.EXAMPLE.COM."))
        assertFalse(rules.blocks("notads.example.com"))
        assertFalse(rules.blocks("ads.example.com.evil.test"))
        assertFalse(rules.blocks("example.com"))
    }
    @Test fun streamReadEnforcesLimit() {
        assertEquals(10, ByteArray(100).inputStream().readBounded(10).size)
        assertEquals(4, ByteArray(4).inputStream().readBounded(10).size)
    }
}
