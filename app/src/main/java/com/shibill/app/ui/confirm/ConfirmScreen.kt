package com.shibill.app.ui.confirm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.shibill.app.ShiBillApp
import com.shibill.app.data.db.CategoryEntity
import com.shibill.app.parse.model.BillType
import com.shibill.app.ui.components.CategoryBadge
import com.shibill.app.ui.components.GroupCard
import com.shibill.app.ui.theme.AppSpacing
import com.shibill.app.util.TimeUtils
import kotlinx.coroutines.launch
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfirmScreen(navController: NavHostController) {
    val context = LocalContext.current
    val container = (context.applicationContext as ShiBillApp).container
    val vm: ConfirmViewModel = viewModel {
        ConfirmViewModel(
            billDao = container.db.billDao(),
            categoryDao = container.db.categoryDao(),
            billWriter = container.billWriter,
            savedStateHandle = createSavedStateHandle()
        )
    }

    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 默认聚焦金额并弹出数字键盘（§7）
    LaunchedEffect(state.loaded) {
        if (state.loaded) focusRequester.requestFocus()
    }

    val title = when {
        state.isEdit -> "编辑账单"
        vm.isConfirming -> "确认入账"
        else -> "记一笔"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(innerPadding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(AppSpacing.sm))

            // 金额区（含收支方向）
            GroupCard {
                Column(Modifier.padding(AppSpacing.card)) {
                    OutlinedTextField(
                        value = state.amountText,
                        onValueChange = vm::updateAmount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        label = { Text("金额") },
                        prefix = { Text("¥", style = MaterialTheme.typography.titleLarge) },
                        textStyle = TextStyle(fontSize = 20.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.type == BillType.EXPENSE,
                            onClick = { vm.updateType(BillType.EXPENSE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.height(36.dp)
                        ) { Text("支出") }
                        SegmentedButton(
                            selected = state.type == BillType.INCOME,
                            onClick = { vm.updateType(BillType.INCOME) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.height(36.dp)
                        ) { Text("收入") }
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.sm))

            // 分类选择区
            GroupCard {
                Column(Modifier.padding(AppSpacing.card)) {
                    SectionLabel("分类")
                    Spacer(Modifier.height(AppSpacing.xs))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 4
                    ) {
                        state.categories.forEach { category ->
                            CategoryGridItem(
                                category = category,
                                selected = category.id == state.categoryId,
                                onClick = { vm.updateCategory(category.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.sm))

            // 备注区
            GroupCard {
                Column(Modifier.padding(AppSpacing.card)) {
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = vm::updateNote,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注") },
                        minLines = 1,
                        maxLines = 4
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.sm))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screenH),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("日期", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showDatePicker = true }) {
                    Text(TimeUtils.formatDate(state.occurredAt))
                }
            }
            Spacer(Modifier.height(AppSpacing.sm))

            Button(
                onClick = {
                    scope.launch {
                        if (vm.save()) {
                            navController.popBackStack()
                        } else {
                            snackbarHostState.showSnackbar("已存在相同账单，未重复入账")
                        }
                    }
                },
                enabled = vm.amountValid,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screenH)
                    .height(48.dp)
            ) {
                Text("保存")
            }
            Spacer(Modifier.height(AppSpacing.lg))
        }
    }

    if (showDatePicker) {
        val initialUtc = remember(state.occurredAt) {
            TimeUtils.toLocalDate(state.occurredAt)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let(vm::updateDate)
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CategoryGridItem(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CategoryBadge(iconName = category.icon, size = 36.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
