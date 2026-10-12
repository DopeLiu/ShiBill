package com.shibill.app.data.backup

import com.shibill.app.data.db.AppDatabase
import com.shibill.app.parse.model.BillSource
import com.shibill.app.parse.model.BillType
import com.shibill.app.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.charset.Charset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * bills 全量 CSV 导出（§8.2）。
 * 表头：日期,类型,分类,账户,商户,金额,备注,来源；GBK 编码（Excel 兼容）。
 */
class CsvExporter(private val db: AppDatabase) {

    suspend fun exportTo(output: OutputStream) = withContext(Dispatchers.IO) {
        val categories = db.categoryDao().getAll().associateBy { it.id }
        val bills = db.billDao().getAll()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val builder = StringBuilder()
        builder.append("日期,类型,分类,账户,商户,金额,备注,来源\r\n")
        for (bill in bills) {
            builder.append(csvField(TimeUtils.toLocalDateTime(bill.occurredAt).format(formatter))).append(',')
            builder.append(if (bill.type == BillType.EXPENSE) "支出" else "收入").append(',')
            builder.append(csvField(categories[bill.categoryId]?.name ?: "未分类")).append(',')
            builder.append(csvField(bill.account)).append(',')
            builder.append(csvField(bill.merchant)).append(',')
            builder.append(String.format(Locale.ROOT, "%.2f", bill.amount)).append(',')
            builder.append(csvField(bill.note)).append(',')
            builder.append(sourceLabel(bill.source)).append("\r\n")
        }
        output.write(builder.toString().toByteArray(Charset.forName("GBK")))
    }

    private fun csvField(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun sourceLabel(source: BillSource): String = when (source) {
        BillSource.NOTIFICATION -> "通知"
        BillSource.SCREENSHOT -> "截屏"
        BillSource.IMPORT -> "导入"
        BillSource.MANUAL -> "手动"
    }
}
