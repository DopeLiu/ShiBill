package com.shibill.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.shibill.app.ui.theme.MotionTokens

/**
 * 可点击卡片：按压时缩放到 0.98，并保留默认涟漪。
 * 动效参数见改造方案 §5.2 M4。
 */
@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) MotionTokens.PressScale else 1f,
        animationSpec = tween(MotionTokens.DurationShort, easing = MotionTokens.EaseStandard),
        label = "pressScale"
    )

    Card(
        onClick = onClick,
        modifier = modifier.scale(scale),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        interactionSource = interaction,
        content = content
    )
}
