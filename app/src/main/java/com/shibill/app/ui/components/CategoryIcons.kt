package com.shibill.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyYuan
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

/** categories.icon（Material Icons 名）→ ImageVector 映射 */
object CategoryIcons {

    const val FALLBACK = "category"

    fun fromName(name: String): ImageVector = when (name) {
        "restaurant" -> Icons.Filled.Restaurant
        "shopping_bag" -> Icons.Filled.ShoppingBag
        "directions_car" -> Icons.Filled.DirectionsCar
        "shopping_cart" -> Icons.Filled.ShoppingCart
        "phone_android" -> Icons.Filled.PhoneAndroid
        "home" -> Icons.Filled.Home
        "movie" -> Icons.Filled.Movie
        "medical_services" -> Icons.Filled.MedicalServices
        "school" -> Icons.Filled.School
        "card_giftcard" -> Icons.Filled.CardGiftcard
        "pets" -> Icons.Filled.Pets
        "paid" -> Icons.Filled.Paid
        "currency_yuan" -> Icons.Filled.CurrencyYuan
        "savings" -> Icons.Filled.Savings
        "account_balance" -> Icons.Filled.AccountBalance
        "help_outline" -> Icons.Filled.HelpOutline
        else -> Icons.Filled.Category
    }
}
