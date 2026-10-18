package org.litespace.browser.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import org.litespace.browser.domain.UrlPolicy
import org.jsoup.Jsoup
import java.util.UUID

@Entity(tableName = "tools")
data class ToolRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val appId: String = "",
    val title: String,
    val url: String = "",
    val payload: String = "",
    val enabled: Boolean = true,
    val snapshot: String = "",
    val checkedAt: Long = 0,
    val error: String = ""
)
@Dao interface ToolDao {
    @Query("SELECT * FROM tools ORDER BY title COLLATE NOCASE") fun observe(): Flow<List<ToolRecord>>
    @Query("SELECT * FROM tools ORDER BY id") suspend fun all(): List<ToolRecord>
    @Query("SELECT * FROM tools WHERE id = :id") suspend fun get(id: String): ToolRecord?
    @Upsert suspend fun save(record: ToolRecord)
    @Query("DELETE FROM tools WHERE appId = :appId AND kind IN ('feed', 'monitor')") suspend fun deleteWatches(appId: String)
    @Query("DELETE FROM tools WHERE id = :id") suspend fun delete(id: String)
}
class ToolsRepository(val dao: ToolDao) {
    val items = dao.observe()
    suspend fun save(record: ToolRecord) { validate(record); dao.save(record) }
    companion object {
        val kinds = setOf("script", "list", "bookmark", "feed", "monitor")
        fun validate(r: ToolRecord) {
            require(r.id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
            require(r.kind in kinds && r.title.isNotBlank() && r.title.length <= 100)
            require(r.appId.isEmpty() || r.appId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
            require(r.payload.length <= 64000 && r.url.length <= 8192)
            if(r.kind != "script") require(UrlPolicy.normalize(r.url) == r.url) { "Use an HTTPS URL without credentials" }
            if(r.kind == "script") require(r.payload.isNotBlank()) { "Script is empty" }
            if(r.kind == "monitor") { require(r.payload.isNotBlank()); Jsoup.parse("<html><body></body></html>").select(r.payload) }
        }
        fun json(r: ToolRecord) = JSONObject().put("id", r.id).put("kind", r.kind).put("appId", r.appId).put("title", r.title).put("url", r.url).put("payload", r.payload).put("enabled", r.enabled)
        fun parse(o: JSONObject): ToolRecord {
            for(k in listOf("id", "kind", "appId", "title", "url", "payload")) require(!o.has(k) || o.get(k) is String) { "Invalid tool field: $k" }
            require(!o.has("enabled") || o.get("enabled") is Boolean)
            return ToolRecord(id = o.getString("id"), kind = o.getString("kind"), appId = o.optString("appId"), title = o.getString("title"), url = o.optString("url"), payload = o.optString("payload"), enabled = o.optBoolean("enabled", true)).also(::validate)
        }
    }
}
