package com.shibill.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.shibill.app.ui.theme.MotionTokens
import com.shibill.app.util.AmountFormat

/**
 * 会"计数"的金额文本（动效 M2）。
 * - 首次显示（含跨 tab 返回）：0 → 目标值，DurationLong
 * - 之后数值变化：当前值 → 新值，DurationMedium
 * 只在单个 Text 上运行，禁止用于列表项（见改造方案 §5.3）。
 */
@Composable
fun AnimatedAmount(
    amount: Double,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    var played by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(amount) {
        val duration = if (played) MotionTokens.DurationMedium else MotionTokens.DurationLong
        played = true
        progress.animateTo(
            targetValue = amount.toFloat(),
            animationSpec = tween(duration, easing = MotionTokens.EaseStandard)
        )
    }

    Text(
        text = AmountFormat.format(progress.value.toDouble()),
        style = style,
        color = color,
        modifier = modifier,
        maxLines = 1
    )
}
