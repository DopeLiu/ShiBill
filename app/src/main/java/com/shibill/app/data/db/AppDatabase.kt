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
    version = 2,
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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
