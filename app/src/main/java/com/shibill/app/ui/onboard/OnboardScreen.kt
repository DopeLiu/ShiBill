package com.shibill.app.ui.onboard

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.shibill.app.ui.components.SectionTitle
import com.shibill.app.ui.nav.Routes
import com.shibill.app.ui.theme.AppSpacing
import com.shibill.app.ui.theme.MotionTokens
import kotlinx.coroutines.launch

/** 首次启动引导（§7）：2 步——说明 → 通知使用权（含 ROM 保活提示） */
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
                // 引导页保留全站最大的留白（方案 §8.8 的唯一例外），但随全站一起收小一档：24 → 20
                .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xxl)
        ) {
            // 步骤指示
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepDots(count = 2, current = step)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "第 ${step + 1} / 2 步",
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
                // 动效 M6：步骤内容水平滑动淡入淡出，方向随前进/后退反转
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(220, easing = MotionTokens.EaseEnter)) { it / 4 } +
                                fadeIn(tween(220, easing = MotionTokens.EaseEnter))) togetherWith
                                (slideOutHorizontally(tween(160, easing = MotionTokens.EaseExit)) { -it / 4 } +
                                    fadeOut(tween(160, easing = MotionTokens.EaseExit)))
                        } else {
                            (slideInHorizontally(tween(220, easing = MotionTokens.EaseEnter)) { -it / 4 } +
                                fadeIn(tween(220, easing = MotionTokens.EaseEnter))) togetherWith
                                (slideOutHorizontally(tween(160, easing = MotionTokens.EaseExit)) { it / 4 } +
                                    fadeOut(tween(160, easing = MotionTokens.EaseExit)))
                        }
                    },
                    label = "onboardStep"
                ) { current ->
                    // AnimatedContent 的内部布局是 Box（多个兄弟节点会重叠在同一位置）。
                    // 必须显式包一层 Column，否则步骤内的若干 Text / Spacer / Bullet
                    // 会全部叠在一起（表现为"所有文字挤成一团"）。
                    Column {
                        when (current) {
                            0 -> StepIntro()
                            else -> StepNotification(openSystemSettings = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            })
                        }
                    }
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
                    if (step < 1) step++ else finishOnboarding()
                }) {
                    Text(if (step < 1) "下一步" else "完成")
                }
            }
        }
    }
}

@Composable
private fun StepDots(count: Int, current: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val active = index == current
            // 动效 M6：激活点宽度 8dp → 20dp
            val width by animateDpAsState(
                targetValue = if (active) MotionTokens.DotWidthActive.dp else MotionTokens.DotWidthIdle.dp,
                animationSpec = tween(220, easing = MotionTokens.EaseStandard),
                label = "dotWidth"
            )
            val color by animateColorAsState(
                targetValue = if (index <= current) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(220, easing = MotionTokens.EaseStandard),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun StepIntro() {
    Text("欢迎使用拾账", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Bullet("自动记账：监听微信 / 支付宝 / 云闪付的支付通知，自动解析金额与商户，确认后入账。")
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
    Spacer(Modifier.height(16.dp))
    SectionTitle("国产 ROM 保活建议", topPadding = 0.dp, bottomPadding = 6.dp)
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
        Text("去开启通知使用权")
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
