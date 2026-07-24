package com.cactus.bitacora.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cactus.bitacora.data.BitacoraRepository
import java.util.concurrent.TimeUnit

class OfflineSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result =
        try {
            Log.i(SYNC_TAG, "worker start runAttemptCount=$runAttemptCount")
            val result = BitacoraRepository(applicationContext).sincronizarPendientes()
            val output = Data.Builder()
                .putInt("revisados", result.revisados)
                .putInt("sincronizados", result.sincronizados)
                .putInt("errores", result.errores)
                .build()
            Log.i(
                SYNC_TAG,
                "worker finish reviewed=${result.revisados} synced=${result.sincronizados} " +
                    "errors=${result.errores} retryable=${result.erroresReintentables}"
            )
            when (workerDecision(result.erroresReintentables)) {
                WorkerDecision.RETRY -> Result.retry()
                WorkerDecision.SUCCESS -> Result.success(output)
            }
        } catch (error: Exception) {
            Log.e(
                SYNC_TAG,
                "worker failed runAttemptCount=$runAttemptCount error=${error.javaClass.simpleName}: " +
                    "${error.message?.take(200)}"
            )
            Result.retry()
        }

    private companion object {
        const val SYNC_TAG = "OfflineSync"
    }
}

internal enum class WorkerDecision { SUCCESS, RETRY }

internal fun workerDecision(retryableErrors: Int): WorkerDecision =
    if (retryableErrors > 0) WorkerDecision.RETRY else WorkerDecision.SUCCESS

object OfflineSyncScheduler {
    internal const val PERIODIC_WORK_NAME = "bitacora-offline-sync-periodic"
    internal const val MANUAL_WORK_NAME = "bitacora-offline-sync-manual"
    internal const val BACKOFF_SECONDS = 30L

    internal fun connectedConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    internal fun manualWorkPolicy(): ExistingWorkPolicy = ExistingWorkPolicy.KEEP

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<OfflineSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            manualWorkPolicy(),
            request
        )
    }
}
