package org.litespace.browser.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.litespace.browser.LiteSpaceApplication
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.domain.UrlPolicy
import org.litespace.browser.web.BrowserActivity
import java.util.concurrent.TimeUnit

object SyncScheduler {
    suspend fun reconcile(context: Context) {
        val app = context.applicationContext as LiteSpaceApplication
        val prefs = app.preferences.flow.first()
        val manager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
        val entries = app.repository.apps.first().associateBy { it.id }
        val active = app.tools.dao.all().any { source ->
            val entry = entries[source.appId]
            source.enabled && source.kind in setOf("feed", "monitor") && entry != null && !entry.lock && !entry.incognito && !(Build.VERSION.SDK_INT >= 28 && entry.profile in org.litespace.browser.web.LegacySandboxes.names)
        }
        if(active) manager.enqueueUniquePeriodicWork("site-updates", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SiteSyncWorker>(prefs.syncMinutes.toLong(), TimeUnit.MINUTES).setConstraints(constraints).build())
        else manager.cancelUniqueWork("site-updates")
        if(prefs.filterUpdates) manager.enqueueUniquePeriodicWork("filter-updates", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<FilterSyncWorker>(7, TimeUnit.DAYS).setConstraints(constraints).build())
        else manager.cancelUniqueWork("filter-updates")
    }
    fun checkNow(context: Context) { WorkManager.getInstance(context).enqueueUniqueWork("site-updates-now", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<SiteSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()) }
}
class FilterSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { (applicationContext as LiteSpaceApplication).blocker.update(); Result.success() }
    catch(e: CancellationException) { throw e } catch(_: Exception) { Result.failure() }
}
class SiteSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = applicationContext as LiteSpaceApplication
        val dao = container.tools.dao
        for(source in dao.all().filter { it.enabled && it.kind in setOf("feed", "monitor") }) {
            if(isStopped) break
            try {
                val app = container.repository.get(source.appId) ?: error("Associated Lite App was deleted")
                require(!app.incognito && !app.lock && !(Build.VERSION.SDK_INT >= 28 && app.profile in org.litespace.browser.web.LegacySandboxes.names)) { "Background checks are disabled for private or protected Lite Apps" }
                val cookie = if(UrlPolicy.origin(source.url) == UrlPolicy.origin(app.url)) withContext(Dispatchers.Main) {
                    runCatching {
                        val manager = if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                            val name = if(app.profile.isBlank()) "app-${app.id}" else "shared-${app.profile}"
                            ProfileStore.getInstance().getOrCreateProfile(name).cookieManager
                        } else CookieManager.getInstance()
                        manager.getCookie(source.url)
                    }.getOrNull()
                } else null
                val entries = inspect(source, cookie)
                val seen = if(source.snapshot.isBlank()) emptySet() else JSONArray(source.snapshot).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                val fresh = entries.filter { it.id !in seen }
                // First fetch establishes a baseline. Later fetches notify only on unseen items.
                if(source.snapshot.isNotBlank() && fresh.isNotEmpty()) {
                    val current = dao.get(source.id)
                    val latestApp = container.repository.get(app.id)
                    if(current != null && current.enabled && current.url == source.url && current.payload == source.payload && latestApp == app) notify(app, source, fresh)
                }
                val combined = (entries.map { it.id } + seen).distinct().take(4000)
                dao.get(source.id)?.takeIf { it.url == source.url && it.payload == source.payload }?.let {
                    dao.save(it.copy(snapshot = JSONArray(combined).toString(), checkedAt = System.currentTimeMillis(), error = ""))
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { dao.get(source.id)?.let { dao.save(it.copy(checkedAt = System.currentTimeMillis(), error = (e.message ?: "Check failed").take(300))) } }
        }
        Result.success()
    }
    private fun notify(app: LiteApp, source: ToolRecord, entries: List<WatchEntry>) {
        val context = applicationContext
        if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "site-${app.id}"
        manager.createNotificationChannel(NotificationChannel(channel, app.name, NotificationManager.IMPORTANCE_DEFAULT).apply { description = "RSS and page monitor updates (not Web Push)" })
        val intent = Intent(context, BrowserActivity::class.java).putExtra("app_id", app.id).putExtra("start_url", entries.first().url)
        val pending = PendingIntent.getActivity(context, source.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = entries.take(5).joinToString("\n") { it.title }.ifBlank { "Page content changed" }
        val builder = NotificationCompat.Builder(context, channel).setSmallIcon(org.litespace.browser.R.drawable.ic_litespace)
            .setContentTitle("${app.name} · ${source.title}").setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setGroup("site-${app.id}").setAutoCancel(true).setContentIntent(pending)
        val image = entries.firstOrNull()?.image?.takeIf { it.startsWith("https://") }?.let { url ->
            runCatching {
                val bytes = SafeHttp.get(url, 1_000_000).bytes
                org.litespace.browser.util.IconImages.read { java.io.ByteArrayInputStream(bytes) }
            }.getOrNull()
        }
        if(image != null) builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(image).setSummaryText(body))
        manager.notify(source.id.hashCode(), builder.build())
        manager.notify(("summary-" + app.id).hashCode(), NotificationCompat.Builder(context, channel)
            .setSmallIcon(org.litespace.browser.R.drawable.ic_litespace).setContentTitle(app.name).setContentText("Website updates")
            .setGroup("site-${app.id}").setGroupSummary(true).setAutoCancel(true).setContentIntent(pending).build())
        image?.recycle()
    }
    companion object {
        fun inspect(source: ToolRecord, cookie: String? = null): List<WatchEntry> {
            val body = SafeHttp.get(source.url, cookies = cookie)
            return if(source.kind == "feed") WatchParser.feed(body.text, body.url) else WatchParser.monitor(body.text, body.url, source.payload)
        }
    }
}
