package com.shibill.app.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtils {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun toLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun toMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfMonthMillis(month: YearMonth): Long =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfMonthExclusiveMillis(month: YearMonth): Long =
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun formatDateTime(millis: Long): String = toLocalDateTime(millis).format(dateTimeFormatter)

    fun formatDate(millis: Long): String = toLocalDate(millis).toString()

    /** "10月11日 周六"（流水列表日期头） */
    fun formatDayHeader(date: LocalDate): String {
        val week = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
        return "${date.monthValue}月${date.dayOfMonth}日 $week"
    }

    /** 解析导入账单时间文本："2026-09-15 12:30:45" / "2026/09/15 12:30" / "2026-09-15" */
    fun parseDateTime(text: String): Long? {
        val normalized = text.trim().replace('/', '-')
        val tryPatterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")
        for (pattern in tryPatterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern)
                val dateTime = if (pattern == "yyyy-MM-dd") {
                    LocalDate.parse(normalized, formatter).atStartOfDay()
                } else {
                    LocalDateTime.parse(normalized, formatter)
                }
                return dateTime.atZone(zone).toInstant().toEpochMilli()
            } catch (_: Exception) {
                // 尝试下一种格式
            }
        }
        return null
    }
}
