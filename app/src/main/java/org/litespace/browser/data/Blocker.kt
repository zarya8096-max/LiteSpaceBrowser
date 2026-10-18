package org.litespace.browser.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.litespace.browser.domain.HostRules
import org.litespace.browser.util.readBounded
import java.io.File

data class BlockingStats(val domains: Int = 0, val requests: Long = 0)
class Blocker(context: Context) {
    private val container = context.applicationContext as org.litespace.browser.LiteSpaceApplication
    private val directory = File(context.filesDir, "block-lists").apply { mkdirs() }
    private val seed = context.assets.open("hosts.txt").bufferedReader().use { HostRules.parse(it.readText()) }
    @Volatile private var rules = HostRules(seed)
    private val mutableStats = MutableStateFlow(BlockingStats(seed.size))
    val stats = mutableStats.asStateFlow()
    val count: Int get() = stats.value.domains
    private val updateLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init { scope.launch {
        val dao = container.database.tools()
        val marker = File(context.filesDir, "default-list-initialized")
        if(!marker.exists()) { if(dao.get("builtin-stevenblack") == null) dao.save(ToolRecord(id = "builtin-stevenblack", kind = "list", title = "StevenBlack unified hosts", url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")); marker.writeText("1") }
        val old = File(context.filesDir, "hosts.txt")
        val migrated = File(directory, "builtin-stevenblack.txt")
        if(old.exists() && !migrated.exists()) old.copyTo(migrated)
        dao.observe().map { rows -> rows.filter { it.kind == "list" } }.distinctUntilChanged().collect { all -> updateLock.withLock { reload(all) } }
    } }
    private fun reload(all: List<ToolRecord>) {
        val hosts = seed.toMutableSet()
        all.filter { it.kind == "list" && it.enabled }.forEach { row ->
            val file = File(directory, row.id + ".txt")
            if(file.exists()) runCatching {
                val bytes = file.inputStream().use { it.readBounded(10_000_001) }; require(bytes.size <= 10_000_000)
                hosts.addAll(HostRules.parse(bytes.toString(Charsets.UTF_8)))
            }
        }
        rules = HostRules(hosts); mutableStats.update { it.copy(domains = hosts.size) }
    }
    fun blocks(host: String) = rules.blocks(host).also { blocked -> if(blocked) mutableStats.update { it.copy(requests = it.requests + 1) } }
    suspend fun update(): Int = withContext(Dispatchers.IO) { updateLock.withLock {
        val dao = container.database.tools(); var success = 0; val failures = mutableListOf<String>()
        for(row in dao.all().filter { it.kind == "list" && it.enabled }) {
            try {
                val text = SafeHttp.get(row.url, 10_000_000).text
                require(HostRules.parse(text).isNotEmpty()) { "No valid hosts found" }
                val tmp = File(directory, row.id + ".tmp"); tmp.writeText(text)
                check(tmp.renameTo(File(directory, row.id + ".txt"))) { "Cannot save list" }
                // Do not re-enable a list that was disabled while downloading.
                dao.get(row.id)?.takeIf { it.url == row.url }?.let { dao.save(it.copy(checkedAt = System.currentTimeMillis(), error = "")) }
                success++
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                failures.add(row.title)
                dao.get(row.id)?.let { dao.save(it.copy(error = (e.message ?: "Update failed").take(300))) }
            }
        }
        reload(dao.all())
        if(failures.isNotEmpty()) error("Updated $success lists; failed: ${failures.joinToString()}. Previous rules retained.")
        count
    } }
}
