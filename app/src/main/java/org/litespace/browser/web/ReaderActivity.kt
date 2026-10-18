package org.litespace.browser.web

import android.content.Intent
import android.os.Bundle
import android.print.PrintManager
import android.view.WindowManager
import android.webkit.*
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.litespace.browser.LiteSpaceApplication
import org.litespace.browser.data.Preferences
import org.litespace.browser.data.SafeHttp
import org.litespace.browser.domain.UrlPolicy
import org.litespace.browser.ui.*
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

/** Reader documents are sanitized, served locally, non-scriptable and never load remote resources. */
class ReaderActivity : FragmentActivity() {
    private var web: WebView? = null
    private val model: ReaderModel by viewModels()
    private val content get() = model.content.value
    private val error get() = model.error.value
    private var palette: Int
        get() = model.palette.intValue
        set(value) { model.palette.intValue = value }
    private var font: Int
        get() = model.font.intValue
        set(value) { model.font.intValue = value }
    private var size: Int
        get() = model.size.intValue
        set(value) { model.size.intValue = value }
    private var locked by mutableStateOf(false)
    private var authenticating = false
    private var needsAuth = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        needsAuth = intent.getBooleanExtra("protected", false)
        locked = needsAuth
        model.load(intent.getStringExtra("reader_token"), intent.getStringExtra("url"))
        val container = application as LiteSpaceApplication
        intent.getStringExtra("source_app_id")?.let { id -> lifecycleScope.launch {
            val revision = intent.getLongExtra("source_revision", -1)
            container.repository.apps.collect { entries ->
                if(entries.firstOrNull { it.id == id }?.updatedAt != revision) finish()
            }
        } }
        setContent {
            val prefs by container.preferences.flow.collectAsStateWithLifecycle(Preferences())
            LiteSpaceTheme(prefs) { Box(Modifier.fillMaxSize()) {
                ReaderScreen()
                if(locked) Surface(Modifier.fillMaxSize()) { Box(Modifier.padding(24.dp)) { Text(tr("Authentication required", "احراز هویت لازم است")) } }
            } }
        }
    }
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable private fun ReaderScreen() {
        Scaffold(topBar = { TopAppBar(title = { Text(tr("Reader", "مطالعه")) }, navigationIcon = { TextButton(onClick = { finish() }) { Text(tr("Back", "بازگشت")) } }, actions = {
            TextButton(onClick = { web?.let { (getSystemService(PRINT_SERVICE) as PrintManager).print("LiteSpace article", it.createPrintDocumentAdapter("Article"), null) } }) { Text("PDF") }
        }) }, bottomBar = {
            Surface {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(tr("White", "سفید"), tr("Sepia", "کاغذی"), tr("Dark", "تیره"), tr("Black", "مشکی")).forEachIndexed { i, label -> FilterChip(palette == i, { palette = i }, label = { Text(label) }) } }
                    Row { TextButton(onClick = { font = (font + 1) % 4 }) { Text(listOf("Serif", "Sans", "Mono", "Vazirmatn")[font]) }; TextButton(onClick = { size = (size - 1).coerceAtLeast(14) }) { Text("A−") }; TextButton(onClick = { size = (size + 1).coerceAtMost(36) }) { Text("A+") } }
                }
            }
        }) { padding ->
            if(error.isNotBlank()) Text(error, Modifier.padding(padding).padding(24.dp))
            else if(content.isBlank()) Box(Modifier.padding(padding).padding(24.dp)) { CircularProgressIndicator() }
            else AndroidView(factory = {
                WebView(it).also { view ->
                    web = view
                    view.settings.javaScriptEnabled = false; view.settings.allowFileAccess = false; view.settings.allowContentAccess = false
                    view.settings.blockNetworkLoads = true
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                            if(request.url.host == "appassets.androidplatform.net" && request.url.path == "/reader/font.ttf") return WebResourceResponse("font/ttf", null, assets.open("reader-vazirmatn.ttf"))
                            if(request.url.scheme == "https" && request.url.host == "appassets.androidplatform.net" && request.url.path == "/reader/article") return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(document().toByteArray()))
                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                            runCatching { UrlPolicy.normalize(request.url.toString()) }.onSuccess { url -> startActivity(Intent(this@ReaderActivity, LinkRouterActivity::class.java).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(url))) }
                            return true
                        }
                    }
                }
            }, update = { it.visibility = if(locked) android.view.View.INVISIBLE else android.view.View.VISIBLE; it.loadUrl(READER_URL + "#${palette}-${font}-${size}"); it.reload() }, modifier = Modifier.padding(padding).fillMaxSize())
        }
    }
    private fun document(): String {
        val bg = listOf("#ffffff", "#f6edda", "#202421", "#000000")[palette]
        val fg = if(palette < 2) "#172219" else "#eeeeee"
        val face = listOf("Georgia,serif", "sans-serif", "monospace", "Vazirmatn,sans-serif")[font]
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'; font-src 'self'\"><style>@font-face{font-family:Vazirmatn;src:url('/reader/font.ttf')}body{background:$bg;color:$fg;font:$size" + "px/1.8 $face;margin:24px auto;padding:0 22px;max-width:760px;overflow-wrap:anywhere}a{color:inherit}pre{white-space:pre-wrap}table{max-width:100%}</style></head><body dir='auto'>$content</body></html>"
    }
    override fun onStop() { if(intent.getBooleanExtra("protected", false)) { locked = true; needsAuth = true }; super.onStop() }
    override fun onResume() { super.onResume(); if(needsAuth && !authenticating) {
        needsAuth = false; authenticating = true
        window.decorView.post { DeviceAuth.verify(this, "Unlock article", failure = { authenticating = false; finish() }) { authenticating = false; locked = false } }
    } }
    override fun onDestroy() { (web?.parent as? android.view.ViewGroup)?.removeView(web); web?.destroy(); web = null; super.onDestroy() }
    companion object {
        private const val READER_URL = "https://appassets.androidplatform.net/reader/article"
        fun sanitize(html: String): String = Jsoup.clean(html, Safelist.basic().removeTags("img").addTags("h1", "h2", "h3", "h4", "pre", "table", "tbody", "tr", "td", "th").removeAttributes("a", "target")).take(1_900_000)
        fun open(activity: android.app.Activity, html: String, protected: Boolean = false, appId: String? = null, revision: Long = -1) {
            val token = UUID.randomUUID().toString()
            // No Activity Bundle or Intent extras containing megabytes of article data.
            File(activity.cacheDir, "reader-$token.html").writeText(sanitize(html))
            activity.startActivity(Intent(activity, ReaderActivity::class.java).putExtra("reader_token", token).putExtra("protected", protected).putExtra("source_app_id", appId).putExtra("source_revision", revision))
        }
    }
}

class ReaderModel(application: android.app.Application) : AndroidViewModel(application) {
    val content = mutableStateOf("")
    val error = mutableStateOf("")
    val palette = mutableIntStateOf(0)
    val font = mutableIntStateOf(0)
    val size = mutableIntStateOf(19)
    private var started = false
    fun load(tokenValue: String?, urlValue: String?) {
        if(started) return
        started = true
        viewModelScope.launch {
            try {
                content.value = withContext(Dispatchers.IO) {
                    val token = tokenValue
                    if(token != null) {
                        require(token.matches(Regex("[a-f0-9-]{36}")))
                        val file = File(getApplication<android.app.Application>().cacheDir, "reader-$token.html")
                        try { require(file.length() <= 2_000_000); ReaderActivity.sanitize(file.readText()) } finally { file.delete() }
                    } else {
                        val url = UrlPolicy.normalize(urlValue ?: error("Missing article URL"))
                        val response = SafeHttp.get(url)
                        val document = Jsoup.parse(response.text, response.url)
                        document.select("script,style,nav,header,footer,aside,form,iframe").remove()
                        val main = document.selectFirst("article,main,[role=main]") ?: document.body()
                        ReaderActivity.sanitize("<h1>${org.jsoup.nodes.Entities.escape(document.title())}</h1>" + main.html())
                    }
                }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { error.value = e.message ?: "Cannot load article" }
        }
    }
}
