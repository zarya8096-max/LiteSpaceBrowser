package org.litespace.browser

import android.app.Activity
import android.os.Bundle
import android.os.ResultReceiver
import android.webkit.CookieManager

/** Debug-only, non-exported device-test probe. Never included in release APKs. */
open class SandboxProbeActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("LiteSpaceSandbox", "probe started " + android.app.Application.getProcessName())
        val receiver = intent.getParcelableExtra<ResultReceiver>("reply") ?: run { finish(); return }
        val manager = CookieManager.getInstance()
        fun respond() { android.util.Log.i("LiteSpaceSandbox", "cookie probe responding"); manager.flush(); receiver.send(0, Bundle().apply { putString("cookie", manager.getCookie("https://sandbox-regression.example")) }); finish() }
        val value = intent.getStringExtra("set")
        if(value != null) manager.setCookie("https://sandbox-regression.example", "session=$value; Secure; Path=/") { respond() } else respond()
    }
}
class RedProbeActivity : SandboxProbeActivity()
class GreenProbeActivity : SandboxProbeActivity()
