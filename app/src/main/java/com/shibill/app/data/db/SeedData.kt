package com.shibill.app.data.db

/**
 * 预置数据（初始化写入，用户可改）：
 * - 分类：§6.4 预置分类（sort_order 顺序即显示顺序），名称精简（多为两个汉字，
 *   "其他支出 / 其他收入 / 未分类"保留原名）
 * - 规则：§6.2 内置规则
 *
 * 规则 pattern 说明：方案 §6.2 的"微信收款 / 支付宝收款"注释要求 direction=INCOME，
 * 但 rules 表无 direction 默认值列，故将 direction 语义并入命名捕获组（在原文基础上增加
 * (?<direction>收款) / (?<direction>收款|到账)），使方向可被 RuleEngine 机械解析。
 */
object SeedData {

    val categories: List<CategoryEntity> = listOf(
        CategoryEntity(name = "餐饮", icon = "restaurant", keywords = "美团,饿了么,肯德基,麦当劳,星巴克,瑞幸,外卖,餐厅", sortOrder = 0),
        CategoryEntity(name = "购物", icon = "shopping_bag", keywords = "淘宝,天猫,京东,拼多多,唯品会", sortOrder = 1),
        CategoryEntity(name = "交通", icon = "directions_car", keywords = "滴滴,高德,地铁,公交,12306,加油", sortOrder = 2),
        CategoryEntity(name = "日用", icon = "shopping_cart", keywords = "超市,便利店,永辉,盒马", sortOrder = 3),
        CategoryEntity(name = "通讯", icon = "phone_android", keywords = "话费,流量,移动,联通,电信", sortOrder = 4),
        CategoryEntity(name = "居住", icon = "home", keywords = "房租,水电,物业,燃气", sortOrder = 5),
        CategoryEntity(name = "娱乐", icon = "movie", keywords = "电影,游戏,视频会员,音乐", sortOrder = 6),
        CategoryEntity(name = "医疗", icon = "medical_services", keywords = "药店,医院,体检", sortOrder = 7),
        CategoryEntity(name = "教育", icon = "school", keywords = "课程,书店,培训", sortOrder = 8),
        CategoryEntity(name = "礼金", icon = "card_giftcard", keywords = "红包,转账,礼金", sortOrder = 9),
        CategoryEntity(name = "宠物", icon = "pets", keywords = "宠物,猫,狗", sortOrder = 10),
        CategoryEntity(name = "其他支出", icon = "category", keywords = "", sortOrder = 11),
        CategoryEntity(name = "工资", icon = "paid", keywords = "工资,薪水", sortOrder = 12),
        CategoryEntity(name = "退款", icon = "currency_yuan", keywords = "退款,返现", sortOrder = 13),
        CategoryEntity(name = "其他收入", icon = "savings", keywords = "", sortOrder = 14),
        CategoryEntity(name = "理财", icon = "account_balance", keywords = "", sortOrder = 15),
        CategoryEntity(name = "未分类", icon = "help_outline", keywords = "", sortOrder = 16)
    )

    /**
     * 收入类分类名单（§6.4 标注 type=INCOME 的分类；"理财"为用户追加）。
     * V1 分类名不可编辑（仅可改 keywords），故以名单判定收支归属；
     * "未分类"为收支共用兜底，不计入其中。
     */
    val incomeCategoryNames: Set<String> = setOf("工资", "退款", "其他收入", "理财")

    fun isIncomeCategory(name: String): Boolean = name in incomeCategoryNames

    /**
     * 正则写法约定（2026-10-13 放宽微信规则，勿改回"品牌词后必须紧跟冒号"）：
     *
     * 监听服务把 标题 + 正文 + 大文本 用换行拼接后交给正则（见 BillNotificationListenerService），
     * 而通知标题恒为「微信支付」/「支付宝通知」，正文是否自带品牌词决定了规则能否命中：
     *   微信收款 → 正文自带品牌词，"微信支付\n微信支付收款0.01元(朋友到店)"
     *   微信消费 → 正文不带品牌词，拼接后是 "微信支付\n已支付¥10.00"
     *   支付宝收款 → 正文自带品牌词，"支付宝通知\n支付宝成功收款1.00元。"
     *
     * 因此两类规则的写法**刻意不同**，不是笔误：
     * - 微信支付通知：品牌词与金额之间可能是换行，**冒号并非恒有**（只有合并通知形态才带半角冒号），
     *   故必须把冒号写成可选 [：:]?，并允许方向词前后出现空白。
     *   旧写法 "微信支付[：:]" 把冒号写死 → "微信支付\n已支付¥10.00" 整笔解析失败、永不入账。
     * - 支付宝支付通知：冒号本来就是可选的（[：:]?），锚点集合 {支付宝, 支付宝付款, 支付成功}
     *   已覆盖公开来源中出现的全部正文形态，故**不改**。
     *
     * 空白只用 \s* 放宽，**不得**改成 [^\d]{0,n} 之类的模糊跳过，否则聊天文本里的数字会被误判入账。
     */
    val rules: List<RuleEntity> = listOf(
        RuleEntity(
            name = "微信收款",
            appPackage = "com.tencent.mm",
            pattern = "微信支付\\s*(?<direction>收款)\\s*[¥￥]?\\s*(?<amount>[\\d,]+\\.?\\d*)",
            priority = 101
        ),
        RuleEntity(
            name = "支付宝收款",
            appPackage = "com.eg.android.AlipayGphone",
            pattern = "(?<direction>收款|到账)[¥￥]?(?<amount>[\\d,]+\\.?\\d*)",
            priority = 101
        ),
        RuleEntity(
            name = "微信支付通知",
            appPackage = "com.tencent.mm",
            pattern = "微信支付[：:]?\\s*(?:(?<direction>已支付|已付款|成功支付|支付成功|付款成功|已收款|成功收款|收款成功)\\s*)?[¥￥]?\\s*(?<amount>[\\d,]+\\.?\\d*)",
            priority = 100
        ),
        RuleEntity(
            name = "支付宝支付通知",
            appPackage = "com.eg.android.AlipayGphone",
            pattern = "(?:支付宝|支付宝付款|支付成功)[：:]?\\s*[¥￥]?(?<amount>[\\d,]+\\.?\\d*)",
            priority = 100
        )
    )

    /** 空库时写入预置分类与规则（幂等，可重复调用） */
    suspend fun seedIfEmpty(db: AppDatabase) {
        if (db.categoryDao().count() == 0) {
            db.categoryDao().insertAll(categories)
        }
        if (db.ruleDao().count() == 0) {
            db.ruleDao().insertAll(rules)
        }
    }
}
