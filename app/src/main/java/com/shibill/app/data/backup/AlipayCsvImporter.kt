package com.shibill.app.data.backup

import com.shibill.app.data.db.BillEntity
import com.shibill.app.data.db.BillWriter
import com.shibill.app.parse.Classifier
import com.shibill.app.parse.Deduplicator
import com.shibill.app.parse.model.Accounts
import com.shibill.app.parse.model.BillSource
import com.shibill.app.parse.model.BillType
import com.shibill.app.util.AmountFormat
import com.shibill.app.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 支付宝官方账单 CSV 导入（§8.3）。
 * - GBK 编码，跳过表头前的说明行，定位"交易时间"表头行
 * - 仅导入 收/支 ∈ {收入, 支出} 且非"交易关闭/失败"的行
 * - fingerprint 去重（IMPORT 来源，包名取支付宝，便于与自动记录交叉去重）
 */
class AlipayCsvImporter(
    private val billWriter: BillWriter,
    private val classifier: Classifier
) {

    data class ImportSummary(val inserted: Int, val duplicates: Int, val skipped: Int)

    suspend fun import(input: InputStream): ImportSummary = withContext(Dispatchers.IO) {
        val lines = input.bufferedReader(Charset.forName("GBK")).use { it.readLines() }

        val headerIndex = lines.indexOfFirst { it.contains("交易时间") }
        if (headerIndex < 0) {
            throw IllegalArgumentException("未找到支付宝账单表头（交易时间），请确认文件为支付宝导出的 CSV")
        }
        val header = parseCsvLine(lines[headerIndex])
        fun columnOf(vararg keys: String): Int =
            header.indexOfFirst { cell -> keys.any { cell.contains(it) } }

        val idxTime = columnOf("交易时间")
        val idxCounterparty = columnOf("交易对方")
        val idxItem = columnOf("商品说明", "商品名称")
        val idxDirection = columnOf("收/支", "收支")
        val idxAmount = columnOf("金额")
        val idxStatus = columnOf("交易状态")
        val idxNote = columnOf("备注")

        if (idxTime < 0 || idxAmount < 0 || idxDirection < 0) {
            throw IllegalArgumentException("支付宝账单列结构不符合预期（缺少 交易时间/金额/收支 列）")
        }

        var inserted = 0
        var duplicates = 0
        var skipped = 0

        for (line in lines.drop(headerIndex + 1)) {
            if (line.isBlank()) continue
            val cells = parseCsvLine(line)
            fun cell(index: Int): String = if (index >= 0) cells.getOrNull(index)?.trim().orEmpty() else ""

            val timeText = cell(idxTime)
            val directionText = cell(idxDirection)
            val amountText = cell(idxAmount)
            val statusText = cell(idxStatus)

            if (timeText.isEmpty() || amountText.isEmpty()) {
                skipped++
                continue
            }
            val type = when (directionText) {
                "收入" -> BillType.INCOME
                "支出" -> BillType.EXPENSE
                else -> null // 不计收支：跳过
            }
            if (type == null) {
                skipped++
                continue
            }
            if (statusText.contains("关闭") || statusText.contains("失败")) {
                skipped++
                continue
            }

            val occurredAt = TimeUtils.parseDateTime(timeText)
            val amount = AmountFormat.parse(amountText)?.takeIf { it > 0.0 }
            if (occurredAt == null || amount == null) {
                skipped++
                continue
            }

            val merchant = cell(idxCounterparty)
            val note = listOf(cell(idxItem), cell(idxNote))
                .filter { it.isNotBlank() }
                .joinToString("；")

            val categoryText = listOf(merchant, note).filter { it.isNotBlank() }.joinToString(" ")
            val categoryId = classifier.classify(categoryText)

            val bill = BillEntity(
                amount = amount,
                type = type,
                categoryId = categoryId,
                account = Accounts.ALIPAY,
                merchant = merchant,
                note = note,
                occurredAt = occurredAt,
                source = BillSource.IMPORT,
                fingerprint = Deduplicator.fingerprint(
                    appPackage = ALIPAY_PACKAGE,
                    amount = amount,
                    merchant = merchant,
                    occurredAt = occurredAt
                ),
                createdAt = System.currentTimeMillis()
            )

            if (billWriter.insert(bill, dedup = true) != null) inserted++ else duplicates++
        }

        ImportSummary(inserted = inserted, duplicates = duplicates, skipped = skipped)
    }

    /** 简单 CSV 行解析：支持双引号包裹与 "" 转义 */
    private fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            current.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        current.append(ch)
                    }
                }

                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    cells.add(current.toString())
                    current.clear()
                }

                else -> current.append(ch)
            }
            i++
        }
        cells.add(current.toString())
        return cells
    }

    private companion object {
        const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"
    }
}
