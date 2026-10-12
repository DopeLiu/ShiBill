package com.shibill.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shibill.app.ShiBillApp
import com.shibill.app.util.Constants

/** 每日清理 7 天前的 raw_events（§4） */
class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ShiBillApp ?: return Result.success()
        val cutoff = System.currentTimeMillis() -
                Constants.RAW_EVENT_RETENTION_DAYS * 24L * 60L * 60L * 1000L
        app.container.db.rawEventDao().deleteOlderThan(cutoff)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "raw_event_cleanup"
    }
}
