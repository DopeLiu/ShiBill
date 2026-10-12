package com.shibill.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shibill.app.R

/**
 * 数字 / 拉丁 / 正则专用等宽字体。
 * 文件为 JetBrains Mono 的子集（Latin-1 + 常用符号，两字重共约 31.8 KB），
 * 依 SIL Open Font License 1.1 使用。禁止用于中文正文。
 */
val MonoDigits = FontFamily(
    Font(R.font.jb_mono_regular, FontWeight.Normal),
    Font(R.font.jb_mono_medium, FontWeight.Medium)
)

/** 中文与正文：系统字体（零体积）。层级由 fontWeight 建立。 */
val SansSystem = FontFamily.Default

/**
 * 字阶。整体比方案 §4.5 原值收小一档（用户要求全站元素缩小），层级关系不变。
 */
val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp)
)

/**
 * 数字专用样式。
 * 采用系统默认字体（原 JetBrains Mono 的 0 带内点，观感不佳，已弃用）；
 * JetBrains Mono 仅保留给规则页的正则预览（MonoDigits），不再用于数字。
 */
object AmountStyle {
    val Hero = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp)
    val Row = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp)
    val Meta = TextStyle(fontFamily = SansSystem, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp)
}
