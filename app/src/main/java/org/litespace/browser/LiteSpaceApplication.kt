package org.litespace.browser

import android.app.Application
import androidx.room.Room
import org.litespace.browser.data.*
import org.litespace.browser.domain.AppRepository
import kotlinx.coroutines.launch

class LiteSpaceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if(android.os.Build.VERSION.SDK_INT >= 28 && org.litespace.browser.web.LegacySandboxes.isSandboxProcess()) {
            // Must precede every CookieManager/WebView/WebKit call in this process.
            android.webkit.WebView.setDataDirectorySuffix(android.app.Application.getProcessName().substringAfter(':'))
            return
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { SyncScheduler.reconcile(this@LiteSpaceApplication) }
        }
    }
    val database by lazy { Room.databaseBuilder(this, LiteDatabase::class.java, "litespace.db").addMigrations(LiteDatabase.MIGRATION_1_2).enableMultiInstanceInvalidation().build() }
    val repository: AppRepository by lazy { LocalRepository(database, preferences) }
    val tools by lazy { ToolsRepository(database.tools()) }
    val preferences by lazy { PreferencesStore(this) }
    val blocker by lazy { Blocker(this) }
}
