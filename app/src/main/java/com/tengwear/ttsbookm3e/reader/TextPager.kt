package com.tengwear.ttsbookm3e.reader

import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * ============================================================================
 *  章节测量 / 排版 → 分页
 * ============================================================================
 *
 * 排版思路移植自 GitHub 项目 Yang-Jia/wearos-novel-reader 的
 * `PageView.kt`（用 `TextPaint` + `StaticLayout` 真实测量后逐页渲染，MIT），
 * 圆屏安全边距思路参考 FredApps/wBooks 的 `WatchSafeArea.kt`（GPL-3.0）。
 *
 * 本项目是 Compose / M3E 工程，不适合直接嵌入一个自绘 `View`，因此这里把
 * 原项目「测量 → 分页 → 按行裁剪」三步拆成纯 Kotlin 的排版结果：
 *
 *   1. 用 `StaticLayout` 按真实字宽把整章排成行（[paginate]）；
 *   2. 交给 [Pagination.compute] 按一屏高度切页；
 *   3. 每页保留精确的字符区间和已经量好的行，Compose 侧只需按行渲染。
 *
 * 这样既拿到了「比较好的阅读器」的真实测量分页效果，又天然保留了
 * 页码 ↔ 章节内字符偏移的映射 —— 听书 TTS 回调的字符进度可以直接定位页码，
 * 从而实现自动翻页与自动翻章节。
 */

/** 一行文本在章节全文中的字符区间（已去掉行尾换行符）。 */
data class LineRange(val startOffset: Int, val endOffset: Int)

/** 一页：整章字符区间 + 已排好版的每一行。 */
data class ReaderPage(
    val startOffset: Int,
    val endOffset: Int,
    val lines: List<LineRange>
)

/** 一章的分页结果。页码与字符偏移可互转。 */
class PagedChapter(
    val text: String,
    val pages: List<ReaderPage>,
    /** 实测行高（px），Compose 渲染时用它设 lineHeight，保证显示与测量一致。 */
    val lineHeightPx: Float
) {
    val pageCount: Int get() = pages.size.coerceAtLeast(1)

    /** 供 [Pagination.pageIndexForOffset] 使用的轻量区间表。 */
    val charRanges: List<PageRange> get() = pages.map { PageRange(it.startOffset, it.endOffset) }

    fun clampPage(index: Int): Int = index.coerceIn(0, pageCount - 1)

    /** 该页首字符在章节中的偏移，手动翻页时用于让 TTS 从页首重新朗读。 */
    fun offsetForPage(index: Int): Int {
        if (text.isEmpty()) return 0
        return (pages.getOrNull(clampPage(index))?.startOffset ?: 0).coerceIn(0, text.length)
    }

    /** TTS 字符偏移 → 页码（二分，O(log n)）。 */
    fun pageIndexForOffset(offset: Int): Int = Pagination.pageIndexForOffset(charRanges, offset)

    /** 当前页的正文；按已量好的行用 \n 连接，渲染时 softWrap=false 即可精确复现排版。 */
    fun pageText(index: Int): String {
        if (text.isEmpty()) return ""
        val page = pages.getOrNull(clampPage(index)) ?: return ""
        return page.lines.joinToString("\n") { line ->
            val s = line.startOffset.coerceIn(0, text.length)
            val e = line.endOffset.coerceIn(s, text.length)
            text.substring(s, e)
        }
    }

    /** 当前朗读字符落在第几行，用于朗读行高亮；不在本页返回 -1。 */
    fun highlightedLine(pageIndex: Int, offset: Int): Int {
        val page = pages.getOrNull(clampPage(pageIndex)) ?: return -1
        return page.lines.indexOfFirst { offset >= it.startOffset && offset < it.endOffset }
    }

    companion object {
        fun empty(): PagedChapter = PagedChapter("", listOf(ReaderPage(0, 0, emptyList())), 1f)
    }
}

object TextPager {

    data class Config(
        val widthPx: Int,
        val heightPx: Int,
        val textSizePx: Float,
        /** 圆屏应给更大的水平安全边距，避免文字被表圈裁切（参考 wBooks WatchSafeArea）。 */
        val horizontalPaddingPx: Float,
        val verticalPaddingPx: Float,
        /** 行距倍率，同时用于测量与渲染。 */
        val lineHeightMultiplier: Float = 1.4f,
        /**
         * 每页最多显示几行（对应控制面板的「每页行数」）。
         * <= 0 表示完全由屏幕高度决定。它只在按高度切页之后再细分一页，
         * 不会改变「按真实测量排版」这一前提。
         */
        val maxLinesPerPage: Int = 0
    )

    /**
     * 对一章正文做真实测量并分页。可在后台线程调用（StaticLayout 不依赖 UI 线程）。
     * 任何异常都退化成「整章一页」，保证阅读界面绝不因排版失败而崩溃。
     */
    fun paginate(text: String, config: Config): PagedChapter {
        val fallback by lazy {
            PagedChapter(
                text,
                listOf(ReaderPage(0, text.length, emptyList())),
                config.textSizePx * config.lineHeightMultiplier
            )
        }
        if (text.isEmpty() || config.widthPx <= 0 || config.heightPx <= 0 || config.textSizePx <= 0f) {
            return fallback
        }

        val contentWidth = (config.widthPx - config.horizontalPaddingPx * 2f).toInt()
        val contentHeight = (config.heightPx - config.verticalPaddingPx * 2f).toInt()
        if (contentWidth < 1 || contentHeight < 1) return fallback

        return try {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = config.textSizePx
                isAntiAlias = true
            }
            // StaticLayout 默认行高来自字体 metrics；额外的行距用 spacingAdd 补齐，
            // 实测行高会回传给 Compose，显示与测量始终一致。
            val spacingAdd = config.textSizePx * (config.lineHeightMultiplier - 1f)
            val layout: StaticLayout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(spacingAdd, 1f)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()

            val lineHeightPx = if (layout.lineCount > 0) {
                (layout.getLineBottom(0) - layout.getLineTop(0)).toFloat()
                    .coerceAtLeast(config.textSizePx)
            } else {
                config.textSizePx * config.lineHeightMultiplier
            }

            val measuredPages = Pagination.compute(layout, contentHeight)
            // 「每页行数」是用户可选项：在按屏幕高度切页的基础上再做一次细分，
            // 这样小屏上仍能按用户习惯控制每页信息量。
            val pageLines = if (config.maxLinesPerPage > 0) {
                measuredPages.flatMap { pl ->
                    val count = pl.endLine - pl.startLine
                    if (count <= config.maxLinesPerPage) {
                        listOf(pl)
                    } else {
                        (pl.startLine until pl.endLine step config.maxLinesPerPage).map { s ->
                            PageLines(s, minOf(s + config.maxLinesPerPage, pl.endLine))
                        }
                    }
                }
            } else {
                measuredPages
            }
            val charRanges = Pagination.toCharRanges(layout, pageLines, text.length)

            val pages = pageLines.mapIndexed { idx, pl ->
                val startLine = pl.startLine.coerceIn(0, layout.lineCount - 1)
                val endLine = pl.endLine.coerceIn(startLine, layout.lineCount)
                val lines = (startLine until endLine).map { i ->
                    val s = layout.getLineStart(i).coerceIn(0, text.length)
                    var e = layout.getLineEnd(i).coerceIn(s, text.length)
                    // StaticLayout 的行尾含 \n；去掉后由 pageText 统一用 \n 连接，
                    // 否则 Compose 渲染会多出空行。
                    while (e > s && (text[e - 1] == '\n' || text[e - 1] == '\r')) e--
                    LineRange(s, e)
                }
                val range = charRanges.getOrNull(idx)
                    ?: PageRange(lines.firstOrNull()?.startOffset ?: 0, lines.lastOrNull()?.endOffset ?: 0)
                ReaderPage(range.startOffset, range.endOffset, lines)
            }

            if (pages.isEmpty()) fallback else PagedChapter(text, pages, lineHeightPx)
        } catch (_: Throwable) {
            fallback
        }
    }
}
