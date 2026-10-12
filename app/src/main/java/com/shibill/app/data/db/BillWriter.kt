package com.shibill.app.data.db

/**
 * 统一写入入口：fingerprint 去重 + 落库（§6.3）。
 *
 * 自动通道（通知 / 截屏 / 导入）走 dedup=true；
 * 手动记账 dedup=false（用户意图优先，指纹含 createdAt 盐值，见 Deduplicator）。
 */
class BillWriter(private val billDao: BillDao) {

    /**
     * @return 新增行 id；重复（或并发冲突兜底）返回 null
     */
    suspend fun insert(bill: BillEntity, dedup: Boolean = true): Long? {
        if (dedup && billDao.countByFingerprint(bill.fingerprint) > 0) return null
        val rowId = billDao.insert(bill)
        return if (rowId == -1L) null else rowId
    }
}
