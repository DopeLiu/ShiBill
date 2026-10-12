package com.shibill.app.parse.model

/** 收支方向 */
enum class BillType {
    EXPENSE,
    INCOME
}

/** 账户（按来源包名映射） */
object Accounts {
    const val WECHAT = "微信"
    const val ALIPAY = "支付宝"
    const val UNIONPAY = "云闪付"
    const val CASH = "现金"
    const val OTHER = "其他"

    val all: List<String> = listOf(WECHAT, ALIPAY, UNIONPAY, CASH, OTHER)

    fun forPackage(pkg: String): String = when {
        pkg == "com.tencent.mm" -> WECHAT
        pkg.startsWith("com.eg.android.AlipayGphone") -> ALIPAY
        pkg == "com.unionpay" -> UNIONPAY
        else -> OTHER
    }
}

/**
 * 解析结果（RuleEngine 输出）。
 * categoryId 由 Classifier 在管道中补充（copy 回填）；0 表示未分类（UI 兜底显示"未分类"）。
 */
data class ParsedBill(
    val amount: Double,
    val type: BillType,
    val merchant: String,
    val account: String,
    val categoryId: Long = 0L,
    val occurredAt: Long,
    val source: BillSource,
    val appPackage: String,
    val rawText: String,
    val fingerprint: String
)
