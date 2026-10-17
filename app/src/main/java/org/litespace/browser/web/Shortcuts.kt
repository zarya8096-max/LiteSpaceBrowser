package org.litespace.browser.web

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.util.Base64
import org.litespace.browser.R
import org.litespace.browser.domain.LiteApp
import org.litespace.browser.util.IconImages

object Shortcuts {
    fun pin(context: Context, app: LiteApp): String = runCatching {
        val manager = context.getSystemService(ShortcutManager::class.java)
        if(!manager.isRequestPinShortcutSupported) return "This launcher does not support pinned shortcuts"
        val icon = runCatching {
            Icon.createWithBitmap(IconImages.decode(app.icon) ?: error("No icon"))
        }.getOrElse { Icon.createWithResource(context, R.drawable.ic_litespace) }
        val shortcut = ShortcutInfo.Builder(context, app.id).setShortLabel(app.name.take(40)).setLongLabel(app.name)
            .setIcon(icon).setIntent(Intent(context, BrowserActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("app_id", app.id)).build()
        manager.enableShortcuts(listOf(app.id))
        manager.updateShortcuts(listOf(shortcut))
        if(manager.requestPinShortcut(shortcut, null)) "Shortcut request sent to your launcher" else "Launcher declined the shortcut request"
    }.getOrElse { "Shortcut failed: ${it.message ?: "launcher error"}" }
    fun disable(context: Context, app: LiteApp): Boolean = runCatching {
        context.getSystemService(ShortcutManager::class.java).disableShortcuts(listOf(app.id), "This Lite App was deleted")
    }.isSuccess
}
