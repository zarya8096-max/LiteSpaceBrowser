package org.litespace.browser.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import org.litespace.browser.R
import org.litespace.browser.data.Preferences

val LocalPersian = staticCompositionLocalOf { false }
@Composable fun tr(en: String, fa: String) = if(LocalPersian.current) fa else en
@Composable fun localNumber(value: Int): String = localNumber(value.toLong())
@Composable fun localNumber(value: Long): String = if(LocalPersian.current) value.toString().map { if(it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it-'0'] else it }.joinToString("") else value.toString()

private val Day = lightColorScheme(
    primary = Color(0xFF235B46), onPrimary = Color.White, primaryContainer = Color(0xFFD7EDDA), onPrimaryContainer = Color(0xFF143D2D),
    secondary = Color(0xFF596B5A), secondaryContainer = Color(0xFFE8EEDC), onSecondaryContainer = Color(0xFF303F2D),
    tertiary = Color(0xFF896642), tertiaryContainer = Color(0xFFF7E5CD), onTertiaryContainer = Color(0xFF503718),
    background = Color(0xFFF7F8F2), onBackground = Color(0xFF1B2721), surface = Color(0xFFFCFDF8), onSurface = Color(0xFF1B2721),
    surfaceVariant = Color(0xFFE8ECE3), onSurfaceVariant = Color(0xFF647168), outline = Color(0xFF78867C), outlineVariant = Color(0xFFDCE3D9),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F4EC), surfaceContainer = Color(0xFFECEFE6), surfaceContainerHigh = Color(0xFFE5EADF)
)
private val Night = darkColorScheme(
    primary = Color(0xFFA7DDB8), onPrimary = Color(0xFF133B28), primaryContainer = Color(0xFF244D38), onPrimaryContainer = Color(0xFFD2F2DB),
    secondary = Color(0xFFC3CFB5), secondaryContainer = Color(0xFF354331), onSecondaryContainer = Color(0xFFE0EAD4),
    tertiary = Color(0xFFE8C39A), tertiaryContainer = Color(0xFF59432C), onTertiaryContainer = Color(0xFFFFDFC0),
    background = Color(0xFF101914), onBackground = Color(0xFFE4EBE1), surface = Color(0xFF141E18), onSurface = Color(0xFFE4EBE1),
    surfaceVariant = Color(0xFF2B3830), onSurfaceVariant = Color(0xFFB0BEB2), outline = Color(0xFF7E9183), outlineVariant = Color(0xFF334239),
    surfaceContainerLowest = Color(0xFF0B130E), surfaceContainerLow = Color(0xFF19251D), surfaceContainer = Color(0xFF1F2C23), surfaceContainerHigh = Color(0xFF28362C)
)
private val SpaceTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 43.sp, letterSpacing = (-.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 37.sp, letterSpacing = (-.4).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 33.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp), bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp), labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 22.sp)
)
private val PersianFont = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)
private val PersianTypography = SpaceTypography.copy(
    displayLarge = SpaceTypography.displayLarge.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    displayMedium = SpaceTypography.displayMedium.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    displaySmall = SpaceTypography.displaySmall.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    headlineLarge = SpaceTypography.headlineLarge.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    headlineMedium = SpaceTypography.headlineMedium.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    headlineSmall = SpaceTypography.headlineSmall.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    titleLarge = SpaceTypography.titleLarge.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    titleMedium = SpaceTypography.titleMedium.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    titleSmall = SpaceTypography.titleSmall.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    bodyLarge = SpaceTypography.bodyLarge.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    bodyMedium = SpaceTypography.bodyMedium.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    bodySmall = SpaceTypography.bodySmall.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    labelLarge = SpaceTypography.labelLarge.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    labelMedium = SpaceTypography.labelMedium.copy(fontFamily = PersianFont, letterSpacing = 0.sp),
    labelSmall = SpaceTypography.labelSmall.copy(fontFamily = PersianFont, letterSpacing = 0.sp)
)
@Composable fun LiteSpaceTheme(preferences: Preferences, accent: Long? = null, content: @Composable () -> Unit) {
    val dark = preferences.theme == "dark" || (preferences.theme == "system" && isSystemInDarkTheme())
    val activity = LocalContext.current as? android.app.Activity
    SideEffect { activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
        isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
    } } }
    val colors = if(preferences.dynamicColors && Build.VERSION.SDK_INT >= 31) {
        if(dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if(dark) Night else Day
    CompositionLocalProvider(LocalPersian provides (preferences.language == "fa"), LocalLayoutDirection provides if(preferences.language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = if(accent != null) colors.copy(primary = if(dark) androidx.compose.ui.graphics.lerp(Color(accent), Color.White, .55f) else Color(accent)) else colors, typography = if(preferences.language == "fa") PersianTypography else SpaceTypography,
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)), content = content)
    }
}
