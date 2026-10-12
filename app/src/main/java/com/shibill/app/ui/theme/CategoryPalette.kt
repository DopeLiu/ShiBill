package com.shibill.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Immutable
data class CategoryColor(val container: Color, val content: Color)

private class Family(
    val lightContainer: Color, val lightContent: Color,
    val darkContainer: Color, val darkContent: Color
)

private val coral = Family(Color(0xFFFAECE7), Color(0xFF993C1D), Color(0xFF712B13), Color(0xFFF5C4B3))
private val pink = Family(Color(0xFFFBEAF0), Color(0xFF993556), Color(0xFF72243E), Color(0xFFF4C0D1))
private val blue = Family(Color(0xFFE6F1FB), Color(0xFF185FA5), Color(0xFF0C447C), Color(0xFFB5D4F4))
private val teal = Family(Color(0xFFE1F5EE), Color(0xFF0F6E56), Color(0xFF085041), Color(0xFF9FE1CB))
private val purple = Family(Color(0xFFEEEDFE), Color(0xFF534AB7), Color(0xFF3C3489), Color(0xFFCECBF6))
private val green = Family(Color(0xFFEAF3DE), Color(0xFF3B6D11), Color(0xFF27500A), Color(0xFFC0DD97))
private val amber = Family(Color(0xFFFAEEDA), Color(0xFF854F0B), Color(0xFF633806), Color(0xFFFAC775))
private val red = Family(Color(0xFFFCEBEB), Color(0xFFA32D2D), Color(0xFF791F1F), Color(0xFFF7C1C1))
private val gray = Family(Color(0xFFF1EFE8), Color(0xFF5F5E5A), Color(0xFF444441), Color(0xFFD3D1C7))

/** icon 名（SeedData.categories.icon）→ 色族。未知 icon 兜底 gray。 */
private val iconToFamily: Map<String, Family> = mapOf(
    "restaurant" to coral,
    "shopping_bag" to pink,
    "directions_car" to blue,
    "shopping_cart" to teal,
    "phone_android" to purple,
    "home" to green,
    "movie" to amber,
    "medical_services" to red,
    "school" to purple,
    "card_giftcard" to pink,
    "pets" to green,
    "category" to gray,
    "paid" to teal,
    "currency_yuan" to green,
    "savings" to gray,
    "account_balance" to blue,
    "help_outline" to gray
)

@Composable
@ReadOnlyComposable
fun categoryColor(iconName: String): CategoryColor {
    val f = iconToFamily[iconName] ?: gray
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        CategoryColor(f.darkContainer, f.darkContent)
    } else {
        CategoryColor(f.lightContainer, f.lightContent)
    }
}
