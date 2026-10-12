package com.shibill.app.util

object Constants {

    const val NOTIFICATION_CHANNEL_CAPTURE = "capture_confirm"

    /** raw_events 保留天数（§4：保留 7 天，WorkManager 每日清理） */
    const val RAW_EVENT_RETENTION_DAYS = 7L

    /**
     * "疑似支付"关键词：解析失败且文本包含任一关键词时才提示"手动补记"。
     * 目的：监控包名（微信/支付宝）的普通聊天通知不触发补记干扰。
     */
    val PAYMENT_HINT_KEYWORDS = listOf(
        "支付", "付款", "收款", "到账", "消费", "扣款",
        "交易", "转账", "支出", "收入", "已收", "已付"
    )

    /** 检查更新仓库（GitHub Releases），格式 owner/repo */
    const val UPDATE_REPO = "DopeLiu/ShiBill"
}
