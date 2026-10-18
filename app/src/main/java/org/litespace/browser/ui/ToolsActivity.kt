package org.litespace.browser.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.litespace.browser.LiteSpaceApplication
import org.litespace.browser.data.*
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.domain.UrlPolicy
import org.litespace.browser.util.readBounded
import org.litespace.browser.web.BrowserActivity
import android.content.Intent
import android.net.Uri

class ToolsActivity : FragmentActivity() {
    private val model: ToolsViewModel by viewModels()
    private val importScript = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = model.importingId; model.importingId = null
        if(uri != null && id != null) lifecycleScope.launch {
            try {
                val source = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBounded(256001); require(bytes.size <= 256000)
                    bytes.toString(Charsets.UTF_8).also { require(it.length <= 64000) }
                } ?: error("Cannot open script") }
                if(model.draft.value.id == id && model.editor.value != null) model.draft.value = model.draft.value.copy(payload = source)
            } catch(e: CancellationException) { throw e } catch(e: Exception) { android.widget.Toast.makeText(this@ToolsActivity, e.message ?: "Cannot import script", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    private var access by mutableStateOf(false)
    private var needsAuth = true
    private var authenticating = false
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val container = application as LiteSpaceApplication
        setContent {
            val prefs by container.preferences.flow.collectAsStateWithLifecycle(Preferences())
            LiteSpaceTheme(prefs) {
                Surface(Modifier.fillMaxSize()) {
                    if(access) ToolsScreen(container, model, intent.getStringExtra("app_id").orEmpty(), { finish() }, { model.importingId = model.draft.value.id; importScript.launch(arrayOf("text/*", "application/javascript", "application/octet-stream")) }, {
                        if(Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) else Box(Modifier.padding(24.dp)) { Text(tr("Authentication required", "احراز هویت لازم است")) }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if(needsAuth && !authenticating) {
            needsAuth = false
            authenticating = true
            lifecycleScope.launch {
                val protected = (application as LiteSpaceApplication).repository.apps.first().any { it.lock }
                if(protected) DeviceAuth.verify(this@ToolsActivity, "Unlock browser tools", failure = { authenticating = false; finish() }) { authenticating = false; needsAuth = false; access = true }
                else { authenticating = false; needsAuth = false; access = true }
            }
        }
    }
    override fun onStop() { super.onStop(); if(!isChangingConfigurations) { access = false; needsAuth = true } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun ToolsScreen(container: LiteSpaceApplication, model: ToolsViewModel, appId: String, back: () -> Unit, onImport: () -> Unit, askNotifications: () -> Unit) {
    val persian = LocalPersian.current
    fun label(en: String, fa: String) = if(persian) fa else en
    val records by container.tools.items.collectAsStateWithLifecycle(emptyList())
    val apps by container.repository.apps.collectAsStateWithLifecycle(emptyList())
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(Preferences())
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var kind by model.kind
    var editor by model.editor
    var deleting by remember { mutableStateOf<ToolRecord?>(null) }
    fun work(block: suspend () -> String) { scope.launch { try { snack.showSnackbar(block()) } catch(e: CancellationException) { throw e } catch(e: Exception) { snack.showSnackbar(e.message ?: "Failed") } } }
    val labels = mapOf("script" to label("Scripts", "اسکریپت‌ها"), "list" to label("Block lists", "فهرست‌های فیلتر"), "bookmark" to label("Bookmarks", "نشانک‌ها"), "feed" to label("RSS / Atom", "RSS / Atom"), "monitor" to label("Web monitors", "پایش صفحات"))
    Scaffold(topBar = { TopAppBar(title = { Text(label("Browser tools", "ابزارهای مرورگر")) }, navigationIcon = { TextButton(onClick = back) { Text(label("Back", "بازگشت")) } }) }, snackbarHost = { SnackbarHost(snack) }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { model.edit(ToolRecord(kind = kind, title = "", appId = if(kind in setOf("script", "list")) "" else appId)) }) { Text(label("Add", "افزودن")) }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { labels.forEach { (id, label) -> FilterChip(kind == id, { kind = id }, label = { Text(label) }) } } }
            if(kind == "list") item { SettingsPanel(label("Selected lists", "فهرست‌های انتخاب‌شده")) {
                Text(label("HOSTS/domain lists only. Disable or remove a list to stop using its rules. A small built-in safety seed remains; individual apps can disable blocking.", "فقط قالب HOSTS/دامنه. با غیرفعال یا حذف‌کردن، قوانین آن فهرست کنار گذاشته می‌شوند. فهرست پایهٔ کوچکی باقی می‌ماند؛ مسدودسازی برای هر برنامه قابل خاموش‌کردن است."))
                Button(onClick = { work { "${container.blocker.update()} domains" } }) { Text(label("Update selected lists", "به‌روزرسانی فهرست‌های فعال")) }
                Toggle(label("Weekly background filter updates", "به‌روزرسانی هفتگی فیلتر در پس‌زمینه"), prefs.filterUpdates) { value -> work { container.preferences.background(prefs.syncMinutes, value); SyncScheduler.reconcile(context); label("Saved", "ذخیره شد") } }
            } }
            if(kind in setOf("feed", "monitor")) item { SettingsPanel(label("Scheduled updates", "بررسی دوره‌ای")) {
                Text(label("Opt-in polling, not Web Push. First check establishes a baseline. Android may delay work. Private and protected apps are excluded. Cookies are sent only to the app's exact HTTPS origin; redirects never receive cross-origin cookies.", "بررسی دوره‌ای اختیاری است، نه پوش وب. بار اول مبنای مقایسه ثبت می‌شود. اندروید ممکن است اجرا را عقب بیندازد. برنامه‌های خصوصی و قفل‌شده مستثنا هستند. کوکی فقط به مبدأ دقیق HTTPS همان برنامه فرستاده می‌شود و به مبدأ دیگر در تغییرمسیر نمی‌رود."))
                Text(label("Interval (minutes)", "فاصلهٔ بررسی (دقیقه)"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(15,30,60,180,360,1440).forEach { minutes -> FilterChip(prefs.syncMinutes == minutes, { work { container.preferences.background(minutes, prefs.filterUpdates); SyncScheduler.reconcile(context); label("Saved", "ذخیره شد") } }, label = { Text(localNumber(minutes)) }) } }
                Button(onClick = { askNotifications(); SyncScheduler.checkNow(context); work { label("Check queued; refresh below for status", "بررسی در صف قرار گرفت؛ نتیجه در وضعیت زیر نمایش داده می‌شود") } }) { Text(label("Check now", "بررسی اکنون")) }
                TextButton(onClick = { val id = appId.ifBlank { apps.firstOrNull()?.id.orEmpty() }; if(id.isNotBlank()) context.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "site-$id")) }) { Text(label("Android notification settings", "تنظیمات اعلان اندروید")) }
            } }
            if(kind == "script") item { Text(label("Scripts are shared definitions. Enable them per Lite App in its editor. Automatic scripts are restricted to that app's configured origin. Manual scripts run only after you select and confirm them in the browser.", "تعریف اسکریپت‌ها مشترک است؛ در ویرایشگر هر برنامه فعالشان کنید. اجرای خودکار به مبدأ همان برنامه محدود است. اجرای دستی فقط با انتخاب و تأیید شما در مرورگر انجام می‌شود.")) }
            val visible = records.filter { it.kind == kind && (kind in setOf("script", "list") || appId.isBlank() || it.appId.isBlank() || it.appId == appId) }
            if(visible.isEmpty()) item { Text(label("No entries yet. Add your own.", "هنوز موردی نیست؛ مورد جدید اضافه کنید.")) }
            items(visible, key = { it.id }) { record ->
                SettingsPanel(record.title) {
                    Text(if(record.kind == "script") (if(record.enabled) label("Automatic", "خودکار") else label("Run manually", "اجرای دستی")) else record.url, maxLines = 3)
                    apps.firstOrNull { it.id == record.appId }?.let { Text(it.name, style = MaterialTheme.typography.labelMedium) }
                    if(record.checkedAt > 0) Text(label("Last check: ", "آخرین بررسی: ") + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(record.checkedAt)), style = MaterialTheme.typography.bodySmall)
                    if(record.error.isNotBlank()) Text(record.error, color = MaterialTheme.colorScheme.error)
                    if(record.kind != "script" && record.kind != "bookmark") Toggle(label("Enabled", "فعال"), record.enabled) { checked -> work { container.tools.save(record.copy(enabled = checked)); SyncScheduler.reconcile(context); label("Saved", "ذخیره شد") } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { model.edit(record) }) { Text(label("Edit", "ویرایش")) }
                        if(record.kind == "bookmark") TextButton(onClick = {
                            if(apps.any { it.id == record.appId }) context.startActivity(Intent(context, BrowserActivity::class.java).putExtra("app_id", record.appId).putExtra("start_url", record.url))
                            else context.startActivity(Intent(context, BrowserActivity::class.java).putExtra("transient_url", record.url))
                        }) { Text(label("Open", "بازکردن")) }
                        TextButton(onClick = { deleting = record }) { Text(label("Delete", "حذف"), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if(kind == "script") item { TextButton(onClick = { work {
                container.tools.save(ToolRecord(id = "builtin-quiet", kind = "script", title = "Quiet page", enabled = false, payload = "document.querySelectorAll('video,audio').forEach(el=>el.pause());"))
                container.tools.save(ToolRecord(id = "builtin-readable", kind = "script", title = "Comfortable line spacing", payload = "document.body.style.lineHeight='1.8';document.body.style.wordSpacing='.06em';"))
                label("Original sample scripts added", "اسکریپت‌های نمونه اضافه شدند")
            } }) { Text(label("Add sample scripts", "افزودن اسکریپت نمونه")) } }
        }
    }
    editor?.let { initial -> ToolEditor(initial, model, apps, onImport, { editor = null }, onSave = { record -> work {
        container.tools.save(record); if(record.kind in setOf("feed", "monitor") && record.enabled) askNotifications()
        SyncScheduler.reconcile(context); editor = null; label("Saved", "ذخیره شد")
    } }) }
    deleting?.let { record -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(label("Delete ${record.title}?", "حذف ${record.title}؟")) }, confirmButton = { TextButton(onClick = { work { container.tools.dao.delete(record.id); SyncScheduler.reconcile(context); deleting = null; label("Deleted", "حذف شد") } }) { Text(label("Delete", "حذف")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(label("Cancel", "لغو")) } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ToolEditor(initial: ToolRecord, model: ToolsViewModel, apps: List<LiteApp>, onImport: () -> Unit, close: () -> Unit, onSave: (ToolRecord) -> Unit) {
    val persian = LocalPersian.current
    fun label(en: String, fa: String) = if(persian) fa else en
    var draft by model.draft
    var error by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun normalized() = draft.copy(url = if(draft.kind == "script") "" else UrlPolicy.normalize(draft.url), snapshot = if(draft.url != initial.url || draft.payload != initial.payload) "" else initial.snapshot)
    AlertDialog(onDismissRequest = close, title = { Text(label("Configure", "پیکربندی")) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(draft.title, { draft = draft.copy(title = it.take(100)) }, label = { Text(label("Name", "نام")) }, singleLine = true)
            if(draft.kind != "script") OutlinedTextField(draft.url, { draft = draft.copy(url = it.take(8192)) }, label = { Text("HTTPS URL") }, singleLine = true)
            if(draft.kind in setOf("script", "monitor")) OutlinedTextField(draft.payload, { draft = draft.copy(payload = it.take(64000)) }, label = { Text(if(draft.kind == "script") "JavaScript" else "CSS selector") }, minLines = if(draft.kind == "script") 5 else 1)
            if(draft.kind == "script") TextButton(onClick = onImport) { Text(label("Import JavaScript file", "ورود فایل جاوااسکریپت")) }
            if(draft.kind == "script") Toggle(label("Run automatically when enabled in an app", "اجرای خودکار پس از فعال‌سازی در برنامه"), draft.enabled) { draft = draft.copy(enabled = it) }
            if(draft.kind in setOf("feed", "monitor", "bookmark")) {
                Text(label("Associated Lite App", "برنامهٔ مرتبط"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { apps.forEach { app -> FilterChip(draft.appId == app.id, { draft = draft.copy(appId = app.id) }, label = { Text(app.name) }) } }
            }
            if(draft.kind == "monitor") Text(label("CSS selector applied to server HTML; JavaScript is not executed. For login-only pages, same-origin cookies are used during scheduled checks.", "انتخابگر CSS روی HTML سرور اعمال می‌شود؛ جاوااسکریپت اجرا نمی‌شود. برای صفحات نیازمند ورود، کوکی هم‌مبدأ در بررسی دوره‌ای استفاده می‌شود."))
            if(draft.kind in setOf("feed", "monitor", "list")) TextButton(enabled = !testing, onClick = {
                scope.launch { testing = true; error = try {
                    val record = normalized(); ToolsRepository.validate(record)
                    withContext(Dispatchers.IO) {
                        if(record.kind == "list") "${org.litespace.browser.domain.HostRules.parse(SafeHttp.get(record.url, 10_000_000).text).size} domains"
                        else "${SiteSyncWorker.inspect(record).size} items (public preview; scheduled checks can use same-origin cookies)"
                    }
                } catch(e: CancellationException) { throw e } catch(e: Exception) { e.message ?: "Test failed" }; testing = false }
            }) { Text(if(testing) label("Testing…", "در حال آزمایش…") else label("Test source", "آزمایش منبع")) }
            if(error.isNotBlank()) Text(error)
        }
    }, confirmButton = { TextButton(enabled = !testing, onClick = { try {
        val record = normalized(); ToolsRepository.validate(record)
        if(record.kind in setOf("feed", "monitor")) { val app = apps.firstOrNull { it.id == record.appId }; require(app != null && !app.lock && !app.incognito && !(Build.VERSION.SDK_INT >= 28 && app.profile in org.litespace.browser.web.LegacySandboxes.names)) { "Select an unlocked, non-private app outside a fixed color sandbox" } }
        onSave(record)
    } catch(e: Exception) { error = e.message ?: "Invalid configuration" } }) { Text(label("Save", "ذخیره")) } }, dismissButton = { TextButton(onClick = close) { Text(label("Cancel", "لغو")) } })
}
