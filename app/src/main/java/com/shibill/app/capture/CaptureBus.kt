package com.shibill.app.capture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** 采集层 → UI 事件 */
sealed interface CaptureEvent {
    /** 解析成功，待用户确认 */
    data class PendingCreated(
        val pendingId: String,
        val amount: Double,
        val merchant: String,
        val account: String
    ) : CaptureEvent

    /** 自动入账已直接入库 */
    data class AutoCommitted(val billId: Long) : CaptureEvent

    /** 疑似支付但解析失败，已发出补记通知 */
    data class ParseFailed(val rawText: String) : CaptureEvent
}

/**
 * Service → UI 事件总线（§3：用 SharedFlow）。
 * 前台界面可据此弹出"已捕获一笔支付"提示并跳转确认页。
 */
object CaptureBus {

    private val _events = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CaptureEvent> = _events

    fun emit(event: CaptureEvent) {
        _events.tryEmit(event)
    }
}
