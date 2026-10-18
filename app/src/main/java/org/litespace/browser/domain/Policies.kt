package org.litespace.browser.domain

import java.net.URI
import java.net.IDN
import java.util.Locale
import java.text.Normalizer

object UrlPolicy {
    fun normalize(input: String, httpsOnly: Boolean = true): String {
        val text = input.trim().let { if (it.startsWith("//")) "https:$it" else if ("://" in it) it else "https://$it" }
        val uri = URI(text)
        val originalScheme = uri.scheme?.lowercase(Locale.ROOT)
        require(originalScheme == "https" || originalScheme == "http") { "Only HTTP(S) websites are supported" }
        val authority = requireNotNull(uri.rawAuthority) { "Website host is missing" }
        require(authority.isNotBlank() && authority.none { it in "@%\\" || it.isWhitespace() }) { "Invalid host or embedded credentials" }
        val host: String
        val portText: String?
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            require(end > 1 && URI("http://${authority.substring(0, end + 1)}").host != null) { "Invalid IPv6 host" }
            host = authority.substring(0, end + 1).lowercase(Locale.ROOT)
            val suffix = authority.substring(end + 1)
            require(suffix.isEmpty() || suffix.startsWith(':')) { "Invalid port" }
            portText = suffix.takeIf { it.isNotEmpty() }?.substring(1)
        } else {
            require(authority.count { it == ':' } <= 1) { "IPv6 hosts need brackets" }
            val rawHost = authority.substringBefore(':')
            host = IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            require(host.isNotBlank()) { "Website host is missing" }
            // Java/older Android use IDNA 2003, while modern browsers differ for
            // characters such as ß. Never silently send faß.de to fass.de.
            require(rawHost.all { it.code < 128 } || IDN.toUnicode(host).equals(Normalizer.normalize(rawHost, Normalizer.Form.NFC), ignoreCase = true)) {
                "Ambiguous international host; enter its ASCII punycode address"
            }
            portText = if (':' in authority) authority.substringAfter(':') else null
        }
        val port = portText?.let {
            require(it.isNotEmpty() && it.all { c -> c in '0'..'9' }) { "Invalid port" }
            it.toIntOrNull()?.takeIf { p -> p in 1..65535 } ?: error("Port must be between 1 and 65535")
        }
        val scheme = if (httpsOnly) "https" else originalScheme
        val normalizedPort = port?.takeUnless { (it == 80 && originalScheme == "http") || (it == 443 && scheme == "https") }
        // Preserve raw components: decoded URI constructors turn %2F into / and
        // %26 into &, breaking routes and signed download URLs during upgrades.
        return URI(buildString {
            append(scheme); append("://"); append(host)
            normalizedPort?.let { append(':'); append(it) }
            append(uri.rawPath ?: "")
            uri.rawQuery?.let { append('?'); append(it) }
            uri.rawFragment?.let { append('#'); append(it) }
        }).toASCIIString()
    }
    fun origin(url: String?): String? = runCatching {
        val source = url ?: return null
        require(URI(source).isAbsolute)
        val uri = URI(normalize(source, false))
        "${uri.scheme}://${uri.host}${if(uri.port == -1) "" else ":${uri.port}"}"
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
            .filter { it.isNotEmpty() }.flatMap { line ->
                val parts = line.split(Regex("\\s+"))
                (if(parts.size > 1 && parts[0] in setOf("0.0.0.0", "127.0.0.1", "::1")) parts.drop(1) else if(parts.size == 1) parts else emptyList()).asSequence()
            }.map { it.lowercase(java.util.Locale.ROOT) }.filter {
                it.contains('.') && it.matches(Regex("[a-z0-9.-]+")) && it != "localhost.localdomain" && !it.matches(Regex("[0-9.]+"))
            }.toSet()
    }
}
