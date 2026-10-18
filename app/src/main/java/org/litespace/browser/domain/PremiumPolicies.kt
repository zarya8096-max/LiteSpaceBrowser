package org.litespace.browser.domain

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** Removes known tracking keys without decoding or rebuilding signed query values. */
object TrackingParameters {
    private val keys = setOf("fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid", "igshid", "yclid", "_hsenc", "_hsmi", "mkt_tok", "vero_id", "oly_anon_id", "oly_enc_id", "wickedid", "rb_clickid", "s_cid", "twclid", "ttclid")
    fun strip(url: String): String {
        val uri = URI(url)
        val query = uri.rawQuery ?: return url
        val kept = query.split('&').filter { part ->
            val key = runCatching { URLDecoder.decode(part.substringBefore('='), "UTF-8").lowercase(Locale.ROOT) }.getOrDefault("")
            !key.startsWith("utm_") && key !in keys
        }
        if(kept.size == query.split('&').size) return url
        return url.substringBefore('?') + (if(kept.isEmpty()) "" else "?" + kept.joinToString("&")) + (uri.rawFragment?.let { "#${it}" } ?: "")
    }
}
fun LiteApp.tagNames(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
fun LiteApp.enabledScriptIds(): Set<String> = scriptIds.split(',').filter { it.isNotBlank() }.toSet()
object NativeTemplates {
    fun expand(template: String, text: String): String {
        require(template.length <= 8192)
        val url = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(text)?.value.orEmpty()
        val title = if(url.isEmpty()) text else text.replace(url, "").trim()
        fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
        return UrlPolicy.normalize(template.replace("%s", encode(text)).replace("%u", encode(url)).replace("%t", encode(title)))
    }
}
