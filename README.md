# Wear TTS Book (M3E)

Wear OS 离线 TTS 听书应用。在手表上离线朗读 TXT / PDF 书籍，无需联网，不收集任何用户数据。

包名：`com.tengwear.ttsbookm3e`

## 特性

- **纯正 M3E 组件库**：全部 UI 基于 AndroidX Wear Compose Material 3 (`compose-material3` 1.5.0) 重构，遵循 Wear OS 设计语言。
- **实时换肤**：基于 HCT（CAM16 色彩模型）与官方色阶推导的主题系统，选择种子色即进程级实时生效，所有界面同步刷新，无需重启。
- **健壮的文本读取**：按书本实际编码解析正文，章节偏移越界自动收敛并重建，避免 `StringIndexOutOfBoundsException` 类崩溃。
- **低版本兼容**：`minSdk 25`（Wear OS 2），`targetSdk 35`，`compileSdk 35`。
- **存储权限双轨制**：Android 11+ 走 `MANAGE_EXTERNAL_STORAGE`；Android 6–9 走 `READ_EXTERNAL_STORAGE` 运行时权限申请，保证低版本仍可正常读取书籍。
- **TTS 朗读**：内置系统 TTS 引擎朗读，支持章节切换与进度记忆。

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

## 许可

本项目以 **GNU General Public License v3.0 (GPL-3.0)** 发布。详情见 [LICENSE](LICENSE)。

## 隐私

本项目为完全离线的本地应用，不联网、不收集、不上传任何用户数据。

仓库已通过 `.gitignore` 排除所有构建产物与本地配置文件（如 `*.apk`、`*.keystore`、`local.properties`、`.gradle/`、`build/` 等），不会泄露任何个人隐私或签名信息。
