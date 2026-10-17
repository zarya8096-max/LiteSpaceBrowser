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

object Shortcuts {
    fun pin(context: Context, app: LiteApp): String {
        val manager = context.getSystemService(ShortcutManager::class.java)
        if(!manager.isRequestPinShortcutSupported) return "This launcher does not support pinned shortcuts"
        val icon = runCatching {
            val bytes = Base64.decode(app.icon, Base64.DEFAULT)
            Icon.createWithBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("No icon"))
        }.getOrElse { Icon.createWithResource(context, R.drawable.ic_litespace) }
        val shortcut = ShortcutInfo.Builder(context, app.id).setShortLabel(app.name.take(40)).setLongLabel(app.name)
            .setIcon(icon).setIntent(Intent(context, BrowserActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("app_id", app.id)).build()
        return if(manager.requestPinShortcut(shortcut, null)) "Shortcut request sent to your launcher" else "Launcher declined the shortcut request"
    }
    fun disable(context: Context, app: LiteApp) { context.getSystemService(ShortcutManager::class.java).disableShortcuts(listOf(app.id), "This Lite App was deleted") }
}
