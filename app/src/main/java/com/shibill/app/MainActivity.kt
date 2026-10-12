package com.shibill.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.shibill.app.ui.nav.AppNavHost
import com.shibill.app.ui.nav.Routes
import com.shibill.app.ui.theme.ShiBillTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 单 Activity（§3）：承载全部 Compose 页面。
 * 通知点击 → EXTRA_ROUTE 路由 → NavHost 导航（含 ConfirmScreen 预填参数）。
 */
class MainActivity : ComponentActivity() {

    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            ShiBillTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(pendingRoute = pendingRoute)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }

    companion object {
        const val EXTRA_ROUTE = "extra_route"
    }
}

@Composable
private fun AppRoot(pendingRoute: MutableStateFlow<String?>) {
    val context = LocalContext.current
    val store = (context.applicationContext as ShiBillApp).container.settingsStore

    // null = 尚未读取到引导状态（首帧等待，毫秒级）
    val onboardedFlow: Flow<Boolean?> = store.onboarded
    val onboarded by onboardedFlow.collectAsStateWithLifecycle(initialValue = null)
    val route by pendingRoute.collectAsState()

    val current = onboarded ?: return

    val navController = rememberNavController()

    // 消费通知路由（confirm?pending=… / confirm?raw=… / home）
    LaunchedEffect(route) {
        val target = route ?: return@LaunchedEffect
        navController.navigate(target) { launchSingleTop = true }
        pendingRoute.value = null
    }

    AppNavHost(
        navController = navController,
        startDestination = if (current) Routes.HOME else Routes.ONBOARD
    )
}
