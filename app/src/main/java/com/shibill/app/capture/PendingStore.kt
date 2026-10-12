package com.shibill.app.capture

import com.shibill.app.parse.model.ParsedBill
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 解析成功但未确认的账单暂存区（内存）。
 * 通知点击 → ConfirmScreen 之间通过 pendingId 传递预填数据。
 *
 * 说明：进程被杀后暂存丢失，此时 ConfirmScreen 退化为"备注中附带原始文本"的空白表单
 * （通知同时携带 rawText 摘要作为兜底）。
 */
object PendingStore {

    private val items = ConcurrentHashMap<String, ParsedBill>()

    /** 同一 fingerprint 只保留一份；返回 pendingId */
    fun put(bill: ParsedBill): String {
        items.entries.firstOrNull { it.value.fingerprint == bill.fingerprint }
            ?.let { return it.key }
        val id = UUID.randomUUID().toString()
        items[id] = bill
        return id
    }

    fun get(id: String): ParsedBill? = items[id]

    fun remove(id: String) {
        items.remove(id)
    }
}
