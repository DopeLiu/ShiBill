package com.shibill.app.parse

import com.shibill.app.data.db.SeedData
import com.shibill.app.parse.model.BillSource
import com.shibill.app.parse.model.BillType
import com.shibill.app.parse.model.RawEvent
import com.shibill.app.parse.model.Rule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RuleEngine 单元测试（§11 用例集，≥20 个用例）。
 * 通过 FakeRuleDao 走生产路径（engine.parse(event)），另附纯函数重载的自定义规则场景。
 */
class RuleEngineTest {

    private val engine = RuleEngine(FakeRuleDao())

    private val fixedTime = 1_760_000_000_000L // 固定时间戳

    private fun event(
        text: String,
        pkg: String = WECHAT,
        source: BillSource = BillSource.NOTIFICATION,
        at: Long = fixedTime
    ) = RawEvent(source = source, appPackage = pkg, rawText = text, occurredAt = at)

    // ---------- §11 用例集 ----------

    @Test
    fun `1 微信支付已支付 → 支出`() = runBlocking {
        val bill = engine.parse(event("微信支付：已支付¥25.00"))
        assertNotNull(bill)
        assertEquals(BillType.EXPENSE, bill!!.type)
        assertEquals(25.00, bill.amount, 0.001)
        assertEquals("微信", bill.account)
    }

    @Test
    fun `2 千分位金额解析`() = runBlocking {
        val bill = engine.parse(event("微信支付：已支付￥1,234.50"))
        assertEquals(1234.50, bill!!.amount, 0.001)
    }

    @Test
    fun `3 微信支付收款 → 收入`() = runBlocking {
        val bill = engine.parse(event("微信支付收款￥88.00"))
        assertNotNull(bill)
        assertEquals(BillType.INCOME, bill!!.type)
        assertEquals(88.00, bill.amount, 0.001)
    }

    @Test
    fun `4 支付宝支付成功 → 支出`() = runBlocking {
        val bill = engine.parse(event("支付宝支付成功：45.20元", pkg = ALIPAY))
        assertNotNull(bill)
        assertEquals(BillType.EXPENSE, bill!!.type)
        assertEquals(45.20, bill.amount, 0.001)
        assertEquals("支付宝", bill.account)
    }

    @Test
    fun `5 退款文本 → 拒绝（防误判）`() = runBlocking {
        assertNull(engine.parse(event("您有一笔退款正在处理")))
        assertNull(engine.parse(event("支付宝退款45.20元已到账", pkg = ALIPAY)))
    }

    @Test
    fun `6 待支付 → 拒绝（防误判，尽管正则可命中）`() = runBlocking {
        assertNull(engine.parse(event("微信支付：待支付¥99.00")))
    }

    @Test
    fun `7 非账单配送通知 → 无规则命中`() = runBlocking {
        assertNull(engine.parse(event("美团外卖：您的外卖正在配送中", pkg = ALIPAY)))
    }

    @Test
    fun `9 同一通知 3 秒内两次 → 指纹一致（可被去重）`() = runBlocking {
        val first = engine.parse(event("微信支付：已支付¥25.00", at = fixedTime))!!
        val second = engine.parse(event("微信支付：已支付¥25.00", at = fixedTime + 3_000L))!!
        assertEquals(first.fingerprint, second.fingerprint)
    }

    // ---------- 方向判定 ----------

    @Test
    fun `10 已收款 → 收入`() = runBlocking {
        val bill = engine.parse(event("微信支付：已收款￥100.00"))
        assertEquals(BillType.INCOME, bill!!.type)
    }

    @Test
    fun `11 支付宝收款 → 收入`() = runBlocking {
        val bill = engine.parse(event("支付宝收款45.20", pkg = ALIPAY))
        assertEquals(BillType.INCOME, bill!!.type)
    }

    @Test
    fun `12 支付宝到账 → 收入`() = runBlocking {
        val bill = engine.parse(event("支付宝：到账100元", pkg = ALIPAY))
        assertEquals(BillType.INCOME, bill!!.type)
        assertEquals(100.0, bill.amount, 0.001)
    }

    @Test
    fun `13 无方向信息默认支出`() = runBlocking {
        val bill = engine.parse(event("支付宝支付成功：45.20元", pkg = ALIPAY))
        assertEquals(BillType.EXPENSE, bill!!.type)
    }

    // ---------- 包名与规则过滤 ----------

    @Test
    fun `14 包名不匹配且无通用规则命中 → null`() = runBlocking {
        // 微信文本 + 支付宝包名：三条包名规则都不命中方向/关键词
        assertNull(engine.parse(event("微信支付：已支付¥25.00", pkg = ALIPAY)))
    }

    @Test
    fun `15 禁用规则不参与匹配`() = runBlocking {
        val disabled = SeedData.rules.map { if (it.name == "微信支付通知") it.copy(enabled = false) else it }
        val dynamicEngine = RuleEngine(FakeRuleDao(disabled))
        // 微信支付通知被停用后，"微信支付：已支付¥25.00" 不应命中
        assertNull(dynamicEngine.parse(event("微信支付：已支付¥25.00")))
    }

    @Test
    fun `16 无金额文本 → null`() = runBlocking {
        assertNull(engine.parse(event("微信支付：已支付")))
    }

    // ---------- 金额边界 ----------

    @Test
    fun `17 整数金额`() = runBlocking {
        val bill = engine.parse(event("微信支付：已支付¥100"))
        assertEquals(100.0, bill!!.amount, 0.001)
    }

    @Test
    fun `18 小额一分钱`() = runBlocking {
        val bill = engine.parse(event("微信支付：已支付¥0.01"))
        assertEquals(0.01, bill!!.amount, 0.001)
    }

    @Test
    fun `19 金额为 0 → 拒绝`() = runBlocking {
        val rule = Rule(
            id = 1, name = "test", appPackage = WECHAT,
            pattern = "支付[¥￥]?(?<amount>[\\d,]+\\.?\\d*)", priority = 1
        )
        assertNull(engine.parse(event("支付0元"), listOf(rule)))
    }

    // ---------- 防误判关键词（6 项逐一验证） ----------

    @Test
    fun `20 防误判 支付失败`() = runBlocking {
        assertNull(engine.parse(event("微信支付：支付失败¥25.00")))
    }

    @Test
    fun `21 防误判 正在处理`() = runBlocking {
        assertNull(engine.parse(event("微信支付：正在处理¥25.00")))
    }

    @Test
    fun `22 防误判 小荷包`() = runBlocking {
        assertNull(engine.parse(event("微信支付：小荷包转入¥25.00")))
    }

    @Test
    fun `23 防误判 还款日`() = runBlocking {
        assertNull(engine.parse(event("您的还款日为每月5日，微信支付已支付¥25.00")))
    }

    @Test
    fun `24 防误判 账单提醒`() = runBlocking {
        assertNull(engine.parse(event("账单提醒：微信支付：已支付¥25.00")))
    }

    @Test
    fun `25 防误判 余额不足`() = runBlocking {
        assertNull(engine.parse(event("微信支付：余额不足，已支付¥25.00")))
    }

    // ---------- 自定义规则 ----------

    @Test
    fun `28 merchant 命名组被捕获`() = runBlocking {
        val rule = Rule(
            id = 1, name = "自定义", appPackage = WECHAT,
            pattern = "消费(?<merchant>[\\u4e00-\\u9fa5A-Za-z]+)[¥￥]?(?<amount>[\\d,]+\\.?\\d*)",
            priority = 1
        )
        val bill = engine.parse(event("消费星巴克咖啡￥38.00"), listOf(rule))
        assertNotNull(bill)
        assertEquals("星巴克咖啡", bill!!.merchant)
        assertEquals(38.0, bill.amount, 0.001)
    }

    @Test
    fun `29 优先级高的规则先匹配`() = runBlocking {
        val low = Rule(
            id = 1, name = "low", appPackage = WECHAT,
            pattern = "支付(?<amount>100)", priority = 1
        )
        val high = Rule(
            id = 2, name = "high", appPackage = WECHAT,
            pattern = "支付(?<amount>999)", priority = 10
        )
        val bill = engine.parse(event("支付999元 支付100元"), listOf(low, high))
        assertEquals(999.0, bill!!.amount, 0.001)
    }

    @Test
    fun `30 逗号分隔多包名规则`() = runBlocking {
        val rule = Rule(
            id = 1, name = "多包名", appPackage = "$ALIPAY, $WECHAT",
            pattern = "已支付[¥￥]?(?<amount>[\\d,]+\\.?\\d*)", priority = 1
        )
        assertNotNull(engine.parse(event("已支付¥10.00", pkg = WECHAT), listOf(rule)))
        assertNotNull(engine.parse(event("已支付¥10.00", pkg = ALIPAY), listOf(rule)))
    }

    @Test
    fun `31 非法正则被跳过不影响其他规则`() = runBlocking {
        val broken = Rule(
            id = 1, name = "broken", appPackage = WECHAT,
            pattern = "(?<amount>", priority = 100
        )
        val rules = listOf(broken) + SeedData.rules
            .map { it.toDomain() }
        val bill = engine.parse(event("微信支付：已支付¥25.00"), rules)
        assertNotNull(bill)
        assertEquals(25.0, bill!!.amount, 0.001)
    }

    // ---------- 指纹一致性 ----------

    @Test
    fun `32 解析结果指纹与 Deduplicator 计算一致`() = runBlocking {
        val bill = engine.parse(event("微信支付：已支付¥25.00"))!!
        assertEquals(
            Deduplicator.fingerprint(WECHAT, 25.0, "", fixedTime),
            bill.fingerprint
        )
        assertTrue(bill.fingerprint.length == 40)
    }

    // ---------- 两行式通知（2026-10-13 内置规则放宽：允许换行与方向词前后空白） ----------

    @Test
    fun `33 微信支付 标题与正文分两行 → 仍可解析`() = runBlocking {
        // 实测形态：标题「微信支付」+ 换行 + 正文「已支付¥10.00」
        val bill = engine.parse(event("微信支付\n已支付¥10.00"))
        assertNotNull(bill)
        assertEquals(BillType.EXPENSE, bill!!.type)
        assertEquals(10.00, bill.amount, 0.001)
    }

    @Test
    fun `34 微信支付 方向词与金额间有空格 → 仍可解析`() = runBlocking {
        // 合并通知形态：「微信支付:成功支付 15.00 元」
        val bill = engine.parse(event("微信支付:成功支付 15.00 元"))
        assertNotNull(bill)
        assertEquals(15.00, bill!!.amount, 0.001)
    }

    @Test
    fun `35 微信收款 分两行 → 收入`() = runBlocking {
        // 实测形态：标题「微信支付」+ 正文「微信支付收款0.01元(朋友到店)」
        val bill = engine.parse(event("微信支付\n微信支付收款0.01元(朋友到店)"))
        assertNotNull(bill)
        assertEquals(BillType.INCOME, bill!!.type)
        assertEquals(0.01, bill.amount, 0.001)
    }

    @Test
    fun `36 支付宝收款 分两行 → 收入（支付宝规则未改，回归）`() = runBlocking {
        // 实测形态：标题「支付宝通知」+ 正文「支付宝成功收款1.00元。」
        val bill = engine.parse(event("支付宝通知\n支付宝成功收款1.00元。", pkg = ALIPAY))
        assertNotNull(bill)
        assertEquals(BillType.INCOME, bill!!.type)
        assertEquals(1.00, bill.amount, 0.001)
    }

    @Test
    fun `37 支付宝支付 分两行 → 仍可解析（支付宝规则未改，回归）`() = runBlocking {
        // 支付宝规则的冒号本来就是可选的，正文自带「支付成功」锚点即可命中
        val bill = engine.parse(event("支付宝通知\n支付成功 45.20元", pkg = ALIPAY))
        assertNotNull(bill)
        assertEquals(BillType.EXPENSE, bill!!.type)
        assertEquals(45.20, bill!!.amount, 0.001)
    }

    @Test
    fun `38 支付宝正文不带品牌词且首词非锚点 → 不解析（已知缺口，待真机正文确认）`() = runBlocking {
        // 「支付宝通知」里"支付宝"后面被"通知"挡住，正文"已付款45.20元"又无品牌词/锚点 →
        // 当前规则无法命中。是否要覆盖取决于真机抓到的正文，不做猜测性放宽。
        assertNull(engine.parse(event("支付宝通知\n已付款45.20元", pkg = ALIPAY)))
    }

    @Test
    fun `39 放宽后聊天文本仍不误判`() = runBlocking {
        // 空白只放宽到 \s*：品牌词与数字之间夹了汉字时不得入账
        assertNull(engine.parse(event("张三\n微信支付转你了100元")))
        assertNull(engine.parse(event("张三\n微信支付很方便，下次还用")))
    }

    private fun com.shibill.app.data.db.RuleEntity.toDomain() =
        com.shibill.app.parse.model.Rule(
            id = id, name = name, appPackage = appPackage, pattern = pattern,
            priority = priority, enabled = enabled, builtIn = builtIn
        )

    private companion object {
        const val WECHAT = "com.tencent.mm"
        const val ALIPAY = "com.eg.android.AlipayGphone"
    }
}
