package com.shibill.app.parse

import java.security.MessageDigest
import java.util.Locale

/**
 * 去重指纹（§6.3）。
 *
 * 指纹 = sha1(app_package + 金额(规范化为两位小数) + 商户 + occurred_at 分钟取整)
 *
 * 说明（见交付说明"分歧裁定"第 2 条）：
 * 方案公式含 source 字段，但同一笔支付被通知与导入两种通道捕获时需"自然去重"，
 * 含 source 则跨通道指纹必然不同、无法去重，且与 M4"导入与自动记录重叠笔正确去重"矛盾。
 * 因此实现为不含 source；同一分钟、同包名、同金额、同商户视为同一笔。
 *
 * manualSalt：手动记账传入 createdAt 时间戳，允许用户在同一分钟内录入两笔相同账单。
 */
object Deduplicator {

    fun fingerprint(
        appPackage: String,
        amount: Double,
        merchant: String,
        occurredAt: Long,
        manualSalt: Long? = null
    ): String {
        val minute = occurredAt / 60_000L
        val amountText = String.format(Locale.ROOT, "%.2f", amount)
        val base = buildString {
            append(appPackage).append('|')
            append(amountText).append('|')
            append(merchant.trim()).append('|')
            append(minute)
            if (manualSalt != null) append("|salt:").append(manualSalt)
        }
        return sha1(base)
    }

    fun sha1(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }
}
