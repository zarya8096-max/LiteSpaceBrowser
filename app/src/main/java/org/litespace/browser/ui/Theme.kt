package org.litespace.browser.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import org.litespace.browser.data.Preferences

val LocalPersian = staticCompositionLocalOf { false }
@Composable fun tr(en: String, fa: String) = if(LocalPersian.current) fa else en
@Composable fun LiteSpaceTheme(preferences: Preferences, accent: Long? = null, content: @Composable () -> Unit) {
    val dark = preferences.theme == "dark" || (preferences.theme == "system" && isSystemInDarkTheme())
    val activity = LocalContext.current as? android.app.Activity
    SideEffect {
        activity?.let {
            androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if(Build.VERSION.SDK_INT >= 31) {
        if(dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if(dark) darkColorScheme(primary = Color(0xFFB4D4A4)) else lightColorScheme(primary = Color(0xFF526849), secondaryContainer = Color(0xFFE0EBD9))
    CompositionLocalProvider(LocalPersian provides (preferences.language == "fa"),
        LocalLayoutDirection provides if(preferences.language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = if(accent != null) colors.copy(primary = Color(accent)) else colors, content = content)
    }
}
