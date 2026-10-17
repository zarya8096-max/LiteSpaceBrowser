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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.litespace.browser.LiteSpaceApplication
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.web.BrowserActivity
import org.litespace.browser.web.Shortcuts
import androidx.webkit.WebViewFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Dashboard(model: MainViewModel) {
    val apps by model.apps.collectAsStateWithLifecycle()
    val prefs by model.preferences.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("home") }
    var editing by rememberSaveable { mutableStateOf<LiteApp?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<LiteApp?>(null) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = context.applicationContext as LiteSpaceApplication
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(model.repository.export().toByteArray()) } ?: error("Cannot open backup") } }
                .onSuccess { snack.showSnackbar("Backup saved") }.onFailure { snack.showSnackbar(it.message ?: "Backup failed") }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { val bytes = it.readBounded(8_000_001); require(bytes.size <= 8_000_000); bytes.toString(Charsets.UTF_8) } ?: error("Cannot read backup") } }
                .onSuccess { restoreText = it }.onFailure { snack.showSnackbar("Invalid or oversized backup") }
        }
    }
    LaunchedEffect(model) { model.messages.collect { snack.showSnackbar(it) } }
    BackHandler(screen != "home") { screen = "home"; editing = null }
    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(title = { Text(when(screen) { "edit" -> tr("App configuration", "تنظیمات برنامه"); "settings" -> tr("Settings", "تنظیمات"); "privacy" -> tr("Privacy", "حریم خصوصی"); "backup" -> tr("Backup & restore", "پشتیبان‌گیری و بازیابی"); else -> "LiteSpace" }) },
            navigationIcon = { if(screen != "home") IconButton(onClick = { screen = "home" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "بازگشت")) } },
            actions = { if(screen == "home") IconButton(onClick = { screen = "settings" }) { Icon(Icons.Default.Settings, tr("Settings", "تنظیمات")) } })
    }, floatingActionButton = {
        if(screen == "home") ExtendedFloatingActionButton(onClick = { editing = LiteApp(); screen = "edit" }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(tr("Create Lite App", "ساخت برنامه سبک")) })
    }) { padding ->
        when(screen) {
            "edit" -> editing?.let { AppEditor(it, Modifier.padding(padding), onSave = { model.save(it) { screen = "home" } }) }
            "settings" -> Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tr("Make space for what matters.", "فضایی برای آنچه مهم است."), style = MaterialTheme.typography.headlineSmall)
                Text(tr("Appearance", "ظاهر"), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system", "light", "dark").forEach { mode -> FilterChip(selected = prefs.theme == mode, onClick = { model.theme(mode) }, label = { Text(when(mode) { "light" -> tr("Light", "روشن"); "dark" -> tr("Dark", "تیره"); else -> tr("System", "سیستم") }) }) } }
                Text(tr("Language", "زبان"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(prefs.language == "en", { model.language("en") }, label = { Text("English") }); FilterChip(prefs.language == "fa", { model.language("fa") }, label = { Text("فارسی") }) }
                FilledTonalButton(onClick = { screen = "privacy" }, modifier = Modifier.fillMaxWidth()) { Text(tr("Privacy dashboard", "داشبورد حریم خصوصی")) }
                FilledTonalButton(onClick = { screen = "backup" }, modifier = Modifier.fillMaxWidth()) { Text(tr("Backup & restore", "پشتیبان‌گیری و بازیابی")) }
                Text(tr("Open source. No analytics. No subscriptions.", "متن‌باز. بدون آمارگیری. بدون اشتراک."))
            }
            "privacy" -> LazyColumn(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(tr("Your browsing stays yours", "مرور شما متعلق به شماست"), style = MaterialTheme.typography.headlineMedium) }
                item { Text(tr("${container.blocker.count} blocked domains • ${container.blocker.blocked.get()} requests blocked this process", "${container.blocker.count} دامنه مسدود • ${container.blocker.blocked.get()} درخواست مسدود در این اجرا")) }
                item { Button(onClick = model::updateFilters) { Text(tr("Update StevenBlack filters", "به‌روزرسانی فیلترهای StevenBlack")) } }
                item { Text(tr("Domain blocking only: no cosmetic filtering. Updates contact raw.githubusercontent.com. Some ads and first-party tracking cannot be blocked.", "فقط مسدودسازی دامنه؛ بدون فیلتر ظاهری. به‌روزرسانی به raw.githubusercontent.com متصل می‌شود. همه تبلیغات و ردیاب‌ها قابل مسدودسازی نیستند.")) }
                item { Text(if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) tr("Isolated WebView profiles are supported on this device. Apps use separate profiles by default; matching profile names deliberately share sessions.", "پروفایل‌های جدا پشتیبانی می‌شوند. برنامه‌ها به‌طور پیش‌فرض جدا هستند؛ نام پروفایل یکسان نشست مشترک ایجاد می‌کند.") else tr("This WebView shares cookies and website storage across apps. Incognito is unavailable. Update Android System WebView for profile support. Clearing data affects every app.", "این WebView کوکی و داده سایت را بین برنامه‌ها به اشتراک می‌گذارد. حالت ناشناس در دسترس نیست. WebView سیستم را به‌روز کنید. پاک‌کردن داده روی همه برنامه‌ها اثر دارد.")) }
                item { Text(tr("Web push and service-worker notifications are not supported by Android WebView. LiteSpace does not simulate them or poll websites in the background. Downloads use Android Download Manager notifications.", "اعلان وب و سرویس‌ورکر در WebView اندروید پشتیبانی نمی‌شوند. برنامه آن‌ها را شبیه‌سازی نمی‌کند و در پس‌زمینه سایت‌ها را بررسی نمی‌کند. اعلان دانلود از مدیریت دانلود اندروید استفاده می‌کند.")) }
                item { Text(tr("Incognito uses a temporary on-disk profile deleted after a normal close and on the next browser launch after a crash. Downloads remain. It does not hide your IP address, network traffic or activity from websites.", "حالت ناشناس پروفایل موقت روی دیسک دارد که پس از خروج عادی یا اجرای بعدی مرورگر پاک می‌شود. دانلودها باقی می‌مانند. نشانی IP و فعالیت شما از شبکه و سایت‌ها مخفی نمی‌شود.")) }
            }
            "backup" -> Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(tr("Keep your space", "فضای خود را نگه دارید"), style = MaterialTheme.typography.headlineMedium)
                Text(tr("Exports names, icons, URLs and app settings as plain JSON. Cookies, login sessions and global theme/language are excluded. Custom scripts and URLs can contain secrets; store backups safely.", "نام، آیکون، نشانی و تنظیمات برنامه به صورت JSON ذخیره می‌شود. کوکی، نشست ورود و زبان و ظاهر کلی ذخیره نمی‌شود. اسکریپت و نشانی ممکن است حاوی اطلاعات محرمانه باشد؛ فایل را امن نگه دارید."))
                Button(onClick = { export.launch("litespace-backup.json") }) { Text(tr("Export backup", "ذخیره پشتیبان")) }
                OutlinedButton(onClick = { val launch = { restore.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }; if(apps.any { it.lock }) DeviceAuth.verify(context as androidx.fragment.app.FragmentActivity, "Unlock restore", launch) else launch() }) { Text(tr("Restore backup", "بازیابی پشتیبان")) }
            }
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(tr("Your web. Your space.", "وب شما. فضای شما."), style = MaterialTheme.typography.headlineLarge) }
                item { Text(tr("${apps.size} Lite Apps · Less clutter, more focus", "${apps.size} برنامه سبک · ساده‌تر و متمرکزتر"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(tr("Search apps or groups", "جستجوی برنامه یا گروه")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(24.dp)) }
                item { AnimatedVisibility(apps.isEmpty()) { Card { Column(Modifier.padding(24.dp)) { Icon(Icons.Default.Home, null, Modifier.size(48.dp)); Spacer(Modifier.height(16.dp)); Text(tr("A lighter way to browse", "راهی سبک‌تر برای مرور"), style = MaterialTheme.typography.titleLarge); Text(tr("Turn any website into a personal app. Start with a name and a URL.", "هر وب‌سایت را به برنامه شخصی تبدیل کنید. با نام و نشانی شروع کنید.")) } } } }
                items(apps.filter { "${it.name} ${it.url} ${it.group}".contains(query, true) }, key = { it.id }) { app ->
                    Card(onClick = { context.startActivity(Intent(context, BrowserActivity::class.java).putExtra("app_id", app.id)) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app)
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(app.name, style = MaterialTheme.typography.titleLarge); Text(app.group.ifBlank { android.net.Uri.parse(app.url).host ?: app.url }, style = MaterialTheme.typography.bodySmall) }
                            var menu by remember { mutableStateOf(false) }
                            Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("App actions", "عملیات برنامه")) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(text = { Text(tr("Edit", "ویرایش")) }, onClick = { menu = false; if(app.lock) DeviceAuth.verify(context as androidx.fragment.app.FragmentActivity, "Unlock configuration") { editing = app; screen = "edit" } else { editing = app; screen = "edit" } })
                                    DropdownMenuItem(text = { Text(tr("Pin to home screen", "افزودن به صفحه اصلی")) }, onClick = { menu = false; scope.launch { snack.showSnackbar(Shortcuts.pin(context, app)) } })
                                    DropdownMenuItem(text = { Text(tr("Private session", "نشست خصوصی")) }, onClick = { menu = false; context.startActivity(Intent(context, BrowserActivity::class.java).putExtra("app_id", app.id).putExtra("incognito", true)) })
                                    DropdownMenuItem(text = { Text(tr("Delete", "حذف")) }, onClick = { menu = false; if(app.lock) DeviceAuth.verify(context as androidx.fragment.app.FragmentActivity, "Unlock configuration") { deleting = app } else deleting = app })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { app -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("Delete ${app.name}?", "حذف ${app.name}؟")) }, text = { Text(tr("Removes this configuration. Clear its website data in the browser first if needed. Existing shortcuts will no longer open it.", "تنظیمات حذف می‌شود. در صورت نیاز ابتدا داده سایت را در مرورگر پاک کنید. میانبر قبلی دیگر آن را باز نمی‌کند.")) }, confirmButton = { TextButton(onClick = { model.delete(app); Shortcuts.disable(context, app); deleting = null }) { Text(tr("Delete", "حذف")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Cancel", "لغو")) } }) }
    restoreText?.let { text -> AlertDialog(onDismissRequest = { restoreText = null }, title = { Text(tr("Restore app configurations?", "بازیابی تنظیمات برنامه؟")) }, text = { Text(tr("Matching IDs are overwritten; other apps are kept. Only import trusted files: custom JavaScript will run when you open imported apps.", "شناسه‌های یکسان جایگزین و بقیه حفظ می‌شوند. فقط فایل قابل اعتماد وارد کنید؛ جاوااسکریپت سفارشی هنگام بازشدن برنامه اجرا می‌شود.")) }, confirmButton = { TextButton(onClick = { restoreText = null; scope.launch { runCatching { withContext(Dispatchers.IO) { model.repository.restore(text) } }.onSuccess { snack.showSnackbar("Restored $it apps") }.onFailure { snack.showSnackbar(it.message ?: "Restore failed") } } }) { Text(tr("Restore", "بازیابی")) } }, dismissButton = { TextButton(onClick = { restoreText = null }) { Text(tr("Cancel", "لغو")) } }) }
}

@Composable fun AppIcon(app: LiteApp) {
    val bitmap = remember(app.icon) { runCatching { val bytes = Base64.decode(app.icon, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
    if(bitmap != null) Image(bitmap.asImageBitmap(), app.name, Modifier.size(48.dp))
    else Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.Color(app.accent)) { Box(contentAlignment = Alignment.Center) { Text(app.name.take(1).uppercase(), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge) } }
}
