package vpn.moonlight.data.local

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import vpn.moonlight.data.model.Latency

/**
 * Keeps the last latency of every node on disk.
 *
 * A measurement is expensive — it opens a connection through every node — and
 * the numbers used to live only in memory, so they were gone whenever the
 * process was: after a reboot, after the system reclaimed the app, often just
 * after an evening in the background. The list then read "—" on every row at
 * exactly the moment someone opened it to choose a server.
 *
 * Stored as node id to milliseconds. A probe that got no answer is remembered
 * too, as [UNREACHABLE], so a node that was down still reads `n/a` after a
 * relaunch rather than looking never checked.
 */
class LatencyStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()

    suspend fun load(): Map<String, Latency> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching emptyMap()
            json.decodeFromString<Map<String, Int>>(file.readText()).mapValues { (_, ms) ->
                if (ms > 0) Latency.Value(ms) else Latency.Failed()
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun save(latencies: Map<String, Latency>) = withContext(Dispatchers.IO) {
        val stored = latencies.mapNotNull { (id, latency) ->
            when (latency) {
                is Latency.Value -> id to latency.ms
                is Latency.Failed -> id to UNREACHABLE
                Latency.Unknown -> null
            }
        }.toMap()

        writeLock.withLock {
            // Write to a sibling then rename, so a kill mid-write cannot leave a
            // truncated file that fails to parse on next launch.
            val temp = File(file.parentFile, "${file.name}.tmp")
            runCatching {
                temp.writeText(json.encodeToString(stored))
                temp.renameTo(file)
            }.onFailure { temp.delete() }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        writeLock.withLock { file.delete() }
        Unit
    }

    private companion object {
        const val UNREACHABLE = -1
    }
}
