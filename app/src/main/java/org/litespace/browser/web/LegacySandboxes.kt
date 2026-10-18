package org.litespace.browser.web

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build

/** Five fixed OS processes provide real storage separation on WebView versions without profiles. */
object LegacySandboxes {
    val names = setOf("red", "green", "blue", "purple", "yellow")
    fun isSandboxProcess(): Boolean = Build.VERSION.SDK_INT >= 28 && Application.getProcessName().substringAfter(':', "") in names.map { "sandbox_$it" }
    fun intent(context: Context, source: Intent, name: String): Intent? {
        if(Build.VERSION.SDK_INT < 28 || name !in names || isSandboxProcess()) return null
        val target = when(name) { "red" -> RedSandboxActivity::class.java; "green" -> GreenSandboxActivity::class.java; "blue" -> BlueSandboxActivity::class.java; "purple" -> PurpleSandboxActivity::class.java; else -> YellowSandboxActivity::class.java }
        return Intent(source).setClass(context, target)
    }
}
class RedSandboxActivity : BrowserActivity()
class GreenSandboxActivity : BrowserActivity()
class BlueSandboxActivity : BrowserActivity()
class PurpleSandboxActivity : BrowserActivity()
class YellowSandboxActivity : BrowserActivity()
