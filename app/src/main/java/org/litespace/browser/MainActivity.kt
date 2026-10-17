package org.litespace.browser

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.activity.viewModels
import android.view.WindowManager
import org.litespace.browser.ui.*

class MainActivity : FragmentActivity() {
    private val model: MainViewModel by viewModels()
    private var editorLocked by mutableStateOf(false)
    private var authenticating = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editorLocked = model.editor.value?.lock == true
        enableEdgeToEdge()
        setContent {
            val preferences by model.preferences.collectAsStateWithLifecycle()
            val protected = model.editor.value?.lock == true
            SideEffect {
                if(protected) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            LiteSpaceTheme(preferences) {
                Box(Modifier.fillMaxSize()) {
                    Dashboard(model)
                    if(editorLocked && protected) Surface(Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) { Text(tr("Authentication required", "احراز هویت لازم است")) }
                    }
                }
            }
        }
    }
    override fun onStop() {
        if(model.editor.value?.lock == true) editorLocked = true
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        if(editorLocked && model.editor.value?.lock == true && !authenticating) {
            authenticating = true
            DeviceAuth.verify(this, "Unlock configuration", failure = {
                authenticating = false; model.editor.value = null; editorLocked = false
            }) { authenticating = false; editorLocked = false }
        }
    }
}
