package com.shibill.app.ui.confirm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shibill.app.capture.PendingStore
import com.shibill.app.data.db.BillDao
import com.shibill.app.data.db.BillEntity
import com.shibill.app.data.db.BillWriter
import com.shibill.app.data.db.CategoryDao
import com.shibill.app.data.db.CategoryEntity
import com.shibill.app.data.db.SeedData
import com.shibill.app.parse.Classifier
import com.shibill.app.parse.Deduplicator
import com.shibill.app.parse.model.BillSource
import com.shibill.app.parse.model.BillType
import com.shibill.app.util.AmountFormat
import com.shibill.app.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class ConfirmUiState(
    val amountText: String = "",
    val type: BillType = BillType.EXPENSE,
    val categoryId: Long = 0L,
    /** 手动记账不采集账户（表单已移除）；自动确认/编辑沿用原值 */
    val account: String = "",
    val merchant: String = "",
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    /** 当前收支类型下的可选分类（已按支出/收入过滤，"未分类"为兜底两组均含） */
    val categories: List<CategoryEntity> = emptyList(),
    val isEdit: Boolean = false,
    val loaded: Boolean = false
)

/**
 * 确认入账页（§5.3 / §7）：手动记账、自动捕获确认、账单编辑共用。
 * 三种入口：confirm（空白）/ confirm?billId=（编辑）/ confirm?pending=&raw=（预填）。
 */
class ConfirmViewModel(
    private val billDao: BillDao,
    private val categoryDao: CategoryDao,
    private val billWriter: BillWriter,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val billId: Long? = savedStateHandle.get<String>("billId")
        ?.toLongOrNull()?.takeIf { it > 0 }

    private val pendingId: String? = savedStateHandle.get<String>("pending")
        ?.takeIf { it.isNotBlank() }

    private val rawParam: String? = savedStateHandle.get<String>("raw")
        ?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(ConfirmUiState())
    val uiState: StateFlow<ConfirmUiState> = _uiState.asStateFlow()

    /** 全量分类（按 sort_order）；按收支类型过滤后下发 UI */
    private var allCategories: List<CategoryEntity> = emptyList()

    /** 是否来自自动捕获的确认入账（用于标题文案） */
    val isConfirming: Boolean
        get() = pendingId != null

    init {
        viewModelScope.launch {
            allCategories = categoryDao.getAll()
            val uncategorizedId = allCategories
                .firstOrNull { it.name == Classifier.UNCATEGORIZED_NAME }?.id ?: 0L

            when {
                billId != null -> {
                    val bill = billDao.getById(billId)
                    _uiState.update { state ->
                        if (bill == null) {
                            state.copy(loaded = true)
                        } else {
                            state.copy(
                                amountText = formatAmountInput(bill.amount),
                                type = bill.type,
                                categoryId = bill.categoryId,
                                account = bill.account,
                                merchant = bill.merchant,
                                note = bill.note,
                                occurredAt = bill.occurredAt,
                                isEdit = true,
                                loaded = true
                            )
                        }
                    }
                }

                pendingId != null -> {
                    val pending = PendingStore.get(pendingId)
                    _uiState.update { state ->
                        if (pending == null) {
                            // 进程被杀暂存丢失：退化为空白表单 + 原文进备注（兜底）
                            state.copy(note = rawParam.orEmpty(), categoryId = uncategorizedId, loaded = true)
                        } else {
                            state.copy(
                                amountText = formatAmountInput(pending.amount),
                                type = pending.type,
                                categoryId = if (pending.categoryId != 0L) pending.categoryId else uncategorizedId,
                                account = pending.account,
                                merchant = pending.merchant,
                                note = "",
                                occurredAt = pending.occurredAt,
                                loaded = true
                            )
                        }
                    }
                }

                rawParam != null -> _uiState.update {
                    it.copy(note = rawParam, categoryId = uncategorizedId, loaded = true)
                }

                else -> _uiState.update {
                    it.copy(categoryId = uncategorizedId, loaded = true)
                }
            }

            _uiState.update { normalizeCategorySelection(it) }
        }
    }

    /** 当前收支类型下可选分类：收入类仅"收入"时显示，其余在"支出"时显示；"未分类"两组均含（兜底） */
    private fun categoriesFor(type: BillType): List<CategoryEntity> =
        allCategories.filter { category ->
            category.name == Classifier.UNCATEGORIZED_NAME ||
                SeedData.isIncomeCategory(category.name) == (type == BillType.INCOME)
        }

    /** 按当前类型下发分类列表；已选分类不属于当前类型时回退到"未分类" */
    private fun normalizeCategorySelection(state: ConfirmUiState): ConfirmUiState {
        val categories = categoriesFor(state.type)
        val keepSelection = categories.any { it.id == state.categoryId }
        return state.copy(
            categories = categories,
            categoryId = if (keepSelection) state.categoryId else fallbackCategoryId(categories)
        )
    }

    private fun fallbackCategoryId(categories: List<CategoryEntity>): Long =
        categories.firstOrNull { it.name == Classifier.UNCATEGORIZED_NAME }?.id ?: 0L

    fun updateAmount(text: String) {
        // 仅保留数字与一个小数点（最多两位小数）
        val digitsAndDots = text.filter { it.isDigit() || it == '.' }
        val firstDot = digitsAndDots.indexOf('.')
        val filtered = if (firstDot == -1) {
            digitsAndDots
        } else {
            digitsAndDots.substring(0, firstDot + 1) +
                    digitsAndDots.substring(firstDot + 1).filter { it.isDigit() }.take(2)
        }
        _uiState.update { it.copy(amountText = filtered) }
    }

    fun updateType(type: BillType) =
        _uiState.update { normalizeCategorySelection(it.copy(type = type)) }

    fun updateCategory(categoryId: Long) = _uiState.update { it.copy(categoryId = categoryId) }

    fun updateNote(text: String) = _uiState.update { it.copy(note = text) }

    /** DatePicker 回调（UTC 午夜 millis）：替换日期，保留原时刻 */
    fun updateDate(utcMillis: Long) {
        val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val time = TimeUtils.toLocalDateTime(_uiState.value.occurredAt).toLocalTime()
        val newMillis = date.atTime(time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        _uiState.update { it.copy(occurredAt = newMillis) }
    }

    val amountValid: Boolean
        get() = AmountFormat.parse(_uiState.value.amountText)?.let { it > 0.0 } == true

    /**
     * 保存（新建或编辑）。
     * @return true=已保存；false=重复账单（未入账）
     */
    suspend fun save(): Boolean {
        val state = _uiState.value
        val amount = AmountFormat.parse(state.amountText)?.takeIf { it > 0.0 } ?: return false

        if (state.isEdit && billId != null) {
            val existing = billDao.getById(billId) ?: return false
            // 编辑保留原指纹（去重约束由原指纹保护）
            billDao.update(
                existing.copy(
                    amount = amount,
                    type = state.type,
                    categoryId = state.categoryId,
                    account = state.account,
                    merchant = state.merchant,
                    note = state.note,
                    occurredAt = state.occurredAt
                )
            )
            return true
        }

        val now = System.currentTimeMillis()
        val pending = pendingId?.let { PendingStore.get(it) }
        val fingerprint = if (pending != null) {
            Deduplicator.fingerprint(
                appPackage = pending.appPackage,
                amount = amount,
                merchant = state.merchant,
                occurredAt = state.occurredAt
            )
        } else {
            Deduplicator.fingerprint(
                appPackage = "manual",
                amount = amount,
                merchant = state.merchant,
                occurredAt = state.occurredAt,
                manualSalt = now
            )
        }

        val entity = BillEntity(
            amount = amount,
            type = state.type,
            categoryId = state.categoryId,
            account = state.account,
            merchant = state.merchant,
            note = state.note,
            occurredAt = state.occurredAt,
            source = pending?.source ?: BillSource.MANUAL,
            fingerprint = fingerprint,
            createdAt = now
        )

        val rowId = billWriter.insert(entity, dedup = true) ?: return false
        pendingId?.let { PendingStore.remove(it) }
        return rowId > 0
    }

    private fun formatAmountInput(amount: Double): String =
        String.format(java.util.Locale.ROOT, "%.2f", amount)
}
