package com.tengwear.ttsbookm3e.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ============================================================================
 *  M3E 官方色阶（Tonal Palette）与种子色推导引擎
 * ============================================================================
 *
 * Wear Material 3 的配色建立在 **色调调色板（Tonal Palette）** 之上：
 * 每一个「色相家族」展开成一条从 tone 0（纯黑）到 tone 100（纯白）的色阶，
 * 组件再按颜色角色（Color Role）从这些色阶上取用固定的 tone 值。
 *
 * 本文件提供两层能力：
 *
 *  1. [M3ePalettes] —— M3E 官方基线色阶常量。
 *     数值与 `androidx.wear.compose.material3.tokens.PaletteTokens` 逐值对齐。
 *
 *  2. [SeedPalette] —— 由**种子颜色（Seed Color）** 动态推导整套配色。
 *     采用 Material Color Utilities 的标准算法：种子色经 CAM16 解出 色相/彩度，
 *     再配合 CIE L* 明度构成 HCT 空间，即可沿任意 tone 重新取色。
 *
 * 算法移植自 Google `material-color-utilities`（Apache-2.0）中的
 * `HctSolver` / `Cam16` / `ViewingConditions` / `ColorUtils`，
 * 以保证与官方 dynamic color 完全一致的取色结果。
 */

// ============================================================================
// 1. 官方基线色阶常量
// ============================================================================

/** 一条完整的色阶：tone → Color。tone 取值 0(黑) ~ 100(白)。 */
class M3eTonalPalette internal constructor(
    private val tones: Map<Int, Color>,
) {
    /** 按 tone 取色；未收录的 tone 返回最接近的已收录值。 */
    operator fun get(tone: Int): Color =
        tones[tone] ?: tones.entries.minByOrNull { abs(it.key - tone) }!!.value

    /** 按 tone 取色，支持小数 tone（在相邻两档间线性插值）。 */
    fun sample(tone: Double): Color {
        val lo = floor(tone).toInt()
        val t = (tone - lo).toFloat().coerceIn(0f, 1f)
        val a = get(lo)
        val b = get(lo + 1)
        return Color(
            red = a.red + (b.red - a.red) * t,
            green = a.green + (b.green - a.green) * t,
            blue = a.blue + (b.blue - a.blue) * t,
        )
    }

    /** 该色阶已收录的全部 tone（升序）。 */
    val availableTones: List<Int> get() = tones.keys.sorted()

    internal companion object {
        fun of(vararg entries: Pair<Int, Long>): M3eTonalPalette =
            M3eTonalPalette(entries.associate { (tone, argb) -> tone to Color(argb) })
    }
}

/** M3E 官方基线色阶集合（数值对齐 PaletteTokens）。 */
object M3ePalettes {

    /** 主色阶（品牌色） */
    val Primary = M3eTonalPalette.of(
        0 to 0xFF000000, 10 to 0xFF210F48, 20 to 0xFF37265E, 30 to 0xFF4D3D76,
        40 to 0xFF665590, 50 to 0xFF7F6DAA, 60 to 0xFF9987C6, 70 to 0xFFB4A1E2,
        80 to 0xFFD0BCFF, 90 to 0xFFE9DDFF, 95 to 0xFFF6EDFF, 100 to 0xFFFFFFFF,
    )

    /** 次色阶（辅助色） */
    val Secondary = M3eTonalPalette.of(
        0 to 0xFF000000, 10 to 0xFF0C1649, 20 to 0xFF232C5E, 30 to 0xFF3A4376,
        40 to 0xFF525B90, 50 to 0xFF6A74AA, 60 to 0xFF848DC6, 70 to 0xFF9FA8E2,
        80 to 0xFFBAC3FF, 90 to 0xFFDEE0FF, 95 to 0xFFF0EFFF, 100 to 0xFFFFFFFF,
    )

    /** 三级色阶（点缀色） */
    val Tertiary = M3eTonalPalette.of(
        0 to 0xFF000000, 10 to 0xFF2E1500, 20 to 0xFF4C2700, 30 to 0xFF6C3A03,
        40 to 0xFF88511B, 50 to 0xFFA56931, 60 to 0xFFC38248, 70 to 0xFFE29C5F,
        80 to 0xFFFFB77A, 90 to 0xFFFFDCC2, 95 to 0xFFFFEEE2, 100 to 0xFFFFFFFF,
    )

    /** 中性色阶（表面 / 背景） */
    val Neutral = M3eTonalPalette.of(
        0 to 0xFF000000, 15 to 0xFF272430, 20 to 0xFF332E3C, 30 to 0xFF494453,
        40 to 0xFF615C6B, 50 to 0xFF7A7484, 60 to 0xFF948E9F, 70 to 0xFFAFA8B9,
        80 to 0xFFCBC3D5, 90 to 0xFFE8DFF2, 95 to 0xFFF6EDFF, 100 to 0xFFFFFFFF,
    )

    /** 中性变体色阶（次级文字 / 描边） */
    val NeutralVariant = M3eTonalPalette.of(
        0 to 0xFF000000, 10 to 0xFF1D1A23, 20 to 0xFF322F38, 30 to 0xFF49454F,
        40 to 0xFF615D67, 50 to 0xFF7A7580, 60 to 0xFF948F9A, 70 to 0xFFAFA9B5,
        80 to 0xFFCAC4D0, 90 to 0xFFE7E0EC, 95 to 0xFFF5EEFB, 100 to 0xFFFFFFFF,
    )

    /** 错误色阶 */
    val Error = M3eTonalPalette.of(
        0 to 0xFF000000, 10 to 0xFF410E0B, 20 to 0xFF601410, 30 to 0xFF8C1D18,
        40 to 0xFFB3261E, 50 to 0xFFDC362E, 60 to 0xFFE46962, 70 to 0xFFEC928E,
        80 to 0xFFF2B8B5, 90 to 0xFFF9DEDC, 95 to 0xFFFCEEEE, 100 to 0xFFFFFFFF,
    )
}

// ============================================================================
// 2. HCT —— 种子色 ⇄ 色阶 的推导引擎
// ============================================================================

/**
 * HCT 颜色：CAM16 的 色相/彩度 + CIE L\* 的 明度。
 *
 * - [hue]    0..360，单位度
 * - [chroma] 彩度，0 为灰，越大越鲜艳（上限随 色相/明度 变化）
 * - [tone]   0..100，0 为黑、100 为白
 *
 * 关键性质：**tone 之差为 40 即保证对比度 ≥ 3.0，差 50 保证 ≥ 4.5**，
 * 因此 M3E 通过固定 tone 取值来天然满足无障碍对比度要求。
 */
data class Hct(val hue: Double, val chroma: Double, val tone: Double) {

    /** 转回 sRGB 颜色。 */
    fun toColor(): Color = Color(HctSolver.solveToInt(hue, chroma, tone))

    /** 仅替换明度（彩度可能因色域上限而自动降低）。 */
    fun withTone(newTone: Double): Hct = Hct(hue, chroma, newTone.coerceIn(0.0, 100.0))

    /** 仅替换彩度。 */
    fun withChroma(newChroma: Double): Hct = Hct(hue, newChroma.coerceAtLeast(0.0), tone)

    /** 仅替换色相。 */
    fun withHue(newHue: Double): Hct = Hct(sanitizeDegrees(newHue), chroma, tone)

    companion object {
        /** 从 sRGB 颜色解析出 HCT。 */
        fun fromColor(color: Color): Hct = fromArgb(color.toArgb())

        /** 从 ARGB 整数解析出 HCT。 */
        fun fromArgb(argb: Int): Hct {
            val cam = Cam16.fromInt(argb)
            return Hct(
                hue = cam.hue,
                chroma = cam.chroma,
                tone = HctColorUtils.lstarFromArgb(argb).toDouble(),
            )
        }

        /** 由 色相/彩度/明度 直接构造（自动解算到色域内）。 */
        fun from(hue: Double, chroma: Double, tone: Double): Hct =
            fromArgb(HctSolver.solveToInt(hue, chroma, tone))
    }
}

/** 角度规范化到 [0, 360)。 */
internal fun sanitizeDegrees(degrees: Double): Double {
    var d = degrees % 360.0
    if (d < 0) d += 360.0
    return d
}

/** 角度规范化到 [0, 2π)。 */
private fun sanitizeRadians(angle: Double): Double = (angle + Math.PI * 8) % (Math.PI * 2)

private fun signum(v: Double): Double = if (v < 0) -1.0 else 1.0

// ---------------------------------------------------------------------------
// HctSolver —— 由 色相/彩度/L* 反解 sRGB（移植自 material-color-utilities）
// ---------------------------------------------------------------------------

private object HctSolver {

    /** 线性 RGB → 缩放后的 CAM16 锥体响应。 */
    private val SCALED_DISCOUNT_FROM_LINRGB = arrayOf(
        doubleArrayOf(0.001200833568784504, 0.002389694492170889, 0.0002795742885861124),
        doubleArrayOf(0.0005891086651375999, 0.0029785502573438758, 0.0003270666104008398),
        doubleArrayOf(0.00010146692491640572, 0.0005364214359186694, 0.0032979401770712076),
    )

    /** 缩放锥体响应 → 线性 RGB。 */
    private val LINRGB_FROM_SCALED_DISCOUNT = arrayOf(
        doubleArrayOf(1373.2198709594231, -1100.4251190754821, -7.278681089101213),
        doubleArrayOf(-271.815969077903, 559.6580465940733, -32.46047482791194),
        doubleArrayOf(1.9622899599665666, -57.173814538844006, 308.7233197812385),
    )

    /** 线性 RGB 的亮度权重。 */
    private val Y_FROM_LINRGB = doubleArrayOf(0.2126, 0.7152, 0.0722)

    /** sRGB 各通道的临界平面（用于色域边界二分）。 */
    private val CRITICAL_PLANES = doubleArrayOf(
        0.015176349177441876, 0.045529047532325624, 0.07588174588720938,
        0.10623444424209313, 0.13658714259697685, 0.16693984095186062,
        0.19729253930674434, 0.2276452376616281, 0.2579979360165119,
        0.28835063437139563, 0.3188300904430532, 0.350925934958123,
        0.3848314933096426, 0.42057480301049466, 0.458183274052838,
        0.4976837250274023, 0.5391024159806381, 0.5824650784040898,
        0.6277969426914107, 0.6751227633498623, 0.7244668422128921,
        0.775853049866786, 0.829304845476233, 0.8848452951698498,
        0.942497089126609, 1.0022825574869039, 1.0642236851973577,
        1.1283421258858297, 1.1946592148522128, 1.2631959812511864,
        1.3339731595349034, 1.407011200216447, 1.4823302800086415,
        1.5599503113873272, 1.6398909516233677, 1.7221716113234105,
        1.8068114625156377, 1.8938294463134073, 1.9832442801866852,
        2.075074464868551, 2.1693382909216234, 2.2660538449872063,
        2.36523901573795, 2.4669114995532007, 2.5710888059345764,
        2.6777882626779785, 2.7870270208169257, 2.898822059350997,
        3.0131901897720907, 3.1301480604002863, 3.2497121605402226,
        3.3718988244681087, 3.4967242352587946, 3.624204428461639,
        3.754355295633311, 3.887192587735158, 4.022731918402185,
        4.160988767090289, 4.301978482107941, 4.445716283538092,
        4.592217266055746, 4.741496401646282, 4.893568542229298,
        5.048448422192488, 5.20615066083972, 5.3666897647573375,
        5.5300801301023865, 5.696336044816294, 5.865471690767354,
        6.037501145825082, 6.212438385869475, 6.390297286737924,
        6.571091626112461, 6.7548350853498045, 6.941541251256611,
        7.131223617812143, 7.323895587840543, 7.5195704746346665,
        7.7182615035334345, 7.919981813454504, 8.124744458384042,
        8.332562408825165, 8.543448553206703, 8.757415699253682,
        8.974476575321063, 9.194643831691977, 9.417930041841839,
        9.644347703669503, 9.873909240696694, 10.106627003236781,
        10.342513269534024, 10.58158024687427, 10.8238400726681,
        11.069304815507364, 11.317986476196008, 11.569896988756009,
        11.825048221409341, 12.083451977536606, 12.345119996613247,
        12.610063955123938, 12.878295467455942, 13.149826086772048,
        13.42466730586372, 13.702830557985108, 13.984327217668513,
        14.269168601521828, 14.55736596900856, 14.848930523210871,
        15.143873411576273, 15.44220572664832, 15.743938506781891,
        16.04908273684337, 16.35764934889634, 16.66964922287304,
        16.985093187232053, 17.30399201960269, 17.62635644741625,
        17.95219714852476, 18.281524751807332, 18.614349837764564,
        18.95068293910138, 19.290534541298456, 19.633915083172692,
        19.98083495742689, 20.331304511189067, 20.685334046541502,
        21.042933821039977, 21.404114048223256, 21.76888489811322,
        22.137256497705877, 22.50923893145328, 22.884842241736916,
        23.264076429332462, 23.6469514538663, 24.033477234264016,
        24.42366364919083, 24.817520537484558, 25.21505769858089,
        25.61628489293138, 26.021211842414342, 26.429848230738664,
        26.842203703840827, 27.258287870275353, 27.678110301598522,
        28.10168053274597, 28.529008062403893, 28.96010235337422,
        29.39497283293396, 29.83362889318845, 30.276079891419332,
        30.722335150426627, 31.172403958865512, 31.62629557157785,
        32.08401920991837, 32.54558406207592, 33.010999283389665,
        33.4802739966603, 33.953417292456834, 34.430438229418264,
        34.911345834551085, 35.39614910352207, 35.88485700094671,
        36.37747846067349, 36.87402238606382, 37.37449765026789,
        37.87891309649659, 38.38727753828926, 38.89959975977785,
        39.41588851594697, 39.93615253289054, 40.460400508064545,
        40.98864111053629, 41.520882981230194, 42.05713473317016,
        42.597404951718396, 43.141702194811224, 43.6900349931913,
        44.24241185063697, 44.798841244188324, 45.35933162437017,
        45.92389141541209, 46.49252901546552, 47.065252796817916,
        47.64207110610409, 48.22299226451468, 48.808024568002054,
        49.3971762874833, 49.9904556690408, 50.587870934119984,
        51.189430279724725, 51.79514187861014, 52.40501387947288,
        53.0190544071392, 53.637271562750364, 54.259673423945976,
        54.88626804504493, 55.517063457223934, 56.15206766869424,
        56.79128866487574, 57.43473440856916, 58.08241284012621,
        58.734331877617365, 59.39049941699807, 60.05092333227251,
        60.715611475655585, 61.38457167773311, 62.057811747619894,
        62.7353394731159, 63.417162620860914, 64.10328893648692,
        64.79372614476921, 65.48848194977529, 66.18756403501224,
        66.89098006357258, 67.59873767827808, 68.31084450182222,
        69.02730813691093, 69.74813616640164, 70.47333615344107,
        71.20291564160104, 71.93688215501312, 72.67524319850172,
        73.41800625771542, 74.16517879925733, 74.9167682708136,
        75.67278210128072, 76.43322770089146, 77.1981124613393,
        77.96744375590167, 78.74122893956174, 79.51947534912904,
        80.30219030335869, 81.08938110306934, 81.88105503125999,
        82.67721935322541, 83.4778813166706, 84.28304815182372,
        85.09272707154808, 85.90692527145302, 86.72564993000343,
        87.54890820862819, 88.3767072518277, 89.2090541872801,
        90.04595612594655, 90.88742016217518, 91.73345337380438,
        92.58406282226491, 93.43925555268066, 94.29903859396902,
        95.16341895893969, 96.03240364439274, 96.9059996312159,
        97.78421388448044, 98.6670533535366, 99.55452497210776,
    )

    /** 线性 RGB → 常规 RGB（0..255）。 */
    private fun trueDelinearized(rgbComponent: Double): Double {
        val normalized = rgbComponent / 100.0
        val delinearized = if (normalized <= 0.0031308) {
            normalized * 12.92
        } else {
            1.055 * normalized.pow(1.0 / 2.4) - 0.055
        }
        return delinearized * 255.0
    }

    /** 色适应（CAM16 锥体响应）。 */
    private fun chromaticAdaptation(component: Double): Double {
        val af = abs(component).pow(0.42)
        return signum(component) * 400.0 * af / (af + 27.13)
    }

    /** 反色适应。 */
    private fun inverseChromaticAdaptation(adapted: Double): Double {
        val adaptedAbs = abs(adapted)
        val base = (27.13 * adaptedAbs / (400.0 - adaptedAbs)).coerceAtLeast(0.0)
        return signum(adapted) * base.pow(1.0 / 0.42)
    }

    private fun matrixMultiply(row: DoubleArray, matrix: Array<DoubleArray>): DoubleArray =
        doubleArrayOf(
            row[0] * matrix[0][0] + row[1] * matrix[0][1] + row[2] * matrix[0][2],
            row[0] * matrix[1][0] + row[1] * matrix[1][1] + row[2] * matrix[1][2],
            row[0] * matrix[2][0] + row[1] * matrix[2][1] + row[2] * matrix[2][2],
        )

    /** 线性 RGB 在 CAM16 中的色相角（弧度）。 */
    private fun hueOf(linrgb: DoubleArray): Double {
        val scaled = matrixMultiply(linrgb, SCALED_DISCOUNT_FROM_LINRGB)
        val rA = chromaticAdaptation(scaled[0])
        val gA = chromaticAdaptation(scaled[1])
        val bA = chromaticAdaptation(scaled[2])
        val a = (11.0 * rA + -12.0 * gA + bA) / 11.0
        val b = (rA + gA - 2.0 * bA) / 9.0
        return kotlin.math.atan2(b, a)
    }

    private fun areInCyclicOrder(a: Double, b: Double, c: Double): Boolean =
        sanitizeRadians(b - a) < sanitizeRadians(c - a)

    private fun lerpPoint(source: DoubleArray, t: Double, target: DoubleArray) = doubleArrayOf(
        source[0] + (target[0] - source[0]) * t,
        source[1] + (target[1] - source[1]) * t,
        source[2] + (target[2] - source[2]) * t,
    )

    private fun setCoordinate(
        source: DoubleArray,
        coordinate: Double,
        target: DoubleArray,
        axis: Int,
    ): DoubleArray {
        val t = (coordinate - source[axis]) / (target[axis] - source[axis])
        return lerpPoint(source, t, target)
    }

    private fun isBounded(x: Double): Boolean = x >= 0.0 && x <= 100.0

    /** y 平面上 RGB 立方体截面的第 n 个顶点。 */
    private fun nthVertex(y: Double, n: Int): DoubleArray {
        val kR = Y_FROM_LINRGB[0]
        val kG = Y_FROM_LINRGB[1]
        val kB = Y_FROM_LINRGB[2]
        val coordA = if (n % 4 <= 1) 0.0 else 100.0
        val coordB = if (n % 2 == 0) 0.0 else 100.0
        return when {
            n < 4 -> {
                val g = coordA; val b = coordB
                val r = (y - g * kG - b * kB) / kR
                if (isBounded(r)) doubleArrayOf(r, g, b) else doubleArrayOf(-1.0, -1.0, -1.0)
            }
            n < 8 -> {
                val b = coordA; val r = coordB
                val g = (y - r * kR - b * kB) / kG
                if (isBounded(g)) doubleArrayOf(r, g, b) else doubleArrayOf(-1.0, -1.0, -1.0)
            }
            else -> {
                val r = coordA; val g = coordB
                val b = (y - r * kR - g * kG) / kB
                if (isBounded(b)) doubleArrayOf(r, g, b) else doubleArrayOf(-1.0, -1.0, -1.0)
            }
        }
    }

    /** 找到包含目标色相的色域边界线段。 */
    private fun bisectToSegment(y: Double, targetHue: Double): Array<DoubleArray> {
        var left = doubleArrayOf(-1.0, -1.0, -1.0)
        var right = left
        var leftHue = 0.0
        var rightHue = 0.0
        var initialized = false
        var uncut = true
        for (n in 0 until 12) {
            val mid = nthVertex(y, n)
            if (mid[0] < 0) continue
            val midHue = hueOf(mid)
            if (!initialized) {
                left = mid; right = mid; leftHue = midHue; rightHue = midHue
                initialized = true
                continue
            }
            if (uncut || areInCyclicOrder(leftHue, midHue, rightHue)) {
                uncut = false
                if (areInCyclicOrder(leftHue, targetHue, midHue)) {
                    right = mid; rightHue = midHue
                } else {
                    left = mid; leftHue = midHue
                }
            }
        }
        return arrayOf(left, right)
    }

    private fun criticalPlaneBelow(x: Double): Int = floor(x - 0.5).toInt()
    private fun criticalPlaneAbove(x: Double): Int = ceil(x - 0.5).toInt()

    /** 在色域边界上二分，逼近给定 Y 与色相的颜色。 */
    private fun bisectToLimit(y: Double, targetHue: Double): DoubleArray {
        val segment = bisectToSegment(y, targetHue)
        var left = segment[0]
        var leftHue = hueOf(left)
        var right = segment[1]
        for (axis in 0 until 3) {
            if (left[axis] != right[axis]) {
                var lPlane: Int
                var rPlane: Int
                if (left[axis] < right[axis]) {
                    lPlane = criticalPlaneBelow(trueDelinearized(left[axis]))
                    rPlane = criticalPlaneAbove(trueDelinearized(right[axis]))
                } else {
                    lPlane = criticalPlaneAbove(trueDelinearized(left[axis]))
                    rPlane = criticalPlaneBelow(trueDelinearized(right[axis]))
                }
                for (i in 0 until 8) {
                    if (abs(rPlane - lPlane) <= 1) break
                    val mPlane = floor((lPlane + rPlane) / 2.0).toInt()
                    val midPlaneCoordinate = CRITICAL_PLANES[mPlane]
                    val mid = setCoordinate(left, midPlaneCoordinate, right, axis)
                    val midHue = hueOf(mid)
                    if (areInCyclicOrder(leftHue, targetHue, midHue)) {
                        right = mid; rPlane = mPlane
                    } else {
                        left = mid; leftHue = midHue; lPlane = mPlane
                    }
                }
            }
        }
        return doubleArrayOf(
            (left[0] + right[0]) / 2,
            (left[1] + right[1]) / 2,
            (left[2] + right[2]) / 2,
        )
    }

    /** 牛顿迭代精确求解（更快，命中率高）。 */
    private fun findResultByJ(hueRadians: Double, chroma: Double, y: Double): Int {
        var j = sqrt(y) * 11.0
        val vc = ViewingConditions.DEFAULT
        val tInnerCoeff = 1 / (1.64 - 0.29.pow(vc.n)).pow(0.73)
        val eHue = 0.25 * (cos(hueRadians + 2.0) + 3.8)
        val p1 = eHue * (50000.0 / 13.0) * vc.nc * vc.ncb
        val hSin = sin(hueRadians)
        val hCos = cos(hueRadians)
        for (iterationRound in 0 until 5) {
            val jNormalized = j / 100.0
            val alpha = if (chroma == 0.0 || j == 0.0) 0.0 else chroma / sqrt(jNormalized)
            val t = (alpha * tInnerCoeff).pow(1.0 / 0.9)
            val ac = vc.aw * jNormalized.pow(1.0 / vc.c / vc.z)
            val p2 = ac / vc.nbb
            val gamma = 23.0 * (p2 + 0.305) * t /
                    (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin)
            val a = gamma * hCos
            val b = gamma * hSin
            val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
            val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
            val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0
            val linrgb = matrixMultiply(
                doubleArrayOf(
                    inverseChromaticAdaptation(rA),
                    inverseChromaticAdaptation(gA),
                    inverseChromaticAdaptation(bA),
                ),
                LINRGB_FROM_SCALED_DISCOUNT,
            )
            if (linrgb[0] < 0 || linrgb[1] < 0 || linrgb[2] < 0) return 0
            val fnj = Y_FROM_LINRGB[0] * linrgb[0] +
                    Y_FROM_LINRGB[1] * linrgb[1] +
                    Y_FROM_LINRGB[2] * linrgb[2]
            if (fnj <= 0) return 0
            if (iterationRound == 4 || abs(fnj - y) < 0.002) {
                if (linrgb[0] > 100.01 || linrgb[1] > 100.01 || linrgb[2] > 100.01) return 0
                return HctColorUtils.argbFromLinrgb(linrgb)
            }
            j -= (fnj - y) * j / (2.0 * fnj)
        }
        return 0
    }

    /** 核心入口：由 色相/彩度/L\* 解算出 sRGB 的 ARGB 值。 */
    fun solveToInt(hueDegrees: Double, chroma: Double, lstar: Double): Int {
        if (chroma < 0.0001 || lstar < 0.0001 || lstar > 99.9999) {
            return HctColorUtils.argbFromLstar(lstar)
        }
        val hue = sanitizeDegrees(hueDegrees)
        val hueRadians = hue / 180.0 * Math.PI
        val y = HctColorUtils.yFromLstar(lstar)
        val exactAnswer = findResultByJ(hueRadians, chroma, y)
        if (exactAnswer != 0) return exactAnswer
        val linrgb = bisectToLimit(y, hueRadians)
        return HctColorUtils.argbFromLinrgb(linrgb)
    }
}

// ---------------------------------------------------------------------------
// Cam16 —— CAM16 外观模型（移植自 material-color-utilities）
// ---------------------------------------------------------------------------

private class ViewingConditions(
    val n: Double,
    val aw: Double,
    val nbb: Double,
    val ncb: Double,
    val c: Double,
    val nc: Double,
    val z: Double,
    val rgbD: DoubleArray,
    val fl: Double,
    val flRoot: Double,
) {
    companion object {
        /** 标准观察条件（D65 白点，约 200 lux 环境）。 */
        val DEFAULT: ViewingConditions = run {
            val whitePoint = doubleArrayOf(95.047, 100.0, 108.883)
            val adaptingLuminance = 200.0 / Math.PI * HctColorUtils.yFromLstar(50.0) / 100.0
            val backgroundLstar = 50.0
            val surround = 2.0
            val discountingIlluminant = false

            val xyzToCam16 = Cam16.XYZ_TO_CAM16RGB
            val rW = whitePoint[0] * xyzToCam16[0][0] +
                    whitePoint[1] * xyzToCam16[0][1] +
                    whitePoint[2] * xyzToCam16[0][2]
            val gW = whitePoint[0] * xyzToCam16[1][0] +
                    whitePoint[1] * xyzToCam16[1][1] +
                    whitePoint[2] * xyzToCam16[1][2]
            val bW = whitePoint[0] * xyzToCam16[2][0] +
                    whitePoint[1] * xyzToCam16[2][1] +
                    whitePoint[2] * xyzToCam16[2][2]

            val f = 0.8 + surround / 10.0
            val c = if (f >= 0.9) {
                val t = (1.0 - (1.0 - f).pow(4.0))
                lerp(0.59, 0.69, t)
            } else {
                lerp(0.525, 0.59, f - 0.8)
            }
            val d = (if (discountingIlluminant) {
                1.0
            } else {
                f * (1.0 - 1.0 / 3.6 * Math.exp((-adaptingLuminance - 42.0) / 92.0))
            }).coerceIn(0.0, 1.0)

            val nc = f
            val rgbD = doubleArrayOf(
                d * (100.0 / rW) + 1.0 - d,
                d * (100.0 / gW) + 1.0 - d,
                d * (100.0 / bW) + 1.0 - d,
            )

            val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
            val k4 = k * k * k * k
            val k4F = 1.0 - k4
            val fl = k4 * adaptingLuminance +
                    0.1 * k4F * k4F * (5.0 * adaptingLuminance).pow(1.0 / 3.0)

            val n = HctColorUtils.yFromLstar(backgroundLstar) / whitePoint[1]
            val z = 1.48 + sqrt(n)
            val nbb = 0.725 / n.pow(0.2)
            val rgbAFactors = doubleArrayOf(
                (fl * rgbD[0] * rW / 100.0).pow(0.42),
                (fl * rgbD[1] * gW / 100.0).pow(0.42),
                (fl * rgbD[2] * bW / 100.0).pow(0.42),
            )
            val rgbA = doubleArrayOf(
                400.0 * rgbAFactors[0] / (rgbAFactors[0] + 27.13),
                400.0 * rgbAFactors[1] / (rgbAFactors[1] + 27.13),
                400.0 * rgbAFactors[2] / (rgbAFactors[2] + 27.13),
            )
            val aw = (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb

            ViewingConditions(
                n = n, aw = aw, nbb = nbb, ncb = nbb, c = c, nc = nc, z = z,
                rgbD = rgbD, fl = fl, flRoot = fl.pow(0.25),
            )
        }

        private fun lerp(start: Double, stop: Double, amount: Double): Double =
            start + (stop - start) * amount
    }
}

private class Cam16(
    val hue: Double,
    val chroma: Double,
    val j: Double,
    val q: Double,
    val m: Double,
    val s: Double,
    val jstar: Double,
    val astar: Double,
    val bstar: Double,
) {
    companion object {
        val XYZ_TO_CAM16RGB = arrayOf(
            doubleArrayOf(0.401288, 0.650173, -0.051461),
            doubleArrayOf(-0.250268, 1.204414, 0.045854),
            doubleArrayOf(-0.002079, 0.048952, 0.953127),
        )

        private val CAM16RGB_TO_XYZ = arrayOf(
            doubleArrayOf(1.86206786, -1.01125463, 0.14918677),
            doubleArrayOf(0.38752654, 0.62144744, -0.00897398),
            doubleArrayOf(-0.01584150, -0.03412294, 1.04996444),
        )

        fun fromInt(argb: Int): Cam16 {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF

            val linR = HctColorUtils.linearized(r)
            val linG = HctColorUtils.linearized(g)
            val linB = HctColorUtils.linearized(b)

            return fromXyz(
                linR * 100.0 * 0.41233895 + linG * 100.0 * 0.35762064 + linB * 100.0 * 0.18051042,
                linR * 100.0 * 0.2126 + linG * 100.0 * 0.7152 + linB * 100.0 * 0.0722,
                linR * 100.0 * 0.01932141 + linG * 100.0 * 0.11916382 + linB * 100.0 * 0.95034478,
            )
        }

        fun fromXyz(x: Double, y: Double, z: Double): Cam16 {
            val vc = ViewingConditions.DEFAULT

            val rT = 0.401288 * x + 0.650173 * y - 0.051461 * z
            val gT = -0.250268 * x + 1.204414 * y + 0.045854 * z
            val bT = -0.002079 * x + 0.048952 * y + 0.953127 * z

            val rD = vc.rgbD[0] * rT
            val gD = vc.rgbD[1] * gT
            val bD = vc.rgbD[2] * bT

            // 注意：色适应前要先乘上亮度适应因子 fl，
            // 这是 M3E / MCU 与教科书 CAM16 的实现差异点，直接影响彩度标度。
            val rA = chromaticAdaptation(rD, vc.fl)
            val gA = chromaticAdaptation(gD, vc.fl)
            val bA = chromaticAdaptation(bD, vc.fl)

            val a = (11.0 * rA + -12.0 * gA + bA) / 11.0
            val bb = (rA + gA - 2.0 * bA) / 9.0

            val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
            val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0

            val atan2 = kotlin.math.atan2(bb, a)
            var atanDegrees = atan2 * 180.0 / Math.PI
            if (atanDegrees < 0) atanDegrees += 360.0
            else if (atanDegrees >= 360.0) atanDegrees -= 360.0
            val hue = atanDegrees

            val hueRadians = hue * Math.PI / 180.0
            val ac = p2 * vc.nbb
            val j = 100.0 * (ac / vc.aw).pow(vc.c * vc.z)
            val q = (4.0 / vc.c) * sqrt(j / 100.0) * (vc.aw + 4.0) * vc.flRoot
            val huePrime = if (hue < 20.14) hue + 360.0 else hue
            val eHue = 0.25 * (cos(huePrime * Math.PI / 180.0 + 2.0) + 3.8)
            val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
            val t = p1 * sqrt(a * a + bb * bb) / (u + 0.305)
            val alpha = t.pow(0.9) * (1.64 - 0.29.pow(vc.n)).pow(0.73)
            val chroma = alpha * sqrt(j / 100.0)
            val m = chroma * vc.flRoot
            val s = 50.0 * sqrt(alpha * vc.c / (vc.aw + 4.0))
            val jstar = (1.0 + 100.0 * 0.007) * j / (1.0 + 0.007 * j)
            val mstar = 1.0 / 0.0228 * kotlin.math.ln(1.0 + 0.0228 * m)
            val astar = mstar * cos(hueRadians)
            val bstar = mstar * sin(hueRadians)

            return Cam16(hue, chroma, j, q, m, s, jstar, astar, bstar)
        }

        /** 色适应：先按 fl 归一化，再做 400·x^0.42/(x^0.42+27.13)。 */
        private fun chromaticAdaptation(component: Double, fl: Double): Double {
            val af = (fl * abs(component) / 100.0).pow(0.42)
            return signum(component) * 400.0 * af / (af + 27.13)
        }
    }
}

// ---------------------------------------------------------------------------
// ColorUtils —— L\* / Y / 线性 RGB 互转（移植自 material-color-utilities）
// ---------------------------------------------------------------------------

private object HctColorUtils {

    private const val LINEARIZED_THRESHOLD = 0.040449936

    /** 线性化一个 sRGB 通道（0..255 → 0..1）。 */
    fun linearized(rgbComponent: Int): Double {
        val normalized = rgbComponent / 255.0
        return if (normalized <= LINEARIZED_THRESHOLD) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    /** 线性 RGB（0..100）→ ARGB。 */
    fun argbFromLinrgb(linrgb: DoubleArray): Int {
        val r = delinearized(linrgb[0])
        val g = delinearized(linrgb[1])
        val b = delinearized(linrgb[2])
        return argbFromRgb(r, g, b)
    }

    /** 去线性化一个通道（0..100 → 0..255 整数）。 */
    private fun delinearized(rgbComponent: Double): Int {
        val normalized = rgbComponent / 100.0
        val delinearized = if (normalized <= 0.0031308) {
            normalized * 12.92
        } else {
            1.055 * normalized.pow(1.0 / 2.4) - 0.055
        }
        return (delinearized * 255.0).roundToInt().coerceIn(0, 255)
    }

    private fun argbFromRgb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    /** L\* → ARGB（灰阶）。 */
    fun argbFromLstar(lstar: Double): Int {
        val y = yFromLstar(lstar)
        val component = delinearized(y)
        return argbFromRgb(component, component, component)
    }

    /** L\* → Y（0..100，与 XYZ 的 Y 同量纲）。 */
    fun yFromLstar(lstar: Double): Double {
        val ke = 8.0
        return if (lstar > ke) {
            ((lstar + 16.0) / 116.0).pow(3.0) * 100.0
        } else {
            lstar / (24389.0 / 27.0) * 100.0
        }
    }

    /** ARGB → L\*。 */
    fun lstarFromArgb(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val y = linearized(r) * 0.2126 + linearized(g) * 0.7152 + linearized(b) * 0.0722
        return lstarFromY(y)
    }

    /** 线性亮度 Y（0..1）→ L\*（0..100）。 */
    private fun lstarFromY(y: Double): Float {
        val y1 = y.coerceIn(0.0, 1.0)
        val e = 216.0 / 24389.0
        val lstar = if (y1 <= e) {
            y1 * (24389.0 / 27.0)
        } else {
            y1.pow(1.0 / 3.0) * 116.0 - 16.0
        }
        return lstar.toFloat().coerceIn(0f, 100f)
    }
}

// ============================================================================
// 3. 由种子色生成整套 M3E 配色
// ============================================================================

/**
 * 由一颗**种子颜色**推导出的完整色调调色板。
 *
 * M3E 的标准做法是：种子色 → HCT → 固定色相，生成若干条不同彩度的色阶：
 *  - [primary]         保留种子色相与彩度
 *  - [secondary]       同色相，彩度降至约 1/3
 *  - [tertiary]        色相旋转 +60°，彩度略降
 *  - [neutral]         接近无彩，作表面/背景
 *  - [neutralVariant]  轻微彩度，作次级文字/描边
 *  - [error]           固定色相 25°
 *
 * 彩度参考 M3E 官方紫色基线（H≈299.8, C≈36.7）与 M3 紫色基线（H≈299.0, C≈47.9），
 * 均落在 36~48 区间，故次级/中性色阶按相同比例缩放。
 *
 * **彩度上限**：主色阶的彩度被夹在 [MAX_PRIMARY_CHROMA]（48）以内。
 * 这是必要的——像纯黄绿这样的种子色实测彩度可达 200+，
 * 若原样带入 tone 90/10，会超出 sRGB 色域并被求解器硬夹到边界，
 * 导致「primary 与 primaryDim 几乎同色」。夹到 48 后，无论种子多艳，
 * 生成的色阶都与官方基线保持同样的层次感。
 */
class SeedPalette(private val seed: Hct) {

    /**
     * 主色相 + 受控彩度。
     * 低彩度种子（如灰蓝）保持原样，高彩度种子被夹到 [MAX_PRIMARY_CHROMA]。
     */
    private val primaryChroma: Double
        get() = seed.chroma.coerceIn(MIN_PRIMARY_CHROMA, MAX_PRIMARY_CHROMA)

    val primary: Hct
        get() = seed.withChroma(primaryChroma)

    val secondary: Hct
        get() = seed.withChroma(primaryChroma * SECONDARY_RATIO)

    val tertiary: Hct
        get() = seed.withHue(seed.hue + 60.0).withChroma(primaryChroma * TERTIARY_RATIO)

    val neutral: Hct
        get() = seed.withChroma(NEUTRAL_CHROMA)

    val neutralVariant: Hct
        get() = seed.withChroma(NEUTRAL_VARIANT_CHROMA)

    val error: Hct
        get() = Hct(25.0, ERROR_CHROMA, seed.tone)

    /** 在该色阶上取 tone 对应的颜色。 */
    fun tone(source: Hct, tone: Double): Color =
        Hct(source.hue, source.chroma, tone.coerceIn(0.0, 100.0)).toColor()

    /** 原始种子色本身。 */
    val seedColor: Color get() = seed.toColor()

    companion object {
        private const val SECONDARY_RATIO = 0.34
        private const val TERTIARY_RATIO = 0.55
        private const val NEUTRAL_CHROMA = 4.0
        private const val NEUTRAL_VARIANT_CHROMA = 8.0
        private const val ERROR_CHROMA = 84.0

        /**
         * 主色阶彩度上限，取自 M3 基线紫 `#6750A4` 的实测彩度 47.86 向上取整。
         * 超过此值后 tone 90 与 tone 10 在 sRGB 中都会顶到色域边界，
         * 层次感反而消失，故统一夹紧。
         */
        private const val MAX_PRIMARY_CHROMA = 48.0

        /** 主色阶彩度下限，避免近乎灰的种子让主色失去辨识度。 */
        private const val MIN_PRIMARY_CHROMA = 8.0

        /** 由种子颜色创建调色板。 */
        fun fromSeed(seedColor: Color): SeedPalette = SeedPalette(Hct.fromColor(seedColor))
    }
}
