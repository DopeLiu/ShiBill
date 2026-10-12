package com.shibill.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.shibill.app.ShiBillApp
import com.shibill.app.ui.components.BillRow
import com.shibill.app.ui.components.EmptyHint
import com.shibill.app.ui.nav.Routes
import com.shibill.app.ui.theme.ExpenseRed
import com.shibill.app.util.AmountFormat
import com.shibill.app.util.TimeUtils
import java.time.LocalDate

@Composable
fun CalendarScreen(navController: NavHostController) {
    val context = LocalContext.current
    val container = (context.applicationContext as ShiBillApp).container
    val vm: CalendarViewModel = viewModel {
        CalendarViewModel(container.db.billDao(), container.db.categoryDao())
    }

    val state by vm.uiState.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 月份切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = vm::previousMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                }
                Text(
                    text = "${state.month.year}年${state.month.monthValue}月",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = vm::nextMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                }
            }

            WeekHeader()
            MonthGrid(
                days = state.days,
                month = state.month,
                selectedDate = state.selectedDate,
                onSelect = vm::selectDate
            )

            HorizontalDivider()

            // 选中日汇总 + 流水
            val dayExpense = state.selectedBills
                .filter { it.type == com.shibill.app.parse.model.BillType.EXPENSE }
                .sumOf { it.amount }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = TimeUtils.formatDayHeader(state.selectedDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "支出 ${AmountFormat.format(dayExpense)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.selectedBills.isEmpty()) {
                EmptyHint("这一天没有账单")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.selectedBills, key = { it.id }) { bill ->
                        BillRow(
                            bill = bill,
                            category = categoryById[bill.categoryId],
                            onClick = { navController.navigate(Routes.confirm(billId = bill.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthGrid(
    days: List<CalendarDay>,
    month: java.time.YearMonth,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val dayMap = remember(days) { days.associateBy { it.date } }

    // 周一开头；前导空格补齐
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells = buildList<LocalDate?> {
        repeat(leading) { add(null) }
        addAll(days.map { it.date })
        while (size % 7 != 0) add(null)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        day = date?.let { dayMap[it] },
                        selected = date != null && date == selectedDate,
                        onClick = { date?.let(onSelect) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable(enabled = day != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (day == null) return@Box
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (day.isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day.isToday) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            if (day.expenseSum > 0) {
                Text(
                    text = compactAmount(day.expenseSum),
                    fontSize = 9.sp,
                    color = ExpenseRed,
                    maxLines = 1
                )
            }
        }
    }
}

/** 日格金额紧凑显示：1234.5 → 1234.5；12000 → 1.2万 */
private fun compactAmount(value: Double): String = when {
    value >= 10000 -> String.format(java.util.Locale.ROOT, "%.1f万", value / 10000)
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> String.format(java.util.Locale.ROOT, "%.1f", value)
}
