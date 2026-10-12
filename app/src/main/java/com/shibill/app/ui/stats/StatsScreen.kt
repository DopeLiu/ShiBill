package com.shibill.app.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.shibill.app.ShiBillApp
import com.shibill.app.parse.model.BillType
import com.shibill.app.ui.components.AnimatedAmount
import com.shibill.app.ui.components.CategoryBadge
import com.shibill.app.ui.components.CategoryIcons
import com.shibill.app.ui.components.EmptyHint
import com.shibill.app.ui.components.SectionTitle
import com.shibill.app.ui.theme.AmountStyle
import com.shibill.app.ui.theme.AppSpacing
import com.shibill.app.ui.theme.MotionTokens
import com.shibill.app.util.AmountFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val container = (context.applicationContext as ShiBillApp).container
    val vm: StatsViewModel = viewModel {
        StatsViewModel(container.db.billDao(), container.db.categoryDao())
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                // 可点击的年月按钮（见 §2.2）
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { pickerOpen = true }
                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.month.year}年${state.month.monthValue}月",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = "选择年月",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = AppSpacing.xxl)
        ) {
            // 1. 收支切换
            item(key = "type") {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screenH, vertical = AppSpacing.sm)
                ) {
                    SegmentedButton(
                        selected = state.type == BillType.EXPENSE,
                        onClick = { vm.updateType(BillType.EXPENSE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("支出") }
                    SegmentedButton(
                        selected = state.type == BillType.INCOME,
                        onClick = { vm.updateType(BillType.INCOME) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("收入") }
                }
            }

            // 2. 合计卡片
            item(key = "total") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screenH, vertical = AppSpacing.sm),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Column(Modifier.padding(AppSpacing.card)) {
                        Text("合计", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(AppSpacing.sm))
                        AnimatedAmount(
                            amount = state.total,
                            style = AmountStyle.Hero,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // 3. 分类排行
            item(key = "rank-title") { SectionTitle("分类排行") }
            if (state.categoryStats.isEmpty()) {
                item(key = "rank-empty") { EmptyHint("本月暂无数据") }
            } else {
                items(state.categoryStats, key = { it.category?.id ?: -1L }) { stat ->
                    CategoryStatRow(stat, modifier = Modifier.animateItem())
                }
            }

            // 4. 近 6 月趋势
            item(key = "trend-title") { SectionTitle("近 6 个月趋势") }
            item(key = "trend-chart") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.screenH),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    TrendChart(
                        points = state.trend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .padding(AppSpacing.md)
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        MonthPickerSheet(
            selected = state.month,
            onSelect = { vm.selectMonth(it); pickerOpen = false },
            onDismiss = { pickerOpen = false }
        )
    }
}

@Composable
private fun CategoryStatRow(stat: CategoryStat, modifier: Modifier = Modifier) {
    // 动效 M5：进度条从 0 生长到目标比例
    val animatedFraction by animateFloatAsState(
        targetValue = stat.fraction,
        animationSpec = tween(MotionTokens.DurationLong, easing = MotionTokens.EaseStandard),
        label = "fraction"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screenH, vertical = AppSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(iconName = stat.category?.icon ?: CategoryIcons.FALLBACK, size = 28.dp)
            Spacer(Modifier.width(AppSpacing.md))
            Text(
                text = stat.category?.name ?: "未分类",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = AmountFormat.format(stat.total),
                style = AmountStyle.Row,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier.weight(1f).height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                text = "${(stat.fraction * 100).roundToInt()}%",
                style = AmountStyle.Meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(38.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun TrendChart(points: List<TrendPoint>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(points) {
        if (points.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = points.indices.map { it },
                    y = points.map { it.value }
                )
            }
        }
    }

    val bottomFormatter = remember(points) {
        CartesianValueFormatter { _, value, _ ->
            points.getOrNull(value.toInt())?.label.orEmpty()
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter)
        ),
        modelProducer = modelProducer,
        modifier = modifier
    )
}
