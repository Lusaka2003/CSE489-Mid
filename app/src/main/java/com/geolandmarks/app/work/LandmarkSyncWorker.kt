package com.geolandmarks.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.geolandmarks.app.LandmarkApplication

class LandmarkSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? LandmarkApplication
            ?: return Result.retry()
        return try {
            val finished = app.container.repository.runBackgroundSync()
            if (finished) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
