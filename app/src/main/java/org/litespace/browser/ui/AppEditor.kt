package org.litespace.browser.ui

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.litespace.browser.domain.UrlPolicy
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
import org.litespace.browser.domain.enabledScriptIds
import org.litespace.browser.LiteSpaceApplication
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalLayoutApi::class)
@Composable fun AppEditor(app: LiteApp, modifier: Modifier = Modifier, onChange: (LiteApp) -> Unit, onSave: (LiteApp) -> Unit) {
    val latestApp by rememberUpdatedState(app)
    var iconError by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tools by (context.applicationContext as LiteSpaceApplication).tools.items.collectAsStateWithLifecycle(emptyList())
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
    val validUrl = remember(app.url, app.httpsOnly) { app.url.isNotBlank() && runCatching { UrlPolicy.normalize(app.url, app.httpsOnly) }.isSuccess }
    Column(modifier.fillMaxSize().imePadding()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon(app.copy(name = app.name.ifBlank { "L" }), 60.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(app.name.ifBlank { tr("Your next favorite", "برنامهٔ محبوب بعدی شما") }, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(tr("A little app. A space of its own.", "برنامه‌ای سبک، با فضایی مخصوص خود."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        SettingsPanel(tr("The essentials", "اطلاعات اصلی")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !readingIcon) { Text(if(readingIcon) tr("Loading…", "در حال بارگذاری…") else tr("Choose icon", "انتخاب آیکون")) }
            if(app.icon.isNotEmpty()) TextButton(onClick = { onChange(app.copy(icon = "")) }) { Text(tr("Reset", "بازنشانی")) }
        }
        if(iconError.isNotEmpty()) Text(iconError, color = MaterialTheme.colorScheme.error)
        Field(tr("Name", "نام"), app.name, limit = 100) { onChange(app.copy(name = it)) }
        Field(tr("Website URL", "نشانی وب‌سایت"), app.url, limit = 8192, keyboard = KeyboardType.Uri) { onChange(app.copy(url = it)) }
        if(app.url.isNotBlank() && !validUrl) Text(tr("Enter a valid website address, such as example.com", "نشانی معتبر وارد کنید؛ مانند example.com"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        SettingsPanel(tr("Make it yours", "به سلیقهٔ خودتان")) {
        Field(tr("Tags (comma separated)", "برچسب‌ها (جداشده با ویرگول)"), app.tags, limit = 2000) { onChange(app.copy(tags = it)) }
        Field(tr("Group", "گروه"), app.group, limit = 100) { onChange(app.copy(group = it)) }
        Toggle(tr("Favorite", "برگزیده"), app.favorite) { onChange(app.copy(favorite = it)) }
        Text(tr("Accent color", "رنگ برنامه"), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { listOf(0xFF526849, 0xFF375F90, 0xFF85546D, 0xFF885B26).forEach { color -> FilterChip(selected = app.accent == color, onClick = { onChange(app.copy(accent = color)) }, label = { Text(if(app.accent == color) "✓" else "●", color = androidx.compose.ui.graphics.Color(color)) }) } }
        }
        Section(tr("Display & navigation", "نمایش و پیمایش")) {
            Toggle(tr("Load images", "بارگذاری تصویرها"), app.images) { onChange(app.copy(images = it)) }
            Toggle(tr("Request reduced data (site-dependent)", "درخواست کاهش مصرف داده (وابسته به سایت)"), app.dataSaver) { onChange(app.copy(dataSaver = it)) }
            Toggle(tr("Pull to refresh", "کشیدن برای نوسازی"), app.pullToRefresh) { onChange(app.copy(pullToRefresh = it)) }
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
            Toggle(tr("Remove known tracking URL parameters", "حذف پارامترهای ردیابی نشانی"), app.removeTracking) { onChange(app.copy(removeTracking = it)) }
            Toggle(tr("Open external origins in another browser", "بازکردن مبدأهای دیگر در مرورگر خارجی"), app.externalLinks) { onChange(app.copy(externalLinks = it)) }
            Toggle(tr("Always use a private session (requires WebView support)", "همیشه نشست خصوصی (نیازمند پشتیبانی WebView)"), app.incognito) { onChange(app.copy(incognito = it)) }
            Toggle(tr("Block ads and trackers", "مسدودسازی تبلیغ و ردیاب"), app.blockTrackers) { onChange(app.copy(blockTrackers = it)) }
            Toggle(tr("Require HTTPS", "الزام HTTPS"), app.httpsOnly) { onChange(app.copy(httpsOnly = it)) }
            Toggle(tr("Allow third-party cookies", "اجازه کوکی شخص ثالث"), app.thirdPartyCookies) { onChange(app.copy(thirdPartyCookies = it)) }
            Toggle(tr("Clear profile data on normal exit", "پاک‌کردن داده پروفایل هنگام خروج عادی"), app.clearOnExit) { onChange(app.copy(clearOnExit = it)) }
            Text(tr("Shared profile clearing affects all apps in that profile. On older WebView it affects all apps and may retain service-worker data. Android cannot guarantee exit cleanup after process termination.", "پاک‌کردن پروفایل مشترک بر همه برنامه‌های آن اثر دارد؛ در WebView قدیمی روی همه برنامه‌ها و ممکن است داده سرویس‌ورکر باقی بماند. پس از توقف اجباری، پاک‌سازی هنگام خروج تضمین نمی‌شود."), style = MaterialTheme.typography.bodySmall)
            if(android.os.Build.VERSION.SDK_INT >= 28) {
                Text(tr("Fixed isolated sandboxes (Android 9+)", "محیط‌های جداگانهٔ ثابت (اندروید ۹+)"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    org.litespace.browser.web.LegacySandboxes.names.forEach { name -> FilterChip(app.profile == name, { onChange(app.copy(profile = if(app.profile == name) "" else name)) }, label = { Text(name) }) }
                }
            }
            Field(tr("Shared profile name (blank = own profile)", "نام پروفایل مشترک (خالی = مستقل)"), app.profile, limit = 60) { onChange(app.copy(profile = it)) }
            Toggle(tr("Require biometrics or device PIN", "نیاز به اثر انگشت یا رمز دستگاه"), app.lock) { onChange(app.copy(lock = it)) }
            Text(tr("Locks this entry. Other entries sharing its website storage can still access the same login session.", "این ورودی قفل می‌شود؛ برنامه‌هایی که داده سایت را با آن مشترک دارند همچنان به همان نشست ورود دسترسی دارند."), style = MaterialTheme.typography.bodySmall)
            Toggle(tr("Allow location requests", "اجازه درخواست موقعیت"), app.location) { onChange(app.copy(location = it)) }
            Toggle(tr("Allow camera requests", "اجازه درخواست دوربین"), app.camera) { onChange(app.copy(camera = it)) }
            Toggle(tr("Allow microphone requests", "اجازه درخواست میکروفون"), app.microphone) { onChange(app.copy(microphone = it)) }
            Toggle(tr("Allow pop-ups (with confirmation)", "اجازه پنجره جدید (با تأیید)"), app.popups) { onChange(app.copy(popups = it)) }
            Toggle(tr("Download completion notifications", "اعلان پایان دانلود"), app.downloadNotifications) { onChange(app.copy(downloadNotifications = it)) }
        }
        Section(tr("Native integrations", "ارتباط با اندروید")) {
            Field(tr("Search URL (%s = query)", "نشانی جستجو (%s = عبارت)"), app.searchUrl, limit = 8192, keyboard = KeyboardType.Uri) { onChange(app.copy(searchUrl = it)) }
            Field(tr("Share URL (%s = text, %u = URL, %t = title)", "نشانی اشتراک (%s متن، %u نشانی، %t عنوان)"), app.shareUrl, limit = 8192, keyboard = KeyboardType.Uri) { onChange(app.copy(shareUrl = it)) }
        }
        Section(tr("Script library", "کتابخانهٔ اسکریپت")) {
            tools.filter { it.kind == "script" }.forEach { record -> Toggle(record.title, record.id in app.enabledScriptIds()) { checked ->
                val ids = app.enabledScriptIds().toMutableSet(); if(checked) ids.add(record.id) else ids.remove(record.id)
                onChange(app.copy(scriptIds = ids.joinToString(",")))
            } }
            TextButton(onClick = { context.startActivity(android.content.Intent(context, ToolsActivity::class.java)) }) { Text(tr("Manage scripts", "مدیریت اسکریپت‌ها")) }
        }
        Section(tr("Custom code", "کد سفارشی")) {
            Toggle(tr("Enable JavaScript", "فعال‌سازی جاوااسکریپت"), app.javascript) { onChange(app.copy(javascript = it)) }
            Text(tr("Only paste code you trust. Code runs on the configured website origin after each page loads. CSS injection also requires JavaScript. Other origins are excluded.", "فقط کد قابل اعتماد وارد کنید. کد پس از بارگذاری در مبدأ سایت تنظیم‌شده اجرا می‌شود. تزریق CSS نیز به جاوااسکریپت نیاز دارد. مبدأهای دیگر مستثنا هستند."), style = MaterialTheme.typography.bodySmall)
            Field(tr("Custom CSS", "CSS سفارشی"), app.css, false) { onChange(app.copy(css = it)) }
            Field(tr("Custom JavaScript", "جاوااسکریپت سفارشی"), app.script, false) { onChange(app.copy(script = it)) }
        }
    }
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 8.dp) {
        Button(onClick = { onSave(latestApp) }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().heightIn(min = 54.dp), enabled = !readingIcon && app.name.isNotBlank() && validUrl) {
            Icon(Icons.Default.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Save Lite App", "ذخیره برنامه سبک"))
        }
    }
    }
}
@Composable private fun Field(label: String, value: String, single: Boolean = true, limit: Int = 64000, keyboard: KeyboardType = KeyboardType.Text, change: (String) -> Unit) {
    OutlinedTextField(value, { change(it.take(limit)) }, label = { Text(label) }, singleLine = single, minLines = if(single) 1 else 5,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = keyboard != KeyboardType.Uri && single),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = if(keyboard == KeyboardType.Uri || !single) TextDirection.Ltr else TextDirection.Content,
            fontFamily = if(!single) FontFamily.Monospace else FontFamily.Default),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
}
@Composable internal fun Toggle(label: String, checked: Boolean, description: String? = null, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = change).padding(vertical = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onCheckedChange = null)
    }
}
@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = if(expanded) tr("Expanded", "باز") else tr("Collapsed", "بسته")
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { stateDescription = state }, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(if(expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
            }
            androidx.compose.animation.AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            }
        }
    }
}
