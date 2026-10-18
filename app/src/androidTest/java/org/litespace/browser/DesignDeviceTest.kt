package org.litespace.browser

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.litespace.browser.domain.LiteApp
import java.io.File

/** Real accessibility interactions; all sample entries and preferences are restored. */
class DesignDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
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
        runBlocking {
            app.preferences.setLanguage("en"); app.preferences.setTheme("light"); app.preferences.setDynamicColors(false)
            samples.forEach { app.repository.save(it) }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(90_000) { compose.onAllNodesWithText("Your collection").fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                capture("design-library-light")
                compose.onNode(hasSetTextAction()).performTextReplacement("GitHub")
                compose.onNodeWithText("1").assertExists()
                compose.onNodeWithTag("lite-app-${samples[2].id}").assertExists()
                compose.onNodeWithTag("collection").performScrollToIndex(0)
                compose.onNodeWithText("Favorites").performClick()
                compose.onNodeWithText("Nothing here yet").assertExists()
                compose.onNodeWithTag("lite-app-${samples[2].id}").assertDoesNotExist()
                compose.onNodeWithText("Show all apps").performScrollTo().performClick()
                compose.onNodeWithContentDescription("Settings").performClick()
                compose.onNodeWithTag("settings").performScrollToNode(hasText("Dark"))
                compose.onNodeWithText("Dark").performClick()
                runBlocking { withTimeout(30_000) { app.preferences.flow.first { it.theme == "dark" } } }
                compose.onNodeWithTag("settings").performScrollToNode(hasText("فارسی"))
                compose.onNodeWithText("فارسی").performClick()
                runBlocking { withTimeout(30_000) { app.preferences.flow.first { it.language == "fa" } } }
                scenario.recreate()
                compose.waitForIdle()
                compose.onNodeWithTag("settings").performScrollToIndex(0)
                compose.onNodeWithText("ظاهر").assertExists()
                scenario.onActivity {
                    if(android.os.Build.VERSION.SDK_INT < 29) assertTrue("Dark-mode system navigation needs readable white controls",
                        androidx.core.graphics.ColorUtils.calculateContrast(android.graphics.Color.WHITE, it.window.navigationBarColor) >= 4.5)
                }
                capture("design-settings-persian-dark")
                compose.onNodeWithContentDescription("بازگشت").performClick()
                compose.onNodeWithTag("collection").performScrollToIndex(0)
                compose.onNodeWithText("مجموعهٔ شما").assertExists()
                val appsTab = compose.onNodeWithText("برنامه‌ها").fetchSemanticsNode().boundsInRoot.center.x
                val settingsTab = compose.onNodeWithText("تنظیمات").fetchSemanticsNode().boundsInRoot.center.x
                assertTrue("Persian bottom navigation must actually mirror", appsTab > settingsTab)
                capture("design-library-persian-dark")
                compose.onNodeWithContentDescription("ساخت برنامه سبک").performClick()
                compose.onNodeWithText("ذخیره برنامه سبک").assertExists()
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
