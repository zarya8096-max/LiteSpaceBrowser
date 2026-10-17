package org.litespace.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the installed provider, rather than assuming isolation from the OS version. */
@RunWith(AndroidJUnit4::class)
class ProfileDeviceTest {
    @Test fun isolatedCookiesAndClearingLeaveOtherProfileUntouched() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var supported = false
        instrumentation.runOnMainSync {
            supported = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)
            val fullClear = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA) }.getOrDefault(false)
            android.util.Log.i("LiteSpaceVerification", "multiProfile=$supported fullDataDeletion=$fullClear")
        }
        assumeTrue("Provider needs isolated profiles", supported)
        lateinit var first: Profile
        lateinit var second: Profile
        val suffix = UUID.randomUUID().toString()
        val set = CountDownLatch(2)
        val origin = "https://profiles.example.test"
        instrumentation.runOnMainSync {
            first = ProfileStore.getInstance().getOrCreateProfile("private-test-a-$suffix")
            second = ProfileStore.getInstance().getOrCreateProfile("private-test-b-$suffix")
            first.cookieManager.setCookie(origin, "session=first; Secure; Path=/") { set.countDown() }
            second.cookieManager.setCookie(origin, "session=second; Secure; Path=/") { set.countDown() }
        }
        try {
            assertTrue("Cookie writes complete", set.await(30, TimeUnit.SECONDS))
            var firstCookies: String? = null
            var secondCookies: String? = null
            instrumentation.runOnMainSync {
                firstCookies = first.cookieManager.getCookie(origin)
                secondCookies = second.cookieManager.getCookie(origin)
            }
            assertTrue(firstCookies.orEmpty().contains("session=first"))
            assertFalse(firstCookies.orEmpty().contains("session=second"))
            assertTrue(secondCookies.orEmpty().contains("session=second"))
            assertFalse(secondCookies.orEmpty().contains("session=first"))
            val cleared = CountDownLatch(1)
            instrumentation.runOnMainSync { clear(first) { cleared.countDown() } }
            assertTrue("Profile clearing completes", cleared.await(30, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                firstCookies = first.cookieManager.getCookie(origin)
                secondCookies = second.cookieManager.getCookie(origin)
            }
            assertTrue("Cleared profile loses cookies", firstCookies.isNullOrEmpty())
            assertTrue("Other profile remains signed in", secondCookies.orEmpty().contains("session=second"))
        } finally {
            // Loaded profiles cannot be deleted until a new process. Clear their data
            // now; BrowserActivity reclaims these private-* directories on next startup.
            val cleared = CountDownLatch(2)
            instrumentation.runOnMainSync {
                clear(first) { cleared.countDown() }
                clear(second) { cleared.countDown() }
            }
            cleared.await(30, TimeUnit.SECONDS)
        }
    }
    private fun clear(profile: Profile, done: () -> Unit) {
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))
            WebStorageCompat.deleteBrowsingData(profile.webStorage) { done() }
        else profile.cookieManager.removeAllCookies { done() }
    }
}
