package org.litespace.browser

import org.junit.Assert.*
import org.junit.Test
import org.litespace.browser.domain.*

class PremiumPoliciesTest {
    @Test fun trackingRemovalPreservesSignedBytesAndFragment() {
        assertEquals("https://example.org/p?token=A%2FB%26C&keep=1#section", TrackingParameters.strip("https://example.org/p?utm_source=x&token=A%2FB%26C&fbclid=123&keep=1#section"))
        assertEquals("https://example.org/p#frag", TrackingParameters.strip("https://example.org/p?UTM_medium=x&gclid=1#frag"))
        assertEquals("https://example.org/p?signature=abc&ref=important", TrackingParameters.strip("https://example.org/p?signature=abc&ref=important"))
    }
    @Test fun trackingKeysMayBePercentEncodedButValuesAreNeverDecoded() {
        assertEquals("https://example.org/?a=%2526", TrackingParameters.strip("https://example.org/?%75tm_campaign=track&a=%2526"))
    }
    @Test fun nativeTemplatesEscapeUntrustedSharedText() {
        assertEquals("https://example.org/share?url=https%3A%2F%2Fnews.example%2Fp%3Fa%3D1&title=A+title", NativeTemplates.expand("https://example.org/share?url=%u&title=%t", "A title https://news.example/p?a=1"))
        assertEquals("https://example.org/search?q=a%26admin%3Dtrue", NativeTemplates.expand("https://example.org/search?q=%s", "a&admin=true"))
        assertTrue(runCatching { NativeTemplates.expand("javascript:alert(%s)", "x") }.isFailure)
    }
    @Test fun multipleHostsPerLineAndTagMembership() {
        val rules = HostRules(HostRules.parse("0.0.0.0 ad.example tracker.example # both\n::1 bad.example\n127.0.0.1 localhost"))
        assertTrue(rules.blocks("image.tracker.example")); assertTrue(rules.blocks("bad.example")); assertFalse(rules.blocks("nottracker.example"))
        val app = LiteApp(tags = "Work, News, Work,  ")
        assertEquals(listOf("Work", "News"), app.tagNames())
    }
}
