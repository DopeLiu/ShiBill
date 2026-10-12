package com.shibill.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shibill.app.data.db.BillDao
import com.shibill.app.data.db.CategoryDao
import com.shibill.app.data.db.CategoryEntity
import com.shibill.app.parse.model.BillType
import com.shibill.app.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class CategoryStat(
    val category: CategoryEntity?,
    val total: Double,
    val fraction: Float
)

data class TrendPoint(val label: String, val value: Double)

data class StatsUiState(
    val month: YearMonth = YearMonth.now(),
    val type: BillType = BillType.EXPENSE,
    val total: Double = 0.0,
    val categoryStats: List<CategoryStat> = emptyList(),
    val trend: List<TrendPoint> = emptyList()
)

class StatsViewModel(
    billDao: BillDao,
    categoryDao: CategoryDao
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val type = MutableStateFlow(BillType.EXPENSE)

    val uiState: StateFlow<StatsUiState> = combine(
        billDao.observeAll(),
        categoryDao.observeAll(),
        month,
        type
    ) { bills, categories, currentMonth, currentType ->
        buildState(bills, categories, currentMonth, currentType)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun buildState(
        bills: List<com.shibill.app.data.db.BillEntity>,
        categories: List<CategoryEntity>,
        currentMonth: YearMonth,
        currentType: BillType
    ): StatsUiState {
        val start = TimeUtils.startOfMonthMillis(currentMonth)
        val end = TimeUtils.endOfMonthExclusiveMillis(currentMonth)

        val monthBills = bills.filter { it.occurredAt in start until end && it.type == currentType }
        val total = monthBills.sumOf { it.amount }

        val categoryStats = monthBills
            .groupBy { it.categoryId }
            .map { (categoryId, list) -> categoryId to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .map { (categoryId, sum) ->
                CategoryStat(
                    category = categories.firstOrNull { it.id == categoryId },
                    total = sum,
                    fraction = if (total > 0.0) (sum / total).toFloat() else 0f
                )
            }

        // 近 6 个月趋势（含当前月）
        val trend = (5 downTo 0).map { ago ->
            val m = currentMonth.minusMonths(ago.toLong())
            val s = TimeUtils.startOfMonthMillis(m)
            val e = TimeUtils.endOfMonthExclusiveMillis(m)
            val sum = bills
                .filter { it.occurredAt in s until e && it.type == currentType }
                .sumOf { it.amount }
            TrendPoint(label = "${m.monthValue}月", value = sum)
        }

        return StatsUiState(
            month = currentMonth,
            type = currentType,
            total = total,
            categoryStats = categoryStats,
            trend = trend
        )
    }

    /** 跳转到指定的年月（由 MonthPickerSheet 调用）。 */
    fun selectMonth(target: YearMonth) {
        month.value = target
    }

    fun updateType(newType: BillType) {
        type.value = newType
    }
}
