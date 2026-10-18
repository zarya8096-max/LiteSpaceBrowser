package org.litespace.browser

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.litespace.browser.domain.LiteApp
import java.io.File

/** Real accessibility interactions; all sample entries and preferences are restored. */
class DesignDeviceTest {
    @Test fun collectionFiltersAndPersianThemeSurviveRecreation() {
        val app = ApplicationProvider.getApplicationContext<LiteSpaceApplication>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val previous = runBlocking { app.preferences.flow.first() }
        val samples = listOf(
            LiteApp(name = "Wikipedia", url = "https://wikipedia.org", group = "Read", favorite = true, accent = 0xFF4C6B57),
            LiteApp(name = "YouTube", url = "https://youtube.com", group = "Watch", favorite = true, accent = 0xFFBA5748),
            LiteApp(name = "GitHub", url = "https://github.com", group = "Work", accent = 0xFF4B536B),
            LiteApp(name = "Figma", url = "https://figma.com", group = "Create", accent = 0xFF7D5CA3),
            LiteApp(name = "Notion", url = "https://notion.so", group = "Work", accent = 0xFF3F565D),
            LiteApp(name = "Dribbble", url = "https://dribbble.com", group = "Create", accent = 0xFF9D5975)
        )
        fun capture(name: String) {
            if(InstrumentationRegistry.getArguments().getString("captureDesign") != "true") return
            device.waitForIdle()
            val output = File(app.cacheDir, "$name.png")
            if(!device.takeScreenshot(output)) {
                // Some older headless providers cannot use UiAutomation's bitmap
                // capture. The platform screencap path still captures real pixels.
                val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p")
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input -> output.outputStream().use { input.copyTo(it) } }
            }
            assertTrue("Screenshot must contain an actual PNG", output.inputStream().use { it.read() == 137 && it.read() == 80 })
        }
        fun scrollTop() {
            repeat(3) { device.swipe(device.displayWidth / 2, device.displayHeight / 3,
                device.displayWidth / 2, device.displayHeight * 3 / 4, 24) }
        }
        fun findText(text: String): androidx.test.uiautomator.UiObject2 {
            device.waitForIdle()
            device.wait(Until.findObject(By.text(text)), 2_000)?.let { return it }
            scrollTop()
            repeat(8) {
                device.waitForIdle()
                device.findObject(By.text(text))?.let { return it }
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 24)
            }
            error("Missing UI text after scrolling: $text")
        }
        runBlocking {
            app.preferences.setLanguage("en"); app.preferences.setTheme("light"); app.preferences.setDynamicColors(false)
            samples.forEach { app.repository.save(it) }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertNotNull(device.wait(Until.findObject(By.text("Your collection")), 90_000))
                capture("design-library-light")
                val search = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 30_000))
                search.text = "GitHub"
                findText("GitHub")
                assertNotNull(device.wait(Until.findObject(By.text("1")), 30_000))
                scrollTop()
                findText("Favorites").click()
                findText("Nothing here yet")
                findText("Show all apps").click()
                // Selected chips and search really affect the repository-backed collection.
                assertNotNull(device.wait(Until.findObject(By.desc("Settings")), 30_000))
                device.findObject(By.desc("Settings")).click()
                findText("Dark").click()
                runBlocking { withTimeout(30_000) { app.preferences.flow.first { it.theme == "dark" } } }
                findText("فارسی").click()
                runBlocking { withTimeout(30_000) { app.preferences.flow.first { it.language == "fa" } } }
                scenario.recreate()
                findText("ظاهر")
                scenario.onActivity {
                    if(android.os.Build.VERSION.SDK_INT < 29) assertTrue("Dark-mode system navigation needs readable white controls",
                        androidx.core.graphics.ColorUtils.calculateContrast(android.graphics.Color.WHITE, it.window.navigationBarColor) >= 4.5)
                }
                capture("design-settings-persian-dark")
                device.wait(Until.findObject(By.desc("بازگشت")), 30_000)!!.click()
                assertNotNull(device.wait(Until.findObject(By.text("مجموعهٔ شما")), 30_000))
                val appsTab = device.findObject(By.text("برنامه‌ها"))
                val settingsTab = device.findObject(By.text("تنظیمات"))
                assertTrue("Persian bottom navigation must actually mirror", appsTab.visibleCenter.x > settingsTab.visibleCenter.x)
                capture("design-library-persian-dark")
                device.wait(Until.findObject(By.desc("ساخت برنامه سبک")), 30_000)!!.click()
                assertNotNull(device.wait(Until.findObject(By.text("ذخیره برنامه سبک")), 30_000))
                capture("design-editor-persian-dark")
            }
        } finally {
            runBlocking {
                samples.forEach { app.repository.delete(it.id) }
                app.preferences.setLanguage(previous.language); app.preferences.setTheme(previous.theme); app.preferences.setDynamicColors(previous.dynamicColors)
            }
        }
    }
}
