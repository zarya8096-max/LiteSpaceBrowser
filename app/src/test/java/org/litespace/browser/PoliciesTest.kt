package org.litespace.browser

import org.junit.Assert.*
import org.junit.Test
import org.litespace.browser.domain.*
import org.litespace.browser.util.readBounded

class PoliciesTest {
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
