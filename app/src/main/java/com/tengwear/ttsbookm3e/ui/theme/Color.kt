package com.tengwear.ttsbookm3e.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

/**
 * ============================================================================
 *  M3E 配色方案（Color Scheme）
 * ============================================================================
 *
 * 这里的每一个颜色角色都遵循 Wear Material 3 的官方规格：
 * **从同一条色阶上取固定 tone**，从而天然满足对比度与层次要求。
 *
 * 官方角色 ↔ 色阶 ↔ tone 对照表（深色主题，与 M3E ColorTokens 完全一致）：
 *
 *   角色                    色阶            tone
 *   ─────────────────────────────────────────────
 *   primary                 Primary          90
 *   primaryDim              Primary          80
 *   primaryContainer        Primary          30
 *   onPrimary               Primary          10
 *   onPrimaryContainer      Primary          95
 *   secondary               Secondary        90
 *   secondaryDim            Secondary        80
 *   secondaryContainer      Secondary        30
 *   onSecondary             Secondary        10
 *   onSecondaryContainer    Secondary        95
 *   tertiary                Tertiary         90
 *   tertiaryDim             Tertiary         80
 *   tertiaryContainer       Tertiary         30
 *   onTertiary              Tertiary         10
 *   onTertiaryContainer     Tertiary         95
 *   surfaceContainerLow     Neutral          15
 *   surfaceContainer        Neutral          20
 *   surfaceContainerHigh    Neutral          30
 *   onSurface               Neutral          95
 *   onSurfaceVariant        NeutralVariant   80
 *   outline                 NeutralVariant   60
 *   outlineVariant          NeutralVariant   40
 *   background              Neutral           0
 *   onBackground            Neutral         100
 *   error                   Error            80
 *   errorDim                Error            70
 *   errorContainer          Error            30
 *   onError                 Error            10
 *   onErrorContainer        Error            95
 *
 * 下面的 [m3eColorScheme] 提供两种构造方式：
 *  - [M3eBaselineScheme]：使用 M3E 官方基线色阶（与官方 Pixel 手表风格一致）
 *  - [seedColorScheme]  ：由一颗**种子颜色（Seed Color）** 动态推导整套配色
 */

/** 从某条色阶取 tone 对应的颜色。 */
private fun tone(palette: M3eTonalPalette, tone: Int): Color = palette[tone]

/** 官方基线配色方案（等价于 M3E 默认 `ColorScheme()`）。 */
fun m3eBaselineColorScheme(): ColorScheme = ColorScheme(
    primary = tone(M3ePalettes.Primary, 90),
    primaryDim = tone(M3ePalettes.Primary, 80),
    primaryContainer = tone(M3ePalettes.Primary, 30),
    onPrimary = tone(M3ePalettes.Primary, 10),
    onPrimaryContainer = tone(M3ePalettes.Primary, 95),

    secondary = tone(M3ePalettes.Secondary, 90),
    secondaryDim = tone(M3ePalettes.Secondary, 80),
    secondaryContainer = tone(M3ePalettes.Secondary, 30),
    onSecondary = tone(M3ePalettes.Secondary, 10),
    onSecondaryContainer = tone(M3ePalettes.Secondary, 95),

    tertiary = tone(M3ePalettes.Tertiary, 90),
    tertiaryDim = tone(M3ePalettes.Tertiary, 80),
    tertiaryContainer = tone(M3ePalettes.Tertiary, 30),
    onTertiary = tone(M3ePalettes.Tertiary, 10),
    onTertiaryContainer = tone(M3ePalettes.Tertiary, 95),

    surfaceContainerLow = tone(M3ePalettes.Neutral, 15),
    surfaceContainer = tone(M3ePalettes.Neutral, 20),
    surfaceContainerHigh = tone(M3ePalettes.Neutral, 30),
    onSurface = tone(M3ePalettes.Neutral, 95),
    onSurfaceVariant = tone(M3ePalettes.NeutralVariant, 80),
    outline = tone(M3ePalettes.NeutralVariant, 60),
    outlineVariant = tone(M3ePalettes.NeutralVariant, 40),
    background = tone(M3ePalettes.Neutral, 0),
    onBackground = tone(M3ePalettes.Neutral, 100),

    error = tone(M3ePalettes.Error, 80),
    errorDim = tone(M3ePalettes.Error, 70),
    errorContainer = tone(M3ePalettes.Error, 30),
    onError = tone(M3ePalettes.Error, 10),
    onErrorContainer = tone(M3ePalettes.Error, 95),
)

/**
 * 由**种子颜色**生成整套 M3E 配色方案。
 *
 * 这是本项目的默认主题入口：调用方只需给一颗种子色，
 * 就能得到一套色调和谐、对比度达标的 Wear M3 配色。
 *
 * @param seed 种子颜色，例如 `Color(0xFF6750A4)`
 */
fun seedColorScheme(seed: Color): ColorScheme {
    val p = SeedPalette.fromSeed(seed)
    fun c(source: Hct, tone: Double) = p.tone(source, tone)

    return ColorScheme(
        // ---- 主色族 ----
        primary = c(p.primary, 90.0),
        primaryDim = c(p.primary, 80.0),
        primaryContainer = c(p.primary, 30.0),
        onPrimary = c(p.primary, 10.0),
        onPrimaryContainer = c(p.primary, 95.0),

        // ---- 次色族 ----
        secondary = c(p.secondary, 90.0),
        secondaryDim = c(p.secondary, 80.0),
        secondaryContainer = c(p.secondary, 30.0),
        onSecondary = c(p.secondary, 10.0),
        onSecondaryContainer = c(p.secondary, 95.0),

        // ---- 三级色族 ----
        tertiary = c(p.tertiary, 90.0),
        tertiaryDim = c(p.tertiary, 80.0),
        tertiaryContainer = c(p.tertiary, 30.0),
        onTertiary = c(p.tertiary, 10.0),
        onTertiaryContainer = c(p.tertiary, 95.0),

        // ---- 表面 / 描边 ----
        surfaceContainerLow = c(p.neutral, 15.0),
        surfaceContainer = c(p.neutral, 20.0),
        surfaceContainerHigh = c(p.neutral, 30.0),
        onSurface = c(p.neutral, 95.0),
        onSurfaceVariant = c(p.neutralVariant, 80.0),
        outline = c(p.neutralVariant, 60.0),
        outlineVariant = c(p.neutralVariant, 40.0),
        background = c(p.neutral, 0.0),
        onBackground = c(p.neutral, 100.0),

        // ---- 错误色族 ----
        error = c(p.error, 80.0),
        errorDim = c(p.error, 70.0),
        errorContainer = c(p.error, 30.0),
        onError = c(p.error, 10.0),
        onErrorContainer = c(p.error, 95.0),
    )
}

// ============================================================================
// 默认种子颜色
// ============================================================================

/**
 * 应用默认种子色。
 *
 * 取 M3E 官方基线紫色的 **tone 50**（`#7F6DAA`）作为种子：
 * 它的 HCT 为 H≈299.6 / C≈36.7，正是官方 Primary 色阶的代表值，
 * 因此由它推导出的配色与 M3E 官方基线保持同一色相家族，
 * 但彩度可按需调节，避免直接照搬官方常量。
 */
val DefaultSeed = Color(0xFF7F6DAA)

/** 备选种子色，供设置页切换主题用。 */
object SeedPaletteChoices {
    /** M3E 官方基线紫（默认） */
    val M3ePurple = Color(0xFF7F6DAA)

    /** Material 3 经典紫 */
    val M3Purple = Color(0xFF6750A4)

    /** 天蓝 */
    val SkyBlue = Color(0xFF64B5F6)

    /** 青碧 */
    val Teal = Color(0xFF009688)

    /** 琥珀 */
    val Amber = Color(0xFFFFB300)

    /** 玫红 */
    val Rose = Color(0xFFE91E63)

    /** 芳草绿 */
    val Green = Color(0xFF4CAF50)

    /** 全部可选种子 */
    val all = listOf(
        "典藏紫" to M3ePurple,
        "经典紫" to M3Purple,
        "晴空蓝" to SkyBlue,
        "青碧" to Teal,
        "琥珀" to Amber,
        "玫红" to Rose,
        "芳草绿" to Green,
    )
}

// ============================================================================
// 工具
// ============================================================================

/**
 * 将十六进制色号字符串解析为 Color。
 * 支持 `RRGGBB` / `AARRGGBB`（可带 `#` 前缀）。
 * 解析失败时返回 [DefaultSeed]，避免主题整体崩掉。
 */
fun parseColor(hex: String): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        when (clean.length) {
            6 -> Color(0xFF000000L or clean.toLong(16))
            8 -> Color(clean.toLong(16))
            else -> DefaultSeed
        }
    } catch (_: Exception) {
        DefaultSeed
    }
}

/** 将 Color 输出为 `AARRGGBB` 十六进制字符串（大写，无 `#`）。 */
fun Color.toHexArgb(): String {
    val v = this.value.toULong() shr 32
    return (v and 0xFFFFFFFFuL).toString(16).padStart(8, '0').uppercase()
}
