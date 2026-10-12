package com.shibill.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AmountFormatTest {

    @Test
    fun `format 输出两位小数与货币符号`() {
        assertEquals("¥25.00", AmountFormat.format(25.0))
        assertEquals("¥0.01", AmountFormat.format(0.01))
        assertEquals("¥1234.50", AmountFormat.format(1234.5))
    }

    @Test
    fun `formatSigned 支出为负号`() {
        assertEquals("-¥25.00", AmountFormat.formatSigned(25.0, isIncome = false))
    }

    @Test
    fun `formatSigned 收入为正号`() {
        assertEquals("+¥25.00", AmountFormat.formatSigned(25.0, isIncome = true))
    }

    @Test
    fun `parse 支持千分位`() {
        assertEquals(1234.5, AmountFormat.parse("1,234.50")!!, 0.001)
    }

    @Test
    fun `parse 非法输入返回 null`() {
        assertNull(AmountFormat.parse("abc"))
        assertNull(AmountFormat.parse(""))
        assertNull(AmountFormat.parse("-5"))
    }

    @Test
    fun `parse 零值合法`() {
        assertEquals(0.0, AmountFormat.parse("0")!!, 0.001)
    }
}

class TimeUtilsTest {

    @Test
    fun `parseDateTime 支持标准格式`() {
        assertNotNull(TimeUtils.parseDateTime("2026-09-15 12:30:45"))
    }

    @Test
    fun `parseDateTime 支持斜杠分隔`() {
        assertNotNull(TimeUtils.parseDateTime("2026/09/15 12:30"))
    }

    @Test
    fun `parseDateTime 支持纯日期`() {
        assertNotNull(TimeUtils.parseDateTime("2026-09-15"))
    }

    @Test
    fun `parseDateTime 非法输入返回 null`() {
        assertNull(TimeUtils.parseDateTime("not a date"))
    }

    @Test
    fun `dayHeader 包含月日与星期`() {
        val millis = TimeUtils.parseDateTime("2026-09-15 12:30:45")!!
        val header = TimeUtils.formatDayHeader(TimeUtils.toLocalDate(millis))
        assertEquals("9月15日 周二", header)
    }
}
