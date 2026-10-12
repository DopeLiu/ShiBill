package com.shibill.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "shibill_settings")

/** 应用设置（DataStore Preferences） */
class SettingsStore(private val context: Context) {

    companion object {
        /** §5.1 预置监控包名：微信 / 支付宝 / 云闪付 / 支付宝（部分渠道包） */
        val DEFAULT_MONITORED_PACKAGES: Set<String> = setOf(
            "com.tencent.mm",
            "com.eg.android.AlipayGphone",
            "com.unionpay",
            "com.eg.android.AlipayGphoneRC"
        )
    }

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val screenshotEnabled = booleanPreferencesKey("screenshot_enabled")
        val screenshotAutoTrigger = booleanPreferencesKey("screenshot_auto_trigger")
        val autoCommit = booleanPreferencesKey("auto_commit")
        val monitoredPackages = stringSetPreferencesKey("monitored_packages")
    }

    /** 是否完成首次引导 */
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboarded] ?: false }

    /** 截屏自动记账主开关（默认关闭，§5.2） */
    val screenshotEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.screenshotEnabled] ?: false }

    /** 截屏二级开关：识别到支付成功页自动触发（默认关闭） */
    val screenshotAutoTrigger: Flow<Boolean> = context.dataStore.data.map { it[Keys.screenshotAutoTrigger] ?: false }

    /** 自动入账（免确认），默认关闭（§5.3） */
    val autoCommit: Flow<Boolean> = context.dataStore.data.map { it[Keys.autoCommit] ?: false }

    /** 监控包名集合 */
    val monitoredPackages: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.monitoredPackages] ?: DEFAULT_MONITORED_PACKAGES }

    suspend fun setOnboarded(value: Boolean) =
        context.dataStore.edit { it[Keys.onboarded] = value }

    suspend fun setScreenshotEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.screenshotEnabled] = value }

    suspend fun setScreenshotAutoTrigger(value: Boolean) =
        context.dataStore.edit { it[Keys.screenshotAutoTrigger] = value }

    suspend fun setAutoCommit(value: Boolean) =
        context.dataStore.edit { it[Keys.autoCommit] = value }

    suspend fun addMonitoredPackage(pkg: String) = context.dataStore.edit { prefs ->
        prefs[Keys.monitoredPackages] =
            (prefs[Keys.monitoredPackages] ?: DEFAULT_MONITORED_PACKAGES) + pkg.trim()
    }

    suspend fun removeMonitoredPackage(pkg: String) = context.dataStore.edit { prefs ->
        prefs[Keys.monitoredPackages] =
            (prefs[Keys.monitoredPackages] ?: DEFAULT_MONITORED_PACKAGES) - pkg.trim()
    }
}
