package com.shibill.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibill.app.data.db.BillEntity
import com.shibill.app.data.db.CategoryEntity
import com.shibill.app.parse.model.BillType
import com.shibill.app.ui.theme.AmountStyle
import com.shibill.app.ui.theme.AppSpacing
import com.shibill.app.ui.theme.categoryColor
import com.shibill.app.ui.theme.semantic
import com.shibill.app.util.AmountFormat

/** 分类图标（圆形底，按分类色族着色） */
@Composable
fun CategoryBadge(iconName: String, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val c = categoryColor(iconName)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(c.container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = CategoryIcons.fromName(iconName),
            contentDescription = null,
            tint = c.content,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/** 金额文本：支出红 / 收入绿（随主题），数字用等宽字体 */
@Composable
fun AmountText(
    amount: Double,
    type: BillType,
    modifier: Modifier = Modifier,
    style: TextStyle = AmountStyle.Row
) {
    val semantic = MaterialTheme.semantic
    Text(
        text = AmountFormat.formatSigned(amount, type == BillType.INCOME),
        color = if (type == BillType.INCOME) semantic.income else semantic.expense,
        style = style,
        maxLines = 1,
        modifier = modifier
    )
}

/**
 * 流水行（Home 专用）。签名与改造前一致，调用方无需改动。
 * 需置于带底色的 Card（surfaceContainerLow）内，否则分组感消失。
 */
@Composable
fun BillRow(
    bill: BillEntity,
    category: CategoryEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.rowV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(iconName = category?.icon ?: CategoryIcons.FALLBACK)
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = bill.merchant.ifEmpty { category?.name ?: "未分类" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildList {
                if (bill.merchant.isNotBlank()) category?.name?.let { add(it) }
                if (bill.account.isNotBlank()) add(bill.account)
                if (bill.note.isNotBlank()) add(bill.note)
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(AppSpacing.md))
        AmountText(amount = bill.amount, type = bill.type)
    }
}

/** 通用确认对话框 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 空列表占位 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screenH, vertical = AppSpacing.xxl + AppSpacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 页面内的信息条：图标 + 说明文字。用于替换裸说明文本。 */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screenH)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(AppSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 卡片分组容器：统一全站"一组内容"的视觉承载 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screenH),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        content = content
    )
}

/** 卡内分割线：自动左右留出 AppSpacing.lg */
@Composable
fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = AppSpacing.lg)
    )
}
