package com.shibill.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 前台常驻通知「记一笔」按钮 → 触发无障碍服务截屏（同进程单例回调） */
class ScreenshotTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        BillAccessibilityService.instance?.triggerManualScreenshot()
    }
}
