package vpn.moonlight.data.local

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import vpn.moonlight.data.model.Subscription

/**
 * Caches the last successful subscription fetch as JSON on disk, so the app opens
 * on real nodes instead of an empty list while the network request is in flight.
 */
class SubscriptionStore(context: Context) {

    private val file = File(context.filesDir, "subscription.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writeLock = Mutex()

    private val _subscription = MutableStateFlow<Subscription?>(null)
    val subscription: StateFlow<Subscription?> = _subscription.asStateFlow()

    suspend fun load(): Subscription? = withContext(Dispatchers.IO) {
        val cached = runCatching {
            if (!file.exists()) return@runCatching null
            json.decodeFromString<Subscription>(file.readText())
        }.getOrNull()
        _subscription.value = cached
        cached
    }

    suspend fun save(subscription: Subscription) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            // Write to a sibling then rename, so a kill mid-write cannot leave a
            // truncated file that fails to parse on next launch.
            val temp = File(file.parentFile, "${file.name}.tmp")
            runCatching {
                temp.writeText(json.encodeToString<Subscription>(subscription))
                temp.renameTo(file)
            }.onFailure { temp.delete() }
        }
        _subscription.value = subscription
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        writeLock.withLock { file.delete() }
        _subscription.value = null
    }
}
