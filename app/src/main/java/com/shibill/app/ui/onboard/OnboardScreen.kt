package com.shibill.app.ui.onboard

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.shibill.app.ShiBillApp
import com.shibill.app.ui.nav.Routes
import kotlinx.coroutines.launch

/** 首次启动引导（§7）：3 步——说明 → 通知使用权 → 无障碍（可跳过）+ ROM 保活提示 */
@Composable
fun OnboardScreen(navController: NavHostController) {
    val context = LocalContext.current
    val store = (context.applicationContext as ShiBillApp).container.settingsStore
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户选择即可，后续可在系统设置调整 */ }

    fun finishOnboarding() {
        scope.launch { store.setOnboarded(true) }
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.ONBOARD) { inclusive = true }
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            // 步骤指示
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == step) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index <= step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "第 ${step + 1} / 3 步",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (step) {
                    0 -> StepIntro()
                    1 -> StepNotification(openSystemSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    })
                    else -> StepScreenshot(openSystemSettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    })
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    if (step > 0) step-- else finishOnboarding()
                }) {
                    Text(if (step > 0) "上一步" else "跳过")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (step < 2) step++ else finishOnboarding()
                }) {
                    Text(if (step < 2) "下一步" else "完成")
                }
            }
        }
    }
}

@Composable
private fun StepIntro() {
    Text("欢迎使用拾账", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Bullet("自动记账：监听微信 / 支付宝 / 云闪付的支付通知，自动解析金额与商户，确认后入账。")
    Bullet("截屏识别（可选）：在支付成功页截屏并用本地 OCR 识别，作为通知通道的补充。")
    Bullet("数据仅存本地：无账号、无云同步，支持 JSON 备份导出与官方账单导入。")
    Bullet("完全免费：本地识别，无需联网，无任何付费接口。")
    Spacer(Modifier.height(16.dp))
    Text(
        "接下来需要开启「通知使用权」，这是自动记账的核心权限。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StepNotification(openSystemSettings: () -> Unit) {
    Text("开启通知使用权", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Bullet("进入系统设置后，找到「拾账」，开启通知使用权。")
    Bullet("开启后，支付通知会被自动解析为待确认账单。")
    Bullet("通知内容仅在本机处理，不会上传。")
    Spacer(Modifier.height(8.dp))
    Text(
        "如果系统提示需要额外允许「发送通知」，请在弹窗中允许，否则确认提醒无法显示。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = openSystemSettings) {
        Text("去开启通知使用权")
    }
}

@Composable
private fun StepScreenshot(openSystemSettings: () -> Unit) {
    Text("截屏识别（可选）", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        Text(
            "当前系统版本低于 Android 11，不支持截屏兜底功能，可直接点击「完成」。",
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }
    Bullet("用途：仅在支付成功页截屏识别，截图不保存、不上传。")
    Bullet("开启方式：系统设置 → 无障碍 → 拾账-截屏识别。")
    Bullet("该权限可在设置页随时关闭，不影响通知自动记账。")
    Spacer(Modifier.height(16.dp))
    Text("国产 ROM 保活建议", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Text(
        "为保证通知监听稳定收到消息，建议在本机「应用管理 / 电池设置」中：\n" +
                "• 电池策略设为「无限制 / 不优化」\n" +
                "• 允许「自启动 / 后台自启动」\n" +
                "• 在最近任务中锁定拾账（部分 ROM：下拉卡片点锁图标）",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = openSystemSettings) {
        Text("去开启无障碍服务（可跳过）")
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("·", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
