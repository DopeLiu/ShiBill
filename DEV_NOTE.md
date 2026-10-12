# 拾账 ShiBill — 开发与维护说明（DEV_NOTE）

面向接手维护的开发者。假定你熟悉 Kotlin 基本语法，但**不假定你用过 Android / Compose / Room**——每个陌生概念第一次出现时都会解释。

**维护者**：[DopeLiu](https://github.com/DopeLiu) · **仓库**：<https://github.com/DopeLiu/ShiBill>

> 阅读顺序建议：先看 §1–§4 建立整体印象，再看 §5（数据怎么流动，最重要）、§6–§8（各层细节）。§11、§12 是「不要踩的坑」和「不要做的事」，改动前务必过一遍。

---

## 目录

1. [项目定位](#1-项目定位)
2. [快速开始：编译与运行](#2-快速开始编译与运行)
3. [技术栈与冻结基线](#3-技术栈与冻结基线)
4. [目录结构导航](#4-目录结构导航)
5. [核心数据流：一笔支付是怎么变成账单的](#5-核心数据流一笔支付是怎么变成账单的)
6. [数据层](#6-数据层)
7. [采集层](#7-采集层)
8. [解析层](#8-解析层)
9. [UI 层](#9-ui-层)
10. [周边模块：导入 / 导出 / 清理 / 更新检查](#10-周边模块导入--导出--清理--更新检查)
11. [测试与验收](#11-测试与验收)
12. [硬性约定（改代码前必读）](#12-硬性约定改代码前必读)
13. [已知限制与待办](#13-已知限制与待办)

---

## 1. 项目定位

**一句话**：监听系统通知 → 用正则解析出金额 → 分类 → 去重 → 入库（或先弹通知让用户确认）。

架构是「**单 Activity + Compose UI + 本地 Room 数据库 + 一个后台通知监听服务**」。没有服务端、没有账号体系、没有网络依赖（只有手动检查更新才联网）。

设计上刻意选择了**最朴素**的做法：

- **不使用依赖注入框架**（没有 Hilt / Koin）。全局对象集中在一个手写的 `AppContainer` 里，谁需要什么就传什么。
- **不使用 MVVM 之外的分层框架**，没有 Repository / UseCase 层；ViewModel 直接调用 DAO。
- **不新增第三方库**（见 §3）。

这意味着代码量小、好读，但代价是依赖关系靠手工维护。改动时注意别破坏这个极简结构。

---

## 2. 快速开始：编译与运行

### 2.1 前置条件

| 需要 | 版本 / 位置 |
| --- | --- |
| JDK | 17 或更高（Gradle 要求） |
| Android SDK | `local.properties` 里的 `sdk.dir`（本机为 `C:\Users\...\AppData\Local\Android\Sdk`） |
| Gradle Wrapper | **9.1.0**（见 `gradle/wrapper/gradle-wrapper.properties`） |

### 2.2 ⚠️ 本机已知的构建陷阱

**本机没有独立安装 JDK 17，shell 默认也没有 `JAVA_HOME`。** 直接跑 gradle 会报 `ERROR: JAVA_HOME is not set`。用 Android Studio 自带的 JBR（内含 Java 25）即可：

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

**但 Java 25 下 `assembleDebug` / 单测正常，`assembleRelease` 会失败**：AGP 8.7.3 的 lint 不支持 Java 25，
报 `Execution failed for task ':app:lintVitalAnalyzeRelease' > 25.0.3` 或
`Could not initialize class org.jetbrains.uast.UastFacade`。两种处理方式：

```bash
# 方式一（推荐）：本地只验证签名产物，跳过 release 的 lint 任务
./gradlew :app:assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease

# 方式二：改用真正的 JDK 17（如安装 temurin 17 并将 JAVA_HOME 指向它）
```

> **CI 不受影响**：`.github/workflows/release.yml` 用的是 temurin **JDK 17**，lint 可正常运行。
> 因此本地这段失败**不代表 CI 会失败**，不要据此改 `build.gradle.kts`。

### 2.3 常用命令

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # 每个新 shell 都要设

./gradlew :app:assembleDebug        # 打 debug 包 → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # 跑 67 个单元测试
python tools/contrast_audit.py      # 配色对比度验收（12 项，必须全 PASS、退出码 0）
./gradlew :app:assembleRelease      # 正式包（需要 keystore.properties，见 §10.4）
```

改完代码后，最低验证标准是「**编译通过 + 单测全绿**」；改了颜色或主题相关代码，再补跑一次对比度脚本。

---

## 3. 技术栈与冻结基线

版本集中在 `gradle/libs.versions.toml`，**该文件视为冻结基线，不新增依赖**。

| 领域 | 选型 | 版本 |
| --- | --- | --- |
| 语言 / 构建 | Kotlin + AGP + KSP | 2.0.21 / 8.7.3 / 2.0.21-1.0.28 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01（material3 = 1.3.1） |
| 导航 | navigation-compose | 2.8.5 |
| 数据库 | Room（KSP 生成代码） | 2.6.1 |
| 键值存储 | DataStore Preferences | 1.1.1 |
| 后台任务 | WorkManager | 2.9.1 |
| 图表 | Vico | 2.0.0 |
| 测试 | JUnit4 + kotlinx-coroutines-test | 4.13.2 / 1.9.0 |

Android 配置：`applicationId = com.shibill.app`，`minSdk 26`，`targetSdk 35` / `compileSdk 35`，Java 17 目标。**未开启代码混淆**（`isMinifyEnabled = false`），`proguard-rules.pro` 目前是空的占位文件。

---

## 4. 目录结构导航

```
app/src/main/java/com/shibill/app/
├── ShiBillApp.kt              Application 入口 + AppContainer（手写单例容器）
├── MainActivity.kt            唯一的 Activity，承载全部 Compose 页面
├── capture/                   【采集层】通知从系统进来后的第一站
│   ├── BillNotificationListenerService.kt  通知监听服务（唯一系统服务）
│   ├── CapturePipeline.kt                   核心管道：解析→分类→去重→落库
│   ├── CaptureBus.kt                        内存事件总线（Service → UI）
│   ├── CaptureNotifier.kt                   记账提醒通知（待确认/失败/已入账）
│   └── PendingStore.kt                      待确认账单的内存暂存区
├── parse/                     【解析层】纯 Kotlin，无 android.* 依赖，可纯 JVM 单测
│   ├── RuleEngine.kt                        正则规则引擎
│   ├── Classifier.kt                        关键词分类器
│   ├── Deduplicator.kt                      去重指纹（SHA-1）
│   └── model/                               BillType / BillSource / Rule / RawEvent / ParsedBill
├── data/
│   ├── db/                    【数据层】Room
│   │   ├── Entities.kt                      4 张表的实体定义
│   │   ├── AppDatabase.kt                   数据库 + 3 个迁移
│   │   ├── *Dao.kt                          4 个 DAO
│   │   ├── BillWriter.kt                    统一写入入口（带去重）
│   │   └── SeedData.kt                      预置分类与内置规则
│   ├── prefs/SettingsStore.kt               DataStore 设置项
│   ├── backup/                【导入导出】CSV / JSON / xlsx
│   ├── CleanupWorker.kt                     每日清理 7 天前的 raw_events
│   └── GithubReleaseChecker.kt              检查更新（唯一联网点）
├── ui/
│   ├── nav/AppNavHost.kt      路由表 + 底部导航栏
│   ├── home/ stats/ settings/ rules/ confirm/ onboard/   各页面 Screen + ViewModel
│   ├── components/            共享组件（BillRow / GroupCard / SectionTitle / …）
│   └── theme/                 设计令牌（颜色、字号、间距、圆角、动效）
└── util/                      AmountFormat / TimeUtils / Constants

app/src/test/java/com/shibill/app/   单元测试（67 个用例）
app/schemas/                         Room schema 历史（1~4.json，迁移比对依赖，勿删）
tools/contrast_audit.py             配色对比度验收脚本
```

---

## 5. 核心数据流：一笔支付是怎么变成账单的

这是整个 App 的主线，**读懂这张图基本就读懂了项目**。

```
用户用微信付款
      ↓
系统发出「微信支付」通知
      ↓
BillNotificationListenerService.onNotificationPosted()        ← 采集层入口
  ├─ 过滤：常驻通知(isOngoing) / 自己发的通知 / 空文本  → 直接丢弃
  ├─ 门禁①：包名必须 ∈（设置页监控包名 ∪ 启用规则的包名）→ 否则连原始记录都不写
  ├─ 拼接 rawText = 标题 + "\n" + 正文 + "\n" + 大文本     ← 关键！见 §8.4
      ↓
CapturePipeline.process(RawEvent)                             ← 核心管道
  ├─ RuleEngine.parse(rawText)
  │    ├─ 命中防误判词（退款/待支付…）→ 拒绝
  │    ├─ 按优先级逐条正则匹配，第一条命中即用
  │    ├─ 门禁②：规则包名必须与通知包名**精确相等**
  │    └─ 取出金额、(可选)方向、(可选)商户 → ParsedBill
  ├─ 解析失败?
  │    ├─ rawText 含「疑似支付」关键词 → 写 raw_events + 弹「手动补记」通知
  │    └─ 否则静默丢弃（聊天通知在此被挡掉，避免骚扰）
  ├─ Classifier.classify(商户+原文) → 分类 id（未命中 = 未分类）
  ├─ 按指纹查重：已存在 → 写 raw_events(重复) 后结束
  └─ 走向入账：
       ├─ 「自动入账」开启 → BillWriter.insert() → 入库 → 弹「已入账」通知
       └─ 关闭（默认）    → 存入 PendingStore → 弹「记一笔」通知（点击进确认页）
      ↓
用户点击通知 → MainActivity(EXTRA_ROUTE) → ConfirmScreen 预填 → 用户确认 → 入库
```

前台还挂了一条**旁路**：`CaptureBus`（内存 SharedFlow）把事件推给正在运行的首页，首页据此弹 Snackbar（「已捕获一笔支付，去确认」）。**这条旁路与通知是并行的，用户走哪条都行。**

---

## 6. 数据层

### 6.1 四张表

定义在 `data/db/Entities.kt`。枚举字段（`type` / `source`）由 Room 按**枚举名字符串**存储。

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `bills` | 账单主表 | `amount`、`type`(EXPENSE/INCOME)、`category_id`、`account`、`merchant`、`note`、`occurred_at`、`source`、`fingerprint`(唯一索引) |
| `categories` | 分类 | `name`(唯一)、`icon`、`keywords`(逗号分隔)、`sort_order`、`built_in` |
| `rules` | 解析规则 | `name`、`app_package`、`pattern`、`priority`、`enabled`、`built_in` |
| `raw_events` | 原始事件审计 | `raw_text`、`fingerprint`、`parsed_ok`、`created_at` |

`raw_events.parsed_ok` 取值：`0` 解析失败 / `1` 解析成功 / `2` 重复丢弃（常量定义在实体伴生对象）。**这张表只写不读**，供排查问题用，7 天后自动清理（§10.3）。

### 6.2 数据库版本与迁移（重要）

`AppDatabase` 当前版本 **4**，注册了三个迁移：

| 迁移 | 做了什么 |
| --- | --- |
| `MIGRATION_1_2` | 预置分类改名（「餐饮美食」→「餐饮」等），只改数据不改结构 |
| `MIGRATION_2_3` | 删除「无障碍截屏识别」通道的遗留数据：把历史 `source='SCREENSHOT'` 改写为 `MANUAL`，删掉空包名的内置规则 |
| `MIGRATION_3_4` | 放宽 2 条**微信**内置正则（原因见 §8.5） |

**两条铁律**：

1. **改动内置规则 / 预置数据，必须配一个迁移。** 因为 `SeedData.seedIfEmpty()` **只在表为空时写入**——已安装用户表里已经有数据，永远拿不到你的修改，只能靠迁移。
2. **迁移 SQL 必须写死字面量，不得引用 `SeedData`。** 否则将来改 `SeedData` 会连带改变历史迁移的行为，造成不可预期的数据损坏。

每次改版本号并编译后，KSP 会自动在 `app/schemas/` 生成新的 `N.json`。**这个目录要入库**（迁移比对依赖它）。

### 6.3 预置数据

`SeedData.kt` 里有 17 个分类 + 4 条内置解析规则，在 App 启动时（`ShiBillApp.onCreate`）幂等写入。

- 分类按 `sort_order` 决定显示顺序；`incomeCategoryNames = {工资, 退款, 其他收入, 理财}` 用来判定分类属于收入还是支出（确认页据此过滤可选分类）。
- `Classifier.UNCATEGORIZED_NAME = "未分类"` 是收支共用的兜底分类。

### 6.4 统一写入入口

`BillWriter.insert(bill, dedup = true)` 是**所有**账单落库的唯一通道：先按 `fingerprint` 查重，重复则返回 `null`。

- 自动通道（通知 / 导入）用 `dedup = true`。
- **手动记账和「撤销删除」用 `dedup = false`**——用户意图优先（手动记账的指纹里带了一个时间戳盐值，见 §8.6）。

---

## 7. 采集层

### 7.1 通知监听服务

`BillNotificationListenerService` 继承系统的 `NotificationListenerService`，需要在 Manifest 声明 `BIND_NOTIFICATION_LISTENER_SERVICE` 权限，并由用户在系统设置中手动授权。

`onNotificationPosted` 里的四道前置过滤（缺一不可）：

1. `isOngoing` 为真 → 常驻通知（音乐播放、充电提示），丢弃。
2. 包名等于自身 → 丢弃（避免自己弹的记账提醒被自己再解析一次）。
3. **门禁①**：包名不属于「设置页监控包名 ∪ 启用规则的包名」→ 直接 `return`，**连 `raw_events` 都不写**。
4. 拼接后的 `rawText` 为空 → 丢弃。

> 这里为了读 DataStore 和规则表，用了 `CoroutineScope(SupervisorJob() + Dispatchers.Default)`，因为 `onNotificationPosted` 是同步回调，不能在主线程做 IO。`onDestroy` 里要 `scope.cancel()`。

### 7.2 核心管道 CapturePipeline

`process(event)` 的顺序**不能随意调整**：

1. 解析（`RuleEngine.parse`）→ 失败走 `handleUnparsed` 并结束。
2. 分类（`Classifier`）。
3. 查重（按指纹）→ **先写 `raw_events` 审计记录，再决定是否跳过**，保证「重复」这件事本身有迹可循。
4. 读 `settingsStore.autoCommit` 分流：入库 or 暂存待确认。

`handleUnparsed` 里有个**降噪门槛**：只有 `Constants.PAYMENT_HINT_KEYWORDS`（支付/付款/收款/扣款…）命中才提示手动补记。否则微信聊天这类通知会造成通知骚扰和审计噪声。

### 7.3 事件总线与通知

- `CaptureBus`：`MutableSharedFlow(extraBufferCapacity = 16)`，`tryEmit` 不阻塞。**只在 App 进程存活时有效**，进程被杀后首页收不到——这是设计上接受的（通知仍在）。
- `CaptureNotifier`：三种通知走同一个渠道 `capture_confirm`。通知 id 用 `fingerprint.hashCode()`，所以同一笔账单不会重复弹。点击通知的 `PendingIntent` 带 `EXTRA_ROUTE`，形如 `confirm?pending=<id>&raw=<原文>`，由 `MainActivity` 转交给 NavHost。

### 7.4 待确认账单暂存 PendingStore

**内存** `ConcurrentHashMap`，进程被杀即丢失。这是刻意的取舍：不引入数据库持久化。

丢失后的兜底：通知本身携带了 `raw` 原文（最多 500 字），确认页会退化成「空白表单 + 原文填进备注」，用户仍能手动补完。

---

## 8. 解析层

解析层整个 `parse/` 包**不 import 任何 `android.*`**，因此可以跑纯 JVM 单元测试——这是刻意设计，务必保持。

### 8.1 RuleEngine：匹配流程

`parse(event)` 的判定顺序：

1. 文本为空 → `null`。
2. 命中 `REJECT_KEYWORDS`（待支付、支付失败、退款、正在处理、小荷包、还款日、账单提醒、余额不足）→ `null`。这层是**硬编码、不走规则表**的防误判。
3. 按 `priority` 降序、`id` 升序排序，逐条匹配，**第一条命中即返回**。
4. 单条规则内部：包名精确匹配 → 正则编译（失败则跳过该条，不影响其他规则）→ 必须有 `amount` 命名组 → 金额 `> 0`。
5. 商户取 `merchant` 命名组（可选）、方向取 `direction` 命名组（可选）。

**命名捕获组约定**（写规则时必须遵守）：

| 组名 | 必需 | 说明 |
| --- | --- | --- |
| `amount` | ✅ 必需 | 金额，允许千分位逗号（`[\d,]+\.?\d*`） |
| `direction` | 可选 | 方向词，含「收」或「到账」判为收入，否则支出 |
| `merchant` | 可选 | 商户名 |

读取命名组的 `namedGroupOrNull` 是必要的：正则里没有该命名组时，`MatchResult.groups[name]` 会抛 `IllegalArgumentException`，而「规则不含 merchant 组」是合法用法。

### 8.2 包名匹配规则（易错点）

`ruleMatchesPackage` 用的是**精确相等**（`rule.packages().contains(pkg)`），其中 `packages()` 会把 `app_package` 按逗号拆成多包名。

- **`app_package` 为空 = 不匹配任何事件**（不是"匹配全部"）。
- 支付宝的通用包名匹配（`Accounts.forPackage` 里的 `startsWith`）**只用于账户归属**，不参与规则匹配。所以支付宝渠道包 `com.eg.android.AlipayGphoneRC` 虽然被默认监控，却没有规则能命中它 → 解析必失败 → 走「手动补记」。这是已知现状。

### 8.3 Classifier：分类

按 `sort_order` 顺序遍历分类，把 `keywords` 按逗号拆开做**包含匹配**（忽略大小写），第一个命中即返回；全不命中则返回「未分类」。

分类输入文本 = `merchant + " " + rawText`。

⚠️ **内置分类关键词全是商户名**（美团、饿了么、淘宝…）。而通知里通常没有商户（见 §8.5），所以**自动捕获的账单大概率落到「未分类」**。要改善只能靠用户自己补关键词，或后续让规则捕获 `merchant`。

### 8.4 rawText 是拼接产物（改规则前必读）

监听服务把通知的**标题 + 正文 + 大文本**用 `\n` 拼接（去空、去重）后才交给正则。所以：

- 微信通知标题恒为「微信支付」，消费正文不带品牌词 → `rawText` 形如 `微信支付\n已支付¥10.00`。
- **规则必须容忍「品牌词后面是换行」**，不能假设品牌词和金额在同一行。

另外，服务只读了 `EXTRA_TITLE` / `EXTRA_TEXT` / `EXTRA_BIG_TEXT`，**没有读** `EXTRA_SUB_TEXT` 和 `EXTRA_TEXT_LINES`（InboxStyle 多行摘要）。如果发现某些通知读不到内容，可以从这里排查。

### 8.5 四条内置规则（写法刻意不同，不是笔误）

`SeedData.rules` 中的 4 条：

| 名称 | 包名 | 优先级 | pattern |
| --- | --- | --- | --- |
| 微信收款 | com.tencent.mm | 101 | `微信支付\s*(?<direction>收款)\s*[¥￥]?\s*(?<amount>[\d,]+\.?\d*)` |
| 支付宝收款 | com.eg.android.AlipayGphone | 101 | `(?<direction>收款\|到账)[¥￥]?(?<amount>[\d,]+\.?\d*)` |
| 微信支付通知 | com.tencent.mm | 100 | `微信支付[：:]?\s*(?:(?<direction>已支付\|已付款\|成功支付\|支付成功\|付款成功\|已收款\|成功收款\|收款成功)\s*)?[¥￥]?\s*(?<amount>[\d,]+\.?\d*)` |
| 支付宝支付通知 | com.eg.android.AlipayGphone | 100 | `(?:支付宝\|支付宝付款\|支付成功)[：:]?\s*[¥￥]?(?<amount>[\d,]+\.?\d*)` |

**为什么微信规则的冒号必须写成可选 `[：:]?`**：旧写法 `微信支付[：:]` 把冒号写死，而真实通知是「标题行 + 正文行」结构（`微信支付\n已支付¥10.00`），换行处没有冒号 → 整笔解析失败、永不入账。这就是 `MIGRATION_3_4` 存在的原因。

**为什么支付宝规则不动**：它的冒号本来就是可选的，锚点集合已覆盖已知形态。这正好与微信形成对照——**微信会漏、支付宝不漏，根因就在冒号是否可选**。

**空白只能用 `\s*` 放宽，禁止改成 `[^\d]{0,n}` 之类的模糊跳过**，否则聊天文本里的数字会被误判入账（有单测守着这条）。

**内置规则的 pattern 中不存在 `merchant` 命名组**，所以自动捕获的商户恒为空串，`BillEntity.merchant = ""`。

### 8.6 Deduplicator：去重指纹

```
fingerprint = SHA1( appPackage | 金额(保留两位小数) | 商户 | occurred_at分钟取整 )
```

两个刻意的设计：

1. **不含 `source` 字段**。这样同一笔支付被「通知」和「导入」两条通道捕获时能自然去重（否则跨通道指纹必然不同）。
2. **手动记账传入 `manualSalt = now`**，使指纹唯一，允许用户在同一分钟内录入两笔相同金额的账单。

由此带来一个**已知缺陷**：商户恒空时指纹退化成「包名 + 金额 + 分钟」，**同一分钟内、同包名、同金额的两笔真实交易会被误合并**。

---

## 9. UI 层

### 9.1 导航结构

单 Activity（`MainActivity`）+ 单 NavHost（`AppNavHost`）。

- 底部导航 **3 栏**：流水（home）/ 统计（stats）/ 设置（settings）。（日历页已删除，月维度职能并入统计页。）
- 二级页面：规则（rules）、分类关键词（categories）、确认入账（confirm）、引导（onboard）。
- 确认页一个 `composable` 承载三种入口：`confirm`（空白手动记账）/ `confirm?billId=`（编辑）/ `confirm?pending=&raw=`（预填）。

启动目的地由 `SettingsStore.onboarded` 决定；未完成引导则从 `onboard` 开始。

### 9.2 设计令牌集中在 `ui/theme/`

**所有颜色、字号、间距、圆角、动画参数都必须引用令牌，不得在页面里硬编码。**

| 文件 | 内容 |
| --- | --- |
| `Color.kt` | 品牌色 `TealPrimary #0F766E` + 4 个收支语义色 |
| `Theme.kt` | 完整的 Material 3 亮/暗色板（35 个 color role 全覆盖） |
| `SemanticColors.kt` | 收支语义色 `MaterialTheme.semantic.expense / .income` |
| `CategoryPalette.kt` | 17 个分类图标 → 9 个色族 → 亮/暗两套取色 |
| `Type.kt` | 字阶 `AppTypography` + 金额专用 `AmountStyle` |
| `Spacing.kt` | `AppSpacing`（xs3 / sm6 / md10 / lg14 / xl16 / xxl20 / screenH14 / rowV10 / card10） |
| `Shape.kt` | `AppShapes`（4 / 8 / 12 / 16 / 24） |
| `Motion.kt` | `MotionTokens`（动画时长、缓动曲线、按压比例） |

⚠️ **全站已按用户要求整体缩小约一档**（相对最初设计文档）。当前数值就是"正确值"，**不要按旧文档改回去**。

⚠️ **数字一律用系统默认字体**（`SansSystem`）。JetBrains Mono 的 `0` 字符内部带点，用户明确表示丑，已弃用。`MonoDigits` 现在**只**用于规则页的正则预览，所以字体文件仍保留在 `res/font/`。

⚠️ **收支语义色随主题切换**（浅色 `#B3261E`/`#0F6E4A`，深色 `#FFB4AB`/`#6FDCA4`），这是对原设计「固定色」的**刻意偏离**，原因是固定红在深色下对比度不达标。

### 9.3 共享组件（`ui/components/`）

| 组件 | 用途 |
| --- | --- |
| `GroupCard` | 统一的「一组内容」卡片容器（含水平边距） |
| `RowDivider` | 卡内分割线（自动左右留白） |
| `SectionTitle` | 分区标题（设置页与规则页原本各写了一份且颜色不一致，已统一） |
| `BillRow` | 流水行（分类徽标 + 商户/副标题 + 金额） |
| `CategoryBadge` | 圆形分类徽标 |
| `AmountText` | 带方向符号的金额文本（红/绿随主题） |
| `AnimatedAmount` | 会「计数」的金额（仅用于单个 Text，**禁止用于列表项**，每帧重组） |
| `InfoBanner` | 页面内信息条 |
| `EmptyHint` | 空列表占位 |
| `ConfirmDialog` | 通用确认弹窗 |
| `PressableCard` | 按压缩放到 0.98 的卡片（**当前全项目未被引用**，属遗留死代码） |

**各页面不得再写私有的分区标题或卡片容器**，一律用上面的共享组件。

### 9.4 Compose 使用陷阱（都踩过，逐条记住）

1. **`ListItem` 不要强压高度。** material3 1.3.1 的高度 = `max(传入约束, 类型下限, 内边距 + 内容)`。带 `supportingContent` 的双行下限是 72dp；一旦把高度压到 56dp，副标题折成两行就会溢出被裁掉。
   **正确做法**：把副标题 `Text` 并入 `headlineContent` 的 `Column`，让 ListItem 判定为「单行」（下限 56dp），且不强制高度时**内容多会自动长高，结构上不可能裁切**。设置页、规则页、分类关键词页均已这样写。

2. **带浮动标签的 M3 输入框不要强压高度。** `OutlinedTextField` 内部上下内边距固定 16dp，外层给 `height(48.dp)` 会把输入文字挤到只剩 16dp 而裁切。保持 M3 默认高度（≈56dp，其 `defaultMinSize` 是**下限**，内容更高时容器自动长高）。

3. **`AnimatedContent` / `Box` 的 content lambda 是重叠布局。** 直接写多个同级 `Text`/`Spacer` 会让它们全部叠在一起（曾导致引导页文字挤成一团）。必须显式包一层 `Column` / `Row`。只有 `SwipeRevealDelete` 的删除按钮遮挡是刻意如此。

4. **在 `LazyColumn` 的 `item` 内部用 `forEach` 渲染可增删的行，必须给每行加 `key(id)`。** 否则行内的 `remember` / `Animatable` 状态会按**位置**复用，出现「左滑删除按钮串到下一项」的回归。

5. **`SegmentedButton` 固定高度要留余量。** 内容 = 文字行高 + 上下内边距 8dp×2。13sp 字阶下内容恰为 34dp，故取 **36dp**；取 34dp 会零余量、字体度量稍偏即裁切。

6. **框架硬约束、不要尝试压缩**：`TopAppBar` 64dp、`NavigationBar` 80dp。

### 9.5 动效

6 项动效全部走 `MotionTokens`，禁止在调用点硬编码时长或曲线：

- M1 日分组卡片增删（`Modifier.animateItem()`）——**只加在日分组卡片上，不加在含左滑手势的流水行上**（避免位置动画与 `draggable` 竞争）。
- M2 金额计数（`AnimatedAmount`）。
- M3 页面切换（NavHost 的 enter/exit/popEnter/popExit）。
- M4 按压缩放（`PressableCard`）。
- M5 统计页进度条生长。
- M6 引导页步骤点宽度 + 内容横向滑入。

---

## 10. 周边模块：导入 / 导出 / 清理 / 更新检查

### 10.1 导入

| 导入器 | 输入 | 要点 |
| --- | --- | --- |
| `AlipayCsvImporter` | 支付宝官方 CSV | **GBK 编码**；先定位含「交易时间」的表头行；只导入收/支 ∈ {收入, 支出} 且状态非「关闭/失败」的行 |
| `WeChatXlsxImporter` | 微信官方 xlsx | 用自研的 `XlsxReader` 读取；金额形如 `¥25.00` 需剥掉货币符号；收/支为 `/` 的行跳过 |

两者都：用**支付宝/微信的真实包名**生成指纹（便于与自动记账交叉去重）、`source = IMPORT`、分类输入 = `商户 + 备注`。

`XlsxReader` 是**零第三方依赖**的最小 xlsx 读取器（`ZipInputStream` + DOM），替代了 Apache POI（POI 在 Android 上依赖大量 `java.awt`/xmlbeans 类，集成脆弱且显著增大包体）。它已按防御 XXE 的方式配置了 XML 解析。

### 10.2 导出

- `BackupExporter`：JSON 全量备份（`version` + `bills` + `categories` + `rules`）。**导入是全量替换**，调用方（设置页）必须先弹二次确认；导入在单个 Room 事务内完成。
- `CsvExporter`：账单 CSV，表头 `日期,类型,分类,账户,商户,金额,备注,来源`，**GBK 编码**（Excel 中文兼容）。

### 10.3 数据清理

`CleanupWorker` 由 `ShiBillApp` 用 WorkManager 注册为**每日一次**的周期任务，删除 `raw_events` 中 7 天前的记录。保留天数在 `Constants.RAW_EVENT_RETENTION_DAYS`。

### 10.4 检查更新与发布

- `GithubReleaseChecker` 请求 `https://api.github.com/repos/{UPDATE_REPO}/releases/latest`，做点分数字的版本比较，**静默失败**（网络异常返回 `null`，不打扰用户）。
  ✅ `Constants.UPDATE_REPO` 已设为真实仓库 `DopeLiu/ShiBill`。**更换仓库或转移所有权时记得同步改这里**，否则检查更新会静默失效。
  ⚠️ **前置条件：该仓库必须是 public 且至少发布过一个 Release，检查更新才能生效。**
  `GithubReleaseChecker` 用的是**匿名**请求，非 200 一律返回 `null`（表现为「已是最新版本（或暂时无法连接更新服务）」）。
  因此：仓库为 private、仓库尚无任何 Release、或尚未推送过 `v*` 标签时，该功能都会**静默失效且不报错**。
  实测状态（2026-10-13）：仓库已设为 **public**，匿名访问 `api.github.com/repos/DopeLiu/ShiBill` 返回 **200**；
  但 `releases/latest` 仍返回 **404**（尚无 Release），故**在发布首个 `v*` 标签之前，检查更新仍不会真正生效**。
- 签名：仓库根目录存在 `keystore.properties` 时自动启用 release 签名。**这两个文件都不入库**（`.gitignore` 已覆盖 `*.keystore` / `keystore.properties`）：
  - `release.keystore` —— PKCS12 密钥库，别名 `shibill`，有效期 30 年
  - `keystore.properties` —— 含 `storeFile` / `storePassword` / `keyAlias` / `keyPassword`
- CI：`.github/workflows/release.yml` 在推送 `v*` 标签时跑单测 → 构建 release APK → 上传到 GitHub Release。
  签名材料通过 **4 个 GitHub Secrets** 注入（工作流会现场生成 `release.keystore` 与 `keystore.properties`）：

  | Secret | 内容 |
  | --- | --- |
  | `KEYSTORE_BASE64` | `release.keystore` 的 Base64 编码 |
  | `KEYSTORE_PASSWORD` | 密钥库口令 |
  | `KEY_ALIAS` | 密钥别名（`shibill`） |
  | `KEY_PASSWORD` | 密钥口令 |

  ⚠️ **这 4 个 Secret 缺失时不要推 `v*` 标签**：工作流仍会生成一个空的 `release.keystore`，
  `keystore.properties` 因文件存在而生效，最终在签名步骤失败、Release 不会产出。
  发版前可用 API 自查：`GET /repos/{owner}/{repo}/actions/secrets`（只返回名称，不返回值）。
  ⚠️ **签名私钥一旦丢失，后续版本将无法覆盖安装**（除非用户卸载重装）。务必备份 `release.keystore` 与口令。

---

## 11. 测试与验收

单元测试共 **67 个用例**，全部是纯 JVM 测试（`RuleEngine` 不依赖 Android，可脱离设备运行）。

| 测试文件 | 用例数 | 覆盖内容 |
| --- | --- | --- |
| `parse/RuleEngineTest.kt` | 36 | 4 条内置规则的命中与拒绝、金额解析、方向判定、包名匹配、防误判、微信两行式通知等 |
| `parse/ClassifierTest.kt` | 10 | 关键词分类、优先级、未分类兜底 |
| `parse/DeduplicatorTest.kt` | 10 | 指纹构成、同分钟合并、跨通道去重、手动盐值 |
| `util/UtilFormatTest.kt` | 11 | 金额格式化/解析、时间格式化与解析 |

`parse/TestDoubles.kt` 提供内存版 `FakeRuleDao` 等替身，让测试走**生产路径**（`engine.parse(event)`）而非纯函数重载。

### 验收命令

```bash
./gradlew :app:testDebugUnitTest      # 全绿：failures=0 errors=0
python tools/contrast_audit.py        # 12/12 PASS，退出码 0
```

### 手工验收：模拟一条微信支付通知

不需要真的付钱，用 adb 直接伪造一条通知即可跑通主链路（记得先在 App 里开启通知使用权）：

```bash
adb shell cmd notification post -S bigtext -t "微信支付" "tag_shibill" "微信支付：已支付¥25.00"
# 预期：数秒内弹出「记一笔：¥25.00（微信）」；点击确认 → 首页出现该笔账单
# 再发一次同样的通知 → 不应重复入账（指纹去重生效）
```

也可以抓真实通知的原文，用于核对规则是否覆盖：

```bash
adb shell dumpsys notification --noredact | grep -A 20 com.tencent.mm
```

`contrast_audit.py` 校验亮/暗两套主题的 12 组关键配色对是否满足 WCAG 2.1 正文对比度 ≥ 4.5:1。**改动主题颜色后必须重跑**。

### 改测试的规矩

- **不得修改已有用例**（它们是防回归的护栏），只允许新增。
- 唯一例外：**删除某个功能时**，可以同步退役该功能专属的用例。
- 一个真实教训：曾试图为「推测的支付宝通知形态」放宽正则并写用例，结果用例失败 → 说明该形态是臆测。**不要为猜测的文案去放宽正则**，改为显式断言「当前不解析」的已知缺口。

---

## 12. 硬性约定（改代码前必读）

**功能边界**

1. **「无障碍截屏识别」通道已于 2026-10-13 整体下线，不要复活它。** 相关文件、Manifest 声明、ML Kit 依赖、常量、字符串、内置规则均已清理完毕。当前只有通知监听这一条自动通道。
2. 规则的 `app_package` 为空 = **不匹配任何事件**（必填），不要恢复「空包名通配」的旧语义。

**数据安全**

3. **改内置规则 / 预置数据，必须配 Room 迁移**，且迁移内固化字面量、不得引用 `SeedData`。
4. 改数据库结构时，版本号必须递增，并保留历史 `app/schemas/*.json`（入库）。

**解析层**

5. `rawText` 是「标题 + 换行 + 正文 + 换行 + 大文本」的拼接产物，规则必须容忍换行。
6. 正则里的空白只能用 `\s*` 放宽，**禁止**用 `[^\d]{0,n}` 之类的模糊跳过。
7. 内置规则的冒号写法差异（微信必须可选、支付宝本就可选）是**刻意的**，不要"统一"。
8. 解析层保持纯 Kotlin，不要 import `android.*`（否则失去可纯 JVM 单测的能力）。

**UI**

9. 界面尺寸 / 字号 / 间距 / 圆角沿用当前令牌值，**不要按旧设计文档改回去**（全站已整体缩小一档）。
10. 数字用系统默认字体，不要把 `MonoDigits` 用回金额。
11. 不要给 `ListItem` 和带浮动标签的 `OutlinedInput` 强压高度。
12. `AnimatedContent` / `Box` 的多子节点必须包一层 `Column` / `Row`。
13. `LazyColumn` item 内 `forEach` 渲染的行必须带 `key(id)`。

**工程**

14. **不新增第三方依赖**（`gradle/libs.versions.toml` 是冻结基线）。确有需要先与维护者确认。
15. 保持现有代码风格与目录结构，不顺手重构无关文件。发现无关死代码或潜在问题时**只说明，不擅自改动**。

---

## 13. 已知限制与待办

按影响程度排序：

1. **自动捕获的账单商户恒为空。** 4 条内置规则都没有 `merchant` 命名组，导致：列表主标题退化为分类名或「未分类」、分类几乎必然落到「未分类」、去重指纹退化为「包名+金额+分钟」。**这是当前最影响体验的问题**，后续可考虑在规则里捕获商户。
2. **去重可能误合并真实交易。** 同一分钟内、同包名、同金额的两笔真实交易会被 L1 指纹判为同一笔（详见 §8.6）。
3. **支付宝部分通知形态解析不了。** 正文不带品牌词且首词非锚点的形态（如 `支付宝通知\n已付款45.20元`）不解析，此行为被 `RuleEngineTest` 用例 38 **显式锁定为已知缺口**——放宽前需先拿到真机通知原文。
4. **`raw_events` 表只写不读。** 全仓只有 `CleanupWorker` 和 DAO 引用它，**没有任何查看界面**。排查线上问题时需要自行 `adb` 拉库。
5. **通知只读三个字段。** 未读 `EXTRA_SUB_TEXT` / `EXTRA_TEXT_LINES`，某些通知形态可能读不全。
6. **跨渠道重复记账**（微信 + 银行各一条通知）：默认配置下**不会**发生——银行 App 既不在监控集合内、也没有规则。只有用户同时「把银行包名加进监控」且「新建了对应规则」才可能触发。
7. **`PressableCard` 全项目未被引用**，属遗留死代码（仅说明，未删除）。
8. `versionName = 0.1.0`，`GithubReleaseChecker` 只做点分数字比较；若要引入 `-alpha` / `-beta` 后缀，需先扩展其版本比较逻辑。
9. 根目录的 `ui-directions.html` / `.png` 与 `app-icon.png` 是历史设计稿归档，非运行时代码。

---

*最后更新：2026-10-13*
