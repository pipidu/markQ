package com.markq

import android.app.Application
import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.markq.data.MarkRepository
import com.markq.data.UpdateChecker
import com.markq.data.UpdateManager
import com.markq.data.local.AttachmentStore
import com.markq.data.local.MIGRATION_1_2
import com.markq.data.local.MIGRATION_2_3
import com.markq.data.local.MarkDatabase
import com.markq.data.local.SettingsStore
import com.markq.data.remote.SyncEngine
import com.markq.data.remote.WebDavClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class AppContainer(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val db: MarkDatabase = Room.databaseBuilder(app, MarkDatabase::class.java, "markq.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .build()

    val settings = SettingsStore(app)
    val files = AttachmentStore(app)
    val dav = WebDavClient(http)
    val sync = SyncEngine(dav, db.entries(), db.attachments(), db.cursors(), files, settings)
    val repository = MarkRepository(db, settings, files, sync, app)
    val updateChecker = UpdateChecker(http, app)
    val updateManager = UpdateManager(updateChecker, app)
}

class MarkQApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.updateManager.startOnLaunch()
        appScope.launch {
            container.repository.pruneCache(
                keepUpdateApk = container.updateManager.state.value.readyToInstall,
            )
        }
    }

    override fun newImageLoader(): ImageLoader {
        val app = this
        return ImageLoader.Builder(app)
            .memoryCache {
                MemoryCache.Builder(app)
                    .maxSizeBytes(24 * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(app.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(com.markq.data.local.CacheJanitor.MAX_IMAGE_CACHE_BYTES)
                    .build()
            }
            .build()
    }
}
