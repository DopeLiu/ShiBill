package com.shibill.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/** 全站动效令牌。所有动画时长/曲线必须引用此处，不得在调用点硬编码。 */
object MotionTokens {

    /** 按压反馈、淡出 */
    const val DurationShort = 180

    /** 数值过渡、列表增删、页面切换 */
    const val DurationMedium = 300

    /** 首屏金额计数、进度条生长 */
    const val DurationLong = 600

    val EaseStandard: Easing = FastOutSlowInEasing
    val EaseEnter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EaseExit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 按压缩放目标值 */
    const val PressScale = 0.98f

    /** 页面切换位移 = 屏幕高度 / 该值（越大越不明显） */
    const val SlideDenominator = 48

    /** 引导页步骤点宽度（dp） */
    const val DotWidthActive = 20
    const val DotWidthIdle = 8
}
