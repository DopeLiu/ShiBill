# 拾账 ShiBill 自动记账 App 设计开发方案

> 版本：v1.2（2026-10-11）  
> 应用名：拾账 ShiBill，包名 `com.shibill.app`（包名上架后不可改，已定稿）  
> 目标读者：任何 AI 或人类开发者，按本文档可实现完整产品，无需额外澄清。  
> 若文档未规定的细节，按"最简实现 + Material 3 默认样式"处理。

---

## 0. 项目定位与硬性约束

**产品**：Android 本地自动记账应用。监听支付类通知 + 支付成功页截屏，自动解析入账，本地存储，提供统计报表。

**硬性约束（违反任意一条即为不合格）**：

1. 语言：全 Kotlin（构建脚本 Kotlin DSL），UI 框架 Jetpack Compose，禁止引入 Flutter/Java 大规模代码。
2. 运行成本为零：**不联网调用任何付费/免费大模型 API，不需要服务器，不需要账号系统**。允许的联网行为仅：应用内检查更新（GitHub Releases JSON）。
3. 识别全部本地完成：通知文本用正则解析；截屏用 ML Kit OCR（本地离线模型，免费）。
4. 数据 100% 本地：Room 数据库单文件，提供导出/导入备份，无云同步。
5. 免 root，不使用 Xposed/LSPosed。
6. 最终面向公众发布（应用商店 + GitHub Releases），代码采用 MIT 协议。

---

## 1. 借鉴策略（减少开发量的具体安排）

| 参考项目                                    | 协议         | 借什么                                                                                                    | 怎么借                                   |
| --------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------- |
| **Goose666666/jizhang**（Flutter+Kotlin） | **MIT**    | ① 无障碍截屏编排逻辑（`ScanService.kt`：takeScreenshot + 前台服务保活模式）② 微信 xlsx / 支付宝 GBK csv 导入解析规则与去重思路 ③ 首次使用引导页流程 | **可直接翻译成 Kotlin 抄**（MIT 允许，保留其版权声明即可） |
| FuShengDuoBuYu/AutoBookKeepingBeta      | 未明确        | 通知解析的触发包名清单、防误判策略（待支付/小荷包排除）                                                                           | 只看 README/Wiki 的产品行为，**不抄代码**         |
| AutoAccountingOrg/AutoAccounting        | **GPL3.0** | 规则引擎的产品设计（关键词/正则/金额提取）                                                                                 | **严禁复制任何代码**（GPL 会传染整个项目），仅参考功能设计     |
| 25283531/BeeCount                       | MIT        | 截图 OCR 记账交互、分类统计 UI 布局参考                                                                               | 参考交互设计，可少量抄 UI 代码                     |

**给实现者的规则**：借鉴项目代码不要标注来源与原协议；GPL 项目只允许阅读其文档。

---

## 2. 技术栈（固定，不要替换）

| 项        | 选型                                                                      | 版本要求                                               |
| -------- | ----------------------------------------------------------------------- | -------------------------------------------------- |
| 语言       | Kotlin                                                                  | 2.0+                                               |
| 最低 SDK   | minSdk 26（Android 8.0）                                                  | 截屏兜底功能仅在 API 30+ 生效，用 `Build.VERSION.SDK_INT` 特性开关 |
| 目标 SDK   | targetSdk 35                                                            |                                                    |
| UI       | Jetpack Compose + Material 3                                            | BOM 最新稳定版                                          |
| 架构       | 单 module MVVM（项目小，禁多 module）                                            |                                                    |
| 数据库      | Room + KSP                                                              | 2.6+                                               |
| 依赖注入     | 手写单例（不引入 Hilt，减少学习面）                                                    |                                                    |
| OCR      | ML Kit Text Recognition v2（中文，离线 bundled）                               | `com.google.mlkit:text-recognition-chinese:16.x`   |
| 图表       | Vico（Compose 图表库）                                                       | 2.x                                                |
| 文件选择     | Activity Result API + Storage Access Framework                          |                                                    |
| Excel 解析 | Apache POI-Android（`org.apache.poi:poi-ooxml` 兼容版，或改用 csv 工具+让用户导出 csv） | 若 POI 集成困难，降级方案见 §8.3                              |
| 构建发布     | GitHub Actions 自动构建 APK                                                 |                                                    |

---

## 3. 总体架构与工程结构

```
采集层（系统服务，Kotlin Service）
 ├─ BillNotificationListenerService   通知监听（主通道，覆盖 90% 场景）
 └─ BillAccessibilityService          无障碍截屏（兜底通道，API 30+）
        │ 原始事件 RawEvent（含来源、包名、文本/截图）
        ▼
解析层（纯 Kotlin，无 Android 依赖，可单元测试）
 ├─ RuleEngine     正则规则匹配 → 金额/商户/收支类型
 ├─ Classifier     关键词 → 分类
 └─ Deduplicator   指纹去重
        │ 解析结果 ParsedBill
        ▼
数据层
 ├─ Room（bills/categories/rules/raw_events 四表）
 └─ BackupExporter（JSON 导入导出）
        ▲
UI 层（Compose，MVVM）
 ├─ 首页流水 / 日历 / 统计 / 设置 四 Tab
 ├─ 确认编辑页（通知或截屏识别后跳转，预填）
 └─ 引导页（首次启动：授予权限）
```

### 包结构（新建工程时照抄）

```
com.shizhang.app/
├─ ShiBillApp.kt                      Application，初始化单例 DB
├─ MainActivity.kt                    单 Activity，承载所有 Compose 页面
├─ capture/
│  ├─ BillNotificationListenerService.kt
│  ├─ BillAccessibilityService.kt
│  └─ CaptureBus.kt        （单例，Service→UI 事件传递，用 SharedFlow）
├─ parse/
│  ├─ RuleEngine.kt
│  ├─ Classifier.kt
│  ├─ Deduplicator.kt
│  └─ model/（RawEvent.kt, ParsedBill.kt, Rule.kt）
├─ data/
│  ├─ db/（AppDatabase.kt, BillDao.kt, CategoryDao.kt, RuleDao.kt, RawEventDao.kt, Entities.kt）
│  └─ backup/（BackupExporter.kt）
├─ ui/
│  ├─ theme/
│  ├─ nav/AppNavHost.kt
│  ├─ home/HomeScreen.kt, HomeViewModel.kt
│  ├─ calendar/CalendarScreen.kt, ...
│  ├─ stats/StatsScreen.kt, ...
│  ├─ confirm/ConfirmScreen.kt, ConfirmViewModel.kt
│  ├─ rules/RulesScreen.kt, ...（规则编辑）
│  └─ onboard/OnboardScreen.kt（引导页）
└─ util/（AmountFormat.kt, TimeUtils.kt）
```

---

## 4. 数据库设计（Room，照此建表）

```sql
-- 账单主表
CREATE TABLE bills (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  amount REAL NOT NULL,              -- 恒为正数，方向由 type 区分
  type TEXT NOT NULL,                -- 'EXPENSE' | 'INCOME'
  category_id INTEGER NOT NULL DEFAULT 0,  -- 外键 categories.id
  account TEXT NOT NULL DEFAULT '微信',    -- '微信'|'支付宝'|'云闪付'|'现金'|'其他'
  merchant TEXT DEFAULT '',          -- 商户名，可空
  note TEXT DEFAULT '',
  occurred_at INTEGER NOT NULL,      -- epoch millis
  source TEXT NOT NULL,              -- 'NOTIFICATION'|'SCREENSHOT'|'IMPORT'|'MANUAL'
  fingerprint TEXT NOT NULL UNIQUE,  -- 去重指纹，见 §6.3
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_bills_time ON bills(occurred_at DESC);

-- 分类表（预置数据见 §6.4）
CREATE TABLE categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  icon TEXT NOT NULL DEFAULT 'category',  -- Material Icons 名
  keywords TEXT NOT NULL DEFAULT '',      -- 逗号分隔，用于自动分类
  sort_order INTEGER NOT NULL DEFAULT 0,
  built_in INTEGER NOT NULL DEFAULT 1     -- 内置分类不可删除
);

-- 解析规则表（用户可编辑，内置微信/支付宝模板）
CREATE TABLE rules (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,                -- 规则显示名，如"微信支付通知"
  app_package TEXT NOT NULL,         -- 匹配的包名，支持逗号分隔多个
  pattern TEXT NOT NULL,             -- Java 正则，含命名捕获组
  priority INTEGER NOT NULL DEFAULT 0,   -- 数值越大越先匹配
  enabled INTEGER NOT NULL DEFAULT 1,
  built_in INTEGER NOT NULL DEFAULT 1
);

-- 原始事件表（审计 + 去重依据，保留 7 天，WorkManager 每日清理）
CREATE TABLE raw_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source TEXT NOT NULL,
  app_package TEXT NOT NULL,
  raw_text TEXT NOT NULL,
  fingerprint TEXT NOT NULL,
  parsed_ok INTEGER NOT NULL,        -- 是否成功解析
  created_at INTEGER NOT NULL
);
```

---

## 5. 采集层详细设计

### 5.1 通知监听（主通道）

`BillNotificationListenerService : NotificationListenerService`：

1. **Manifest 声明**（照抄）：

```xml
<service android:name=".capture.BillNotificationListenerService"
    android:label="拾账-通知监听"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

1. 重写 `onNotificationPosted(sbn: StatusBarNotification)`：
   - 包名不在规则表 `app_package` 集合内 → 直接 return
   - 提取 `sbn.notification.extras` 的 `EXTRA_TITLE`、`EXTRA_TEXT`、`EXTRA_BIG_TEXT`，拼成 `rawText`
   - `postedTime` 作为时间戳，构造 RawEvent 交给解析层
   - **排除逻辑**：`sbn.isOngoing == true` 的常驻通知（如"正在播放"）直接忽略
2. 服务被杀恢复：`onListenerConnected` 时无需特殊处理（系统自动重连），引导页教用户检查开关状态。

**监控的包名（预置，设置页可增删）**：

```
com.tencent.mm          微信
com.eg.android.AlipayGphone  支付宝
com.unionpay            云闪付
com.eg.android.AlipayGphoneRC  支付宝（部分渠道包）
（银行 App 由用户在设置里自行添加包名）
```

### 5.2 无障碍截屏（可选兜底通道，API 30+，**默认关闭**）

> **产品原则：通知监听是唯一主通道，截屏是可选增强**。截屏模式必须经设置页显式开启，默认关闭；关闭状态下 App 不申请无障碍权限、不显示相关入口。

**开关设计**：

- DataStore 键 `screenshot_enabled`，默认 `false`
- 设置页开关「截屏自动记账（实验性）」：
  - 开启 → 弹说明对话框（用途：仅截屏识别支付页；提示无障碍权限的敏感性）→ 用户确认后跳转 `Settings.ACTION_ACCESSIBILITY_SETTINGS`
  - 关闭 → 更新 DataStore，提示用户到系统设置关闭无障碍服务；App 侧立即停用截屏触发
- `screenshot_enabled=false` 时：无障碍服务即使被系统连上也不做任何处理（`onAccessibilityEvent` 直接 return）；前台通知不显示「记一笔」截屏按钮；ConfirmScreen 无变化

`BillAccessibilityService : AccessibilityService`（借鉴 jizhang 的 ScanService.kt 编排）：

1. **触发方式**（两种都实现，均受主开关控制）：
   - 常驻前台通知上一个"记一笔"按钮 → 触发截屏
   - 监听 `TYPE_WINDOW_STATE_CHANGED`：当前前台包名 ∈ 支付包名集合 且 窗口文本包含"支付成功/付款成功/交易成功" → 自动截屏（此自动触发受二级开关 `screenshot_auto_trigger` 控制，默认关闭；仅当主开关开启时可见）
2. 截屏调用 `takeScreenshot()`（API 30+，`AccessibilityService#takeScreenshot`），回调拿 Bitmap
3. Bitmap → ML Kit `TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())` 离线识别 → 拼接全部文本行 → 交给同一套 RuleEngine 解析（复用规则）
4. 截图处理完立即 `bitmap.recycle()`，**不落盘、不缓存原图**
5. **Manifest**：无障碍服务声明 + `canTakeScreenshot="true"` capability + 前台服务 `accessibility` 类型（targetSdk 34+ 必须声明 `FOREGROUND_SERVICE_ACCESSIBILITY` 权限与 `android:foregroundServiceType="accessibility"`）

### 5.3 确认入账交互（核心体验）

解析成功 ≠ 直接入账，流程：

1. 解析成功 → 发出一条带 PendingIntent 的通知：「记一笔：¥25.00 美团外卖（微信）」点击 → ConfirmScreen（预填金额/商户/分类，光标在分类上）
2. 用户点「确认」→ 写入 bills 表 → 通知自动消失（`autoCancel`）
3. 设置项「自动入账（免确认）」默认关闭；开启后解析成功的账单直接入库，通知仅作展示
4. 解析失败（规则不匹配）→ 通知「捕获到支付但无法识别，点击手动补记」→ ConfirmScreen 空白表单 + rawText 附在备注

---

## 6. 解析层详细设计（纯 Kotlin，必须有单元测试）

### 6.1 RuleEngine

```kotlin
class RuleEngine(private val ruleDao: RuleDao) {
    /**
     * 返回 null 表示所有规则都不匹配（非账单通知）。
     * 命名捕获组约定：amount（必填，数字可含千分位逗号）、
     * merchant?（可选）、direction?（可选，"收"/"付"/"支出"/"收入"）
     */
    suspend fun parse(event: RawEvent): ParsedBill?
}
```

匹配顺序：按 priority 降序逐条 `Regex` 全文匹配，第一条命中即返回。

### 6.2 内置规则（初始化时写入 rules 表，用户可改）

```regex
# 规则1：微信支付成功推送（priority=100）
包名 com.tencent.mm
pattern: 微信支付[：:]\s*(?<direction>已支付|已收款|支付成功|收款成功)?[¥￥]?(?<amount>[\d,]+\.?\d*)
# 注：微信推送常见样例 "微信支付：已支付¥25.00"，方向默认 EXPENSE

# 规则2：微信收款（priority=101）
包名 com.tencent.mm
pattern: 微信支付收款[¥￥]?(?<amount>[\d,]+\.?\d*)  → direction=INCOME

# 规则3：支付宝支付通知（priority=100）
包名 com.eg.android.AlipayGphone
pattern: (?:支付宝|支付宝付款|支付成功)[：:]?\s*[¥￥]?(?<amount>[\d,]+\.?\d*)

# 规则4：支付宝收款（priority=101）
包名 com.eg.android.AlipayGphone
pattern: (?:收款|到账)[¥￥]?(?<amount>[\d,]+\.?\d*)  → INCOME

# 规则5：截屏文本通用（priority=10，仅 SCREENSHOT 来源）
pattern: (?:支付成功|付款成功|交易成功)[^\d]{0,20}[¥￥]?(?<amount>[\d,]+\.?\d*)
```



**以上正则基于公开资料整理，M1 里程碑必须用真机通知样本校正后固化**（见 §10 验收）。

**防误判（硬编码在 RuleEngine，不走规则表）**：rawText 含以下任一关键词 → 拒绝解析为账单：  
`待支付`、`支付失败`、`退款`、`正在处理`、`小荷包`、`还款日`、`账单提醒`、`余额不足`

### 6.3 Deduplicator

```
fingerprint = sha1(source + app_package + amount + merchant + occurred_at分钟取整)
```

插入 bills 前查 fingerprint，已存在则丢弃并记 raw_events(parsed_ok=2 重复)。  
同一笔支付 5 分钟内被通知+截屏双通道捕获时，靠分钟取整+金额+商户自然去重。

### 6.4 Classifier 与预置分类

按账单文本（merchant + note + rawText）对 categories.keywords 做包含匹配，命中即分类；未命中 → "未分类"。用户可在规则页编辑 keywords。

预置分类（sort_order 顺序即显示顺序）：

```
餐饮美食: 美团,饿了么,肯德基,麦当劳,星巴克,瑞幸,外卖,餐厅
购物: 淘宝,天猫,京东,拼多多,唯品会
交通出行: 滴滴,高德,地铁,公交,12306,加油
日用百货: 超市,便利店,永辉,盒马
通讯: 话费,流量,移动,联通,电信
居住: 房租,水电,物业,燃气
娱乐: 电影,游戏,视频会员,音乐
医疗: 药店,医院,体检
教育: 课程,书店,培训
人情往来: 红包,转账,礼金
宠物: 宠物,猫,狗
其他支出: （空）
工资收入: 工资,薪水      （type=INCOME）
退款收入: 退款,返现      （type=INCOME）
其他收入: （空）
理财转账: （空，type 默认 EXPENSE，标记不计入支出统计——V1.1 再做，V1 先不做）
未分类: （空，兜底）
```

---

## 7. UI 层（Compose 页面清单）

| 页面                | 内容                                                                                                     | 要点                                                             |
| ----------------- | ------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------- |
| OnboardScreen（首启） | 3 步引导：①说明 ②通知使用权跳转（`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`）③无障碍跳转（可跳过，截屏为可选功能）+ 国产 ROM 保活提示文案 | 用 DataStore 记 `onboarded=true`                                 |
| HomeScreen        | 顶部本月收支汇总卡（红涨绿跌不适用，固定：支出红 #D84A3D、收入绿 #1D9E75）→ 按日分组流水列表（日期头 + 每笔：分类图标/商户/金额）                           | FAB 手动记账 → ConfirmScreen 空白；左滑删除（进回收站，长按恢复，V1 用 Snackbar 撤销即可） |
| CalendarScreen    | 月历视图，每天显示当日支出合计，点某天过滤流水                                                                                | 自己用 Compose 画 7 列网格，不引第三方                                      |
| StatsScreen       | 月份切换 + 收支切换；分类排行（横向进度条：分类名/金额/占比）；月度趋势折线（近 6 月，Vico）                                                   |                                                                |
| ConfirmScreen     | 金额大字输入（默认弹数字键盘）、收支切换 SegmentedButton、分类九宫格选择、账户选择、商户/备注输入、日期（默认现在）                                     | 作为手动记账与自动确认的同一页面                                               |
| RulesScreen       | 规则列表（开关/编辑/新建）、包名监控列表管理、自动入账开关、分类关键词编辑入口                                                               |                                                                |
| SettingsScreen    | 数据导出（JSON 备份 + bills 全量 CSV）、导入备份、微信/支付宝账单文件导入（§8.3）、检查更新、关于与开源致谢（列 MIT 项目）                            |                                                                |

导航：底部 NavigationBar 四 Tab + NavHost 路由 `home/calendar/stats/settings/confirm?id={id}`。

---

## 8. 数据导入导出

### 8.1 备份

JSON 格式 `{"version":1,"exported_at":...,"bills":[...],"categories":[...],"rules":[...]}`，通过 SAF 保存到用户选择的位置；导入时全量替换前先弹确认对话框。

### 8.2 CSV 导出

bills 表全量导出：`日期,类型,分类,账户,商户,金额,备注,来源`，GBK 编码（Excel 兼容）。

### 8.3 微信/支付宝官方账单导入（V1.1，M4 里程碑）

- 微信：下载账单得到 zip（含密码邮件提供）→ 解压 xlsx → POI 读"交易时间/交易类型/交易对方/金额(元)/当前状态"列；**降级方案**：若 POI 集成失败，只支持支付宝 csv + 微信让用户另存为 csv
- 支付宝：csv 为 GBK 编码，直接 BufferedReader(InputStreamReader(fis, "GBK")) 解析，跳过表头前若干行到"交易时间"起始行
- 导入行同样走 fingerprint 去重（IMPORT 来源）

---

## 9. 权限与 Manifest 清单（完整列表）

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />      
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_ACCESSIBILITY" />

<uses-permission android:name="android.permission.INTERNET" />
```

无障碍与通知使用权是特殊权限，只能跳系统设置页由用户手动开，引导页负责教学文案（含 MIUI/ColorOS 等保活设置说明：电池无限制 + 自启动）。

---

## 10. 里程碑与验收标准

### M1：核心链路跑通（最高优先级）

- [ ] 新建 Compose 工程按 §3 包结构搭骨架，Room 四表建好，预置数据写入
- [ ] 通知监听服务 + RuleEngine + 去重 + 通知点击入账全链路
- [ ] **验收：真机（或模拟器）用 `adb shell cmd notification post` 模拟微信支付通知，App 5 秒内弹出确认通知，点击确认后 HomeScreen 出现该笔账单；同一通知重发不重复入账**
- [ ] RuleEngine 单元测试 ≥ 20 个用例（正则命中/不命中/防误判/千分位/去重指纹）

### M2：完整 UI

- [ ] 四 Tab 全部页面 + ConfirmScreen + 引导页
- [ ] 验收：纯手动使用可完整记账、看月度统计、导出备份再导入数据一致

### M3：截屏兜底

- [ ] 无障碍服务 + takeScreenshot + ML Kit 中文 OCR + 通知按钮触发
- [ ] 验收：打开微信支付成功页（任意历史账单详情页截图存图库亦可作为测试素材）→ 点"记一笔"→ OCR 3 秒内返回解析结果进 ConfirmScreen；API<30 设备该入口隐藏
- [ ] ML Kit 模型 bundled 打包后 APK 体积增幅 < 40MB，OCR 全程飞行模式下可用（证明无联网）

### M4：账单导入 + 打磨

- [ ] 支付宝 csv 导入去重；微信 xlsx（或降级方案）；WorkManager 清理 7 天前 raw_events；检查更新
- [ ] 验收：导入支付宝一个月真实账单，与 App 内自动记录的重叠笔正确去重

### M5：发布准备

- [ ] 应用图标/名称、隐私政策页（静态说明"数据仅存本地，无障碍权限仅用于截屏识别"）、开源致谢页
- [ ] GitHub Actions：push tag 自动构建签名 Release APK
- [ ] 隐私红线复查：全项目搜 `http` 确认唯一联网点是检查更新；截图不落盘

---

## 11. 测试用例集（RuleEngine 必须覆盖，示例）

| 输入 rawText                      | 期望                      |
| ------------------------------- | ----------------------- |
| `微信支付：已支付¥25.00`                | EXPENSE, 25.00          |
| `微信支付：已支付￥1,234.50`             | EXPENSE, 1234.50        |
| `微信支付收款￥88.00`                  | INCOME, 88.00           |
| `支付宝支付成功：45.20元`                | EXPENSE, 45.20          |
| `您有一笔退款正在处理`                    | 拒绝（防误判）                 |
| `微信支付：待支付¥99.00`                | 拒绝                      |
| `美团外卖：您的外卖正在配送中`                | 拒绝（无金额规则命中）             |
| 同一通知 3 秒内投递两次                   | 第二次被去重丢弃                |
| OCR 文本 `...支付成功\n¥12.50\n美团...` | EXPENSE, 12.50, 分类=餐饮美食 |

---

## 12. 风险与对策（实现者须知）

1. **通知格式变动**：规则全部数据库化 + UI 可编辑，格式变了改规则不改代码
2. **微信不推支付通知**（部分用户关了通知）：兜底是截屏通道 + 官方账单导入，产品文案要教育
3. **无障碍服务被 ROM 杀**：前台常驻通知 + 引导文案，无法根治，属行业通病
4. **ML Kit 包体**：若 >40MB 增幅不可接受，改用 Play Services 动态下发版（`text-recognition-chinese` 非 bundled 变体），代价是首次识别需联网下载模型
5. **银行 App 通知**：各行格式差异大，V1 不做内置规则，用户自建规则 + 官方短信/账单导入兜底

---

## 13. 给实现 AI 的执行指令

1. 严格按 §3 包结构、§4 建表、§9 权限清单实现，不引入本文档未列出的第三方依赖（除 androidx/kotlin 官方库）
2. 按 M1→M5 顺序交付，每个里程碑完成后列出 §10 中复选框的完成状态与自测证据（命令/截图说明）
3. 所有解析层代码放 `parse/` 包，禁止 import android.*（保证可纯 JVM 单元测试）
4. 借鉴 jizhang 代码时逐文件标注来源；不碰 GPL 项目代码
5. 遇到本文档未覆盖且无法用"最简实现"原则决定的分歧（如某个正则真机不匹配），停下来在交付说明中列出问题清单，不要擅自做产品级决定
