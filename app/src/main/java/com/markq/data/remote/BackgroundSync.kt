package com.markq.data.remote

import androidx.annotation.Keep
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.markq.MarkQApplication
import java.util.concurrent.TimeUnit

@Keep
class IncrementalSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MarkQApplication ?: return Result.success()
        val cfg = app.container.settings.current()
        if (!cfg.backgroundSync || !cfg.isConfigured) return Result.success()
        app.container.sync.sync()
        return Result.success()
    }
}

object BackgroundSyncScheduler {
    const val UNIQUE_WORK = "markq-incremental-sync"
    const val PERIOD_MINUTES = 30L

    fun apply(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            wm.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<IncrementalSyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
