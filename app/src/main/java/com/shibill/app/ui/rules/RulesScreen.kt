package com.shibill.app.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.shibill.app.ShiBillApp
import com.shibill.app.data.db.RuleEntity
import com.shibill.app.ui.nav.Routes

/** 规则页（§7）：规则列表（开关/编辑/新建）、包名监控管理、自动入账开关、分类关键词入口 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(navController: NavHostController) {
    val context = LocalContext.current
    val container = (context.applicationContext as ShiBillApp).container
    val vm: RulesViewModel = viewModel {
        RulesViewModel(container.db.ruleDao(), container.settingsStore)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()

    var editingRule by remember { mutableStateOf<RuleEntity?>(null) }
    var creatingRule by remember { mutableStateOf(false) }
    var addingPackage by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("规则与监控") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 自动入账开关（§5.3）
            item(key = "auto-commit") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动入账（免确认）", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "开启后解析成功的账单直接入库，通知仅作展示",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.autoCommit,
                        onCheckedChange = vm::setAutoCommit
                    )
                }
            }

            item(key = "rules-title") {
                SectionTitle("解析规则（按优先级从高到低匹配）")
            }

            items(state.rules, key = { "rule-${it.id}" }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { enabled -> vm.setRuleEnabled(rule, enabled) },
                    onEdit = { editingRule = rule },
                    onDelete = { vm.deleteRule(rule) }
                )
            }

            item(key = "rule-add") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedButton(onClick = { creatingRule = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("新建规则")
                    }
                }
            }

            item(key = "packages-title") {
                SectionTitle("监控包名（通知监听范围，可与规则包名并用）")
            }

            items(state.monitoredPackages.sorted().toList(), key = { "pkg-$it" }) { pkg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pkg,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.removePackage(pkg) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "移除 $pkg",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "package-add") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedButton(onClick = { addingPackage = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("添加包名（银行 App 等）")
                    }
                }
            }

            item(key = "category-title") {
                SectionTitle("分类关键词")
            }

            item(key = "category-entry") {
                TextButton(
                    onClick = { navController.navigate(Routes.CATEGORY_KEYWORDS) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("编辑分类关键词 →")
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (creatingRule) {
        RuleEditDialog(
            initial = null,
            onDismiss = { creatingRule = false },
            onSave = {
                vm.saveRule(it)
                creatingRule = false
            }
        )
    }

    editingRule?.let { rule ->
        RuleEditDialog(
            initial = rule,
            onDismiss = { editingRule = null },
            onSave = {
                vm.saveRule(it)
                editingRule = null
            }
        )
    }

    if (addingPackage) {
        AddPackageDialog(
            onDismiss = { addingPackage = false },
            onAdd = {
                vm.addPackage(it)
                addingPackage = false
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun RuleCard(
    rule: RuleEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (rule.builtIn) {
                    Text(
                        text = "内置",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            Text(
                text = "包名：${rule.appPackage.ifEmpty { "（任意，仅截屏来源生效）" }} · 优先级 ${rule.priority}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = rule.pattern,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) { Text("编辑") }
                if (!rule.builtIn) {
                    TextButton(onClick = onDelete) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun RuleEditDialog(
    initial: RuleEntity?,
    onDismiss: () -> Unit,
    onSave: (RuleEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var packages by remember { mutableStateOf(initial?.appPackage ?: "") }
    var pattern by remember { mutableStateOf(initial?.pattern ?: "") }
    var priority by remember { mutableStateOf((initial?.priority ?: 0).toString()) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建规则" else "编辑规则") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = packages,
                    onValueChange = { packages = it },
                    label = { Text("包名（逗号分隔；留空=仅截屏来源）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("正则（必须包含 (?<amount>…) 命名组）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priority,
                    onValueChange = { input -> priority = input.filter { it.isDigit() || it == '-' } },
                    label = { Text("优先级（数值越大越先匹配）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && pattern.isNotBlank(),
                onClick = {
                    onSave(
                        RuleEntity(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            appPackage = packages.trim(),
                            pattern = pattern.trim(),
                            priority = priority.toIntOrNull() ?: 0,
                            enabled = enabled,
                            builtIn = initial?.builtIn ?: false
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AddPackageDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var pkg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加监控包名") },
        text = {
            Column {
                Text(
                    "输入应用包名（如 com.example.bank）。可在系统「应用信息」中查看包名。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pkg.contains('.') && pkg.isNotBlank(),
                onClick = { onAdd(pkg.trim()) }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
