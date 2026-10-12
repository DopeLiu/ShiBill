package com.shibill.app.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.shibill.app.ShiBillApp
import com.shibill.app.parse.model.BillSource
import com.shibill.app.parse.model.RawEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 主通道：通知监听（§5.1）。
 * 覆盖 90% 场景；服务被杀由系统自动重连（onListenerConnected 无需特殊处理）。
 */
class BillNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbnNotNull = sbn ?: return

        // 常驻通知（"正在播放"、充电提示等）直接忽略
        if (sbnNotNull.isOngoing) return

        val pkg = sbnNotNull.packageName ?: return
        // 自身发出的通知不处理
        if (pkg == packageName) return

        val app = applicationContext as? ShiBillApp ?: return
        scope.launch {
            val container = app.container

            // 监控范围 = 设置页包名集合 ∪ 启用规则中的包名集合
            val monitored = container.settingsStore.monitoredPackages.first()
            val rulePackages = container.db.ruleDao().getEnabledRules()
                .flatMap { rule -> rule.appPackage.split(',').map { it.trim() } }
                .filter { it.isNotEmpty() }
                .toSet()
            if (pkg !in monitored && pkg !in rulePackages) return@launch

            // 提取标题 / 正文 / 大文本，拼接为 rawText
            val extras = sbnNotNull.notification?.extras ?: return@launch
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            val rawText = listOf(title, text, bigText)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")
            if (rawText.isBlank()) return@launch

            container.capturePipeline.process(
                RawEvent(
                    source = BillSource.NOTIFICATION,
                    appPackage = pkg,
                    rawText = rawText,
                    occurredAt = sbnNotNull.postTime
                )
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
