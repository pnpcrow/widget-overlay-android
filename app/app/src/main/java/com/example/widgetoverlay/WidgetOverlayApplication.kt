package com.example.widgetoverlay

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WidgetOverlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MaintenanceScheduler.schedule(this)
    }
}

/** A maintenance-only worker: it validates a saved platform ID and does not poll widget data. */
class DailyMaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        WidgetHostController(applicationContext).validateOrClear()
        return Result.success()
    }
}

object MaintenanceScheduler {
    private const val UNIQUE_WORK_NAME = "widget_id_maintenance"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(24, TimeUnit.HOURS)
            .addTag(UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

