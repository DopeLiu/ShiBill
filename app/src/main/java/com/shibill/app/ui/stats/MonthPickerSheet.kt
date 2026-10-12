package com.shibill.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shibill.app.ui.theme.AmountStyle
import com.shibill.app.ui.theme.AppSpacing
import java.time.YearMonth

/**
 * 年月选择面板（替代原逐月步进的 ‹ › 按钮）。
 * 见改造方案 §2.2。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerSheet(
    selected: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val today = remember { YearMonth.now() }
    var year by rememberSaveable(selected) { mutableStateOf(selected.year) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = AppSpacing.xl)) {

            // 年份步进
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenH),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { year-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一年")
                }
                Text(
                    text = "$year 年",
                    style = AmountStyle.Row,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { year++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一年")
                }
            }

            Spacer(Modifier.height(AppSpacing.sm))

            // 12 个月份网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenH),
                contentPadding = PaddingValues(AppSpacing.xs)
            ) {
                items((1..12).toList()) { m ->
                    val isSelected = year == selected.year && m == selected.monthValue
                    val isCurrent = year == today.year && m == today.monthValue
                    Box(
                        modifier = Modifier
                            .padding(AppSpacing.xs)
                            .height(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .then(
                                if (isCurrent && !isSelected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = MaterialTheme.shapes.small
                                    )
                                } else Modifier
                            )
                            .clickable { onSelect(YearMonth.of(year, m)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$m 月",
                            style = AmountStyle.Meta,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 回到本月
            if (selected != today) {
                Spacer(Modifier.height(AppSpacing.md))
                TextButton(
                    onClick = { onSelect(today) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("回到本月")
                }
            }
        }
    }
}
