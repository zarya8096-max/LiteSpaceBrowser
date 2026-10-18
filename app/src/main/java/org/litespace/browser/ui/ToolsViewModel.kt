package org.litespace.browser.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import org.litespace.browser.data.ToolRecord

/** Large code drafts stay out of Activity Bundles and survive credential/document-picker recreation. */
class ToolsViewModel(application: Application) : AndroidViewModel(application) {
    val editor = mutableStateOf<ToolRecord?>(null)
    val draft = mutableStateOf(ToolRecord(kind = "script", title = ""))
    val kind = mutableStateOf("script")
    var importingId: String? = null
    fun edit(record: ToolRecord) { editor.value = record; draft.value = record }
}
