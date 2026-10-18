package org.litespace.browser.ui

import org.litespace.browser.util.readBounded
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.litespace.browser.LiteSpaceApplication
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.web.BrowserActivity
import org.litespace.browser.web.Shortcuts
import androidx.webkit.WebViewFeature
import org.litespace.browser.util.IconImages
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Dashboard(model: MainViewModel) {
    val apps by model.apps.collectAsStateWithLifecycle()
    val prefs by model.preferences.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("home") }
    var editing by model.editor
        var filtering by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LiteApp?>(null) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    val createLabel = tr("Create Lite App", "ساخت برنامه سبک")
    val scope = rememberCoroutineScope()
    val container = context.applicationContext as LiteSpaceApplication
    val blocking by container.blocker.stats.collectAsStateWithLifecycle()
    fun authorizeBackup(title: String, action: () -> Unit) {
        scope.launch {
            runCatching { model.repository.apps.first().any { it.lock } }
                .onSuccess { if(it) DeviceAuth.verify(context as FragmentActivity, title, success = action) else action() }
                .onFailure { snack.showSnackbar(it.message ?: "Cannot read configurations") }
        }
    }
    fun authorizeApp(app: LiteApp, action: (LiteApp) -> Unit) {
        scope.launch {
            val current = model.repository.get(app.id) ?: return@launch
            if(current.lock) DeviceAuth.verify(context as FragmentActivity, "Unlock configuration") { action(current) }
            else action(current)
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) authorizeBackup("Unlock backup export") { scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val bytes = model.repository.export().toByteArray(Charsets.UTF_8)
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Cannot open backup")
            } }
                .onSuccess { snack.showSnackbar("Backup saved") }.onFailure { snack.showSnackbar(it.message ?: "Backup failed") }
        } }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { val bytes = it.readBounded(8_000_001); require(bytes.size <= 8_000_000); bytes.toString(Charsets.UTF_8) } ?: error("Cannot read backup") } }
                .onSuccess { restoreText = it }.onFailure { snack.showSnackbar("Invalid or oversized backup") }
        }
    }
    LaunchedEffect(model) { model.messages.collect { snack.showSnackbar(it) } }
    LaunchedEffect(screen, editing == null) { if(screen == "edit" && editing == null) screen = "home" }
    fun leaveScreen() {
        if(screen == "edit" && model.hasDraftChanges()) confirmDiscard = true
        else { screen = "home"; model.discardDraft() }
    }
    BackHandler(screen != "home") { leaveScreen() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
        if(screen != "edit" && screen != "backup") NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf(Triple("home", tr("My apps", "برنامه‌ها"), Icons.Default.Home), Triple("privacy", tr("Privacy", "حریم خصوصی"), Icons.Default.Lock), Triple("settings", tr("Settings", "تنظیمات"), Icons.Default.Settings)).forEach { (route, label, icon) ->
                NavigationBarItem(selected = screen == route, onClick = { screen = route }, icon = { Icon(icon, null) }, label = { Text(label) })
            }
        }
    }, topBar = {
        TopAppBar(title = { Text(when(screen) { "edit" -> tr("App configuration", "تنظیمات برنامه"); "settings" -> tr("Settings", "تنظیمات"); "privacy" -> tr("Privacy", "حریم خصوصی"); "backup" -> tr("Backup & restore", "پشتیبان‌گیری و بازیابی"); else -> "LiteSpace" }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            navigationIcon = { if(screen != "home") IconButton(onClick = { leaveScreen() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "بازگشت")) } },
            actions = { if(screen == "home") {
                if(filtering) IconButton(onClick = { model.beginEditing(LiteApp()); screen = "edit" }) { Icon(Icons.Default.Add, createLabel) }
                IconButton(onClick = { screen = "settings" }) { Icon(Icons.Default.Settings, tr("Settings", "تنظیمات")) }
            } })
    }, floatingActionButton = {
        if(screen == "home" && !filtering) ExtendedFloatingActionButton(onClick = { model.beginEditing(LiteApp()); screen = "edit" }, modifier = Modifier.semantics { contentDescription = createLabel }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(createLabel) })
    }) { padding ->
        when(screen) {
            "edit" -> editing?.let { AppEditor(it, Modifier.padding(padding).consumeWindowInsets(padding), onChange = { editing = it }, onSave = { model.save(it) { model.discardDraft(); screen = "home" } }) }
            "settings" -> SettingsScreen(prefs, Modifier.padding(padding), model::theme, model::language, model::dynamicColors, { screen = "privacy" }, { screen = "backup" })
            "privacy" -> LazyColumn(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { PageIntro(tr("PRIVACY, IN PLAIN SIGHT", "حریم خصوصی، روشن و شفاف"), tr("Your browsing\nstays yours.", "مرور شما،\nمتعلق به شماست."), tr("See what is protected and understand the limits.", "حفاظت‌های فعال و محدودیت‌ها را بشناسید.")) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrivacyStat(localNumber(blocking.domains), tr("Domains in filters", "دامنه در فیلترها"), Modifier.weight(1f))
                    PrivacyStat(localNumber(blocking.requests.toLong()), tr("Blocked this session", "مسدودشده در این اجرا"), Modifier.weight(1f))
                } }
                item { Button(onClick = model::updateFilters, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(tr("Update StevenBlack filters", "به‌روزرسانی فیلترهای StevenBlack")) } }
                item { SettingsPanel(tr("Content blocking", "مسدودسازی محتوا")) { Text(tr("Domain blocking only: no cosmetic filtering. Updates contact raw.githubusercontent.com. Some ads and first-party tracking cannot be blocked.", "فقط مسدودسازی دامنه؛ بدون فیلتر ظاهری. به‌روزرسانی به raw.githubusercontent.com متصل می‌شود. همه تبلیغات و ردیاب‌ها قابل مسدودسازی نیستند.")) } }
                item { SettingsPanel(tr("Website storage", "داده‌های وب‌سایت")) { Text(if(runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)) tr("Isolated WebView profiles are supported on this device. Apps use separate profiles by default; matching profile names deliberately share sessions.", "پروفایل‌های جدا پشتیبانی می‌شوند. برنامه‌ها به‌طور پیش‌فرض جدا هستند؛ نام پروفایل یکسان نشست مشترک ایجاد می‌کند.") else tr("This WebView shares cookies and website storage across apps. Incognito is unavailable. Update Android System WebView for profile support. Clearing data affects every app.", "این WebView کوکی و داده سایت را بین برنامه‌ها به اشتراک می‌گذارد. حالت ناشناس در دسترس نیست. WebView سیستم را به‌روز کنید. پاک‌کردن داده روی همه برنامه‌ها اثر دارد.")) } }
                item { SettingsPanel(tr("Notifications", "اعلان‌ها")) { Text(tr("Web push and service-worker notifications are not supported by Android WebView. LiteSpace does not simulate them or poll websites in the background. Downloads use Android Download Manager notifications.", "اعلان وب و سرویس‌ورکر در WebView اندروید پشتیبانی نمی‌شوند. برنامه آن‌ها را شبیه‌سازی نمی‌کند و در پس‌زمینه سایت‌ها را بررسی نمی‌کند. اعلان دانلود از مدیریت دانلود اندروید استفاده می‌کند.")) } }
                item { SettingsPanel(tr("Private sessions", "نشست‌های خصوصی")) { Text(tr("Private sessions require isolated profiles and full data-clearing support. Browsing data clears on normal close; profile folders are removed on a browser launch after the process restarts. Force-stop can delay cleanup. Downloads remain. Your IP address and network activity are not hidden.", "نشست خصوصی به پروفایل جدا و پاک‌سازی کامل نیاز دارد. داده هنگام خروج عادی پاک می‌شود؛ پوشه پروفایل در اجرای مرورگر پس از راه‌اندازی مجدد فرایند حذف می‌شود. توقف اجباری پاک‌سازی را به تأخیر می‌اندازد. دانلود، نشانی IP و فعالیت شبکه مخفی نمی‌شوند.")) } }
            }
            "backup" -> BackupScreen(Modifier.padding(padding), apps.size, { export.launch("litespace-backup.json") }, { restore.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
            else -> LibraryScreen(apps, Modifier.padding(padding),
                onOpen = { context.startActivity(Intent(context, BrowserActivity::class.java).putExtra("app_id", it.id)) },
                onEdit = { app -> authorizeApp(app) { model.beginEditing(it); screen = "edit" } },
                onPin = { app -> scope.launch { snack.showSnackbar(Shortcuts.pin(context, app)) } },
                onPrivate = { context.startActivity(Intent(context, BrowserActivity::class.java).putExtra("app_id", it.id).putExtra("incognito", true)) },
                onDelete = { app -> authorizeApp(app) { deleting = it } },
                onCreate = { model.beginEditing(LiteApp()); screen = "edit" }, onFilterActive = { filtering = it })
        }
    }
    if(confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text(tr("Discard your changes?", "تغییرات کنار گذاشته شود؟")) },
        text = { Text(tr("Your changes have not been saved yet.", "تغییرات شما هنوز ذخیره نشده است.")) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; model.discardDraft(); screen = "home" }) { Text(tr("Discard changes", "کنار گذاشتن تغییرات"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(tr("Keep editing", "ادامهٔ ویرایش")) } })
    deleting?.let { app -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("Delete ${app.name}?", "حذف ${app.name}؟")) }, text = { Text(tr("Removes this configuration. Clear its website data in the browser first if needed. Existing shortcuts will no longer open it.", "تنظیمات حذف می‌شود. در صورت نیاز ابتدا داده سایت را در مرورگر پاک کنید. میانبر قبلی دیگر آن را باز نمی‌کند.")) }, confirmButton = { TextButton(onClick = { model.delete(app); deleting = null }) { Text(tr("Delete", "حذف")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Cancel", "لغو")) } }) }
    restoreText?.let { text -> AlertDialog(onDismissRequest = { restoreText = null }, title = { Text(tr("Restore app configurations?", "بازیابی تنظیمات برنامه؟")) }, text = { Text(tr("Matching IDs are overwritten; other apps are kept. Only import trusted files: custom JavaScript will run when you open imported apps.", "شناسه‌های یکسان جایگزین و بقیه حفظ می‌شوند. فقط فایل قابل اعتماد وارد کنید؛ جاوااسکریپت سفارشی هنگام بازشدن برنامه اجرا می‌شود.")) }, confirmButton = { TextButton(onClick = { restoreText = null; authorizeBackup("Unlock restore") { scope.launch { runCatching { withContext(Dispatchers.IO) { model.repository.restore(text) } }.onSuccess { snack.showSnackbar("Restored $it apps") }.onFailure { snack.showSnackbar(it.message ?: "Restore failed") } } } }) { Text(tr("Restore", "بازیابی")) } }, dismissButton = { TextButton(onClick = { restoreText = null }) { Text(tr("Cancel", "لغو")) } }) }
}

@Composable fun AppIcon(app: LiteApp, size: Dp = 48.dp) {
    val bitmap = remember(app.icon) { IconImages.decode(app.icon) }
    if(bitmap != null) Image(bitmap.asImageBitmap(), app.name, Modifier.size(size).clip(RoundedCornerShape(16.dp)))
    else Surface(modifier = Modifier.size(size).clip(RoundedCornerShape(16.dp)), shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.Color(app.accent)) { Box(contentAlignment = Alignment.Center) { Text(app.name.trim().takeIf { it.isNotEmpty() }?.let { String(Character.toChars(it.codePointAt(0))).uppercase(java.util.Locale.ROOT) } ?: "L", color = if(androidx.compose.ui.graphics.Color(app.accent).luminance() > .45f) androidx.compose.ui.graphics.Color(0xFF132D21) else androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge) } }
}

@Composable private fun PrivacyStat(value: String, label: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
