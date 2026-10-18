package org.litespace.browser.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("settings")
data class Preferences(val theme: String = "system", val language: String = "en", val dynamicColors: Boolean = false, val syncMinutes: Int = 60, val filterUpdates: Boolean = false)
class PreferencesStore(private val context: Context) {
    private val sync = intPreferencesKey("sync_minutes")
    private val filterUpdates = booleanPreferencesKey("filter_updates")
    private val dynamicColors = booleanPreferencesKey("dynamic_colors")
    private val theme = stringPreferencesKey("theme")
    private val language = stringPreferencesKey("language")
    val flow = context.settingsStore.data.catch { error ->
        if(error is IOException) emit(emptyPreferences()) else throw error
    }.map { Preferences(it[theme] ?: "system", it[language] ?: if(java.util.Locale.getDefault().language == "fa") "fa" else "en", it[dynamicColors] ?: false, it[sync] ?: 60, it[filterUpdates] ?: false) }
    suspend fun background(minutes: Int, update: Boolean) { require(minutes in setOf(15, 30, 60, 180, 360, 1440)); context.settingsStore.edit { it[sync] = minutes; it[filterUpdates] = update } }
    suspend fun restore(p: Preferences) { context.settingsStore.edit { it[theme] = p.theme; it[language] = p.language; it[dynamicColors] = p.dynamicColors; it[sync] = p.syncMinutes; it[filterUpdates] = p.filterUpdates } }
    companion object {
        fun parse(o: org.json.JSONObject): Preferences {
            for(k in listOf("theme", "language")) require(!o.has(k) || o.get(k) is String)
            for(k in listOf("dynamicColors", "filterUpdates")) require(!o.has(k) || o.get(k) is Boolean)
            require(!o.has("syncMinutes") || (o.get("syncMinutes") is Number && o.getDouble("syncMinutes") == o.getInt("syncMinutes").toDouble()))
            val p = Preferences(o.optString("theme", "system"), o.optString("language", "en"), o.optBoolean("dynamicColors"), o.optInt("syncMinutes", 60), o.optBoolean("filterUpdates"))
            require(p.theme in setOf("system", "light", "dark") && p.language in setOf("en", "fa"))
            require(p.syncMinutes in setOf(15, 30, 60, 180, 360, 1440))
            return p
        }
    }
    suspend fun setDynamicColors(value: Boolean) { context.settingsStore.edit { it[dynamicColors] = value } }
    suspend fun setTheme(value: String) { context.settingsStore.edit { it[theme] = value } }
    suspend fun setLanguage(value: String) { context.settingsStore.edit { it[language] = value } }
}
