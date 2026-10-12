package com.shibill.app.data.backup

import androidx.room.withTransaction
import com.shibill.app.data.db.AppDatabase
import com.shibill.app.data.db.BillEntity
import com.shibill.app.data.db.CategoryEntity
import com.shibill.app.data.db.RuleEntity
import com.shibill.app.parse.model.BillSource
import com.shibill.app.parse.model.BillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * JSON 备份导出 / 导入（§8.1）。
 * 格式：{"version":1,"exported_at":…,"bills":[…],"categories":[…],"rules":[…]}
 * 导入为全量替换（调用方负责先弹确认对话框）。
 */
class BackupExporter(private val db: AppDatabase) {

    data class ImportSummary(val bills: Int, val categories: Int, val rules: Int)

    suspend fun exportTo(output: OutputStream) = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exported_at", System.currentTimeMillis())
            put("bills", JSONArray().apply {
                db.billDao().getAll().forEach { put(billToJson(it)) }
            })
            put("categories", JSONArray().apply {
                db.categoryDao().getAll().forEach { put(categoryToJson(it)) }
            })
            put("rules", JSONArray().apply {
                db.ruleDao().getAll().forEach { put(ruleToJson(it)) }
            })
        }
        output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
    }

    suspend fun importFrom(input: InputStream): ImportSummary = withContext(Dispatchers.IO) {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        require(root.optInt("version", 0) == BACKUP_VERSION) {
            "备份文件版本不受支持（需要 version=$BACKUP_VERSION）"
        }

        val bills = root.optJSONArray("bills")
            ?.let { array -> (0 until array.length()).map { billFromJson(array.getJSONObject(it)) } }
            .orEmpty()
        val categories = root.optJSONArray("categories")
            ?.let { array -> (0 until array.length()).map { categoryFromJson(array.getJSONObject(it)) } }
            .orEmpty()
        val rules = root.optJSONArray("rules")
            ?.let { array -> (0 until array.length()).map { ruleFromJson(array.getJSONObject(it)) } }
            .orEmpty()

        // 全量替换（事务）
        db.withTransaction {
            db.billDao().clear()
            db.categoryDao().clear()
            db.ruleDao().clear()
            db.categoryDao().insertAll(categories)
            db.ruleDao().insertAll(rules)
            db.billDao().insertAll(bills)
        }
        ImportSummary(bills = bills.size, categories = categories.size, rules = rules.size)
    }

    private fun billToJson(bill: BillEntity): JSONObject = JSONObject().apply {
        put("id", bill.id)
        put("amount", bill.amount)
        put("type", bill.type.name)
        put("category_id", bill.categoryId)
        put("account", bill.account)
        put("merchant", bill.merchant)
        put("note", bill.note)
        put("occurred_at", bill.occurredAt)
        put("source", bill.source.name)
        put("fingerprint", bill.fingerprint)
        put("created_at", bill.createdAt)
    }

    private fun billFromJson(json: JSONObject): BillEntity = BillEntity(
        id = json.optLong("id"),
        amount = json.optDouble("amount"),
        type = runCatching { BillType.valueOf(json.getString("type")) }.getOrDefault(BillType.EXPENSE),
        categoryId = json.optLong("category_id"),
        account = json.optString("account", "其他"),
        merchant = json.optString("merchant"),
        note = json.optString("note"),
        occurredAt = json.optLong("occurred_at"),
        source = runCatching { BillSource.valueOf(json.getString("source")) }.getOrDefault(BillSource.MANUAL),
        fingerprint = json.getString("fingerprint"),
        createdAt = json.optLong("created_at")
    )

    private fun categoryToJson(category: CategoryEntity): JSONObject = JSONObject().apply {
        put("id", category.id)
        put("name", category.name)
        put("icon", category.icon)
        put("keywords", category.keywords)
        put("sort_order", category.sortOrder)
        put("built_in", category.builtIn)
    }

    private fun categoryFromJson(json: JSONObject): CategoryEntity = CategoryEntity(
        id = json.optLong("id"),
        name = json.getString("name"),
        icon = json.optString("icon", "category"),
        keywords = json.optString("keywords"),
        sortOrder = json.optInt("sort_order"),
        builtIn = json.optBoolean("built_in", true)
    )

    private fun ruleToJson(rule: RuleEntity): JSONObject = JSONObject().apply {
        put("id", rule.id)
        put("name", rule.name)
        put("app_package", rule.appPackage)
        put("pattern", rule.pattern)
        put("priority", rule.priority)
        put("enabled", rule.enabled)
        put("built_in", rule.builtIn)
    }

    private fun ruleFromJson(json: JSONObject): RuleEntity = RuleEntity(
        id = json.optLong("id"),
        name = json.getString("name"),
        appPackage = json.optString("app_package"),
        pattern = json.getString("pattern"),
        priority = json.optInt("priority"),
        enabled = json.optBoolean("enabled", true),
        builtIn = json.optBoolean("built_in", true)
    )

    companion object {
        const val BACKUP_VERSION = 1
    }
}
