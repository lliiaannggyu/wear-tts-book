# Wear TTS Book (M3E)

Wear OS 离线 TTS 听书应用。在手表上离线朗读 TXT / PDF 书籍，无需联网，不收集任何用户数据。

包名：`com.tengwear.ttsbookm3e`

## 特性

- **纯正 M3E 组件库**：全部 UI 基于 AndroidX Wear Compose Material 3 (`compose-material3` 1.5.0) 重构，遵循 Wear OS 设计语言。
- **实时换肤**：基于 HCT（CAM16 色彩模型）与官方色阶推导的主题系统，选择种子色即进程级实时生效，所有界面同步刷新，无需重启。
- **健壮的文本读取**：按书本实际编码解析正文，章节偏移越界自动收敛并重建，避免 `StringIndexOutOfBoundsException` 类崩溃。
- **测量式分页**：正文按真实字宽 / 行高排版分页，不再「每页固定 N 个字符」硬切；页码与章节内字符偏移可互转，听书自动翻页 / 自动翻章节与朗读进度严格同步，并支持朗读行高亮。
- **低版本兼容**：`minSdk 25`（Wear OS 2），`targetSdk 35`，`compileSdk 35`。
- **存储权限双轨制**：Android 11+ 走 `MANAGE_EXTERNAL_STORAGE`；Android 6–9 走 `READ_EXTERNAL_STORAGE` 运行时权限申请，保证低版本仍可正常读取书籍。
- **TTS 朗读**：内置系统 TTS 引擎朗读，按句分块送入引擎，支持章节切换、进度记忆与自动连章播放。

## 技术栈

| 项目 | 版本 |
|---|---|
| AGP | 8.6.0 |
| Kotlin | 1.9.25 |
| Wear Compose Material 3 | 1.5.0 |
| Compose BOM | 2024.06.00 |
| minSdk / targetSdk / compileSdk | 25 / 35 / 35 |

## 构建

### 环境要求

- **JDK 17**（Gradle 构建时使用，请确保 `JAVA_HOME` 指向 JDK 17）。
- Android SDK（含 Wear OS 模拟器或真机用于调试）。

### 构建命令

```bash
# 调试包
./gradlew :app:assembleDebug

# 发布包（会执行 lintVitalRelease，需通过 NewApi 检查）
./gradlew :app:assembleRelease
```

若系统 `JAVA_HOME` 未正确指向 JDK 17，可在命令前显式指定：

```bash
JAVA_HOME="/path/to/jdk-17" ./gradlew :app:assembleDebug
```

## 移植来源与许可

阅读页的**文本显示 / 分页 / 翻页**逻辑移植自以下开源项目，并针对本项目的
Compose for Wear OS (M3E) 技术栈与 TTS 听书模型做了适配：

| 来源 | 许可 | 移植内容 |
|---|---|---|
| [Yang-Jia/wearos-novel-reader](https://github.com/Yang-Jia/wearos-novel-reader) | MIT | `Pagination.compute / computeRanges`（按真实行高切页）、`PageView` 的 `StaticLayout` 真实测量排版思路、以 `Int.MAX_VALUE` 表示「章末」的翻章约定 |
| [FredApps/wBooks](https://github.com/FredApps/wBooks) | GPL-3.0 | 圆屏安全边距（`WatchSafeArea`）的处理思路 |

对应实现：`app/src/main/java/com/tengwear/ttsbookm3e/reader/Pagination.kt` 与
`TextPager.kt`。MIT 与 GPL-3.0 兼容，本项目自身的 GPL-3.0 许可不变。

## 修复记录

- **闪退**：前台服务先 `startForeground` 再初始化 TTS（原先 TTS 构造异常会让
  `startForeground` 永不执行，触发 `ForegroundServiceDidNotStartInTimeException`）；
  TTS 引擎包名校验与构造异常兜底；`BookManager` 在 Application 中统一初始化并
  容忍损坏的 JSON；自定义章节关键词做正则转义；冷启动恢复书籍时的文件 IO 异常兜底；
  章节索引越界收敛；补充 Android 11+ 的 TTS 引擎 `<queries>` 声明。
- **朗读**：整章一次性 `speak` 改为按句分块排队，规避引擎文本长度上限；
  `onRangeStart` 的相对偏移换算为章节全局偏移；用播放代次作废旧队列回调，
  避免切章时误触发「章节完成」而连跳。
- **翻页 / 自动翻章节**：见上文「测量式分页」。TTS 进度 → 页码 → 页末 / 章末
  → 切下一章并继续朗读，全程由同一套字符偏移表驱动。

## 许可

本项目以 **GNU General Public License v3.0 (GPL-3.0)** 发布。详情见 [LICENSE](LICENSE)。

## 隐私

本项目为完全离线的本地应用，不联网、不收集、不上传任何用户数据。

仓库已通过 `.gitignore` 排除所有构建产物与本地配置文件（如 `*.apk`、`*.keystore`、`local.properties`、`.gradle/`、`build/` 等），不会泄露任何个人隐私或签名信息。
