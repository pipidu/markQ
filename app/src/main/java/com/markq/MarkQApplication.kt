package com.markq

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.markq.data.MarkRepository
import com.markq.data.UpdateChecker
import com.markq.data.UpdateManager
import com.markq.data.local.AttachmentStore
import com.markq.data.local.MarkDatabase
import com.markq.data.local.SettingsStore
import com.markq.data.remote.SyncEngine
import com.markq.data.remote.WebDavClient
import java.util.concurrent.TimeUnit
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

class MarkQApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.updateManager.startOnLaunch()
    }
}
