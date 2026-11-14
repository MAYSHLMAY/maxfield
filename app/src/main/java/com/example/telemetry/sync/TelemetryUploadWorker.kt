package com.example.telemetry.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.telemetry.BuildConfig
import com.example.telemetry.data.TelemetryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelemetryUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val dao = TelemetryDatabase.get(appContext).telemetryDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uploader = TelemetryUploader(BuildConfig.TELEMETRY_ENDPOINT)
        var batchesSent = 0

        while (true) {
            val batch = dao.getUnsynced(BATCH_SIZE)
            if (batch.isEmpty()) break

            when (uploader.upload(batch)) {
                is UploadOutcome.Success -> {
                    dao.markSynced(batch.map { it.id })
                    batchesSent++
                }
                is UploadOutcome.Retryable ->
                    return@withContext if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
                    else Result.failure()
                is UploadOutcome.Permanent ->
                    return@withContext Result.failure()
            }
        }
        if (batchesSent > 0) dao.purgeSyncedBefore(System.currentTimeMillis() - RETENTION_MS)
        Result.success()
    }

    companion object {
        const val BATCH_SIZE = 200
        const val MAX_ATTEMPTS = 5
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}