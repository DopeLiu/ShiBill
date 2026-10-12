package com.shibill.app.parse

import com.shibill.app.data.db.CategoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClassifierTest {

    private val classifier = Classifier(FakeCategoryDao())

    @Test
    fun `美团 → 餐饮`() {
        val hit = classifier.classify("美团外卖", defaultCategories())
        assertNotNull(hit)
        assertEquals("餐饮", hit!!.name)
    }

    @Test
    fun `京东 → 购物`() {
        val hit = classifier.classify("京东商城", defaultCategories())
        assertEquals("购物", hit?.name)
    }

    @Test
    fun `星巴克 → 餐饮`() {
        val hit = classifier.classify("星巴克咖啡￥38.00", defaultCategories())
        assertEquals("餐饮", hit?.name)
    }

    @Test
    fun `工资 → 工资`() {
        val hit = classifier.classify("9月工资", defaultCategories())
        assertEquals("工资", hit?.name)
    }

    @Test
    fun `未命中 → null（纯函数）`() {
        assertNull(classifier.classify("某个不存在的商户XYZ", defaultCategories()))
    }

    @Test
    fun `未命中 → 生产路径归入未分类`() = runBlocking {
        val id = classifier.classify("某个不存在的商户XYZ")
        val uncategorized = defaultCategories().first { it.name == "未分类" }
        assertEquals(uncategorized.id, id)
    }

    @Test
    fun `空文本 → null`() {
        assertNull(classifier.classify("  ", defaultCategories()))
    }

    @Test
    fun `命中生产路径返回对应分类 id`() = runBlocking {
        val id = classifier.classify("美团外卖")
        val food = defaultCategories().first { it.name == "餐饮" }
        assertEquals(food.id, id)
    }

    @Test
    fun `按 sort_order 顺序第一个命中即返回`() {
        // "美团"（餐饮 sortOrder=0）与"京东"（购物 sortOrder=1）同时出现 → 餐饮优先
        val hit = classifier.classify("美团 京东", defaultCategories())
        assertEquals("餐饮", hit?.name)
    }

    @Test
    fun `关键词匹配大小写不敏感`() {
        val categories = listOf(
            CategoryEntity(id = 1, name = "咖啡", keywords = "starbucks", sortOrder = 0)
        )
        val hit = classifier.classify("STARBUCKS COFFEE", categories)
        assertEquals("咖啡", hit?.name)
    }

    private fun defaultCategories() = com.shibill.app.data.db.SeedData.categories
}
