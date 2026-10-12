package com.shibill.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.shibill.app.ui.theme.AppSpacing

/**
 * 统一分区标题。
 * 替换 SettingsScreen 与 RulesScreen 各自的私有实现（两者颜色原本不一致）。
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = AppSpacing.lg,
    bottomPadding: Dp = AppSpacing.sm
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AppSpacing.screenH,
                end = AppSpacing.screenH,
                top = topPadding,
                bottom = bottomPadding
            )
    )
}
