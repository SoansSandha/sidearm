package io.github.soanssandha.sidearm.data

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import io.github.soanssandha.sidearm.core.config.TargetComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads launcher icons as bitmaps of [sizePx], off the main thread. Recent ones are kept for the whole process, so
 * they survive the activity being recreated, as on rotation.
 */
class AppIcons(private val packageManager: PackageManager, private val sizePx: Int) {

    /** The icon of [component] if it's already loaded, without waiting. */
    fun cached(component: TargetComponent): Bitmap? = cache.get(key(component))

    /** The icon of [component], or null if the app is gone. */
    suspend fun load(component: TargetComponent): Bitmap? = cached(component) ?: withContext(Dispatchers.IO) {
        runCatching {
            packageManager.getActivityIcon(ComponentName(component.packageName, component.className))
                .toBitmap(sizePx, sizePx)
        }.getOrNull()?.also { cache.put(key(component), it) }
    }

    private fun key(component: TargetComponent) = "${component.flatten()}@$sizePx"

    private companion object {
        const val CACHE_BYTES = 8 * 1024 * 1024

        val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }
    }
}
