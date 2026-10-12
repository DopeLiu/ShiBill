package com.shibill.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shibill.app.data.db.RuleDao
import com.shibill.app.data.db.RuleEntity
import com.shibill.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RulesUiState(
    val rules: List<RuleEntity> = emptyList(),
    val monitoredPackages: Set<String> = emptySet(),
    val autoCommit: Boolean = false
)

class RulesViewModel(
    private val ruleDao: RuleDao,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val uiState: StateFlow<RulesUiState> = combine(
        ruleDao.observeAll(),
        settingsStore.monitoredPackages,
        settingsStore.autoCommit
    ) { rules, packages, autoCommit ->
        RulesUiState(rules = rules, monitoredPackages = packages, autoCommit = autoCommit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    fun setAutoCommit(value: Boolean) = viewModelScope.launch {
        settingsStore.setAutoCommit(value)
    }

    fun setRuleEnabled(rule: RuleEntity, enabled: Boolean) = viewModelScope.launch {
        ruleDao.update(rule.copy(enabled = enabled))
    }

    fun saveRule(rule: RuleEntity) = viewModelScope.launch {
        if (rule.id == 0L) ruleDao.insert(rule) else ruleDao.update(rule)
    }

    /** 内置规则不可删除（§6.2），仅可编辑与停用 */
    fun deleteRule(rule: RuleEntity) = viewModelScope.launch {
        if (!rule.builtIn) ruleDao.deleteById(rule.id)
    }

    fun addPackage(pkg: String) = viewModelScope.launch {
        if (pkg.isNotBlank()) settingsStore.addMonitoredPackage(pkg.trim())
    }

    fun removePackage(pkg: String) = viewModelScope.launch {
        settingsStore.removeMonitoredPackage(pkg)
    }
}
