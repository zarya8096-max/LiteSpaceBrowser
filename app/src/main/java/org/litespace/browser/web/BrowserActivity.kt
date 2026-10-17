@file:Suppress("DEPRECATION")
package org.litespace.browser.web

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.litespace.browser.LiteSpaceApplication
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.domain.UrlPolicy
import org.litespace.browser.domain.RuntimePermissionGate
import org.litespace.browser.ui.*
import java.io.ByteArrayInputStream
import java.util.UUID

class BrowserActivity : FragmentActivity() {
    private val container get() = application as LiteSpaceApplication
    private lateinit var app: LiteApp
    private var web: WebView? = null
    private var refresh: SwipeRefreshLayout? = null
    private var profile: Profile? = null
    private var temporary = false
    private var loadingProgress by mutableIntStateOf(0)
    private var currentUrl by mutableStateOf("")
    private var menu by mutableStateOf(false)
    private var toolbar by mutableStateOf(true)
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var chooserInFlight = false
    private val permissionGate = RuntimePermissionGate()
    private val websiteDialogs = mutableSetOf<android.app.AlertDialog>()
    private var pageGeneration = 0L
    private var browserOpened = false
    private var authenticating = false
    private var locked by mutableStateOf(false)
    private var pendingSelection: Array<Uri>? = null
    private var selectionReady = false
    private var popup: WebView? = null
    private val dialogs = mutableSetOf<android.app.AlertDialog>()
    private var cleaned = false
    private var storageReady = false
    private var errorLoading = false
    private var retryUrl: String? = null
    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        chooserInFlight = false
        val selected = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)?.filter { it.scheme == "content" }?.toTypedArray()
        pendingSelection = selected?.takeIf { it.isNotEmpty() }; selectionReady = true
        deliverSelection()
    }
    private val runtimePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionGate.resolve(if(locked) emptyMap() else grants)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            val found = intent.getStringExtra("app_id")?.let { container.repository.get(it) }
            if(found == null) { toast("This Lite App no longer exists"); finish(); return@launch }
            app = found
            // Revoking settings/deleting an entry must also close an already
            // running Activity, including one retained behind the manager.
            lifecycleScope.launch {
                container.repository.apps.collect { entries ->
                    if(entries.firstOrNull { it.id == app.id } != app) {
                        toast("Configuration changed. Reopen this Lite App."); finish()
                    }
                }
            }
            if(runCatching { WebViewCompat.getCurrentWebViewPackage(this@BrowserActivity) }.getOrNull() == null) {
                val persian = container.preferences.flow.first().language == "fa"
                showDialog(android.app.AlertDialog.Builder(this@BrowserActivity)
                    .setTitle(if(persian) "WebView در دسترس نیست" else "WebView unavailable")
                    .setMessage(if(persian) "Android System WebView یا Chrome را در تنظیمات اندروید فعال یا نصب کنید و دوباره برنامه را باز کنید." else "Enable or install Android System WebView or Chrome in Android settings, then reopen this Lite App.")
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }.setOnCancelListener { finish() })
                return@launch
            }
            temporary = intent.getBooleanExtra("incognito", false)
            if(temporary && (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) || !WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))) {
                showDialog(android.app.AlertDialog.Builder(this@BrowserActivity).setTitle("Private session unavailable")
                    .setMessage("Update Android System WebView. Private sessions require isolated profiles and complete browsing-data deletion support.")
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }.setOnCancelListener { finish() })
                return@launch
            }
            if(app.lock) authenticate() else openBrowser()
        }
    }

    private fun authenticate() {
        if(authenticating || isFinishing || isDestroyed) return
        authenticating = true
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        // On API 26–29 the compat prompt supports weak biometrics plus device credential.
        val allowed = if(Build.VERSION.SDK_INT >= 30) authenticators else BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if(BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            toast("Set a device screen lock or enroll biometrics before opening this protected app"); finish(); return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticating = false
                if(isFinishing || isDestroyed) return
                if(!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { locked = true; return }
                locked = false
                if(browserOpened) { web?.visibility = View.VISIBLE; web?.onResume(); deliverSelection() } else openBrowser()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { authenticating = false; toast(errString.toString()); finish() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock ${app.name}").setAllowedAuthenticators(allowed).build())
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createWebView(): SwipeRefreshLayout {
        val view = WebView(this)
        web = view
        if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val store = ProfileStore.getInstance()
            // AndroidX cannot delete profiles loaded in this process, even after WebView destruction.
            // Unloaded orphan profiles from an earlier process can be deleted here.
            store.allProfileNames.filter { it.startsWith("private-") && it !in activeProfiles }.forEach { runCatching { store.deleteProfile(it) } }
            val name = if(temporary) "private-${UUID.randomUUID()}" else if(app.profile.isBlank()) "app-${app.id}" else "shared-${app.profile}"
            profile = store.getOrCreateProfile(name)
            activeProfiles.add(name)
            WebViewCompat.setProfile(view, name)
            if(app.profile.isBlank() || temporary) {
                val rules = container.blocker
                val block = app.blockTrackers
                val requireHttps = app.httpsOnly
                profile!!.serviceWorkerController.apply {
                    serviceWorkerWebSettings.allowFileAccess = false
                    serviceWorkerWebSettings.allowContentAccess = false
                    setServiceWorkerClient(object : ServiceWorkerClient() {
                        override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                            return if((requireHttps && request.url.scheme == "http") || (block && rules.blocks(request.url.host ?: "")))
                                WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0))) else null
                        }
                    })
                }
            }
        } else toast("Shared WebView storage: clearing data affects every Lite App")
        view.setBackgroundColor(Color.WHITE)
        with(view.settings) {
            javaScriptEnabled = app.javascript
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true // document-picker uploads only; navigation to content: is denied
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(app.location)
            setSupportMultipleWindows(app.popups)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportZoom(app.zoom)
            builtInZoomControls = app.zoom
            displayZoomControls = false
            textZoom = app.fontSize
            useWideViewPort = true
            loadWithOverviewMode = true
            if(app.userAgent.isNotBlank()) userAgentString = app.userAgent
            else if(app.desktop) userAgentString = userAgentString.replace("; wv", "").replace("Android", "X11; Linux x86_64").replace("Mobile", "")
            if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, app.dark)
        }
        cookies().setAcceptThirdPartyCookies(view, app.thirdPartyCookies)
        storageReady = true
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme
                if(scheme !in listOf("http", "https")) {
                    if(request.isForMainFrame && request.hasGesture() && scheme in listOf("mailto", "tel", "sms", "geo")) {
                        confirm("Open another app?", request.url.toString()) { runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }.onFailure { toast("No application can open this link") } }
                    }
                    return true
                }
                if(app.httpsOnly && scheme == "http") {
                    if(request.isForMainFrame) navigate(request.url.toString())
                    return true
                }
                return false
            }
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                loader.shouldInterceptRequest(request.url)?.let { return it }
                if((app.httpsOnly && request.url.scheme == "http") || (app.blockTrackers && container.blocker.blocks(request.url.host ?: "")))
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                return null
            }
            override fun onPageStarted(v: WebView, url: String, favicon: android.graphics.Bitmap?) {
                currentUrl = url; loadingProgress = 0; pageGeneration++; dismissRequests()
                fileCallback?.onReceiveValue(null); fileCallback = null; pendingSelection = null; selectionReady = false; errorLoading = false
            }
            override fun onPageFinished(v: WebView, url: String) {
                refresh?.isRefreshing = false
                cookies().flush()
                val expected = UrlPolicy.origin(app.url)
                if(app.javascript && expected != null && UrlPolicy.origin(url) == expected && UrlPolicy.origin(v.url) == expected) {
                    // Check inside the renderer too: a redirect can change the
                    // document between onPageFinished and evaluateJavascript.
                    val guard = "if(location.origin!==${JSONObject.quote(expected)})return;"
                    if(app.css.isNotBlank()) v.evaluateJavascript("(function(){$guard let s=document.getElementById('litespace-css');if(!s){s=document.createElement('style');s.id='litespace-css';document.documentElement.appendChild(s);}s.textContent=${JSONObject.quote(app.css)};})()", null)
                    if(app.script.isNotBlank()) v.evaluateJavascript("(function(){$guard try{${app.script}\n}catch(e){console.error('LiteSpace script',e);}})()", null)
                }
            }
            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if(request.isForMainFrame && !errorLoading && request.url.host != WebViewAssetLoader.DEFAULT_DOMAIN) {
                    errorLoading = true; retryUrl = request.url.toString(); toast(error.description.toString()); v.loadUrl(ERROR_PAGE)
                }
            }
            override fun onReceivedSslError(v: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) { handler.cancel(); toast("Certificate error. Connection blocked.") }
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                toast("Web renderer stopped. Reopen this Lite App."); (v.parent as? android.view.ViewGroup)?.removeView(v); v.destroy(); web = null; finish(); return true
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, newProgress: Int) { loadingProgress = newProgress }
            override fun onShowFileChooser(v: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                if(chooserInFlight || locked) { callback.onReceiveValue(null); return true }
                fileCallback?.onReceiveValue(null); fileCallback = callback
                return try { chooserInFlight = true; chooser.launch(params.createIntent().addCategory(Intent.CATEGORY_OPENABLE)); true }
                catch(_: Exception) { chooserInFlight = false; callback.onReceiveValue(null); fileCallback = null; true }
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                val requestedOrigin = UrlPolicy.origin(origin)
                if(!app.location || requestedOrigin == null || !requestedOrigin.startsWith("https://") || requestedOrigin != UrlPolicy.origin(view.url)) { callback.invoke(origin, false, false); return }
                askWebsite("Share location with $origin?", arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), anyOf = true) { granted ->
                    callback.invoke(origin, granted && UrlPolicy.origin(origin) == UrlPolicy.origin(view.url), false)
                }
            }
            override fun onPermissionRequest(request: PermissionRequest) {
                val origin = UrlPolicy.origin(request.origin.toString())
                if(origin == null || !origin.startsWith("https://") || origin != UrlPolicy.origin(view.url)) { request.deny(); return }
                val resources = request.resources.filter { (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE && app.camera) || (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && app.microphone) }
                if(resources.isEmpty()) { request.deny(); return }
                val permissions = resources.map { if(it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO }.toTypedArray()
                askWebsite("Allow ${resources.joinToString { if(it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) "camera" else "microphone" }} for $origin?", permissions) { granted ->
                    runCatching { if(granted && origin == UrlPolicy.origin(view.url)) request.grant(resources.toTypedArray()) else request.deny() }
                }
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) { dismissRequests() }
            override fun onShowCustomView(v: View, callback: CustomViewCallback) {
                if(customView != null) { callback.onCustomViewHidden(); return }
                customView = v; customCallback = callback
                addContentView(v, FrameLayout.LayoutParams(-1, -1)); immersive(true)
            }
            override fun onHideCustomView() { hideVideo() }
            override fun onCreateWindow(v: WebView, isDialog: Boolean, isUserGesture: Boolean, message: android.os.Message): Boolean {
                if(!app.popups || !isUserGesture) return false
                popup?.destroy()
                val child = WebView(this@BrowserActivity)
                if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    profile?.let { WebViewCompat.setProfile(child, it.name) }
                }
                // The temporary window only captures a URL. It must never send an
                // unconfirmed request (POST navigations bypass shouldOverrideUrlLoading).
                child.settings.blockNetworkLoads = true
                child.settings.allowFileAccess = false
                child.settings.allowContentAccess = false
                popup = child
                child.webViewClient = object : WebViewClient() {
                    override fun onReceivedError(w: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if(request.isForMainFrame) {
                            toast("This pop-up needs an external browser")
                            w.post { w.destroy(); if(popup === w) popup = null }
                        }
                    }
                    override fun shouldOverrideUrlLoading(w: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        if(request.url.scheme in listOf("https", "http")) confirm("Open pop-up in this app?", target) { navigate(target) }
                        w.post { w.destroy(); if(popup === w) popup = null }; return true
                    }
                }
                (message.obj as WebView.WebViewTransport).webView = child; message.sendToTarget(); return true
            }
        }
        view.setDownloadListener { url, agent, disposition, mime, _ ->
            if(Uri.parse(url).scheme !in listOf("https", "http")) toast("Blob/data downloads are not supported. Use the website's direct download link.")
            else confirm("Download file?", URLUtil.guessFileName(url, disposition, mime)) {
                val action = { download(url, agent, disposition, mime) }
                if(Build.VERSION.SDK_INT <= 28) requestRuntime(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { if(it) action() } else action()
            }
        }
        val wrapper = SwipeRefreshLayout(this).apply {
            addView(view)
            setOnChildScrollUpCallback { _, _ -> view.canScrollVertically(-1) }
            setOnRefreshListener { reloadPage() }
        }
        refresh = wrapper
        var startX = 0f; var startY = 0f
        view.setOnTouchListener { _, event ->
            if(event.action == MotionEvent.ACTION_DOWN) { startX = event.x; startY = event.y }
            if(app.gestures && event.action == MotionEvent.ACTION_UP && kotlin.math.abs(event.y - startY) < 120 && kotlin.math.abs(event.x - startX) > 180) {
                val edge = 32 * resources.displayMetrics.density
                if(startX < edge && event.x > startX && view.canGoBack()) view.goBack()
                else if(startX > view.width - edge && event.x < startX && view.canGoForward()) view.goForward()
            }
            false
        }
        view.loadUrl(app.url)
        return wrapper
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun openBrowser() {
        if(browserOpened || isFinishing || isDestroyed) return
        browserOpened = true
        toolbar = app.navigation
        if(app.dark) setTheme(org.litespace.browser.R.style.Theme_LiteSpace_Dark)
        if(!app.lock && !temporary) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        immersive(app.fullscreen)
        lifecycleScope.launch {
            val preferences = container.preferences.flow.first()
            setContent {
                LiteSpaceTheme(preferences.copy(theme = if(app.dark) "dark" else preferences.theme), accent = app.accent) {
                    Box(Modifier.fillMaxSize()) {
                    BackHandler { if(customView != null) hideVideo() else if(web?.canGoBack() == true) web?.goBack() else finish() }
                    Scaffold(topBar = {
                        if(toolbar) TopAppBar(title = { Column { Text(app.name, maxLines = 1, style = MaterialTheme.typography.titleMedium); Text(if(temporary) tr("Private session", "نشست خصوصی") else Uri.parse(currentUrl).host ?: "", maxLines = 1, style = MaterialTheme.typography.labelSmall) } }, navigationIcon = {
                            IconButton(onClick = { finish() }) { Icon(Icons.Default.Close, tr("Close", "بستن")) }
                        }, actions = {
                            IconButton(onClick = { reloadPage() }) { Icon(Icons.Default.Refresh, tr("Reload", "بارگذاری دوباره")) }
                            Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("Menu", "فهرست")) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(text = { Text(tr("Home", "خانه")) }, onClick = { menu = false; web?.loadUrl(app.url) })
                                    DropdownMenuItem(text = { Text(tr("Clear cookies & site storage", "پاک‌کردن کوکی و داده سایت")) }, onClick = { menu = false; confirm("Clear profile data?", "This signs out every Lite App sharing this profile. Older WebView shares storage across all apps and may retain service-worker data.") { clearData { web?.loadUrl(app.url) } } })
                                    DropdownMenuItem(text = { Text(tr("Clear cache", "پاک‌کردن حافظه نهان")) }, onClick = { menu = false; web?.clearCache(true); toast("Cache cleared") })
                                    DropdownMenuItem(text = { Text(tr("Zoom in", "بزرگ‌نمایی")) }, onClick = { menu = false; web?.zoomIn() }, enabled = app.zoom)
                                    DropdownMenuItem(text = { Text(tr("Zoom out", "کوچک‌نمایی")) }, onClick = { menu = false; web?.zoomOut() }, enabled = app.zoom)
                                    DropdownMenuItem(text = { Text(tr("Hide controls", "مخفی‌کردن کنترل‌ها")) }, onClick = { menu = false; toolbar = false })
                                    DropdownMenuItem(text = { Text(tr("Open externally", "بازکردن در مرورگر دیگر")) }, onClick = { menu = false; runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }.onFailure { toast("No browser installed") } })
                                }
                            }
                        })
                    }, bottomBar = {
                        if(toolbar) BottomAppBar {
                            IconButton(onClick = { if(web?.canGoBack() == true) web?.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "عقب")) }
                            IconButton(onClick = { if(web?.canGoForward() == true) web?.goForward() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, tr("Forward", "جلو")) }
                            Spacer(Modifier.weight(1f)); Text(if(temporary) tr("Private", "خصوصی") else tr("LiteSpace", "لایت‌اسپیس"), Modifier.padding(16.dp))
                        }
                    }, floatingActionButton = { if(!toolbar) SmallFloatingActionButton(onClick = { toolbar = true }) { Icon(Icons.Default.MoreVert, tr("Show controls", "نمایش کنترل‌ها")) } }) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding)) {
                            if(loadingProgress < 100) LinearProgressIndicator(progress = { loadingProgress / 100f }, modifier = Modifier.fillMaxWidth())
                            AndroidView(factory = {
                                try { createWebView() } catch(error: Exception) {
                                    toast("Unable to initialize WebView. Update Android System WebView and retry."); finish(); FrameLayout(this@BrowserActivity)
                                }
                            }, modifier = Modifier.fillMaxSize())
                        }
                    }
                    if(locked) { BackHandler { finish() }; Surface(Modifier.fillMaxSize()) {
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Text(tr("Authentication required", "احراز هویت لازم است")) }
                    } }
                    }
                }
            }
        }
    }

    private fun navigate(url: String) {
        // Redirects and popup URLs are untrusted website input, unlike saved configs.
        runCatching { UrlPolicy.normalize(url, app.httpsOnly) }
            .onSuccess { web?.loadUrl(it) }
            .onFailure { toast("This website link is not supported") }
    }
    private fun reloadPage() { if(web?.url == ERROR_PAGE) web?.loadUrl(retryUrl ?: app.url) else web?.reload() }

    private fun cookies(): CookieManager = if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) profile?.cookieManager ?: CookieManager.getInstance() else CookieManager.getInstance()
    private fun clearData(done: () -> Unit = {}) {
        web?.stopLoading()
        val storage = if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) profile?.webStorage ?: WebStorage.getInstance() else WebStorage.getInstance()
        (if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) profile?.geolocationPermissions ?: GeolocationPermissions.getInstance() else GeolocationPermissions.getInstance()).clearAll()
        web?.clearCache(true); web?.clearHistory(); web?.clearFormData()
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            WebStorageCompat.deleteBrowsingData(storage) { done() }
        } else {
            storage.deleteAllData()
            val manager = cookies()
            manager.removeAllCookies { manager.flush(); done() }
        }
    }
    private fun askWebsite(message: String, permissions: Array<String>, anyOf: Boolean = false, callback: (Boolean) -> Unit) {
        if(isFinishing || isDestroyed || locked || websiteDialogs.isNotEmpty() || permissionGate.busy) { callback(false); return }
        val generation = pageGeneration
        var completed = false
        var handedOff = false
        val complete: (Boolean) -> Unit = { granted ->
            if(!completed) { completed = true; callback(granted && generation == pageGeneration && !locked && !isFinishing && !isDestroyed) }
        }
        val dialog = showDialog(android.app.AlertDialog.Builder(this).setTitle(app.name).setMessage(message)
            .setPositiveButton("Allow once") { _, _ ->
                handedOff = true
                if(generation == pageGeneration) requestRuntime(permissions, anyOf, complete) else complete(false)
            }
            .setNegativeButton("Deny") { _, _ -> complete(false) }) { if(!handedOff) complete(false) }
        websiteDialogs.add(dialog)
    }
    private fun requestRuntime(permissions: Array<String>, anyOf: Boolean = false, callback: (Boolean) -> Unit) {
        if(locked || isFinishing || isDestroyed || permissionGate.busy) { callback(false); return }
        val existing = permissions.map { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if(if(anyOf) existing.any { it } else existing.all { it }) { callback(true); return }
        if(permissionGate.begin(permissions.toSet(), anyOf, callback)) {
            runCatching { runtimePermissions.launch(permissions) }.onFailure { permissionGate.resolve(emptyMap()) }
        }
    }
    private fun dismissRequests() {
        websiteDialogs.toList().forEach { it.dismiss() }
        permissionGate.cancel()
    }
    private fun confirm(title: String, message: String, yes: () -> Unit) {
        showDialog(android.app.AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(android.R.string.ok) { _, _ -> yes() }.setNegativeButton(android.R.string.cancel, null))
    }
    private fun showDialog(builder: android.app.AlertDialog.Builder, onDismiss: () -> Unit = {}): android.app.AlertDialog {
        val dialog = builder.create()
        dialogs.add(dialog)
        dialog.setOnDismissListener { dialogs.remove(dialog); websiteDialogs.remove(dialog); onDismiss() }
        dialog.show()
        return dialog
    }
    private fun download(url: String, agent: String, disposition: String?, mime: String?) {
        runCatching {
            val request = DownloadManager.Request(Uri.parse(UrlPolicy.normalize(url, app.httpsOnly)))
                .setTitle(URLUtil.guessFileName(url, disposition, mime)).setMimeType(mime)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, disposition, mime))
                .setNotificationVisibility(if(app.downloadNotifications) DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED else DownloadManager.Request.VISIBILITY_VISIBLE)
                .addRequestHeader("User-Agent", agent)
            // DownloadManager can forward custom headers across redirects. Never
            // export profile cookies to it; protected files need a signed link or
            // an external browser instead of risking cross-origin credential leaks.
            getSystemService(DownloadManager::class.java).enqueue(request)
        }.onSuccess { toast("Download started") }.onFailure { toast(it.message ?: "Download failed") }
    }
    private fun immersive(enabled: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if(enabled) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }
    private fun hideVideo() { (customView?.parent as? android.view.ViewGroup)?.removeView(customView); customView = null; customCallback?.onCustomViewHidden(); customCallback = null; if(::app.isInitialized) immersive(app.fullscreen) }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun deliverSelection() {
        if(!selectionReady || locked) return
        fileCallback?.onReceiveValue(pendingSelection); fileCallback = null
        pendingSelection = null; selectionReady = false
    }
    override fun onPause() { web?.onPause(); super.onPause() }
    override fun onStop() {
        if(::app.isInitialized && app.lock && browserOpened && !isChangingConfigurations) {
            locked = true; web?.visibility = View.INVISIBLE; hideVideo(); dismissRequests()
        }
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        if(locked) authenticate() else web?.onResume()
    }
    override fun onDestroy() {
        dialogs.toList().forEach { it.dismiss() }; dialogs.clear()
        hideVideo(); dismissRequests(); fileCallback?.onReceiveValue(null); fileCallback = null; pendingSelection = null
        web?.stopLoading()
        web?.settings?.blockNetworkLoads = true
        if(::app.isInitialized && storageReady && !cleaned && (app.clearOnExit || temporary)) { clearData(); cleaned = true }
        popup?.destroy(); popup = null
        web?.let { view -> (view.parent as? android.view.ViewGroup)?.removeView(view); view.stopLoading(); view.webChromeClient = null; view.destroy() }; web = null; refresh = null
        if(profile != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) profile?.let { p -> activeProfiles.remove(p.name) }
        super.onDestroy()
    }
    companion object {
        private const val ERROR_PAGE = "https://appassets.androidplatform.net/assets/error.html"
        private val activeProfiles = mutableSetOf<String>()
    }
}
