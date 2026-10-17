package org.litespace.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import org.litespace.browser.domain.LiteApp

@Composable fun AppEditor(initial: LiteApp, modifier: Modifier = Modifier, onSave: (LiteApp) -> Unit) {
    var app by rememberSaveable(initial.id) { mutableStateOf(initial) }
    var iconError by remember { mutableStateOf("") }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri != null) runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
            val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 192).coerceAtLeast(1) }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("Cannot open image")
            val scaled = Bitmap.createScaledBitmap(bitmap, 192, 192, true)
            val bytes = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            require(bytes.size < 300_000)
            app = app.copy(icon = Base64.encodeToString(bytes, Base64.NO_WRAP))
            iconError = ""
        }.onFailure { iconError = it.message ?: "Cannot read icon" }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { AppIcon(app); OutlinedButton(onClick = { picker.launch("image/*") }) { Text(tr("Choose icon", "انتخاب آیکون")) }; if(app.icon.isNotEmpty()) TextButton(onClick = { app = app.copy(icon = "") }) { Text(tr("Reset", "بازنشانی")) } }
        if(iconError.isNotEmpty()) Text(iconError, color = MaterialTheme.colorScheme.error)
        Field(tr("Name", "نام"), app.name) { app = app.copy(name = it) }
        Field(tr("Website URL", "نشانی وب‌سایت"), app.url) { app = app.copy(url = it) }
        Field(tr("Group", "گروه"), app.group) { app = app.copy(group = it) }
        Toggle(tr("Favorite", "برگزیده"), app.favorite) { app = app.copy(favorite = it) }
        Text(tr("Accent color", "رنگ برنامه"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0xFF526849, 0xFF375F90, 0xFF85546D, 0xFF885B26).forEach { color -> FilterChip(selected = app.accent == color, onClick = { app = app.copy(accent = color) }, label = { Text(if(app.accent == color) "✓" else "●", color = androidx.compose.ui.graphics.Color(color)) }) } }
        Section(tr("Display & navigation", "نمایش و پیمایش")) {
            Toggle(tr("Immersive fullscreen", "تمام‌صفحه"), app.fullscreen) { app = app.copy(fullscreen = it) }
            Toggle(tr("Show navigation toolbar", "نمایش نوار پیمایش"), app.navigation) { app = app.copy(navigation = it) }
            Toggle(tr("Edge swipe back / forward", "حرکت لبه برای عقب و جلو"), app.gestures) { app = app.copy(gestures = it) }
            Toggle(tr("Desktop website", "نسخه دسکتاپ"), app.desktop) { app = app.copy(desktop = it) }
            Toggle(tr("Darken supported websites", "تیره‌کردن سایت‌های پشتیبانی‌شده"), app.dark) { app = app.copy(dark = it) }
            Toggle(tr("Pinch and button zoom", "بزرگ‌نمایی با لمس و دکمه"), app.zoom) { app = app.copy(zoom = it) }
            Text(tr("Text size: ${app.fontSize}%", "اندازه متن: ${app.fontSize}٪"))
            Slider(app.fontSize.toFloat(), { app = app.copy(fontSize = it.toInt()) }, valueRange = 50f..200f, steps = 29)
            Field(tr("Custom User-Agent (optional)", "User-Agent سفارشی (اختیاری)"), app.userAgent) { app = app.copy(userAgent = it) }
        }
        Section(tr("Privacy & permissions", "حریم خصوصی و مجوزها")) {
            Toggle(tr("Block ads and trackers", "مسدودسازی تبلیغ و ردیاب"), app.blockTrackers) { app = app.copy(blockTrackers = it) }
            Toggle(tr("Require HTTPS", "الزام HTTPS"), app.httpsOnly) { app = app.copy(httpsOnly = it) }
            Toggle(tr("Allow third-party cookies", "اجازه کوکی شخص ثالث"), app.thirdPartyCookies) { app = app.copy(thirdPartyCookies = it) }
            Toggle(tr("Clear profile data on normal exit", "پاک‌کردن داده پروفایل هنگام خروج عادی"), app.clearOnExit) { app = app.copy(clearOnExit = it) }
            Text(tr("Shared profile clearing affects all apps in that profile. On older WebView it affects all apps. Android cannot guarantee exit cleanup after process termination.", "پاک‌کردن پروفایل مشترک بر همه برنامه‌های آن اثر دارد؛ در WebView قدیمی روی همه برنامه‌ها. پس از بسته‌شدن اجباری، پاک‌سازی هنگام خروج تضمین نمی‌شود."), style = MaterialTheme.typography.bodySmall)
            Field(tr("Shared profile name (blank = own profile)", "نام پروفایل مشترک (خالی = مستقل)"), app.profile) { app = app.copy(profile = it) }
            Toggle(tr("Require biometrics or device PIN", "نیاز به اثر انگشت یا رمز دستگاه"), app.lock) { app = app.copy(lock = it) }
            Toggle(tr("Allow location requests", "اجازه درخواست موقعیت"), app.location) { app = app.copy(location = it) }
            Toggle(tr("Allow camera requests", "اجازه درخواست دوربین"), app.camera) { app = app.copy(camera = it) }
            Toggle(tr("Allow microphone requests", "اجازه درخواست میکروفون"), app.microphone) { app = app.copy(microphone = it) }
            Toggle(tr("Allow pop-ups (with confirmation)", "اجازه پنجره جدید (با تأیید)"), app.popups) { app = app.copy(popups = it) }
            Toggle(tr("Download completion notifications", "اعلان پایان دانلود"), app.downloadNotifications) { app = app.copy(downloadNotifications = it) }
        }
        Section(tr("Custom code", "کد سفارشی")) {
            Toggle(tr("Enable JavaScript", "فعال‌سازی جاوااسکریپت"), app.javascript) { app = app.copy(javascript = it) }
            Text(tr("Only paste code you trust. Code runs on the configured website origin after each page loads. CSS injection also requires JavaScript. Other origins are excluded.", "فقط کد قابل اعتماد وارد کنید. کد پس از بارگذاری در مبدأ سایت تنظیم‌شده اجرا می‌شود. تزریق CSS نیز به جاوااسکریپت نیاز دارد. مبدأهای دیگر مستثنا هستند."), style = MaterialTheme.typography.bodySmall)
            Field(tr("Custom CSS", "CSS سفارشی"), app.css, false) { app = app.copy(css = it) }
            Field(tr("Custom JavaScript", "جاوااسکریپت سفارشی"), app.script, false) { app = app.copy(script = it) }
        }
        Button(onClick = { onSave(app) }, modifier = Modifier.fillMaxWidth(), enabled = app.name.isNotBlank() && app.url.isNotBlank()) { Text(tr("Save Lite App", "ذخیره برنامه سبک")) }
        Spacer(Modifier.height(24.dp))
    }
}
@Composable private fun Field(label: String, value: String, single: Boolean = true, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = single, minLines = if(single) 1 else 4, modifier = Modifier.fillMaxWidth())
}
@Composable private fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, change) }
}
@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text("$title ${if(expanded) "−" else "+"}", style = MaterialTheme.typography.titleMedium) }
        androidx.compose.animation.AnimatedVisibility(expanded) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}
