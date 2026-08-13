package vpn.moonlight.ui.split

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads launcher icons for the split-tunnel list.
 *
 * Icons are decoded on demand rather than with the app list: a device with a few
 * hundred apps would otherwise rasterise every icon before the screen could draw,
 * and most are never scrolled to. Decoded results are cached, because a fling
 * through the list revisits the same rows repeatedly.
 */
class AppIconLoader(private val context: Context) {

    // Icons are ~48dp; a hundred of them is a couple of megabytes at most.
    private val cache = LruCache<String, ImageBitmap>(120)

    suspend fun load(packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }

        return withContext(Dispatchers.IO) {
            val bitmap = runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    // Adaptive icons have no intrinsic size worth trusting, so the
                    // target size is fixed rather than read from the drawable.
                    .toBitmap(ICON_PX, ICON_PX)
                    .asImageBitmap()
            }.getOrElse { error ->
                if (error is PackageManager.NameNotFoundException) null else null
            }
            bitmap?.also { cache.put(packageName, it) }
        }
    }

    private companion object {
        const val ICON_PX = 144
    }
}
