package org.litespace.browser

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import org.litespace.browser.ui.*

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: MainViewModel = viewModel()
            val preferences by model.preferences.collectAsStateWithLifecycle()
            LiteSpaceTheme(preferences) { Dashboard(model) }
        }
    }
}
