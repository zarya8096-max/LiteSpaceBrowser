package org.litespace.browser

import android.content.ContentValues
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.litespace.browser.data.*
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.web.ReaderActivity

class PremiumDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<LiteSpaceApplication>()
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LiteDatabase::class.java)
    @Test fun upgradesRealVersionOneDatabaseWithoutLosingApps() = runBlocking {
        val name = "premium-migration-test.db"
        context.deleteDatabase(name)
        migration.createDatabase(name, 1).use { db ->
            val values = ContentValues()
            db.query("PRAGMA table_info(lite_apps)").use { cursor ->
                while(cursor.moveToNext()) {
                    val column = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                    if(type == "TEXT") values.put(column, "") else values.put(column, 0)
                }
            }
            values.put("id", "migration-entry"); values.put("config_id", "migration-entry"); values.put("config_name", "Existing app"); values.put("config_url", "https://example.org")
            db.insert("lite_apps", 0, values)
        }
        migration.runMigrationsAndValidate(name, 2, true, LiteDatabase.MIGRATION_1_2).close()
        val db = Room.databaseBuilder(context, LiteDatabase::class.java, name).addMigrations(LiteDatabase.MIGRATION_1_2).build()
        try { val app = db.apps().get("migration-entry")!!.configuration; assertEquals("Existing app", app.name); assertTrue(app.images); assertTrue(app.pullToRefresh); assertFalse(app.removeTracking); assertEquals("", app.tags) }
        finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun rssAtomAndMonitorParsingRejectsUnsafeXml() {
        val rss = WatchParser.feed("<rss><channel><item><guid>entry-1</guid><title>Hello</title><link>https://example.org/a</link></item></channel></rss>", "https://example.org/feed")
        assertEquals("Hello", rss.single().title); assertEquals("https://example.org/a", rss.single().url)
        val atom = WatchParser.feed("<feed xmlns='http://www.w3.org/2005/Atom'><entry><id>x</id><title>Atom title</title><link href='/post' rel='alternate'/></entry></feed>", "https://example.org/feed")
        assertEquals("https://example.org/post", atom.single().url)
        assertTrue(runCatching { WatchParser.feed("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rss/>", "https://example.org"); }.isFailure)
        val a = WatchParser.monitor("<article><a href='/a'>Story</a><p>5 min ago</p><img src='/picture.png'></article>", "https://example.org", "article")
        val b = WatchParser.monitor("<article><a href='/a'>Story</a><p>6 min ago</p><img src='/picture.png'></article>", "https://example.org", "article")
        assertEquals(a.single().id, b.single().id); assertEquals("https://example.org/picture.png", a.single().image)
        assertTrue(runCatching { WatchParser.monitor("<p>Empty</p>", "https://example.org", "article") }.isFailure)
    }
    @Test fun advancedConfigurationAndToolsBackupRoundTripAndRollback() = runBlocking {
        val a = Room.inMemoryDatabaseBuilder(context, LiteDatabase::class.java).build()
        val b = Room.inMemoryDatabaseBuilder(context, LiteDatabase::class.java).build()
        try {
            val source = LocalRepository(a); val target = LocalRepository(b)
            source.save(LiteApp(id = "premium-entry", name = "Premium", url = "https://example.org", tags = "Work,News", removeTracking = true, dataSaver = true, scriptIds = "script-entry", searchUrl = "https://example.org/search?q=%s"))
            a.tools().save(ToolRecord(id = "script-entry", kind = "script", title = "Shared script", payload = "window.answer=42"))
            a.tools().save(ToolRecord(id = "feed-entry", kind = "feed", title = "News", appId = "premium-entry", url = "https://example.org/feed", snapshot = "private-not-exported"))
            val json = source.export(); assertFalse(json.contains("private-not-exported"))
            assertEquals(1, target.restore(json)); assertTrue(target.get("premium-entry")!!.removeTracking)
            assertEquals("Work,News", target.get("premium-entry")!!.tags); assertEquals(2, b.tools().all().size)
            val invalid = JSONObject(json); invalid.getJSONArray("tools").getJSONObject(0).put("enabled", "false")
            invalid.getJSONArray("apps").getJSONObject(0).put("name", "Should not persist")
            assertTrue(runCatching { target.restore(invalid.toString()) }.isFailure)
            assertEquals("Premium", target.get("premium-entry")!!.name)
        } finally { a.close(); b.close() }
    }
    @Test fun readerSanitizesActiveContentAndTrackingImages() {
        val clean = ReaderActivity.sanitize("<h1>Title</h1><p>Hello</p><script>alert(1)</script><img src='https://tracker.example/x'><iframe src='https://bad.example'></iframe><a href='javascript:alert(2)' onclick='bad()'>Bad link</a>")
        assertTrue(clean.contains("Title")); assertTrue(clean.contains("Hello"))
        assertFalse(clean.contains("<script")); assertFalse(clean.contains("<img")); assertFalse(clean.contains("<iframe")); assertFalse(clean.contains("javascript:")); assertFalse(clean.contains("onclick"))
    }
    @Test fun backgroundSourcesAndPreferenceTypesAreValidated() {
        assertTrue(runCatching { ToolsRepository.validate(ToolRecord(kind = "feed", title = "Unsafe", url = "http://example.org/feed")) }.isFailure)
        assertTrue(runCatching { PreferencesStore.parse(JSONObject().put("syncMinutes", 5)) }.isFailure)
        assertTrue(runCatching { PreferencesStore.parse(JSONObject().put("filterUpdates", "true")) }.isFailure)
        assertTrue(runCatching { ToolsRepository.validate(ToolRecord(kind = "monitor", title = "Broken selector", url = "https://example.org", payload = "[")) }.isFailure)
    }
}
