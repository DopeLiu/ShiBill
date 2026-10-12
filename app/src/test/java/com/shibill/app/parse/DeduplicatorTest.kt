package com.shibill.app.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeduplicatorTest {

    private val fixedTime = 1_760_000_000_000L // 某分钟内的固定时间戳

    @Test
    fun `相同输入产生相同指纹`() {
        val a = Deduplicator.fingerprint("com.tencent.mm", 25.0, "美团", fixedTime)
        val b = Deduplicator.fingerprint("com.tencent.mm", 25.0, "美团", fixedTime)
        assertEquals(a, b)
    }

    @Test
    fun `同一分钟内不同秒 → 指纹相同（分钟取整）`() {
        val a = Deduplicator.fingerprint("com.tencent.mm", 25.0, "", fixedTime)
        val b = Deduplicator.fingerprint("com.tencent.mm", 25.0, "", fixedTime + 3_000L)
        assertEquals(a, b)
    }

    @Test
    fun `跨分钟 → 指纹不同`() {
        val a = Deduplicator.fingerprint("com.tencent.mm", 25.0, "", fixedTime)
        val b = Deduplicator.fingerprint("com.tencent.mm", 25.0, "", fixedTime + 60_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `金额规范化 25 与 25_00 一致`() {
        val a = Deduplicator.fingerprint("com.tencent.mm", 25.0, "", fixedTime)
        val b = Deduplicator.fingerprint("com.tencent.mm", 25.00, "", fixedTime)
        assertEquals(a, b)
    }

    @Test
    fun `商户名 trim 后一致`() {
        val a = Deduplicator.fingerprint("com.tencent.mm", 25.0, " 美团 ", fixedTime)
        val b = Deduplicator.fingerprint("com.tencent.mm", 25.0, "美团", fixedTime)
        assertEquals(a, b)
    }

    @Test
    fun `不同包名 → 指纹不同`() {
        val a = Deduplicator.fingerprint("com.tencent.mm", 25.0, "", fixedTime)
        val b = Deduplicator.fingerprint("com.eg.android.AlipayGphone", 25.0, "", fixedTime)
        assertNotEquals(a, b)
    }

    @Test
    fun `不同金额 → 指纹不同`() {
        val a = Deduplicator.fingerprint("com.tencent.mm", 25.0, "", fixedTime)
        val b = Deduplicator.fingerprint("com.tencent.mm", 25.01, "", fixedTime)
        assertNotEquals(a, b)
    }

    @Test
    fun `manualSalt 不同 → 指纹不同（手动记账允许同分钟重复录入）`() {
        val a = Deduplicator.fingerprint("manual", 15.0, "食堂", fixedTime, manualSalt = 1L)
        val b = Deduplicator.fingerprint("manual", 15.0, "食堂", fixedTime, manualSalt = 2L)
        assertNotEquals(a, b)
    }

    @Test
    fun `指纹为 40 位十六进制（sha1）`() {
        val fp = Deduplicator.fingerprint("com.tencent.mm", 25.0, "", fixedTime)
        assertEquals(40, fp.length)
        assertEquals(true, fp.all { it in "0123456789abcdef" })
    }

    @Test
    fun `跨通道（通知与截屏）同一笔支付指纹一致`() {
        // 当前实现不含 source：同一分钟、同包名、同金额、同商户视为同一笔（见交付说明裁定 2）
        val notificationFp = Deduplicator.fingerprint("com.tencent.mm", 25.0, "", fixedTime)
        val screenshotFp = Deduplicator.fingerprint("com.tencent.mm", 25.0, "", fixedTime + 5_000L)
        assertEquals(notificationFp, screenshotFp)
    }
}
