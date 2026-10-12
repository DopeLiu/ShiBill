package com.shibill.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** M3 未提供的语义色（收支方向）。 */
@Immutable
data class SemanticColors(val expense: Color, val income: Color)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(ExpenseRedLight, IncomeGreenLight)
}

/** 用法：MaterialTheme.semantic.expense */
val MaterialTheme.semantic: SemanticColors
    @Composable @ReadOnlyComposable get() = LocalSemanticColors.current

@Composable
internal fun ProvideSemanticColors(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        SemanticColors(ExpenseRedDark, IncomeGreenDark)
    } else {
        SemanticColors(ExpenseRedLight, IncomeGreenLight)
    }
    CompositionLocalProvider(LocalSemanticColors provides colors, content = content)
}
