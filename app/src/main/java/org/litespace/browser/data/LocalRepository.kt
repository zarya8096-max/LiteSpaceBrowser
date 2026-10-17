package org.litespace.browser.data

import org.litespace.browser.domain.LiteApp
import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import org.litespace.browser.domain.AppRepository
import org.litespace.browser.domain.UrlPolicy

class LocalRepository(private val db: LiteDatabase) : AppRepository {
    private val dao = db.apps()
    override val apps = dao.observe().map { entries -> entries.map { it.configuration } }
    override suspend fun get(id: String) = dao.get(id)?.configuration
    override suspend fun save(app: LiteApp) {
        require(app.name.isNotBlank() && app.name.length <= 100) { "Name must contain 1–100 characters" }
        require(app.fontSize in 50..200)
        require(app.userAgent.length <= 4096 && '\n' !in app.userAgent && '\r' !in app.userAgent) { "Invalid User-Agent" }
        require(app.url.length <= 8192 && app.group.length <= 100) { "URL or group is too long" }
        require(app.css.length <= 64000 && app.script.length <= 64000) { "Custom code is limited to 64,000 characters per field" }
        require(app.profile.length <= 60 && app.profile.matches(Regex("[a-zA-Z0-9_-]*"))) { "Profile: use letters, numbers, underscores or dashes" }
        dao.save(LiteAppEntity(app.id, app.copy(url = UrlPolicy.normalize(app.url, app.httpsOnly), updatedAt = System.currentTimeMillis())))
    }
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun export(): String {
        val array = JSONArray()
        dao.all().map { it.configuration }.forEach { app ->
            val obj = JSONObject()
            // Only immutable configuration fields; no cookies, credentials or browsing history.
            LiteApp::class.java.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach { field ->
                field.isAccessible = true
                obj.put(field.name, field.get(app))
            }
            array.put(obj)
        }
        return JSONObject().put("version", 1).put("apps", array).toString(2)
    }
    override suspend fun restore(json: String): Int {
        require(json.length <= 8_000_000) { "Backup too large (8 MB limit)" }
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "Unsupported backup version" }
        val list = root.getJSONArray("apps")
        require(list.length() <= 5000) { "Backup exceeds 5000 entries" }
        val apps = (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            val id = o.getString("id")
            require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
            LiteApp(id = id, name = o.getString("name"), url = o.getString("url"),
                icon = o.optString("icon").takeIf { it.length <= 400_000 } ?: "",
                group = o.optString("group"), favorite = o.optBoolean("favorite"),
                javascript = o.optBoolean("javascript", true), desktop = o.optBoolean("desktop"),
                userAgent = o.optString("userAgent"), css = o.optString("css"), script = o.optString("script"),
                dark = o.optBoolean("dark"), fontSize = o.optInt("fontSize", 100), zoom = o.optBoolean("zoom", true),
                thirdPartyCookies = o.optBoolean("thirdPartyCookies"), blockTrackers = o.optBoolean("blockTrackers", true),
                clearOnExit = o.optBoolean("clearOnExit"), httpsOnly = o.optBoolean("httpsOnly", true),
                location = o.optBoolean("location"), camera = o.optBoolean("camera"), microphone = o.optBoolean("microphone"),
                popups = o.optBoolean("popups"), fullscreen = o.optBoolean("fullscreen"), navigation = o.optBoolean("navigation", true),
                gestures = o.optBoolean("gestures", true), lock = o.optBoolean("lock"),
                downloadNotifications = o.optBoolean("downloadNotifications", true), profile = o.optString("profile"),
                accent = o.optLong("accent", 0xFF526849))
        }
        db.withTransaction { apps.forEach { save(it) } }
        return apps.size
    }
}
