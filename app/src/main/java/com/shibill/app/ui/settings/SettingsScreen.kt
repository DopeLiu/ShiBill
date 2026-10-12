package com.shibill.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.shibill.app.ShiBillApp
import com.shibill.app.data.GithubReleaseChecker
import com.shibill.app.ui.components.ConfirmDialog
import com.shibill.app.ui.components.GroupCard
import com.shibill.app.ui.components.RowDivider
import com.shibill.app.ui.components.SectionTitle
import com.shibill.app.ui.nav.Routes
import com.shibill.app.ui.theme.AppSpacing
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val container = (context.applicationContext as ShiBillApp).container
    val vm: SettingsViewModel = viewModel {
        SettingsViewModel(context.applicationContext, container.db)
    }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun toast(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val dateSuffix = remember { LocalDate.now().toString() }

    var pendingImportBackupUri by remember { mutableStateOf<Uri?>(null) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<GithubReleaseChecker.UpdateInfo?>(null) }

    // SAF 导出：JSON 备份
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                vm.exportBackup(uri)
                    .onSuccess { toast("备份已导出") }
                    .onFailure { toast("导出失败：${it.message ?: "未知错误"}") }
            }
        }
    }

    // SAF 导出：CSV 账单
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                vm.exportCsv(uri)
                    .onSuccess { toast("账单已导出") }
                    .onFailure { toast("导出失败：${it.message ?: "未知错误"}") }
            }
        }
    }

    // SAF 导入：备份（先确认全量替换）
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportBackupUri = uri
    }

    // SAF 导入：支付宝 CSV
    val importAlipayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                vm.importAlipayCsv(uri)
                    .onSuccess { summary -> toast("支付宝账单导入完成：新增 ${summary.inserted} 笔，重复跳过 ${summary.duplicates} 笔，忽略 ${summary.skipped} 行") }
                    .onFailure { toast("导入失败：${it.message ?: "未知错误"}") }
            }
        }
    }

    // SAF 导入：微信 xlsx
    val importWeChatLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                vm.importWeChatXlsx(uri)
                    .onSuccess { summary -> toast("微信账单导入完成：新增 ${summary.inserted} 笔，重复跳过 ${summary.duplicates} 笔，忽略 ${summary.skipped} 行") }
                    .onFailure { toast("导入失败：${it.message ?: "未知错误"}") }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item { SectionTitle("数据") }
            item {
                GroupCard {
                    SettingRow("导出备份（JSON）", "包含全部账单、分类与规则") {
                        exportBackupLauncher.launch("拾账备份_$dateSuffix.json")
                    }
                    RowDivider()
                    SettingRow("导出账单（CSV）", "GBK 编码，Excel 可直接打开") {
                        exportCsvLauncher.launch("拾账账单_$dateSuffix.csv")
                    }
                    RowDivider()
                    SettingRow("导入备份（JSON）", "全量替换当前数据，导入前会二次确认") {
                        importBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                    }
                    RowDivider()
                    SettingRow("导入支付宝账单（CSV）", "支付宝 App 导出，自动去重") {
                        importAlipayLauncher.launch(arrayOf("*/*"))
                    }
                    RowDivider()
                    SettingRow("导入微信账单（xlsx）", "微信「账单下载」导出，自动去重") {
                        importWeChatLauncher.launch(arrayOf("*/*"))
                    }
                }
            }

            item { SectionTitle("记账") }
            item {
                GroupCard {
                    SettingRow("规则与监控", "解析规则、监控包名、自动入账、分类关键词") {
                        navController.navigate(Routes.RULES)
                    }
                }
            }

            item { SectionTitle("通用") }
            item {
                GroupCard {
                    SettingRow("检查更新", "仅在此时联网访问 GitHub Releases") {
                        scope.launch {
                            val info = vm.checkUpdate()
                            if (info != null) {
                                updateInfo = info
                            } else {
                                toast("已是最新版本（或暂时无法连接更新服务）")
                            }
                        }
                    }
                    RowDivider()
                    SettingRow("隐私政策", "数据仅存本地，无账号、不上传") {
                        showPrivacyDialog = true
                    }
                    RowDivider()
                    SettingRow("关于", "拾账 ShiBill v${vm.versionName} · 作者 $AUTHOR_NAME") {
                        showAboutDialog = true
                    }
                }
            }
            item { Spacer(Modifier.height(AppSpacing.xxl)) }
        }
    }

    // 导入备份：全量替换确认（§8.1）
    pendingImportBackupUri?.let { uri ->
        ConfirmDialog(
            title = "导入备份",
            message = "导入将全量替换当前所有数据（账单 / 分类 / 规则），此操作不可撤销。是否继续？",
            confirmText = "替换导入",
            onConfirm = {
                pendingImportBackupUri = null
                scope.launch {
                    vm.importBackup(uri)
                        .onSuccess { s -> toast("导入完成：账单 ${s.bills} 笔，分类 ${s.categories} 个，规则 ${s.rules} 条") }
                        .onFailure { toast("导入失败：${it.message ?: "未知错误"}") }
                }
            },
            onDismiss = { pendingImportBackupUri = null }
        )
    }

    if (showPrivacyDialog) {
        TextDialog(
            title = "隐私政策",
            text = PRIVACY_TEXT,
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于") },
            text = {
                Text(
                    text = "拾账 ShiBill\n" +
                            "版本 v${vm.versionName}\n" +
                            "作者 $AUTHOR_NAME\n\n" +
                            PROJECT_URL,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAboutDialog = false
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
                    }
                }) { Text("访问 GitHub") }
            },
            dismissButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("关闭") }
            }
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("发现新版本 v${info.version}") },
            text = {
                Text(
                    text = if (info.notes.isNotBlank()) info.notes.take(500) else "前往 GitHub Releases 查看更新内容",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    updateInfo = null
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(info.url))
                        )
                    }
                }) { Text("前往下载") }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("稍后") }
            }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        // 副标题并入 headlineContent（同一文本块），使 ListItem 判定为"单行"类型：
        // 最小高度 56dp（用 supportingContent 时双行类型下限为 72dp）。依据 material3 1.3.1
        // ListItem.calculateHeight：高度 = max(下限, 内边距 + 内容) 且不被强制时上限无界，
        // 因此副标题换行时行高会自动增长，**不可能**出现内容被裁切。
        headlineContent = {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

@Composable
private fun TextDialog(title: String, text: String, onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(text = text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

private val PRIVACY_TEXT = """
拾账（ShiBill）隐私政策

1. 数据仅存本地
所有账单、分类、规则数据保存在您的设备本地数据库中（应用私有目录），不进行任何云同步或上传。

2. 通知监听
仅解析支付类通知的标题与正文文本，用于自动记账。通知内容仅在本机处理，原始事件记录保留 7 天后自动清理。

3. 联网行为
仅在您主动点击「检查更新」时，访问 GitHub Releases 接口获取版本信息。除此之外，应用不发起任何网络请求。

4. 数据导出
备份与导出文件由您自行保存与分享，应用不会主动外传。
""".trimIndent()

/** App 作者与项目主页（与 GitHub 账户一致） */
private const val AUTHOR_NAME = "DopeLiu"
private const val PROJECT_URL = "https://github.com/DopeLiu/ShiBill"
