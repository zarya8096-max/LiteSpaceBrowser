package org.litespace.browser

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
import org.junit.Test
import org.litespace.browser.data.ToolRecord
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.web.BrowserActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PremiumBrowserDeviceTest {
    private val application get() = ApplicationProvider.getApplicationContext<LiteSpaceApplication>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val offline = "https://appassets.androidplatform.net/assets/error.html"
    @Test fun sharedAutomaticScriptRunsOnlyOnConfiguredOriginAndImagesSettingApplies() {
        val script = ToolRecord(kind = "script", title = "Library regression", payload = "window.libraryMarker='verified-library';")
        val app = LiteApp(name = "Library regression", url = offline, scriptIds = script.id, images = false, pullToRefresh = false)
        runBlocking { application.tools.save(script); application.repository.save(app) }
        try {
            ActivityScenario.launch<BrowserActivity>(Intent(application, BrowserActivity::class.java).putExtra("app_id", app.id)).use { scenario ->
                waitUntil { evaluate(scenario, "window.libraryMarker") == "\"verified-library\"" }
                scenario.onActivity { val web = findWeb(it.window.decorView)!!; assertFalse(web.settings.loadsImagesAutomatically); assertTrue(web.settings.blockNetworkImage)
                    web.loadDataWithBaseURL("https://foreign.example/", "<html><body>Foreign</body></html>", "text/html", "UTF-8", null)
                }
                waitUntil { evaluate(scenario, "location.origin") == "\"https://foreign.example\"" }
                assertEquals("\"undefined\"", evaluate(scenario, "typeof window.libraryMarker"))
            }
        } finally { runBlocking { application.repository.delete(app.id); application.tools.dao.delete(script.id) } }
    }
    @Test fun readerExtractsActualWebViewArticleAndOpensLocalNonScriptableView() {
        val app = LiteApp(name = "Reader regression", url = offline)
        runBlocking { application.repository.save(app) }
        try {
            ActivityScenario.launch<BrowserActivity>(Intent(application, BrowserActivity::class.java).putExtra("app_id", app.id)).use { scenario ->
                waitUntil { evaluate(scenario, "document.readyState") == "\"complete\"" }
                val paragraphs = (1..12).joinToString("") { "<p>Reading should remain private and comfortable. This long article paragraph number $it contains useful text for the article extraction engine to identify the main content rather than navigation links or unrelated controls.</p>" }
                scenario.onActivity { findWeb(it.window.decorView)!!.loadDataWithBaseURL(offline, "<html><head><title>Actual reader fixture</title></head><body><nav>Irrelevant navigation</nav><article><h1>Actual reader fixture</h1>$paragraphs</article></body></html>", "text/html", "UTF-8", null) }
                waitUntil { evaluate(scenario, "document.title") == "\"Actual reader fixture\"" }
                val menu = device.wait(Until.findObject(By.desc("Menu")), 30000); assertNotNull(menu); menu.click()
                val reader = device.wait(Until.findObject(By.text("Reader mode")), 15000); assertNotNull(reader); reader.click()
                assertTrue(device.wait(Until.hasObject(By.textContains("Actual reader fixture")), 40000))
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first { it is org.litespace.browser.web.ReaderActivity }
                    val web = findWeb(activity.window.decorView)!!
                    assertFalse(web.settings.javaScriptEnabled); assertTrue(web.settings.blockNetworkLoads); assertFalse(web.settings.allowFileAccess)
                    assertTrue(web.url.orEmpty().startsWith("https://appassets.androidplatform.net/reader/article")); activity.finish()
                }
            }
        } finally { runBlocking { application.repository.delete(app.id) } }
    }
    private fun evaluate(scenario: ActivityScenario<BrowserActivity>, expression: String): String {
        val result = AtomicReference(""); val latch = CountDownLatch(1)
        scenario.onActivity { activity -> val web = findWeb(activity.window.decorView); if(web == null) latch.countDown() else web.evaluateJavascript(expression) { result.set(it.orEmpty()); latch.countDown() } }
        latch.await(10, TimeUnit.SECONDS); return result.get()
    }
    private fun waitUntil(check: () -> Boolean) { val end = System.currentTimeMillis() + 45000; while(System.currentTimeMillis() < end) { if(check()) return; Thread.sleep(150) }; fail("Browser state timed out") }
    private fun findWeb(view: View): WebView? { if(view is WebView) return view; if(view is ViewGroup) for(i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }; return null }
}
