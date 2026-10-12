package com.shibill.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BillEntity::class,
        CategoryEntity::class,
        RuleEntity::class,
        RawEventEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun billDao(): BillDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ruleDao(): RuleDao
    abstract fun rawEventDao(): RawEventDao

    companion object {
        private const val DB_NAME = "shibill.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * V1→V2：预置分类名精简（仅更新数据，不改表结构），已有账单的 category_id 不变。
         * 迁移 SQL 必须固化字面量，不得引用 SeedData（后续版本改动不得影响历史迁移）。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val renames = listOf(
                    "餐饮美食" to "餐饮",
                    "交通出行" to "交通",
                    "日用百货" to "日用",
                    "人情往来" to "礼金",
                    "工资收入" to "工资",
                    "退款收入" to "退款",
                    "理财转账" to "理财"
                )
                renames.forEach { (oldName, newName) ->
                    db.execSQL(
                        "UPDATE categories SET name = ? WHERE name = ?",
                        arrayOf(newName, oldName)
                    )
                }
            }
        }

        /**
         * V2→V3：移除无障碍截屏识别通道。
         * - 历史截屏来源的账单 / 原始事件统一改写为手动来源（BillSource.SCREENSHOT 已删除）
         * - 删除仅服务于截屏来源的内置通配规则（空包名 + 内置）
         * 迁移 SQL 必须固化字面量，不得引用 SeedData。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE bills SET source = 'MANUAL' WHERE source = 'SCREENSHOT'")
                db.execSQL("UPDATE raw_events SET source = 'MANUAL' WHERE source = 'SCREENSHOT'")
                db.execSQL("DELETE FROM rules WHERE app_package = '' AND built_in = 1")
            }
        }

        /**
         * V3→V4：放宽 2 条**微信**内置规则（允许「品牌词 → 换行 → 方向词 → 金额」的两行式通知）。
         *
         * 背景：监听服务把 标题+正文+大文本 用换行拼接后再做正则匹配，而微信通知标题恒为「微信支付」、
         * 消费通知正文不带品牌词，故 rawText 形如 "微信支付\n已支付¥10.00"。
         * 旧规则把冒号写死（微信支付[：:]），此处是换行 → 整笔解析失败（只弹"手动补记"、永不入账）。
         *
         * 支付宝两条规则**不在本迁移内**：其冒号本来就是可选的（[：:]?），无需修改。
         *
         * seedIfEmpty 仅在 rules 表为空时写入，所以**已安装的老用户必须靠本迁移才能拿到修复**。
         * 迁移 SQL 必须固化字面量，不得引用 SeedData（后续改动不得影响历史迁移）。
         * 按 built_in + name 定位：用户自建规则的 built_in 为 0（见规则编辑弹窗），不受影响。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "微信收款" to
                            "微信支付\\s*(?<direction>收款)\\s*[¥￥]?\\s*(?<amount>[\\d,]+\\.?\\d*)",
                    "微信支付通知" to
                            "微信支付[：:]?\\s*(?:(?<direction>已支付|已付款|成功支付|支付成功|付款成功|已收款|成功收款|收款成功)\\s*)?[¥￥]?\\s*(?<amount>[\\d,]+\\.?\\d*)"
                ).forEach { (name, pattern) ->
                    db.execSQL(
                        "UPDATE rules SET pattern = ? WHERE built_in = 1 AND name = ?",
                        arrayOf(pattern, name)
                    )
                }
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
