package org.litespace.browser.ui

import android.graphics.Bitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.litespace.browser.util.IconImages
import org.litespace.browser.domain.LiteApp

@Composable fun AppEditor(app: LiteApp, modifier: Modifier = Modifier, onChange: (LiteApp) -> Unit, onSave: (LiteApp) -> Unit) {
    val latestApp by rememberUpdatedState(app)
    var iconError by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var readingIcon by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri != null && !readingIcon) scope.launch {
            readingIcon = true
            runCatching { withContext(Dispatchers.IO) {
                val bitmap = IconImages.read { context.contentResolver.openInputStream(uri) ?: error("Cannot open image") }
                try {
                    val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    require(bytes.size < 300_000) { "Icon too large" }
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                } finally { bitmap.recycle() }
            } }.onSuccess { onChange(latestApp.copy(icon = it)); iconError = "" }
                .onFailure { iconError = it.message ?: "Cannot read icon" }
            readingIcon = false
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { AppIcon(app); OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !readingIcon) { Text(tr("Choose icon", "انتخاب آیکون")) }; if(app.icon.isNotEmpty()) TextButton(onClick = { onChange(app.copy(icon = "")) }) { Text(tr("Reset", "بازنشانی")) } }
        if(iconError.isNotEmpty()) Text(iconError, color = MaterialTheme.colorScheme.error)
        Field(tr("Name", "نام"), app.name, limit = 100) { onChange(app.copy(name = it)) }
        Field(tr("Website URL", "نشانی وب‌سایت"), app.url, limit = 8192) { onChange(app.copy(url = it)) }
        Field(tr("Group", "گروه"), app.group, limit = 100) { onChange(app.copy(group = it)) }
        Toggle(tr("Favorite", "برگزیده"), app.favorite) { onChange(app.copy(favorite = it)) }
        Text(tr("Accent color", "رنگ برنامه"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0xFF526849, 0xFF375F90, 0xFF85546D, 0xFF885B26).forEach { color -> FilterChip(selected = app.accent == color, onClick = { onChange(app.copy(accent = color)) }, label = { Text(if(app.accent == color) "✓" else "●", color = androidx.compose.ui.graphics.Color(color)) }) } }
        Section(tr("Display & navigation", "نمایش و پیمایش")) {
            Toggle(tr("Immersive fullscreen", "تمام‌صفحه"), app.fullscreen) { onChange(app.copy(fullscreen = it)) }
            Toggle(tr("Show navigation toolbar", "نمایش نوار پیمایش"), app.navigation) { onChange(app.copy(navigation = it)) }
            Toggle(tr("Edge swipe back / forward", "حرکت لبه برای عقب و جلو"), app.gestures) { onChange(app.copy(gestures = it)) }
            Toggle(tr("Desktop website", "نسخه دسکتاپ"), app.desktop) { onChange(app.copy(desktop = it)) }
            Toggle(tr("Darken supported websites", "تیره‌کردن سایت‌های پشتیبانی‌شده"), app.dark) { onChange(app.copy(dark = it)) }
            Toggle(tr("Pinch and button zoom", "بزرگ‌نمایی با لمس و دکمه"), app.zoom) { onChange(app.copy(zoom = it)) }
            Text(tr("Text size: ${app.fontSize}%", "اندازه متن: ${app.fontSize}٪"))
            Slider(app.fontSize.toFloat(), { onChange(app.copy(fontSize = it.toInt())) }, valueRange = 50f..200f, steps = 29)
            Field(tr("Custom User-Agent (optional)", "User-Agent سفارشی (اختیاری)"), app.userAgent, limit = 4096) { onChange(app.copy(userAgent = it)) }
        }
        Section(tr("Privacy & permissions", "حریم خصوصی و مجوزها")) {
            Toggle(tr("Block ads and trackers", "مسدودسازی تبلیغ و ردیاب"), app.blockTrackers) { onChange(app.copy(blockTrackers = it)) }
            Toggle(tr("Require HTTPS", "الزام HTTPS"), app.httpsOnly) { onChange(app.copy(httpsOnly = it)) }
            Toggle(tr("Allow third-party cookies", "اجازه کوکی شخص ثالث"), app.thirdPartyCookies) { onChange(app.copy(thirdPartyCookies = it)) }
            Toggle(tr("Clear profile data on normal exit", "پاک‌کردن داده پروفایل هنگام خروج عادی"), app.clearOnExit) { onChange(app.copy(clearOnExit = it)) }
            Text(tr("Shared profile clearing affects all apps in that profile. On older WebView it affects all apps and may retain service-worker data. Android cannot guarantee exit cleanup after process termination.", "پاک‌کردن پروفایل مشترک بر همه برنامه‌های آن اثر دارد؛ در WebView قدیمی روی همه برنامه‌ها و ممکن است داده سرویس‌ورکر باقی بماند. پس از توقف اجباری، پاک‌سازی هنگام خروج تضمین نمی‌شود."), style = MaterialTheme.typography.bodySmall)
            Field(tr("Shared profile name (blank = own profile)", "نام پروفایل مشترک (خالی = مستقل)"), app.profile, limit = 60) { onChange(app.copy(profile = it)) }
            Toggle(tr("Require biometrics or device PIN", "نیاز به اثر انگشت یا رمز دستگاه"), app.lock) { onChange(app.copy(lock = it)) }
            Text(tr("Locks this entry. Other entries sharing its website storage can still access the same login session.", "این ورودی قفل می‌شود؛ برنامه‌هایی که داده سایت را با آن مشترک دارند همچنان به همان نشست ورود دسترسی دارند."), style = MaterialTheme.typography.bodySmall)
            Toggle(tr("Allow location requests", "اجازه درخواست موقعیت"), app.location) { onChange(app.copy(location = it)) }
            Toggle(tr("Allow camera requests", "اجازه درخواست دوربین"), app.camera) { onChange(app.copy(camera = it)) }
            Toggle(tr("Allow microphone requests", "اجازه درخواست میکروفون"), app.microphone) { onChange(app.copy(microphone = it)) }
            Toggle(tr("Allow pop-ups (with confirmation)", "اجازه پنجره جدید (با تأیید)"), app.popups) { onChange(app.copy(popups = it)) }
            Toggle(tr("Download completion notifications", "اعلان پایان دانلود"), app.downloadNotifications) { onChange(app.copy(downloadNotifications = it)) }
        }
        Section(tr("Custom code", "کد سفارشی")) {
            Toggle(tr("Enable JavaScript", "فعال‌سازی جاوااسکریپت"), app.javascript) { onChange(app.copy(javascript = it)) }
            Text(tr("Only paste code you trust. Code runs on the configured website origin after each page loads. CSS injection also requires JavaScript. Other origins are excluded.", "فقط کد قابل اعتماد وارد کنید. کد پس از بارگذاری در مبدأ سایت تنظیم‌شده اجرا می‌شود. تزریق CSS نیز به جاوااسکریپت نیاز دارد. مبدأهای دیگر مستثنا هستند."), style = MaterialTheme.typography.bodySmall)
            Field(tr("Custom CSS", "CSS سفارشی"), app.css, false) { onChange(app.copy(css = it)) }
            Field(tr("Custom JavaScript", "جاوااسکریپت سفارشی"), app.script, false) { onChange(app.copy(script = it)) }
        }
        Button(onClick = { onSave(app) }, modifier = Modifier.fillMaxWidth(), enabled = !readingIcon && app.name.isNotBlank() && app.url.isNotBlank()) { Text(tr("Save Lite App", "ذخیره برنامه سبک")) }
        Spacer(Modifier.height(24.dp))
    }
}
@Composable private fun Field(label: String, value: String, single: Boolean = true, limit: Int = 64000, change: (String) -> Unit) {
    OutlinedTextField(value, { change(it.take(limit)) }, label = { Text(label) }, singleLine = single, minLines = if(single) 1 else 4, modifier = Modifier.fillMaxWidth())
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
