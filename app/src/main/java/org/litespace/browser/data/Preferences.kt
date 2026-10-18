package org.litespace.browser.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("settings")
data class Preferences(val theme: String = "system", val language: String = "en", val dynamicColors: Boolean = false)
class PreferencesStore(private val context: Context) {
    private val dynamicColors = booleanPreferencesKey("dynamic_colors")
    private val theme = stringPreferencesKey("theme")
    private val language = stringPreferencesKey("language")
    val flow = context.settingsStore.data.catch { error ->
        if(error is IOException) emit(emptyPreferences()) else throw error
    }.map { Preferences(it[theme] ?: "system", it[language] ?: if(java.util.Locale.getDefault().language == "fa") "fa" else "en", it[dynamicColors] ?: false) }
    suspend fun setDynamicColors(value: Boolean) { context.settingsStore.edit { it[dynamicColors] = value } }
    suspend fun setTheme(value: String) { context.settingsStore.edit { it[theme] = value } }
    suspend fun setLanguage(value: String) { context.settingsStore.edit { it[language] = value } }
}
