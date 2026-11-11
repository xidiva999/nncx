package dev.nncx.apps

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An icon plus the one fact that decides how to draw it.
 *
 * Adaptive icons are designed to be masked — they bleed past the shape on
 * purpose, so they fill the tile and get clipped. Legacy icons already carry
 * their own silhouette on transparency; clipping those to a circle slices
 * corners off. So they are drawn inset instead, never masked.
 */
data class AppIcon(
    val image: ImageBitmap,
    val adaptive: Boolean,
)

object Icons {

    /** One render size for every surface; Compose scales down for list rows. */
    const val RENDER_PX = 132

    // ~132² × 4B ≈ 70KB each. 96 entries is about 6MB, which is a fair trade
    // for never loading the same icon twice.
    private val cache = object : LruCache<String, AppIcon>(96) {}

    private val missing = HashSet<String>()

    fun cached(packageName: String): AppIcon? = cache.get(packageName)

    suspend fun load(context: Context, entry: AppEntry): AppIcon? {
        cache.get(entry.packageName)?.let { return it }
        if (entry.packageName in missing) return null

        return withContext(Dispatchers.IO) {
            val drawable = resolve(context, entry)
            if (drawable == null) {
                missing.add(entry.packageName)
                return@withContext null
            }
            val icon = AppIcon(
                image = drawable.toBitmap(RENDER_PX, RENDER_PX).asImageBitmap(),
                adaptive = drawable is AdaptiveIconDrawable,
            )
            cache.put(entry.packageName, icon)
            icon
        }
    }

    private fun resolve(context: Context, entry: AppEntry): Drawable? {
        val pm = context.packageManager
        return runCatching { pm.getActivityIcon(entry.component) }
            .recoverCatching { pm.getApplicationIcon(entry.packageName) }
            .getOrNull()
    }

    fun clear() {
        cache.evictAll()
        missing.clear()
    }
}
