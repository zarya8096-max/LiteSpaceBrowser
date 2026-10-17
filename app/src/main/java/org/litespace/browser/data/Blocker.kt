package org.litespace.browser.data

import org.litespace.browser.util.readBounded
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.litespace.browser.domain.HostRules
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

data class BlockingStats(val domains: Int = 0, val requests: Long = 0)

class Blocker(context: Context) {
    private val file = File(context.filesDir, "hosts.txt")
    @Volatile private var rules = HostRules(emptySet())
    private val mutableStats = MutableStateFlow(BlockingStats())
    val stats = mutableStats.asStateFlow()
    val count: Int get() = stats.value.domains
    private val updateLock = Mutex()
    init {
        apply(context.assets.open("hosts.txt").bufferedReader().use { it.readText() })
        // Cached lists can contain hundreds of thousands of rules. Never parse
        // them on the UI thread or let a corrupt cache prevent browser startup.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            updateLock.withLock {
                if(file.exists()) runCatching {
                    val bytes = file.inputStream().use { it.readBounded(10_000_001) }
                    require(bytes.size <= 10_000_000)
                    val text = bytes.toString(Charsets.UTF_8)
                    require(HostRules.parse(text).isNotEmpty())
                    apply(text)
                }
            }
        }
    }
    private fun apply(text: String) { val hosts = HostRules.parse(text); rules = HostRules(hosts); mutableStats.update { it.copy(domains = hosts.size) } }
    fun blocks(host: String): Boolean = rules.blocks(host).also { blocked -> if(blocked) mutableStats.update { it.copy(requests = it.requests + 1) } }
    suspend fun update(): Int = withContext(Dispatchers.IO) { updateLock.withLock {
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
    } }
}
