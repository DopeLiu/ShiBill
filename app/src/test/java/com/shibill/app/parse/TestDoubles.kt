package com.shibill.app.parse

import com.shibill.app.data.db.CategoryDao
import com.shibill.app.data.db.CategoryEntity
import com.shibill.app.data.db.RuleDao
import com.shibill.app.data.db.RuleEntity
import com.shibill.app.data.db.SeedData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 纯 JVM 测试替身（不依赖 Android / Room）。
 * 默认使用生产预置规则与分类（SeedData），保证测试对象与线上一致。
 */
class FakeRuleDao(
    private val rules: List<RuleEntity> = SeedData.rules
) : RuleDao {

    override fun observeAll(): Flow<List<RuleEntity>> = flowOf(rules)

    override suspend fun getEnabledRules(): List<RuleEntity> = rules.filter { it.enabled }

    override suspend fun getAll(): List<RuleEntity> = rules

    override suspend fun getById(id: Long): RuleEntity? = rules.firstOrNull { it.id == id }

    override suspend fun insert(rule: RuleEntity): Long = TODO("测试不使用")

    override suspend fun insertAll(rules: List<RuleEntity>) = TODO("测试不使用")

    override suspend fun update(rule: RuleEntity) = TODO("测试不使用")

    override suspend fun deleteById(id: Long) = TODO("测试不使用")

    override suspend fun count(): Int = rules.size

    override suspend fun clear() = TODO("测试不使用")
}

class FakeCategoryDao(
    private val categories: List<CategoryEntity> = SeedData.categories
) : CategoryDao {

    override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(categories)

    override suspend fun getAll(): List<CategoryEntity> = categories

    override suspend fun getById(id: Long): CategoryEntity? = categories.firstOrNull { it.id == id }

    override suspend fun insertAll(categories: List<CategoryEntity>) = TODO("测试不使用")

    override suspend fun update(category: CategoryEntity) = TODO("测试不使用")

    override suspend fun count(): Int = categories.size

    override suspend fun clear() = TODO("测试不使用")
}
