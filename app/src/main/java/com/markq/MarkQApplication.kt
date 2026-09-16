package com.markq

import android.app.Application
import androidx.room.Room
import com.markq.data.MarkRepository
import com.markq.data.UpdateChecker
import com.markq.data.local.AttachmentStore
import com.markq.data.local.MarkDatabase
import com.markq.data.local.SettingsStore
import com.markq.data.remote.SyncEngine
import com.markq.data.remote.WebDavClient
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class AppContainer(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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
    val updates = UpdateChecker(http)
}

class MarkQApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
