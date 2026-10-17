package org.litespace.browser.data

import org.litespace.browser.util.readBounded
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.litespace.browser.domain.HostRules
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class Blocker(context: Context) {
    private val file = File(context.filesDir, "hosts.txt")
    @Volatile private var rules = HostRules(emptySet())
    @Volatile var count = 0; private set
    val blocked = AtomicLong(0)
    init {
        val text = if(file.exists()) file.readText() else context.assets.open("hosts.txt").bufferedReader().use { it.readText() }
        apply(text)
    }
    private fun apply(text: String) { val hosts = HostRules.parse(text); rules = HostRules(hosts); count = hosts.size }
    fun blocks(host: String): Boolean = rules.blocks(host).also { if(it) blocked.incrementAndGet() }
    suspend fun update(): Int = withContext(Dispatchers.IO) {
        val connection = URL("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            require(connection.responseCode == 200) { "Filter server returned ${connection.responseCode}" }
            val bytes = connection.inputStream.use { it.readBounded(10_000_001) }
            require(bytes.size <= 10_000_000) { "Filter list too large" }
            val text = bytes.toString(Charsets.UTF_8)
            require(HostRules.parse(text).size > 1000) { "Invalid filter response" }
            val temp = File(file.parentFile, "hosts.tmp"); temp.writeText(text)
            check(temp.renameTo(file)) { "Cannot save filters" }
            apply(text); count
        } finally { connection.disconnect() }
    }
}
