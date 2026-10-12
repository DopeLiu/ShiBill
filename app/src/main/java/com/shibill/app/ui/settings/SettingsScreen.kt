package com.shibill.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.shibill.app.ShiBillApp
import com.shibill.app.data.GithubReleaseChecker
import com.shibill.app.ui.components.ConfirmDialog
import com.shibill.app.ui.nav.Routes
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val container = (context.applicationContext as ShiBillApp).container
    val vm: SettingsViewModel = viewModel {
        SettingsViewModel(context.applicationContext, container.db, container.settingsStore)
    }

    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun toast(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val dateSuffix = remember { LocalDate.now().toString() }

    var pendingImportBackupUri by remember { mutableStateOf<Uri?>(null) }
    var showScreenshotDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showThanksDialog by remember { mutableStateOf(false) }
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
                SettingRow("导出备份（JSON）", "包含全部账单、分类与规则") {
                    exportBackupLauncher.launch("拾账备份_$dateSuffix.json")
                }
            }
            item {
                SettingRow("导出账单（CSV）", "GBK 编码，Excel 可直接打开") {
                    exportCsvLauncher.launch("拾账账单_$dateSuffix.csv")
                }
            }
            item {
                SettingRow("导入备份（JSON）", "全量替换当前数据，导入前会二次确认") {
                    importBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }
            }
            item {
                SettingRow("导入支付宝账单（CSV）", "支付宝 App 导出，自动去重") {
                    importAlipayLauncher.launch(arrayOf("*/*"))
                }
            }
            item {
                SettingRow("导入微信账单（xlsx）", "微信「账单下载」导出，自动去重") {
                    importWeChatLauncher.launch(arrayOf("*/*"))
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                item { SectionTitle("截屏自动记账（实验性，可选）") }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("截屏自动记账", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "开启后需授予无障碍权限；截图仅本地识别，不保存不上传",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.screenshotEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showScreenshotDialog = true
                                } else {
                                    vm.setScreenshotEnabled(false)
                                    toast("已关闭截屏识别；建议到系统设置中关闭无障碍服务")
                                }
                            }
                        )
                    }
                }
                if (state.screenshotEnabled) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("识别到支付成功页自动截屏", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "在微信/支付宝支付成功页自动识别，无需手动点击",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.screenshotAutoTrigger,
                                onCheckedChange = vm::setScreenshotAutoTrigger
                            )
                        }
                    }
                    item {
                        SettingRow("打开系统无障碍设置", "如服务被系统关闭，可在此重新开启") {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                }
            }

            item { SectionTitle("记账") }
            item {
                SettingRow("规则与监控", "解析规则、监控包名、自动入账、分类关键词") {
                    navController.navigate(Routes.RULES)
                }
            }

            item { SectionTitle("通用") }
            item {
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
            }
            item {
                SettingRow("隐私政策", "数据仅存本地，无障碍权限仅用于截屏识别") {
                    showPrivacyDialog = true
                }
            }
            item {
                SettingRow("开源致谢", "参考的开源项目（MIT）") {
                    showThanksDialog = true
                }
            }
            item {
                SettingRow("关于", "拾账 ShiBill v${vm.versionName}") { }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // 截屏主开关说明对话框（§5.2）
    if (showScreenshotDialog) {
        ConfirmDialog(
            title = "开启截屏自动记账？",
            message = "用途：仅在检测到支付成功页面（或点击「记一笔」）时截屏，并用本地 OCR 识别金额。\n\n" +
                    "无障碍权限较为敏感：截图不落盘、不上传、不联网，识别后立即释放内存。\n\n" +
                    "确认后将前往系统设置开启「拾账-截屏识别」服务。",
            confirmText = "我已知晓，去开启",
            onConfirm = {
                showScreenshotDialog = false
                vm.setScreenshotEnabled(true)
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { showScreenshotDialog = false }
        )
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

    if (showThanksDialog) {
        TextDialog(
            title = "开源致谢",
            text = THANKS_TEXT,
            onDismiss = { showThanksDialog = false }
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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

3. 无障碍权限（可选，默认关闭）
仅用于在支付成功页面截屏并本地识别金额。截图不落盘、不上传、不联网，识别完成后立即释放内存。该权限可随时在系统设置中关闭，关闭后不影响通知自动记账。

4. 联网行为
仅在您主动点击「检查更新」时，访问 GitHub Releases 接口获取版本信息。除此之外，应用不发起任何网络请求。

5. 数据导出
备份与导出文件由您自行保存与分享，应用不会主动外传。
""".trimIndent()

private val THANKS_TEXT = """
本应用的设计与实现参考了以下开源项目（均为 MIT 协议）的公开产品设计思路：

• Goose666666/jizhang —— 无障碍截屏编排、账单导入解析思路、首次使用引导流程
• 25283531/BeeCount —— 截图 OCR 记账交互与分类统计 UI 布局参考

以及公开资料中关于支付通知格式的整理。

本应用代码以 MIT 协议开源。
""".trimIndent()
