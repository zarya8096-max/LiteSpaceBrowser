package org.litespace.browser.ui

import org.litespace.browser.domain.LiteApp
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.litespace.browser.LiteSpaceApplication
import org.litespace.browser.data.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application as LiteSpaceApplication
    val repository = container.repository
    val apps = repository.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val preferences = container.preferences.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Preferences())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    fun save(app: LiteApp, done: () -> Unit) = viewModelScope.launch {
        runCatching { repository.save(app) }.onSuccess { done() }.onFailure { messages.emit(it.message ?: "Unable to save") }
    }
    fun delete(app: LiteApp) = viewModelScope.launch { repository.delete(app.id) }
    fun theme(value: String) = viewModelScope.launch { container.preferences.setTheme(value) }
    fun language(value: String) = viewModelScope.launch { container.preferences.setLanguage(value) }
    fun updateFilters() = viewModelScope.launch {
        messages.emit("Updating filters…")
        runCatching { container.blocker.update() }.onSuccess { messages.emit("$it blocked domains loaded") }
            .onFailure { messages.emit(it.message ?: "Filter update failed") }
    }
}
