# 拾账 ShiBill

Android 本地自动记账应用：监听支付类通知 + 支付成功页截屏 OCR，自动解析金额与商户，本地存储与统计。

- **零账号、零服务器、零费用**：不调用任何大模型 API，不需要登录
- **数据 100% 本地**：Room 数据库单文件，支持 JSON 备份导出 / 导入
- **识别全部离线**：通知文本正则解析；截屏使用 ML Kit 中文 OCR（离线模型）
- **免 root**：仅使用官方通知监听与无障碍 API

## 功能

| 模块 | 说明 |
| --- | --- |
| 通知自动记账（主通道） | 微信 / 支付宝 / 云闪付支付通知自动解析，通知栏一键确认入账；可开启"自动入账（免确认）" |
| 截屏兜底（可选，Android 11+） | 检测到"支付成功"页面自动截屏识别，或点击常驻通知"记一笔"手动识别；截图不落盘 |
| 流水 / 日历 / 统计 | 按日分组流水（左滑删除可撤销）、月历热力查看、分类排行与近 6 月趋势 |
| 规则引擎（可编辑） | 包名 + 正则规则全部数据库化，格式变化时改规则不改代码 |
| 账单导入导出 | JSON 全量备份 / CSV（GBK）导出；支付宝 CSV、微信 xlsx 官方账单导入并自动去重 |
| 隐私 | 唯一联网点为"检查更新"（GitHub Releases）；无障碍权限仅用于截屏识别 |

## 技术栈

Kotlin 2.0 · Jetpack Compose (Material 3) · Room (KSP) · DataStore · WorkManager · ML Kit Text Recognition v2（中文，bundled）· Vico 图表 · 单 module MVVM · 手写单例注入

- minSdk 26（Android 8.0），targetSdk 35
- 截屏兜底功能仅在 API 30+ 生效（运行时特性开关）

## 构建

```bash
# 单元测试（解析层 ≥ 30 用例）
./gradlew :app:testDebugUnitTest

# Debug APK
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 已签名的 Release APK
# 在仓库根目录创建 keystore.properties：
#   storeFile=release.keystore
#   storePassword=…
#   keyAlias=…
#   keyPassword=…
./gradlew :app:assembleRelease
```

## 发布

推送 `v*` 标签后，GitHub Actions 自动构建签名 Release APK 并上传到 Release。
需配置仓库 Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

## 真机验收（M1 核心链路）

```bash
# 模拟微信支付通知（替换设备序列号）
adb shell cmd notification post -S bigtext -t "微信支付" "tag_shibill" "微信支付：已支付¥25.00"
# 预期：5 秒内弹出「记一笔：¥25.00（微信）」确认通知
# 点击确认 → 首页出现该笔账单；重发同一通知 → 不重复入账
```

## 隐私声明

1. 所有数据仅保存在设备本地，无云同步、无上传。
2. 通知监听仅解析支付类通知文本；原始事件记录 7 天后自动清理。
3. 无障碍权限（默认关闭）仅用于截屏识别，截图不落盘、不联网。
4. 应用唯一联网行为：用户主动点击「检查更新」时访问 GitHub Releases API。

## License

[MIT](LICENSE)
