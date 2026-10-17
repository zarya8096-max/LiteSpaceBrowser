package org.litespace.browser.domain

import java.net.URI

object UrlPolicy {
    fun normalize(input: String, httpsOnly: Boolean = true): String {
        val text = input.trim().let { if ("://" in it) it else "https://$it" }
        val uri = URI(text)
        require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Enter a valid HTTP(S) website without embedded credentials" }
        return if (httpsOnly && uri.scheme == "http") URI("https", null, uri.host, if (uri.port == 80) -1 else uri.port, uri.path, uri.query, uri.fragment).toASCIIString() else uri.toASCIIString()
    }
    fun origin(url: String?): String? = runCatching {
        val uri = URI(url ?: return null)
        if (uri.scheme !in setOf("http", "https") || uri.host == null) null
        else "${uri.scheme}://${uri.host.lowercase()}${if(uri.port == -1) "" else ":${uri.port}"}"
    }.getOrNull()
}

/** Domain-only hosts syntax. Cosmetic / EasyList rules are deliberately not claimed. */
class HostRules(private val hosts: Set<String>) {
    fun blocks(host: String): Boolean {
        var value = host.lowercase().trimEnd('.')
        while (value.contains('.')) {
            if (value in hosts) return true
            value = value.substringAfter('.')
        }
        return false
    }
    companion object {
        fun parse(text: String): Set<String> = text.lineSequence().map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }.mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                val host = if (parts.size > 1 && parts[0] in setOf("0.0.0.0", "127.0.0.1")) parts[1] else if(parts.size == 1) parts[0] else null
                host?.lowercase()?.takeIf { it.contains('.') && it.matches(Regex("[a-z0-9.-]+")) && it != "localhost.localdomain" }
            }.toSet()
    }
}
