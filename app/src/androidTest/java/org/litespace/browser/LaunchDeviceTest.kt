package org.litespace.browser

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.web.BrowserActivity

@RunWith(AndroidJUnit4::class)
class LaunchDeviceTest {
    @Test fun dashboardStarts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { assertFalse(it.isFinishing) } }
    }
    @Test fun configuredBrowserStartsAndCloses() {
        val app = ApplicationProvider.getApplicationContext<LiteSpaceApplication>()
        val config = LiteApp(name = "Offline help", url = "https://appassets.androidplatform.net/assets/error.html")
        runBlocking { app.repository.save(config) }
        ActivityScenario.launch<BrowserActivity>(Intent(app, BrowserActivity::class.java).putExtra("app_id", config.id)).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
        runBlocking { app.repository.delete(config.id) }
    }
}
