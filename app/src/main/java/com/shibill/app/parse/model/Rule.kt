package com.shibill.app.parse.model

/**
 * 解析规则（领域模型，对应 rules 表）。
 *
 * appPackage 支持逗号分隔多个包名；空字符串为"通配规则"，
 * 约定仅对 SCREENSHOT 来源生效（对应内置"截屏文本通用"规则"仅 SCREENSHOT 来源"的约束）。
 */
data class Rule(
    val id: Long = 0L,
    val name: String,
    val appPackage: String,
    val pattern: String,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val builtIn: Boolean = true
) {
    fun packages(): List<String> =
        appPackage.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
