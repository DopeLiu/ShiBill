package com.shibill.app.capture

import android.content.Context
import com.shibill.app.data.db.BillDao
import com.shibill.app.data.db.BillEntity
import com.shibill.app.data.db.BillWriter
import com.shibill.app.data.db.RawEventDao
import com.shibill.app.data.db.RawEventEntity
import com.shibill.app.data.prefs.SettingsStore
import com.shibill.app.parse.Classifier
import com.shibill.app.parse.Deduplicator
import com.shibill.app.parse.RuleEngine
import com.shibill.app.parse.model.ParsedBill
import com.shibill.app.parse.model.RawEvent
import com.shibill.app.util.Constants
import kotlinx.coroutines.flow.first

/**
 * 采集管道（§5.3 核心链路）：解析 → 分类 → 去重审计 → 通知 / 暂存 / 自动入库。
 * 通知监听服务与无障碍截屏服务共用。
 */
class CapturePipeline(
    private val context: Context,
    private val ruleEngine: RuleEngine,
    private val classifier: Classifier,
    private val billWriter: BillWriter,
    private val billDao: BillDao,
    private val rawEventDao: RawEventDao,
    private val settingsStore: SettingsStore
) {

    suspend fun process(event: RawEvent) {
        val parsed = ruleEngine.parse(event)
        if (parsed == null) {
            handleUnparsed(event)
            return
        }

        val categoryId = classifier.classify(parsed.classifyText())
        val enriched = parsed.copy(categoryId = categoryId)

        val duplicated = billDao.countByFingerprint(parsed.fingerprint) > 0
        rawEventDao.insert(
            RawEventEntity(
                source = event.source,
                appPackage = event.appPackage,
                rawText = event.rawText,
                fingerprint = parsed.fingerprint,
                parsedOk = if (duplicated) RawEventEntity.PARSED_DUPLICATE else RawEventEntity.PARSED_OK,
                createdAt = System.currentTimeMillis()
            )
        )
        if (duplicated) return

        if (settingsStore.autoCommit.first()) {
            val rowId = billWriter.insert(enriched.toEntity()) ?: return
            CaptureBus.emit(CaptureEvent.AutoCommitted(rowId))
            CaptureNotifier.notifyAutoCommitted(context, enriched)
        } else {
            val pendingId = PendingStore.put(enriched)
            CaptureBus.emit(
                CaptureEvent.PendingCreated(
                    pendingId = pendingId,
                    amount = enriched.amount,
                    merchant = enriched.merchant,
                    account = enriched.account
                )
            )
            CaptureNotifier.notifyConfirm(context, pendingId, enriched)
        }
    }

    /**
     * 解析失败处理：仅当文本"疑似支付"时记录审计与补记通知。
     * 监控包名（微信/支付宝）会推送大量非支付通知（聊天等），
     * 若无门槛会导致通知骚扰与 raw_events 噪声（见交付说明"分歧裁定"第 3 条）。
     */
    private suspend fun handleUnparsed(event: RawEvent) {
        val looksLikePayment = Constants.PAYMENT_HINT_KEYWORDS.any { event.rawText.contains(it) }
        if (!looksLikePayment) return
        rawEventDao.insert(
            RawEventEntity(
                source = event.source,
                appPackage = event.appPackage,
                rawText = event.rawText,
                fingerprint = Deduplicator.sha1(
                    "${event.appPackage}|${event.rawText}|${event.occurredAt / 60_000L}"
                ),
                parsedOk = RawEventEntity.PARSED_FAILED,
                createdAt = System.currentTimeMillis()
            )
        )
        CaptureBus.emit(CaptureEvent.ParseFailed(event.rawText))
        CaptureNotifier.notifyFailure(context, event.rawText)
    }

    /** §6.4：分类文本 = merchant + note + rawText（解析阶段无 note） */
    private fun ParsedBill.classifyText(): String =
        listOf(merchant, rawText).filter { it.isNotBlank() }.joinToString(" ")

    private fun ParsedBill.toEntity(): BillEntity = BillEntity(
        amount = amount,
        type = type,
        categoryId = categoryId,
        account = account,
        merchant = merchant,
        note = "",
        occurredAt = occurredAt,
        source = source,
        fingerprint = fingerprint,
        createdAt = System.currentTimeMillis()
    )
}
