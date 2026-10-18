package org.litespace.browser.web

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.litespace.browser.LiteSpaceApplication
import org.litespace.browser.domain.*
import org.litespace.browser.ui.*

/** Exported HTTP(S)/text entry point. All caller input is untrusted. */
class LinkRouterActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = if(intent.action == Intent.ACTION_SEND && intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT)?.take(16000).orEmpty() else ""
        val candidate = if(intent.action == Intent.ACTION_VIEW) intent.dataString.orEmpty() else Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(shared)?.value.orEmpty()
        val url = candidate.takeIf { it.length <= 8192 }?.let { runCatching { UrlPolicy.normalize(it) }.getOrNull() }
        val container = application as LiteSpaceApplication
        lifecycleScope.launch {
            val apps = container.repository.apps.first()
            val matching = apps.filter { Uri.parse(it.url).host == url?.let { u -> Uri.parse(u).host } }
            if(intent.action == Intent.ACTION_VIEW && matching.size == 1 && url != null) {
                startActivity(Intent(this@LinkRouterActivity, BrowserActivity::class.java).putExtra("app_id", matching.single().id).putExtra("start_url", url)); finish(); return@launch
            }
            val prefs = container.preferences.flow.first()
            setContent { LiteSpaceTheme(prefs) {
                Surface(Modifier.fillMaxSize()) {
                    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item { Text(tr("Open in LiteSpace", "بازکردن در لایت‌اسپیس"), style = MaterialTheme.typography.headlineMedium) }
                        if(url != null) {
                            item { Text(url, maxLines = 4) }
                            item { Button(onClick = { startActivity(Intent(this@LinkRouterActivity, BrowserActivity::class.java).putExtra("transient_url", url)); finish() }) { Text(tr("Open temporary session", "بازکردن نشست موقت")) } }
                            item { OutlinedButton(onClick = { startActivity(Intent(this@LinkRouterActivity, ReaderActivity::class.java).putExtra("url", url)); finish() }) { Text(tr("Read article", "مطالعهٔ مقاله")) } }
                        }
                        val targets = if(intent.action == Intent.ACTION_SEND) apps.filter { it.shareUrl.isNotBlank() } else matching
                        items(targets, key = { it.id }) { app ->
                            OutlinedButton(onClick = {
                                val target = if(shared.isNotEmpty()) runCatching { NativeTemplates.expand(app.shareUrl, shared) }.getOrNull() else url
                                if(target != null) { startActivity(Intent(this@LinkRouterActivity, BrowserActivity::class.java).putExtra("app_id", app.id).putExtra("start_url", target)); finish() }
                            }, modifier = Modifier.fillMaxWidth()) { Text(app.name) }
                        }
                        if(url == null && targets.isEmpty()) item { Text(tr("Share a valid HTTP(S) URL, or configure a Share URL in a Lite App.", "یک نشانی HTTP(S) معتبر بفرستید یا نشانی اشتراک‌گذاری را در تنظیمات یک برنامه تعریف کنید.")) }
                        item { TextButton(onClick = { finish() }) { Text(tr("Close", "بستن")) } }
                    }
                }
            } }
        }
    }
}
