package org.litespace.browser

import android.app.KeyguardManager
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.web.BrowserActivity

/** Opt in on a disposable test device already configured with the supplied PIN.
 * This test never changes a real device's credentials. */
class ForegroundLockDeviceTest {
    @Test fun returningFromHomeRequiresCredentialAgain() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pin = InstrumentationRegistry.getArguments().getString("testDevicePin")
        val app = ApplicationProvider.getApplicationContext<LiteSpaceApplication>()
        assumeTrue("Supply testDevicePin on a disposable, secured emulator", pin != null && app.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val device = UiDevice.getInstance(instrumentation)
        val config = LiteApp(name = "Lock regression", url = "https://appassets.androidplatform.net/assets/error.html", lock = true)
        runBlocking { app.repository.save(config) }
        val monitor = instrumentation.addMonitor(BrowserActivity::class.java.name, null, false)
        var activity: BrowserActivity? = null
        fun unlock() {
            val entry = device.wait(Until.findObject(By.res("com.android.settings", "password_entry")), 60_000)
            assertNotNull("Android credential confirmation must appear", entry)
            entry.text = pin!!
            device.pressEnter()
        }
        fun waitVisible() {
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120)
            while(System.nanoTime() < deadline) {
                var visible = false
                instrumentation.runOnMainSync { findWeb(activity!!.window.decorView)?.let { visible = it.visibility == View.VISIBLE && it.progress == 100 } }
                if(visible) return
                Thread.sleep(200)
            }
            fail("Unlocked WebView must become visible")
        }
        try {
            val intent = Intent(app, BrowserActivity::class.java).putExtra("app_id", config.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            activity = monitor.waitForActivityWithTimeout(30_000) as? BrowserActivity
            assertNotNull(activity)
            unlock()
            waitVisible()
            var original: WebView? = null
            instrumentation.runOnMainSync { original = findWeb(activity!!.window.decorView) }
            device.pressHome()
            device.waitForIdle()
            instrumentation.runOnMainSync {
                assertEquals(View.INVISIBLE, original!!.visibility)
                assertTrue(activity!!.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            unlock()
            waitVisible()
            instrumentation.runOnMainSync { assertSame("Unlock must preserve the existing WebView", original, findWeb(activity!!.window.decorView)) }
        } finally {
            activity?.let { browser -> instrumentation.runOnMainSync { browser.finish() } }
            instrumentation.removeMonitor(monitor)
            runBlocking { app.repository.delete(config.id) }
        }
    }
    private fun findWeb(view: View): WebView? {
        if(view is WebView) return view
        if(view is ViewGroup) for(index in 0 until view.childCount) findWeb(view.getChildAt(index))?.let { return it }
        return null
    }
}
