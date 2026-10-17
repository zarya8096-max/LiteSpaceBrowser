package org.litespace.browser.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")
data class Preferences(val theme: String = "system", val language: String = "en")
class PreferencesStore(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val language = stringPreferencesKey("language")
    val flow = context.settingsStore.data.map { Preferences(it[theme] ?: "system", it[language] ?: if(java.util.Locale.getDefault().language == "fa") "fa" else "en") }
    suspend fun setTheme(value: String) { context.settingsStore.edit { it[theme] = value } }
    suspend fun setLanguage(value: String) { context.settingsStore.edit { it[language] = value } }
}
