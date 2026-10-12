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

/**
 * 微信官方账单 xlsx 导入（§8.3）。
 * 列：交易时间 / 交易类型 / 交易对方 / 商品 / 收/支 / 金额(元) / 支付方式 / 当前状态 / … / 备注
 * 金额(元) 形如 "¥25.00"；收/支 ∈ {支出, 收入, /}，"/" 表示不计收支、跳过。
 */
class WeChatXlsxImporter(
    private val billWriter: BillWriter,
    private val classifier: Classifier
) {

    data class ImportSummary(val inserted: Int, val duplicates: Int, val skipped: Int)

    suspend fun import(input: InputStream): ImportSummary = withContext(Dispatchers.IO) {
        val sheet = XlsxReader.readFirstSheet(input)

        val headerIndex = sheet.indexOfFirst { row -> row.any { it.contains("交易时间") } }
        if (headerIndex < 0) {
            throw IllegalArgumentException("未找到微信账单表头（交易时间），请确认文件为微信导出的 xlsx 账单")
        }
        val header = sheet[headerIndex]
        fun columnOf(vararg keys: String): Int =
            header.indexOfFirst { cell -> keys.any { cell.contains(it) } }

        val idxTime = columnOf("交易时间")
        val idxType = columnOf("交易类型")
        val idxCounterparty = columnOf("交易对方")
        val idxItem = columnOf("商品")
        val idxDirection = columnOf("收/支", "收支")
        val idxAmount = columnOf("金额")
        val idxStatus = columnOf("当前状态")
        val idxRemark = columnOf("备注")

        if (idxTime < 0 || idxAmount < 0 || idxDirection < 0) {
            throw IllegalArgumentException("微信账单列结构不符合预期（缺少 交易时间/金额/收支 列）")
        }

        var inserted = 0
        var duplicates = 0
        var skipped = 0

        for (row in sheet.drop(headerIndex + 1)) {
            fun cell(index: Int): String = if (index >= 0) row.getOrNull(index)?.trim().orEmpty() else ""

            val timeText = cell(idxTime)
            val directionText = cell(idxDirection)
            val amountText = cell(idxAmount).replace("¥", "").replace("￥", "")
            val statusText = cell(idxStatus)

            if (timeText.isEmpty() || amountText.isEmpty()) {
                skipped++
                continue
            }
            val type = when {
                directionText == "收入" -> BillType.INCOME
                directionText == "支出" -> BillType.EXPENSE
                else -> null // "/" 等不计收支行
            }
            if (type == null) {
                skipped++
                continue
            }
            if (statusText.contains("失败") || statusText.contains("退款")) {
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
            val note = listOf(cell(idxType), cell(idxItem), cell(idxRemark))
                .filter { it.isNotBlank() }
                .joinToString("；")

            val categoryText = listOf(merchant, note).filter { it.isNotBlank() }.joinToString(" ")
            val categoryId = classifier.classify(categoryText)

            val bill = BillEntity(
                amount = amount,
                type = type,
                categoryId = categoryId,
                account = Accounts.WECHAT,
                merchant = merchant,
                note = note,
                occurredAt = occurredAt,
                source = BillSource.IMPORT,
                fingerprint = Deduplicator.fingerprint(
                    appPackage = WECHAT_PACKAGE,
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

    private companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
    }
}
