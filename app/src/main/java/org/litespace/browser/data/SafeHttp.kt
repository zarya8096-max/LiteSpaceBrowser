package org.litespace.browser.data

import org.litespace.browser.domain.UrlPolicy
import org.litespace.browser.util.readBounded
import java.net.HttpURLConnection
import java.net.URL

/** No cleartext, automatic redirects, cookie jar or unbounded bodies. */
object SafeHttp {
    data class Body(val text: String, val url: String, val bytes: ByteArray)
    fun get(url: String, limit: Int = 2_000_000, cookies: String? = null): Body {
        var target = UrlPolicy.normalize(url)
        require(target == url) { "Background sources require an HTTPS URL" }
        val originalOrigin = UrlPolicy.origin(target)
        repeat(6) {
            val c = URL(target).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false; c.connectTimeout = 15000; c.readTimeout = 20000
                c.setRequestProperty("User-Agent", "LiteSpace/1.2 Android")
                if(cookies != null && UrlPolicy.origin(target) == originalOrigin) c.setRequestProperty("Cookie", cookies)
                val code = c.responseCode
                if(code in listOf(301,302,303,307,308)) {
                    val next = URL(URL(target), c.getHeaderField("Location") ?: error("Missing redirect location")).toString()
                    require(next.startsWith("https://")) { "Insecure redirect blocked" }
                    target = UrlPolicy.normalize(next)
                } else {
                    require(code in 200..299) { "HTTP $code" }
                    val bytes = c.inputStream.use { it.readBounded(limit + 1) }
                    require(bytes.size <= limit) { "Response too large" }
                    val charsetName = c.contentType.orEmpty().substringAfter("charset=", "UTF-8").substringBefore(';').trim().trim('"')
                    val charset = runCatching { java.nio.charset.Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
                    return Body(bytes.toString(charset), target, bytes)
                }
            } finally { c.disconnect() }
        }
        error("Too many redirects")
    }
}
