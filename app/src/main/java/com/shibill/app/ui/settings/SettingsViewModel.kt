package com.shibill.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shibill.app.BuildConfig
import com.shibill.app.data.GithubReleaseChecker
import com.shibill.app.data.backup.AlipayCsvImporter
import com.shibill.app.data.backup.BackupExporter
import com.shibill.app.data.backup.CsvExporter
import com.shibill.app.data.backup.WeChatXlsxImporter
import com.shibill.app.data.db.AppDatabase
import com.shibill.app.data.db.BillWriter
import com.shibill.app.data.prefs.SettingsStore
import com.shibill.app.parse.Classifier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val screenshotEnabled: Boolean = false,
    val screenshotAutoTrigger: Boolean = false
)

class SettingsViewModel(
    private val context: Context,
    private val db: AppDatabase,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val versionName: String = BuildConfig.VERSION_NAME

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.screenshotEnabled,
        settingsStore.screenshotAutoTrigger
    ) { enabled, auto ->
        SettingsUiState(screenshotEnabled = enabled, screenshotAutoTrigger = auto)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setScreenshotEnabled(value: Boolean) = viewModelScope.launch {
        settingsStore.setScreenshotEnabled(value)
    }

    fun setScreenshotAutoTrigger(value: Boolean) = viewModelScope.launch {
        settingsStore.setScreenshotAutoTrigger(value)
    }

    // ---- 数据导出 / 导入（SAF Uri）----

    suspend fun exportBackup(uri: Uri): Result<Unit> = runCatching {
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("无法写入所选位置")
        output.use { BackupExporter(db).exportTo(it) }
    }

    suspend fun exportCsv(uri: Uri): Result<Unit> = runCatching {
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("无法写入所选位置")
        output.use { CsvExporter(db).exportTo(it) }
    }

    suspend fun importBackup(uri: Uri): Result<BackupExporter.ImportSummary> = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法读取所选文件")
        input.use { BackupExporter(db).importFrom(it) }
    }

    suspend fun importAlipayCsv(uri: Uri): Result<AlipayCsvImporter.ImportSummary> = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法读取所选文件")
        input.use {
            AlipayCsvImporter(BillWriter(db.billDao()), Classifier(db.categoryDao())).import(it)
        }
    }

    suspend fun importWeChatXlsx(uri: Uri): Result<WeChatXlsxImporter.ImportSummary> = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法读取所选文件")
        input.use {
            WeChatXlsxImporter(BillWriter(db.billDao()), Classifier(db.categoryDao())).import(it)
        }
    }

    /** 检查更新：唯一联网点（GitHub Releases） */
    suspend fun checkUpdate(): GithubReleaseChecker.UpdateInfo? =
        GithubReleaseChecker.check(versionName)
}
