package com.shibill.app

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shibill.app.capture.CaptureNotifier
import com.shibill.app.capture.CapturePipeline
import com.shibill.app.data.CleanupWorker
import com.shibill.app.data.db.AppDatabase
import com.shibill.app.data.db.BillWriter
import com.shibill.app.data.db.SeedData
import com.shibill.app.data.prefs.SettingsStore
import com.shibill.app.parse.Classifier
import com.shibill.app.parse.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ShiBillApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CaptureNotifier.ensureChannel(this)
        container.appScope.launch { SeedData.seedIfEmpty(container.db) }
        enqueueRawEventCleanup()
    }

    /** §4：raw_events 保留 7 天，WorkManager 每日清理 */
    private fun enqueueRawEventCleanup() {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CleanupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

/** 手写单例容器（§2：不引入 Hilt） */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase = AppDatabase.get(appContext)
    val settingsStore = SettingsStore(appContext)
    val ruleEngine = RuleEngine(db.ruleDao())
    val classifier = Classifier(db.categoryDao())
    val billWriter = BillWriter(db.billDao())
    val capturePipeline = CapturePipeline(
        context = appContext,
        ruleEngine = ruleEngine,
        classifier = classifier,
        billWriter = billWriter,
        billDao = db.billDao(),
        rawEventDao = db.rawEventDao(),
        settingsStore = settingsStore
    )
}
