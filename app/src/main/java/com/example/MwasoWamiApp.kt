package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers

class MwasoWamiApp : Application(), ImageLoaderFactory {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .dispatcher(Dispatchers.IO)
            .interceptorDispatcher(Dispatchers.IO)
            .transformationDispatcher(Dispatchers.Default)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // 15% max memory for thumbnails (safe for 2GB devices)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("mwaso_image_cache"))
                    .maxSizeBytes(25L * 1024 * 1024) // 25 MB disk cache
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowRgb565(true) // 16-bit RGB 565 uses half the memory of ARGB_8888
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    companion object {
        lateinit var instance: MwasoWamiApp
            private set
    }
}

