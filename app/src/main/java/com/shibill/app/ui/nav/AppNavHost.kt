package com.shibill.app.ui.nav

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.shibill.app.ui.confirm.ConfirmScreen
import com.shibill.app.ui.home.HomeScreen
import com.shibill.app.ui.onboard.OnboardScreen
import com.shibill.app.ui.rules.CategoryKeywordsScreen
import com.shibill.app.ui.rules.RulesScreen
import com.shibill.app.ui.settings.SettingsScreen
import com.shibill.app.ui.stats.StatsScreen
import com.shibill.app.ui.theme.MotionTokens

/** 路由表（v2：移除 CALENDAR） */
object Routes {
    const val HOME = "home"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val ONBOARD = "onboard"
    const val RULES = "rules"
    const val CATEGORY_KEYWORDS = "categories"

    const val CONFIRM = "confirm?billId={billId}&pending={pending}&raw={raw}"

    fun confirm(billId: Long? = null, pending: String? = null, raw: String? = null): String {
        val params = listOfNotNull(
            billId?.let { "billId=$it" },
            pending?.let { "pending=${Uri.encode(it)}" },
            raw?.let { "raw=${Uri.encode(it)}" }
        )
        return if (params.isEmpty()) "confirm" else "confirm?" + params.joinToString("&")
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

/** v2：4 栏 → 3 栏（日历页已删除，其月维度职能由统计页承担） */
private val tabs = listOf(
    TabItem(Routes.HOME, "流水", Icons.Filled.Home),
    TabItem(Routes.STATS, "统计", Icons.Filled.PieChart),
    TabItem(Routes.SETTINGS, "设置", Icons.Filled.Settings)
)

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val bottomPadding = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(bottom = bottomPadding),
            // 动效 M3：淡入 + 轻微上移（位移 = 屏高 / SlideDenominator）
            enterTransition = {
                fadeIn(tween(MotionTokens.DurationMedium, easing = MotionTokens.EaseEnter)) +
                    slideInVertically(
                        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EaseEnter),
                        initialOffsetY = { it / MotionTokens.SlideDenominator }
                    )
            },
            exitTransition = {
                fadeOut(tween(MotionTokens.DurationShort, easing = MotionTokens.EaseExit))
            },
            popEnterTransition = {
                fadeIn(tween(MotionTokens.DurationMedium, easing = MotionTokens.EaseEnter))
            },
            popExitTransition = {
                fadeOut(tween(MotionTokens.DurationShort, easing = MotionTokens.EaseExit))
            }
        ) {
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.RULES) { RulesScreen(navController) }
            composable(Routes.CATEGORY_KEYWORDS) { CategoryKeywordsScreen(navController) }
            composable(
                route = Routes.CONFIRM,
                arguments = listOf(
                    navArgument("billId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("pending") { type = NavType.StringType; defaultValue = "" },
                    navArgument("raw") { type = NavType.StringType; defaultValue = "" }
                )
            ) { ConfirmScreen(navController) }
            composable(Routes.ONBOARD) { OnboardScreen(navController) }
        }
    }
}
