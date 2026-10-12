package com.shibill.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shibill.app.parse.model.BillSource
import com.shibill.app.parse.model.BillType
import com.shibill.app.parse.model.Rule

/**
 * 四张表严格对应方案 §4 的 SQL 定义（列名与约束一致）。
 * 枚举字段（type / source）由 Room 自动转换为 TEXT（枚举名）。
 */

/** 账单主表 */
@Entity(
    tableName = "bills",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["fingerprint"], unique = true)
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amount: Double,
    val type: BillType,
    @ColumnInfo(name = "category_id") val categoryId: Long = 0L,
    val account: String = "微信",
    val merchant: String = "",
    val note: String = "",
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    val source: BillSource,
    val fingerprint: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** 分类表 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val icon: String = "category",
    val keywords: String = "",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "built_in") val builtIn: Boolean = true
)

/** 解析规则表 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "app_package") val appPackage: String,
    val pattern: String,
    val priority: Int = 0,
    val enabled: Boolean = true,
    @ColumnInfo(name = "built_in") val builtIn: Boolean = true
)

/** 原始事件表（审计 + 去重依据，保留 7 天，WorkManager 每日清理） */
@Entity(
    tableName = "raw_events",
    indices = [Index(value = ["created_at"])]
)
data class RawEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val source: BillSource,
    @ColumnInfo(name = "app_package") val appPackage: String,
    @ColumnInfo(name = "raw_text") val rawText: String,
    val fingerprint: String,
    @ColumnInfo(name = "parsed_ok") val parsedOk: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
) {
    companion object {
        /** parsed_ok 取值：0=解析失败 1=解析成功 2=重复丢弃 */
        const val PARSED_FAILED = 0
        const val PARSED_OK = 1
        const val PARSED_DUPLICATE = 2
    }
}

/** 实体 → 领域模型映射 */
fun RuleEntity.toDomainRule(): Rule = Rule(
    id = id,
    name = name,
    appPackage = appPackage,
    pattern = pattern,
    priority = priority,
    enabled = enabled,
    builtIn = builtIn
)
