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
        require(app.id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "Invalid app ID" }
        require(app.name.isNotBlank() && app.name.length <= 100) { "Name must contain 1–100 characters" }
        require(app.fontSize in 50..200)
        require(app.userAgent.length <= 4096 && '\n' !in app.userAgent && '\r' !in app.userAgent) { "Invalid User-Agent" }
        require(app.url.length <= 8192 && app.group.length <= 100) { "URL or group is too long" }
        require(app.css.length <= 64000 && app.script.length <= 64000) { "Custom code is limited to 64,000 characters per field" }
        require(app.icon.length <= 400_000) { "Icon exceeds 400,000 characters" }
        require(app.profile.length <= 60 && app.profile.matches(Regex("[a-zA-Z0-9_-]*"))) { "Profile: use letters, numbers, underscores or dashes" }
        val normalized = UrlPolicy.normalize(app.url, app.httpsOnly)
        require(normalized.length <= 8192) { "Encoded URL exceeds 8192 characters" }
        dao.save(LiteAppEntity(app.id, app.copy(url = normalized, updatedAt = System.currentTimeMillis())))
    }
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun export(): String {
        val array = JSONArray()
        val configurations = dao.all().map { it.configuration }
        require(configurations.size <= MAX_BACKUP_APPS) { "A JSON backup supports up to $MAX_BACKUP_APPS apps" }
        configurations.forEach { app ->
            val obj = JSONObject()
            // Only immutable configuration fields; no cookies, credentials or browsing history.
            LiteApp::class.java.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach { field ->
                field.isAccessible = true
                obj.put(field.name, field.get(app))
            }
            array.put(obj)
        }
        val json = JSONObject().put("version", 1).put("apps", array).toString(2)
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup exceeds 8 MB; reduce custom icons/code before exporting" }
        return json
    }
    override suspend fun restore(json: String): Int {
        require(json.length <= MAX_BACKUP_BYTES && json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup too large (8 MB limit)" }
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "Unsupported backup version" }
        val list = root.getJSONArray("apps")
        require(list.length() <= MAX_BACKUP_APPS) { "Backup exceeds $MAX_BACKUP_APPS entries" }
        val ids = mutableSetOf<String>()
        val apps = (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            validateTypes(o)
            val id = o.getString("id")
            require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
            require(ids.add(id)) { "Duplicate app ID in backup: $id" }
            LiteApp(id = id, name = o.getString("name"), url = o.getString("url"),
                icon = o.optString("icon"),
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
    private fun validateTypes(obj: JSONObject) {
        for(field in LiteApp::class.java.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }) {
            if(!obj.has(field.name)) continue
            val value = obj.get(field.name)
            val valid = when(field.type) {
                String::class.java -> value is String
                Boolean::class.javaPrimitiveType -> value is Boolean
                Int::class.javaPrimitiveType -> value is Number && value.toDouble() == value.toInt().toDouble()
                Long::class.javaPrimitiveType -> value is Number && value.toDouble() == value.toLong().toDouble()
                else -> false
            }
            require(valid) { "Invalid type for ${field.name}" }
        }
    }
    companion object {
        const val MAX_BACKUP_BYTES = 8_000_000
        const val MAX_BACKUP_APPS = 5000
    }
}
