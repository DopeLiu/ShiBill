package com.shibill.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shibill.app.data.db.BillDao
import com.shibill.app.data.db.BillEntity
import com.shibill.app.data.db.BillWriter
import com.shibill.app.data.db.CategoryDao
import com.shibill.app.data.db.CategoryEntity
import com.shibill.app.parse.model.BillType
import com.shibill.app.util.TimeUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DayGroup(
    val date: LocalDate,
    val dayExpense: Double,
    val bills: List<BillEntity>
)

data class HomeUiState(
    val monthExpense: Double = 0.0,
    val monthIncome: Double = 0.0,
    val groups: List<DayGroup> = emptyList()
)

class HomeViewModel(
    private val billDao: BillDao,
    private val categoryDao: CategoryDao,
    private val billWriter: BillWriter
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<HomeUiState> = billDao.observeAll()
        .map { bills -> buildState(bills) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun buildState(bills: List<BillEntity>): HomeUiState {
        val month = YearMonth.now()
        val monthStart = TimeUtils.startOfMonthMillis(month)
        val monthEnd = TimeUtils.endOfMonthExclusiveMillis(month)

        var expense = 0.0
        var income = 0.0
        for (bill in bills) {
            if (bill.occurredAt in monthStart until monthEnd) {
                if (bill.type == BillType.EXPENSE) expense += bill.amount else income += bill.amount
            }
        }

        // bills 已按时间倒序；按日分组（LinkedHashMap 保持顺序）
        val groups = LinkedHashMap<LocalDate, MutableList<BillEntity>>()
        for (bill in bills) {
            groups.getOrPut(TimeUtils.toLocalDate(bill.occurredAt)) { mutableListOf() }.add(bill)
        }
        val dayGroups = groups.map { (date, dayBills) ->
            DayGroup(
                date = date,
                dayExpense = dayBills.filter { it.type == BillType.EXPENSE }.sumOf { it.amount },
                bills = dayBills
            )
        }
        return HomeUiState(monthExpense = expense, monthIncome = income, groups = dayGroups)
    }

    fun deleteBill(bill: BillEntity) {
        viewModelScope.launch { billDao.deleteById(bill.id) }
    }

    /** 撤销删除：原样恢复（用户意图，跳过去重） */
    fun restoreBill(bill: BillEntity) {
        viewModelScope.launch { billWriter.insert(bill, dedup = false) }
    }
}
