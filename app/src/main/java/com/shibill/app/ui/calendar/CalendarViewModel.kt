package com.shibill.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shibill.app.data.db.BillDao
import com.shibill.app.data.db.BillEntity
import com.shibill.app.data.db.CategoryDao
import com.shibill.app.data.db.CategoryEntity
import com.shibill.app.parse.model.BillType
import com.shibill.app.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class CalendarDay(
    val date: LocalDate,
    val expenseSum: Double,
    val isToday: Boolean
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val days: List<CalendarDay> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedBills: List<BillEntity> = emptyList()
)

class CalendarViewModel(
    billDao: BillDao,
    categoryDao: CategoryDao
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<CalendarUiState> = combine(
        billDao.observeAll(),
        month,
        selectedDate
    ) { bills, currentMonth, selected ->
        buildState(bills, currentMonth, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    private fun buildState(
        bills: List<BillEntity>,
        currentMonth: YearMonth,
        selected: LocalDate
    ): CalendarUiState {
        val today = LocalDate.now()
        val start = TimeUtils.startOfMonthMillis(currentMonth)
        val end = TimeUtils.endOfMonthExclusiveMillis(currentMonth)

        // 当月按日支出合计
        val dayExpense = mutableMapOf<LocalDate, Double>()
        for (bill in bills) {
            if (bill.occurredAt in start until end && bill.type == BillType.EXPENSE) {
                val date = TimeUtils.toLocalDate(bill.occurredAt)
                dayExpense[date] = (dayExpense[date] ?: 0.0) + bill.amount
            }
        }

        val days = (1..currentMonth.lengthOfMonth()).map { day ->
            val date = currentMonth.atDay(day)
            CalendarDay(
                date = date,
                expenseSum = dayExpense[date] ?: 0.0,
                isToday = date == today
            )
        }

        val selectedBills = bills.filter { TimeUtils.toLocalDate(it.occurredAt) == selected }

        // 选中日期若不在当前月，切换到包含它的月份（点击其他月日期时）
        val effectiveSelected = selected

        return CalendarUiState(
            month = currentMonth,
            days = days,
            selectedDate = effectiveSelected,
            selectedBills = selectedBills
        )
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        // 点击相邻月的日期时同步切换月份网格
        if (YearMonth.from(date) != month.value) {
            month.value = YearMonth.from(date)
        }
    }
}
