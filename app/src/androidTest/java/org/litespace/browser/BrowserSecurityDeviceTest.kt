package org.litespace.browser

import android.app.KeyguardManager
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.web.BrowserActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class BrowserSecurityDeviceTest {
    private val application get() = ApplicationProvider.getApplicationContext<LiteSpaceApplication>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val offline = "https://appassets.androidplatform.net/assets/error.html"

    @Test fun javascriptDisabledDoesNotRunCustomCode() = withConfig(LiteApp(name = "No script", url = offline,
        javascript = false, script = "document.title='UNSAFE_CUSTOM_CODE'")) { scenario ->
        waitForPage(scenario, offline)
        scenario.onActivity { assertNotEquals("UNSAFE_CUSTOM_CODE", findWeb(it.window.decorView)!!.title) }
    }

    @Test fun navigationCancelsVisibleWebsitePermissionPrompt() = withConfig(LiteApp(name = "Permission test", url = offline,
        location = true)) { scenario ->
        waitForPage(scenario, offline)
        scenario.onActivity { activity -> findWeb(activity.window.decorView)!!.evaluateJavascript(
            "navigator.geolocation.getCurrentPosition(function(){},function(){});", null) }
        assertTrue("Real WebView location request must show native website confirmation",
            device.wait(Until.hasObject(By.textContains("Share location with")), 30_000))
        val next = "$offline?next"
        scenario.onActivity { findWeb(it.window.decorView)!!.loadUrl(next) }
        waitForPage(scenario, next)
        assertTrue("Navigating must dismiss the old page's permission dialog",
            device.wait(Until.gone(By.textContains("Share location with")), 15_000))
    }

    @Test fun foreignOriginDoesNotReceiveConfiguredScript() = withConfig(LiteApp(name = "Origin test", url = offline,
        script = "window.privateMarker='configured-secret'")) { scenario ->
        waitForPage(scenario, offline)
        assertTrue(evaluate(scenario, "window.privateMarker").contains("configured-secret"))
        scenario.onActivity { findWeb(it.window.decorView)!!.loadDataWithBaseURL("https://different.example/", "<html><body>Foreign</body></html>", "text/html", "UTF-8", null) }
        // loadDataWithBaseURL is not an HTTP navigation; old providers can keep
        // different native URL/progress values after its document has committed.
        waitUntil { evaluate(scenario, "document.body && document.body.textContent") == "\"Foreign\"" }
        assertEquals("\"https://different.example\"", evaluate(scenario, "location.origin"))
        assertEquals("\"undefined\"", evaluate(scenario, "typeof window.privateMarker"))
    }

    @Test fun missingConfigurationClosesWithoutCreatingWebView() {
        val intent = Intent(application, BrowserActivity::class.java).putExtra("app_id", "missing-config")
        ActivityScenario.launch<BrowserActivity>(intent).use { scenario ->
            waitUntil { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        }
    }
    @Test fun backUsesWebHistoryBeforeClosingActivity() = withConfig(LiteApp(name = "History", url = offline)) { scenario ->
        waitForPage(scenario, offline)
        val second = "$offline?second"
        scenario.onActivity { findWeb(it.window.decorView)!!.loadUrl(second) }
        waitForPage(scenario, second)
        device.pressBack()
        waitForPage(scenario, offline)
        scenario.onActivity { assertFalse(it.isFinishing) }
        device.pressBack()
        waitUntil { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
    }
    @Test fun changingPermissionsClosesAlreadyRunningBrowser() {
        val config = LiteApp(name = "Revocation", url = offline, camera = true)
        withConfig(config) { scenario ->
            waitForPage(scenario, offline)
            runBlocking { application.repository.save(config.copy(camera = false, lock = true)) }
            waitUntil { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        }
    }
    @Test fun websiteDialogDoesNotSurviveBackgrounding() = withConfig(LiteApp(name = "Dialog lifecycle", url = offline)) { scenario ->
        waitForPage(scenario, offline)
        scenario.onActivity { findWeb(it.window.decorView)!!.evaluateJavascript("alert('LiteSpace website dialog test'); window.afterDialog='finished';", null) }
        assertTrue(device.wait(Until.hasObject(By.text("LiteSpace website dialog test")), 30_000))
        device.pressHome()
        waitUntil { scenario.state == androidx.lifecycle.Lifecycle.State.CREATED }
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        assertTrue("Website dialogs must be dismissed on stop", device.wait(Until.gone(By.text("LiteSpace website dialog test")), 15_000))
        waitUntil { evaluate(scenario, "window.afterDialog") == "\"finished\"" }
    }

    @Test fun protectedAppFailsClosedWithoutDeviceCredential() {
        assumeFalse(application.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val config = LiteApp(name = "Locked", url = offline, lock = true)
        runBlocking { application.repository.save(config) }
        try {
            ActivityScenario.launch<BrowserActivity>(Intent(application, BrowserActivity::class.java).putExtra("app_id", config.id)).use { scenario ->
                waitUntil { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            }
        } finally { runBlocking { application.repository.delete(config.id) } }
    }

    private fun withConfig(config: LiteApp, action: (ActivityScenario<BrowserActivity>) -> Unit) {
        runBlocking { application.repository.save(config) }
        try {
            ActivityScenario.launch<BrowserActivity>(Intent(application, BrowserActivity::class.java).putExtra("app_id", config.id)).use(action)
        } finally { runBlocking { application.repository.delete(config.id) } }
    }
    private fun waitForPage(scenario: ActivityScenario<BrowserActivity>, url: String) {
        var actual = "No WebView"
        waitUntil(description = { "Expected $url; last native state: $actual" }) {
            var loaded = false
            scenario.onActivity { findWeb(it.window.decorView)?.let { view ->
                actual = "${view.url} (${view.progress}%)"; loaded = view.url == url && view.progress == 100
            } }
            loaded
        }
    }
    private fun evaluate(scenario: ActivityScenario<BrowserActivity>, script: String): String {
        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)
        scenario.onActivity { findWeb(it.window.decorView)!!.evaluateJavascript(script) { value -> result.set(value); latch.countDown() } }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        return result.get()
    }
    private fun waitUntil(description: () -> String = { "Timed out waiting for Android/WebView state" }, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120)
        while(System.nanoTime() < deadline) { if(condition()) return; Thread.sleep(200) }
        fail(description())
    }
    private fun findWeb(view: View): WebView? {
        if(view is WebView) return view
        if(view is ViewGroup) for(index in 0 until view.childCount) findWeb(view.getChildAt(index))?.let { return it }
        return null
    }
}
