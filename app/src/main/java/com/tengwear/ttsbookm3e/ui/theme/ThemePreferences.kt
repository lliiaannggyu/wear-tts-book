package com.tengwear.ttsbookm3e.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

/**
 * 主题偏好门面。
 *
 * 真实的种子色持久化与状态广播由 [Theme.kt] 里的 [SeedColorState] / [setSeedColor] 负责，
 * 本对象只做**按索引**的查询与写入（设置页的 UI 是「一排色块」的形式，天然以索引表达）。
 *
 * 注意：这里刻意不再自己存一份索引，避免出现「索引」与「种子色」两份数据不同步的问题。
 * 索引始终由当前种子色在 [SeedPaletteChoices.all] 中的位置反推。
 */
object ThemePreferences {

    /** 按（并缓存）指定索引对应的配色方案。 */
    private val schemeCache = mutableMapOf<Int, ColorScheme>()

    /** 当前选中的种子色。 */
    fun currentSeed(context: Context): Color = readSeedColor(context)

    /** 当前选中的种子色索引。 */
    fun currentSeedIndex(context: Context): Int {
        val seed = readSeedColor(context)
        val idx = SeedPaletteChoices.all.indexOfFirst { it.second == seed }
        return if (idx >= 0) idx else 0
    }

    /** 写入种子色索引（立即生效）。 */
    fun setSeedIndex(context: Context, index: Int) {
        setSeedColorByIndex(context, index)
        schemeCache.clear()
    }

    /** 取得当前种子色对应的配色方案（带缓存，便于非 Composable 场景使用）。 */
    fun colorScheme(context: Context): ColorScheme {
        val index = currentSeedIndex(context)
        return schemeCache.getOrPut(index) {
            seedColorScheme(SeedPaletteChoices.all[index].second)
        }
    }

    /** 清除缓存。种子色由外部改变后调用。 */
    fun invalidate() {
        schemeCache.clear()
    }
}
