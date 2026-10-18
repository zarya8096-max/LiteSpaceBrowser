package org.litespace.browser.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.litespace.browser.data.Preferences

@Composable internal fun PageIntro(eyebrow: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun SettingsPanel(title: String, description: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}
@Composable private fun SettingsLink(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SettingsScreen(prefs: Preferences, modifier: Modifier, onTheme: (String) -> Unit, onLanguage: (String) -> Unit,
    onDynamic: (Boolean) -> Unit, onPrivacy: () -> Unit, onBackup: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().testTag("settings"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageIntro(tr("PERSONALIZE", "به سلیقهٔ شما"), tr("Make it feel\nlike you.", "فضایی شبیه\nخودتان."), tr("A few thoughtful choices for your everyday space.", "چند انتخاب ساده برای فضای هر روزتان.")) }
        item { SettingsPanel(tr("Appearance", "ظاهر"), tr("Choose the light that feels right.", "روشنایی مناسب خودتان را انتخاب کنید.")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("system", "light", "dark").forEach { mode -> FilterChip(prefs.theme == mode, { onTheme(mode) }, label = { Text(when(mode) { "light" -> tr("Light", "روشن"); "dark" -> tr("Dark", "تیره"); else -> tr("System", "سیستم") }) }, leadingIcon = { if(prefs.theme == mode) Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) }
            }
            if(Build.VERSION.SDK_INT >= 31) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Toggle(tr("Use wallpaper colors", "رنگ‌های تصویر زمینه"), prefs.dynamicColors, tr("Material You colors from your Android wallpaper.", "رنگ‌های Material You بر اساس تصویر زمینهٔ اندروید."), onDynamic)
            }
        } }
        item { SettingsPanel(tr("Language", "زبان"), tr("Your words. Your direction.", "زبان شما، با چیدمان مناسب شما.")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(prefs.language == "en", { onLanguage("en") }, label = { Text("English") }, leadingIcon = { if(prefs.language == "en") Icon(Icons.Default.Check, null, Modifier.size(16.dp)) })
                FilterChip(prefs.language == "fa", { onLanguage("fa") }, label = { Text("فارسی") }, leadingIcon = { if(prefs.language == "fa") Icon(Icons.Default.Check, null, Modifier.size(16.dp)) })
            }
        } }
        item { SettingsLink(tr("Privacy dashboard", "داشبورد حریم خصوصی"), tr("Blocking, profiles & permissions", "مسدودسازی، پروفایل‌ها و مجوزها"), Icons.Default.Lock, onPrivacy) }
        item { SettingsLink(tr("Backup & restore", "پشتیبان‌گیری و بازیابی"), tr("Take your collection with you", "مجموعه‌تان را همراه خود داشته باشید"), Icons.Default.Refresh, onBackup) }
        item { Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("LiteSpace", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(tr("Open source. No analytics. Always yours.", "متن‌باز، بدون آمارگیری، متعلق به شما."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}
@Composable internal fun BackupScreen(modifier: Modifier, count: Int, onExport: () -> Unit, onRestore: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageIntro(tr("YOUR COLLECTION, TO GO", "مجموعهٔ شما، همراه شما"), tr("Keep your space", "فضای خود را نگه دارید"), tr("A fresh device. The same familiar collection.", "دستگاه تازه، با همان مجموعهٔ آشنا.")) }
        item { SettingsPanel(tr("Save a copy", "یک نسخه نگه دارید"), tr("${localNumber(count)} apps · Names, icons, addresses and individual settings.", "${localNumber(count)} برنامه · نام‌ها، آیکون‌ها، نشانی‌ها و تنظیمات هر برنامه.")) {
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Export backup", "ذخیره پشتیبان")) }
        } }
        item { SettingsPanel(tr("Bring your apps back", "برنامه‌هایتان را برگردانید"), tr("Choose a LiteSpace JSON backup. Review and confirm before anything is restored.", "فایل JSON پشتیبان لایت‌اسپیس را انتخاب کنید؛ بازیابی پس از تأیید شما انجام می‌شود.")) {
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Restore backup", "بازیابی پشتیبان")) }
        } }
        item { Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(tr("What stays on your device", "چه چیزهایی روی دستگاه می‌ماند؟"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(tr("Cookies, login sessions, theme and language are not included. Backups are plain JSON: addresses and custom code may contain secrets. Store them safely and restore only trusted files.", "کوکی‌ها، نشست‌های ورود، ظاهر و زبان در بکاپ نیستند. فایل JSON رمزگذاری نمی‌شود؛ نشانی‌ها و کد سفارشی ممکن است محرمانه باشند. آن را امن نگه دارید و فقط فایل قابل اعتماد را بازیابی کنید."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        } }
    }
}
