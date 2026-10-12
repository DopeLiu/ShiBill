package com.shibill.app.parse

import com.shibill.app.data.db.RuleDao
import com.shibill.app.data.db.toDomainRule
import com.shibill.app.parse.model.Accounts
import com.shibill.app.parse.model.BillSource
import com.shibill.app.parse.model.BillType
import com.shibill.app.parse.model.ParsedBill
import com.shibill.app.parse.model.RawEvent
import com.shibill.app.parse.model.Rule

/**
 * 规则引擎（§6.1）。纯 Kotlin（不 import android.*），可纯 JVM 单元测试。
 *
 * 匹配顺序：按 priority 降序逐条正则全文匹配，第一条命中即返回。
 * 命名捕获组约定：amount（必填，数字可含千分位逗号）、merchant?（可选）、direction?（可选）。
 */
class RuleEngine(private val ruleDao: RuleDao) {

    /** 从数据库读取启用规则解析（生产路径） */
    suspend fun parse(event: RawEvent): ParsedBill? =
        parse(event, ruleDao.getEnabledRules().map { it.toDomainRule() })

    /** 纯函数解析（单元测试 / 复用）。返回 null 表示所有规则都不匹配（非账单通知）。 */
    fun parse(event: RawEvent, rules: List<Rule>): ParsedBill? {
        val text = event.rawText
        if (text.isBlank()) return null

        // 防误判（硬编码在 RuleEngine，不走规则表）
        if (REJECT_KEYWORDS.any { text.contains(it) }) return null

        val ordered = rules
            .filter { it.enabled }
            .sortedWith(compareByDescending<Rule> { it.priority }.thenBy { it.id })

        for (rule in ordered) {
            if (!ruleMatchesPackage(rule, event)) continue
            val regex = try {
                Regex(rule.pattern)
            } catch (_: Exception) {
                continue // 用户编辑了非法正则：跳过该规则，不影响其他规则
            }
            val match = regex.find(text) ?: continue
            val amountRaw = match.namedGroupOrNull("amount") ?: continue
            val amount = amountRaw.replace(",", "").trim().toDoubleOrNull() ?: continue
            if (amount <= 0.0) continue

            val merchant = match.namedGroupOrNull("merchant")?.trim().orEmpty()
            val direction = match.namedGroupOrNull("direction")
            return ParsedBill(
                amount = amount,
                type = resolveType(direction),
                merchant = merchant,
                account = Accounts.forPackage(event.appPackage),
                occurredAt = event.occurredAt,
                source = event.source,
                appPackage = event.appPackage,
                rawText = text,
                fingerprint = Deduplicator.fingerprint(
                    appPackage = event.appPackage,
                    amount = amount,
                    merchant = merchant,
                    occurredAt = event.occurredAt
                )
            )
        }
        return null
    }

    /**
     * 包名匹配：规则 app_package 为逗号分隔多包名；
     * 空串通配规则仅对 SCREENSHOT 来源生效。
     */
    private fun ruleMatchesPackage(rule: Rule, event: RawEvent): Boolean {
        val packages = rule.packages()
        return if (packages.isEmpty()) {
            event.source == BillSource.SCREENSHOT
        } else {
            packages.contains(event.appPackage)
        }
    }

    /** direction → 收支方向：含"收/到账"为收入，其余（含未捕获）默认支出 */
    fun resolveType(direction: String?): BillType {
        if (direction.isNullOrBlank()) return BillType.EXPENSE
        return if (direction.contains("收") || direction.contains("到账")) BillType.INCOME
        else BillType.EXPENSE
    }

    /**
     * 安全读取命名捕获组：
     * MatchResult.groups[name] 在"模式中不存在该命名组"时会抛 IllegalArgumentException，
     * 而未定义 merchant/direction 组的规则是合法用法（均为可选组），此处统一兜底为 null。
     */
    private fun MatchResult.namedGroupOrNull(name: String): String? = try {
        groups[name]?.value
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        /** 防误判关键词（§6.1）：rawText 含任一 → 拒绝解析为账单 */
        val REJECT_KEYWORDS = listOf(
            "待支付",
            "支付失败",
            "退款",
            "正在处理",
            "小荷包",
            "还款日",
            "账单提醒",
            "余额不足"
        )
    }
}
