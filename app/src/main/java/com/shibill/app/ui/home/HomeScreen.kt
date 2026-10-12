package com.shibill.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.shibill.app.ShiBillApp
import com.shibill.app.capture.CaptureBus
import com.shibill.app.capture.CaptureEvent
import com.shibill.app.data.db.BillEntity
import com.shibill.app.data.db.CategoryEntity
import com.shibill.app.ui.components.AnimatedAmount
import com.shibill.app.ui.components.BillRow
import com.shibill.app.ui.components.EmptyHint
import com.shibill.app.ui.components.RowDivider
import com.shibill.app.ui.nav.Routes
import com.shibill.app.ui.theme.AmountStyle
import com.shibill.app.ui.theme.AppSpacing
import com.shibill.app.util.AmountFormat
import com.shibill.app.util.TimeUtils
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val container = (context.applicationContext as ShiBillApp).container
    val vm: HomeViewModel = viewModel {
        HomeViewModel(container.db.billDao(), container.db.categoryDao(), container.billWriter)
    }

    val state by vm.uiState.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 采集事件 → 前台提示（CaptureBus：Service → UI）
    LaunchedEffect(Unit) {
        CaptureBus.events.collect { event ->
            when (event) {
                is CaptureEvent.PendingCreated -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "已捕获一笔支付：${AmountFormat.format(event.amount)}，待确认",
                        actionLabel = "去确认",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        navController.navigate(Routes.confirm(pending = event.pendingId))
                    }
                }

                is CaptureEvent.ParseFailed -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "捕获到疑似支付，未能识别",
                        actionLabel = "补记",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        navController.navigate(Routes.confirm(raw = event.rawText))
                    }
                }

                is CaptureEvent.AutoCommitted ->
                    snackbarHostState.showSnackbar("已自动入账一笔账单")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("流水", style = MaterialTheme.typography.titleLarge) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.confirm()) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "手动记一笔")
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item(key = "hero") { MonthHero(state.monthExpense, state.monthIncome) }

            if (state.groups.isEmpty()) {
                item(key = "empty") {
                    EmptyHint("还没有账单\n点击右下角 + 记一笔，或等待自动捕获")
                }
            }

            state.groups.forEach { group ->
                item(key = "group-${group.date}") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.screenH, vertical = AppSpacing.xs)
                            // 动效 M1：日分组卡片的增删动画
                            .animateItem(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column {
                            DayHeaderRow(group)
                            RowDivider()
                            group.bills.forEach { bill ->
                                // 用 bill.id 作为组合键：否则删除后该行槽位被下一行继承，
                                // 前一行的"已左滑展开"状态会串到下一项上。
                                key(bill.id) {
                                    SwipeRevealDelete(
                                        bill = bill,
                                        category = categoryById[bill.categoryId],
                                        onClick = { navController.navigate(Routes.confirm(billId = bill.id)) },
                                        onDelete = {
                                            vm.deleteBill(bill)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "已删除一笔账单",
                                                    actionLabel = "撤销",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    vm.restoreBill(bill)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "bottom-space") { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MonthHero(monthExpense: Double, monthIncome: Double) {
    val month = YearMonth.now()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screenH, vertical = AppSpacing.md),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(Modifier.padding(AppSpacing.card)) {
            Text(
                text = "${month.year}年${month.monthValue}月 · 本月支出",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(AppSpacing.sm))
            // 动效 M2：首次从 0 计数，之后平滑过渡
            AnimatedAmount(
                amount = monthExpense,
                style = AmountStyle.Hero,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(AppSpacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f))
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                text = "收入 ${AmountFormat.format(monthIncome)}"
                    + " · 结余 ${AmountFormat.format(monthIncome - monthExpense)}",
                style = AmountStyle.Meta
            )
        }
    }
}

/** 日分组头（卡片内的一行，替代原整宽色带） */
@Composable
private fun DayHeaderRow(group: DayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = TimeUtils.formatDayHeader(group.date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "支出 ${AmountFormat.format(group.dayExpense)}",
            style = AmountStyle.Meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 左滑露出删除按钮（§7）。
 * 首次左滑仅露出红色删除按钮，点击按钮才真正删除；右滑或点击账单行可收起。
 * 不使用 SwipeToDismissBox：其在滑开后禁用手势，无法回滑收起（material3 1.3.1）。
 */
@Composable
private fun SwipeRevealDelete(
    bill: BillEntity,
    category: CategoryEntity?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealWidth = 72.dp
    val revealPx = with(density) { revealWidth.toPx() }
    val flingThreshold = with(density) { 250.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(Modifier.clipToBounds()) {
        // 背景层：删除按钮（左侧滑出前被前景行完全覆盖，不可见）
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(revealWidth)
                    .fillMaxHeight()
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // 前景层：账单行（不透明底色；左滑最多移出 72dp 露出删除按钮）
        BillRow(
            bill = bill,
            category = category,
            onClick = {
                if (offsetX.value != 0f) {
                    scope.launch { offsetX.animateTo(0f) }
                } else {
                    onClick()
                }
            },
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStopped = { velocity ->
                        val open = when {
                            velocity <= -flingThreshold -> true
                            velocity >= flingThreshold -> false
                            else -> offsetX.value <= -revealPx / 2
                        }
                        offsetX.animateTo(if (open) -revealPx else 0f)
                    }
                )
        )
    }
}
