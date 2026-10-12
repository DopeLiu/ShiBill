package com.shibill.app.capture

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.shibill.app.R
import com.shibill.app.ShiBillApp
import com.shibill.app.parse.model.BillSource
import com.shibill.app.parse.model.RawEvent
import com.shibill.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 兜底通道：无障碍截屏（§5.2，API 30+，默认关闭）。
 *
 * 触发方式（均受主开关 screenshot_enabled 控制）：
 * 1. 前台常驻通知「记一笔」按钮 → 手动截屏
 * 2. TYPE_WINDOW_STATE_CHANGED 检测到支付成功页 → 自动截屏（二级开关，默认关闭）
 *
 * 截图 → ML Kit 中文 OCR（离线）→ 复用 RuleEngine 解析；截图不落盘、处理完立即 recycle。
 */
class BillAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private var lastAutoTriggerAt = 0L
    private var lastForegroundPackage: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // 主开关状态驱动常驻通知显隐（关闭状态下不显示「记一笔」入口）
        scope.launch {
            val app = applicationContext as? ShiBillApp ?: return@launch
            app.container.settingsStore.screenshotEnabled.collect { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    postScreenshotButtonNotification()
                } else {
                    cancelScreenshotButtonNotification()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val pkg = e.packageName?.toString()
        if (!pkg.isNullOrEmpty()) lastForegroundPackage = pkg

        scope.launch {
            val app = applicationContext as? ShiBillApp ?: return@launch
            val settings = app.container.settingsStore

            // 主开关关闭：不做任何处理
            if (!settings.screenshotEnabled.first()) return@launch
            if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return@launch
            // 二级开关：自动触发
            if (!settings.screenshotAutoTrigger.first()) return@launch

            val currentPkg = pkg ?: return@launch
            if (currentPkg !in settings.monitoredPackages.first()) return@launch

            val now = SystemClock.elapsedRealtime()
            if (now - lastAutoTriggerAt < Constants.SCREENSHOT_AUTO_COOLDOWN_MS) return@launch
            if (!rootInActiveWindowContainsKeyword(Constants.PAYMENT_SUCCESS_KEYWORDS)) return@launch

            lastAutoTriggerAt = now
            takeScreenshotAndParse(currentPkg)
        }
    }

    override fun onInterrupt() {
        // 中断系统反馈（本服务无反馈输出，空实现）
    }

    /** 「记一笔」按钮入口（同进程回调） */
    fun triggerManualScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        scope.launch {
            val app = applicationContext as? ShiBillApp ?: return@launch
            if (!app.container.settingsStore.screenshotEnabled.first()) return@launch
            val pkg = lastForegroundPackage
                .ifEmpty { rootInActiveWindow?.packageName?.toString().orEmpty() }
            takeScreenshotAndParse(pkg)
        }
    }

    /**
     * 常驻通知（含「记一笔」按钮）。
     *
     * 说明（见交付说明"分歧裁定"第 5 条）：不使用 startForeground —— API 34+ 前台服务
     * 类型清单中不存在 accessibility 类型（javap android-35 验证），声明会导致 AAPT 构建失败；
     * 无障碍服务本身即系统绑定服务（生命周期由系统托管），常驻 ongoing 通知同样满足
     * §5.2 "常驻通知上一个'记一笔'按钮" 的产品要求。
     */
    private fun postScreenshotButtonNotification() {
        val triggerIntent = Intent(this, ScreenshotTriggerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("截屏识别已开启")
            .setContentText("支付成功后点「记一笔」可截屏识别")
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(0, getString(R.string.action_screenshot_now), pendingIntent)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(Constants.NOTIFICATION_ID_FOREGROUND, notification)
    }

    private fun cancelScreenshotButtonNotification() {
        getSystemService(NotificationManager::class.java)
            .cancel(Constants.NOTIFICATION_ID_FOREGROUND)
    }

    /** 遍历窗口节点（限量 200 个节点，避免长列表卡顿） */
    private fun rootInActiveWindowContainsKeyword(keywords: List<String>): Boolean {
        return nodeContainsKeyword(rootInActiveWindow, keywords, 200)
    }

    private fun nodeContainsKeyword(
        node: AccessibilityNodeInfo?,
        keywords: List<String>,
        budget: Int
    ): Boolean {
        if (node == null) return false
        var remaining = budget
        fun walk(current: AccessibilityNodeInfo?): Boolean {
            if (current == null || remaining <= 0) return false
            remaining--
            val text = current.text?.toString()
            val desc = current.contentDescription?.toString()
            if (text != null && keywords.any { text.contains(it) }) return true
            if (desc != null && keywords.any { desc.contains(it) }) return true
            for (index in 0 until current.childCount) {
                if (walk(current.getChild(index))) return true
            }
            return false
        }
        return walk(node)
    }

    private suspend fun takeScreenshotAndParse(pkg: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val screenshot = takeScreenshotSuspending() ?: return
        processScreenshot(screenshot.first, screenshot.second, pkg)
    }

    @Suppress("NewApi")
    private suspend fun takeScreenshotSuspending(): Pair<Bitmap, HardwareBuffer>? =
        suspendCancellableCoroutine { continuation ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val buffer = screenshotResult.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshotResult.colorSpace)
                        if (bitmap == null) {
                            buffer.close()
                            continuation.resume(null)
                        } else {
                            continuation.resume(bitmap to buffer)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        continuation.resume(null)
                    }
                }
            )
        }

    private suspend fun processScreenshot(hardware: Bitmap, buffer: HardwareBuffer, pkg: String) {
        // 转软件位图（hardware bitmap 不能直接复用），随后立即回收，不落盘
        val soft = hardware.copy(Bitmap.Config.ARGB_8888, false)
        hardware.recycle()
        buffer.close()
        if (soft == null) return

        val text = recognizeText(soft)
        soft.recycle()

        if (text.isNullOrBlank()) return
        val app = applicationContext as? ShiBillApp ?: return
        app.container.capturePipeline.process(
            RawEvent(
                source = BillSource.SCREENSHOT,
                appPackage = pkg,
                rawText = text,
                occurredAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun recognizeText(bitmap: Bitmap): String? =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result -> continuation.resume(result.text) }
                .addOnFailureListener { continuation.resume(null) }
        }

    override fun onDestroy() {
        instance = null
        runCatching { recognizer.close() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: BillAccessibilityService? = null
            private set
    }
}
