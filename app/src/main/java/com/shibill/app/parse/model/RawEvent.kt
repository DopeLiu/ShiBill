package com.shibill.app.parse.model

/** 采集来源 */
enum class BillSource {
    NOTIFICATION,
    SCREENSHOT,
    IMPORT,
    MANUAL
}

/** 采集层原始事件（通知文本或截屏 OCR 文本） */
data class RawEvent(
    val source: BillSource,
    val appPackage: String,
    val rawText: String,
    val occurredAt: Long
)
