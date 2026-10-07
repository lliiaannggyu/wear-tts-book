package com.tengwear.ttsbookm3e.ui.theme

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme

/**
 * ============================================================================
 *  TtsbookTheme —— 基于**种子颜色**的纯正 M3E 主题
 * ============================================================================
 *
 * 主题的构造完全遵循 Wear Material 3 官方做法：
 *
 *   种子色 (Seed) ──HCT 分析──▶ 6 条官方色阶 ──取固定 tone──▶ 29 个配色角色
 *
 * 也就是说，[ColorScheme] 里的每个角色都不是手工挑的，而是
 * 「官方色阶 + 官方 tone 映射表」唯一确定的产物（见 [seedColorScheme]）。
 * 这样做的好处是任何一颗种子色都能自动得到：
 *   · 前后景对比度 ≥ 4.5（Δtone = 50 保证）
 *   · 主/次/三级色同色相家族、彩度递减，层次自然
 *   · 深色主题基调（背景为 Neutral0，即纯黑，贴合 OLED 手表）
 *
 * ── 实时生效机制 ──
 * 种子色保存于进程级的 [SeedColorState]（Compose 可观察状态）。
 * [TtsbookTheme] 默认订阅该状态，因此任意页面调用 [setSeedColor] 后，
 * **所有已存在的 Activity 都会立刻重组并换色**，无需重启，也无需手动 setContent。
 *
 * @param seedColor 种子颜色。默认订阅 [SeedColorState]，即跟随用户选择。
 * @param animate  是否在种子色切换时做颜色过渡动画。
 * @param content  主题内容。
 */
@Composable
fun TtsbookTheme(
    seedColor: Color? = null,
    animate: Boolean = true,
    content: @Composable () -> Unit
) {
    // 未显式指定种子色时，订阅进程级状态，实现「选色即换肤」。
    // 显式传入时（如预览、局部演示）则固定使用传入值。
    val seed: Color = if (seedColor != null) {
        seedColor
    } else {
        RememberStoredSeed()
        SeedColorState.current
    }

    val target = remember(seed) { seedColorScheme(seed) }
    val scheme = if (animate) target.animated() else target

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        motionScheme = MotionScheme.standard(),
        content = content
    )
}

// ============================================================================
// 进程级种子色状态 —— 实时生效的核心
// ============================================================================

/**
 * 全局种子色状态。
 *
 * 用 Compose 的 [MutableState] 承载，任何读取它的 Composable 都会在值变化时重组，
 * 这正是「选择即生效」的关键：不需要重启 Activity，也不需要重建 setContent。
 *
 * 生命周期与应用进程一致；首次读取时从 SharedPreferences 惰性加载。
 */
object SeedColorState {

    private var loaded = false

    private val state: MutableState<Color> = mutableStateOf(DefaultSeed)

    /** 当前种子色（可直接在 Composable 中读取，会自动订阅变化）。 */
    val current: Color
        get() = state.value

    /** 是否已从磁盘加载过。 */
    fun isLoaded(): Boolean = loaded

    /** 确保已从磁盘加载过（幂等）。 */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val disk = readSeedColorFromDisk(context)
        if (state.value != disk) state.value = disk
    }

    /** 就地更新种子色并标记为已加载（不写盘），用于外部同步。 */
    fun update(color: Color) {
        loaded = true
        if (state.value != color) state.value = color
    }

    /** 失效缓存，下次 [ensureLoaded] 重新读盘。 */
    fun reset() {
        loaded = false
    }
}

/**
 * 让主题状态与磁盘同步的便捷入口。
 *
 * 应在 `setContent` 开头、[TtsbookTheme] 之外调用一次：
 * ```
 * setContent {
 *     RememberStoredSeed()        // 或直接在 Activity.onCreate 里 SeedColorState.ensureLoaded(this)
 *     TtsbookTheme { ... }
 * }
 * ```
 */
@Composable
fun RememberStoredSeed(context: Context = LocalContext.current) {
    // 用 LaunchedEffect 而非直接在组合里读盘：避免"组合期间写入状态"。
    // 应用启动时 TtsBookApplication 已预加载，这里通常是一次空操作。
    LaunchedEffect(Unit) { SeedColorState.ensureLoaded(context) }
}

/**
 * 应用主题种子色：**落盘 + 立即生效**。
 *
 * 这是全应用唯一的换肤入口。调用后：
 *   1. 写入 SharedPreferences，重启后依然保留；
 *   2. 更新 [SeedColorState]，所有正在显示的界面立刻换色。
 */
fun setSeedColor(context: Context, color: Color) {
    val app = context.applicationContext
    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_SEED, color.toHexArgb())
        .apply()
    SeedColorState.update(color)
}

/**
 * 按种子色候选索引换肤（供设置页使用）。
 * 索引越界时自动收敛，非法索引不会导致主题异常。
 */
fun setSeedColorByIndex(context: Context, index: Int) {
    val choices = SeedPaletteChoices.all
    val safe = index.coerceIn(0, choices.size - 1)
    setSeedColor(context, choices[safe].second)
}

/** 当前选中的种子色在 [SeedPaletteChoices.all] 中的下标；无匹配时返回 0。 */
fun currentSeedIndex(context: Context): Int {
    val seed = readSeedColor(context)
    val idx = SeedPaletteChoices.all.indexOfFirst { it.second == seed }
    return if (idx >= 0) idx else 0
}

// ============================================================================
// 颜色过渡动画：让整套配色在切换种子色时平滑插值
// ============================================================================

@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = tween<Color>(durationMillis = 500)

    @Composable
    fun anim(target: Color) =
        animateColorAsState(targetValue = target, animationSpec = spec, label = "seed").value

    return ColorScheme(
        primary = anim(primary),
        primaryDim = anim(primaryDim),
        primaryContainer = anim(primaryContainer),
        onPrimary = anim(onPrimary),
        onPrimaryContainer = anim(onPrimaryContainer),

        secondary = anim(secondary),
        secondaryDim = anim(secondaryDim),
        secondaryContainer = anim(secondaryContainer),
        onSecondary = anim(onSecondary),
        onSecondaryContainer = anim(onSecondaryContainer),

        tertiary = anim(tertiary),
        tertiaryDim = anim(tertiaryDim),
        tertiaryContainer = anim(tertiaryContainer),
        onTertiary = anim(onTertiary),
        onTertiaryContainer = anim(onTertiaryContainer),

        surfaceContainerLow = anim(surfaceContainerLow),
        surfaceContainer = anim(surfaceContainer),
        surfaceContainerHigh = anim(surfaceContainerHigh),
        onSurface = anim(onSurface),
        onSurfaceVariant = anim(onSurfaceVariant),
        outline = anim(outline),
        outlineVariant = anim(outlineVariant),
        background = anim(background),
        onBackground = anim(onBackground),

        error = anim(error),
        errorDim = anim(errorDim),
        errorContainer = anim(errorContainer),
        onError = anim(onError),
        onErrorContainer = anim(onErrorContainer),
    )
}

// ============================================================================
// 种子色的本地持久化
// ============================================================================

private const val PREFS = "ttsbook_theme"
private const val KEY_SEED = "seed_color"

/** 直接从磁盘读取种子色；未设置过或解析失败时返回 [DefaultSeed]。 */
private fun readSeedColorFromDisk(context: Context): Color {
    val stored = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_SEED, null)
    return if (stored.isNullOrBlank()) DefaultSeed else parseColor(stored)
}

/** 读取已保存的种子色。优先取内存状态，避免同进程内读盘与内存不一致。 */
fun readSeedColor(context: Context): Color {
    SeedColorState.ensureLoaded(context)
    return SeedColorState.current
}
