package org.litespace.browser

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Parcel
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.ui.MainViewModel
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

class EditorStateDeviceTest {
    @Test fun largeDraftSurvivesRotationWithoutEnteringActivityBundle() {
        val app = ApplicationProvider.getApplicationContext<LiteSpaceApplication>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val bundleBytes = AtomicInteger(0)
        val callback = object : Application.ActivityLifecycleCallbacks {
            // The ordinary callback fires inside Activity.super before
            // ComponentActivity adds Compose's SavedStateRegistry payload.
            override fun onActivityPostSaveInstanceState(activity: Activity, state: Bundle) {
                if(activity is MainActivity) {
                    val parcel = Parcel.obtain()
                    try { parcel.writeBundle(state); bundleBytes.set(parcel.dataSize()) } finally { parcel.recycle() }
                }
            }
            override fun onActivitySaveInstanceState(a: Activity, state: Bundle) {}
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivityDestroyed(a: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callback)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val create = device.wait(Until.findObject(By.desc(Pattern.compile("Create Lite App|ساخت برنامه سبک"))), 30_000)
                assertNotNull("Create button must have an accessible label", create)
                create!!.click()
                val draft = LiteApp(name = "Large draft", url = "https://example.com", icon = "A".repeat(399_000),
                    css = "ش".repeat(64_000), script = "ش".repeat(64_000))
                scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].editor.value = draft }
                scenario.recreate()
                scenario.onActivity { assertEquals(draft, ViewModelProvider(it)[MainViewModel::class.java].editor.value) }
                if(Build.VERSION.SDK_INT >= 29) {
                    assertTrue("Android must actually save Activity state", bundleBytes.get() > 0)
                    assertTrue("Large icons/code must stay out of Binder saved state: ${bundleBytes.get()} bytes", bundleBytes.get() < 200_000)
                }
                device.wait(Until.findObject(By.desc(Pattern.compile("Back|بازگشت"))), 30_000)!!.click()
                // Input injection can return before Compose handles the click,
                // especially on software-rendered emulators. Await the actual
                // state transition instead of racing the event queue.
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
                var discarded = false
                while(!discarded && System.nanoTime() < deadline) {
                    scenario.onActivity { discarded = ViewModelProvider(it)[MainViewModel::class.java].editor.value == null }
                    if(!discarded) Thread.sleep(50)
                }
                assertTrue("Leaving the editor must discard its draft", discarded)
            }
        } finally { app.unregisterActivityLifecycleCallbacks(callback) }
    }
}
