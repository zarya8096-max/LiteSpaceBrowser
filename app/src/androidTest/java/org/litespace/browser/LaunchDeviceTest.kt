package org.litespace.browser

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.web.BrowserActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class LaunchDeviceTest {
    @Test fun dashboardStarts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { assertFalse(it.isFinishing) } }
    }
    @Test fun configuredBrowserLoadsPageAndInjectsTrustedScript() {
        val app = ApplicationProvider.getApplicationContext<LiteSpaceApplication>()
        val url = "https://appassets.androidplatform.net/assets/error.html"
        val config = LiteApp(name = "Offline integration test", url = url,
            script = "window.litespaceTestMarker = 'ready'", css = "h1 { color: rgb(1, 2, 3); }")
        runBlocking { app.repository.save(config) }
        try {
            ActivityScenario.launch<BrowserActivity>(Intent(app, BrowserActivity::class.java).putExtra("app_id", config.id)).use { scenario ->
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180)
                var loaded = false
                while(System.nanoTime() < deadline && !loaded) {
                    scenario.onActivity { activity ->
                        val view = findWebView(activity.window.decorView)
                        loaded = view?.url == url && view.progress == 100
                    }
                    if(!loaded) Thread.sleep(250)
                }
                assertTrue("Real WebView must load the bundled HTTPS asset", loaded)
                val result = AtomicReference<String>()
                val latch = CountDownLatch(1)
                scenario.onActivity { activity ->
                    findWebView(activity.window.decorView)!!.evaluateJavascript(
                        "JSON.stringify({marker:window.litespaceTestMarker,color:getComputedStyle(document.querySelector('h1')).color})"
                    ) { value -> result.set(value); latch.countDown() }
                }
                assertTrue("WebView JavaScript callback must complete", latch.await(30, TimeUnit.SECONDS))
                assertTrue("Configured custom script must execute", result.get().contains("ready"))
                assertTrue("Configured CSS must apply", result.get().contains("rgb(1, 2, 3)"))
            }
        } finally { runBlocking { app.repository.delete(config.id) } }
    }
    private fun findWebView(view: View): WebView? {
        if(view is WebView) return view
        if(view is ViewGroup) for(index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        return null
    }
}
