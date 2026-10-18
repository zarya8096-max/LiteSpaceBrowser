package org.litespace.browser.data

import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import org.litespace.browser.domain.UrlPolicy
import java.io.StringReader
import java.security.MessageDigest

data class WatchEntry(val id: String, val title: String, val url: String, val image: String = "")
object WatchParser {
    fun hash(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    fun feed(xml: String, base: String): List<WatchEntry> {
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "DTD/entities are not accepted" }
        val p = Xml.newPullParser(); p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); p.setInput(StringReader(xml))
        val items = mutableListOf<WatchEntry>(); var inside = false; var field = ""; var title = ""; var link = ""; var id = ""; var valid = false
        while(p.eventType != XmlPullParser.END_DOCUMENT) {
            when(p.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = p.name.lowercase()
                    if(tag in setOf("rss", "feed", "rdf")) valid = true
                    if(tag in setOf("item", "entry")) { inside = true; title = ""; link = ""; id = "" }
                    if(inside) {
                        field = tag
                        if(tag == "link" && p.getAttributeValue(null, "href") != null && p.getAttributeValue(null, "rel") in listOf(null, "alternate")) link = p.getAttributeValue(null, "href")
                    }
                }
                XmlPullParser.TEXT -> if(inside) when(field) { "title" -> title += p.text; "link" -> link += p.text; "guid", "id" -> id += p.text }
                XmlPullParser.END_TAG -> { if(p.name.lowercase() in setOf("item", "entry")) {
                    val resolved = runCatching { UrlPolicy.normalize(java.net.URL(java.net.URL(base), link.trim()).toString()) }.getOrDefault(base)
                    items.add(WatchEntry(hash(id.trim().ifBlank { link.trim().ifBlank { title } }), Jsoup.parse(title).text().take(240), resolved)); inside = false
                    require(items.size <= 2000) { "Feed has too many entries" }
                }; field = "" }
            }
            p.next()
        }
        require(valid) { "Not an RSS or Atom feed" }
        return items.distinctBy { it.id }
    }
    fun monitor(html: String, base: String, selector: String): List<WatchEntry> {
        val document = Jsoup.parse(html, base)
        document.select("script,style,noscript").remove()
        val nodes = document.select(selector)
        require(nodes.isNotEmpty()) { "Selector matched no content. JavaScript-only pages cannot be monitored." }
        require(nodes.size <= 2000)
        return nodes.map { n ->
            val text = n.text().replace(Regex("\\b\\d+\\s*(seconds?|minutes?|hours?|days?|sec|min|hr)s?\\s+ago\\b", RegexOption.IGNORE_CASE), "").replace(Regex("\\s+"), " ").trim()
            val link = n.selectFirst("a[href]")?.absUrl("href").orEmpty().ifBlank { base }
            val inline = n.selectFirst("[style*=background]")?.attr("style").orEmpty().ifBlank { n.attr("style") }
            val cssImage = Regex("url\\(['\"]?([^)'\"]+)").find(inline)?.groupValues?.get(1)
            val image = n.selectFirst("img[src]")?.absUrl("src").orEmpty().ifBlank {
                cssImage?.let { runCatching { java.net.URL(java.net.URL(base), it).toString() }.getOrNull() }.orEmpty()
            }
            WatchEntry(hash(text), text.take(240), runCatching { UrlPolicy.normalize(link) }.getOrDefault(base), image)
        }.filter { it.title.isNotBlank() }.distinctBy { it.id }
    }
}
