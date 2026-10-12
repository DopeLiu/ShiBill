package com.shibill.app.util

object Constants {

    const val NOTIFICATION_CHANNEL_CAPTURE = "capture_confirm"

    /** 截屏识别常驻通知 id（含「记一笔」按钮） */
    const val NOTIFICATION_ID_FOREGROUND = 1001

    /** raw_events 保留天数（§4：保留 7 天，WorkManager 每日清理） */
    const val RAW_EVENT_RETENTION_DAYS = 7L

    /** 截屏自动触发的冷却时间，避免支付成功页多次窗口变化重复截屏 */
    const val SCREENSHOT_AUTO_COOLDOWN_MS = 10_000L

    /** 截屏自动触发检测的页面关键词（§5.2） */
    val PAYMENT_SUCCESS_KEYWORDS = listOf("支付成功", "付款成功", "交易成功")

    /**
     * "疑似支付"关键词：解析失败且文本包含任一关键词时才提示"手动补记"。
     * 目的：监控包名（微信/支付宝）的普通聊天通知不触发补记干扰。
     */
    val PAYMENT_HINT_KEYWORDS = listOf(
        "支付", "付款", "收款", "到账", "消费", "扣款",
        "交易", "转账", "支出", "收入", "已收", "已付"
    )

    /**
     * 检查更新仓库（GitHub Releases）。
     * TODO 发布前替换为实际仓库：owner/repo
     */
    const val UPDATE_REPO = "shibill/shibill"
}
