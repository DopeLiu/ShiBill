package com.shibill.app.parse.model

/**
 * 解析规则（领域模型，对应 rules 表）。
 *
 * appPackage 支持逗号分隔多个包名；为空时不匹配任何事件（视为不生效）。
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
