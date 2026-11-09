package com.zynerio.bibliotecajelly.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zynerio.bibliotecajelly.data.ServiceLocator
import com.zynerio.bibliotecajelly.data.SyncResult

class JellyfinSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = ServiceLocator.provideRepository(applicationContext)

        val result = repository.syncIncremental { processed, total, phase ->
            setProgressAsync(
                workDataOf(
                    "processed" to processed,
                    "total" to total,
                    "phase" to phase.name
                )
            )
        }

        return when (result) {
            SyncResult.Success -> Result.success()
            is SyncResult.NetworkError -> Result.retry()
            is SyncResult.UnknownError -> Result.failure(
                workDataOf("error" to result.message)
            )
        }
    }
}
