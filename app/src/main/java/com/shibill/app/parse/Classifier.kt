package com.shibill.app.parse

import com.shibill.app.data.db.CategoryDao
import com.shibill.app.data.db.CategoryEntity

/**
 * 分类器（§6.4）：按账单文本对 categories.keywords 做包含匹配，命中即分类；
 * 未命中 → "未分类"。
 */
class Classifier(private val categoryDao: CategoryDao) {

    /** 生产路径：返回分类 id；未命中返回"未分类"分类 id（极端兜底 0） */
    suspend fun classify(text: String): Long {
        val categories = categoryDao.getAll()
        return classify(text, categories)?.id
            ?: categories.firstOrNull { it.name == UNCATEGORIZED_NAME }?.id
            ?: UNCATEGORIZED_ID
    }

    /** 纯函数：按 sort_order 顺序逐分类做关键词包含匹配，第一个命中即返回；未命中返回 null */
    fun classify(text: String, categories: List<CategoryEntity>): CategoryEntity? {
        if (text.isBlank()) return null
        for (category in categories.sortedBy { it.sortOrder }) {
            val keywords = category.keywords
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (keywords.any { text.contains(it, ignoreCase = true) }) return category
        }
        return null
    }

    companion object {
        const val UNCATEGORIZED_ID = 0L
        const val UNCATEGORIZED_NAME = "未分类"
    }
}
