package com.shibill.app.util

import java.util.Locale
import kotlin.math.abs

object AmountFormat {

    /** "¥25.00" */
    fun format(amount: Double): String = "¥" + String.format(Locale.ROOT, "%.2f", abs(amount))

    /** 带方向符号："-¥25.00"（支出） / "+¥25.00"（收入） */
    fun formatSigned(amount: Double, isIncome: Boolean): String =
        (if (isIncome) "+" else "-") + format(amount)

    /** 解析用户输入 / 账单文本："1,234.50" → 1234.5；非法或负数返回 null */
    fun parse(text: String): Double? =
        text.replace(",", "").trim().toDoubleOrNull()?.takeIf { it >= 0 }
}
