package com.tengwear.ttsbookm3e.reader

import android.text.Layout

/**
 * ============================================================================
 *  测量式分页算法 —— 移植自 GitHub 项目 Yang-Jia/wearos-novel-reader
 *  源文件：app/src/main/java/com/wear/novelreader/reader/Pagination.kt
 *  许可：MIT License（Copyright (c) Yang-Jia，见 README「移植来源与许可」）
 * ============================================================================
 *
 * 原实现把整章文本交给 `StaticLayout` 做真实排版，得到每一行的 top/bottom，
 * 再按「一屏可容纳的像素高度」把连续的行切成页。
 *
 * 相比本项目原先「每页固定 N 个字符」的硬切方式，它按真实字宽 / 行高分页：
 *   · 中英文混排、标点、数字不会被从中间截断；
 *   · 改字号、改字体后重新排版即可，不会出现圆屏梯形字数表错乱；
 *   · 每页能精确换算出在章节全文中的字符区间，供 TTS 朗读进度定位。
 *
 * 本文件在保留原算法（[compute] / [computeRanges] / [PageLines]）的基础上，
 * 补充了「页码 ↔ 字符偏移」转换，用于把听书朗读进度映射到阅读页码，
 * 是「听书自动翻页 / 自动翻章节」适配的基础。
 */

/** 一页由 [startLine, endLine) 这些行组成。与原项目 `PageLines` 完全一致。 */
data class PageLines(val startLine: Int, val endLine: Int)

/** 一页对应的正文字符区间 `[startOffset, endOffset)`。 */
data class PageRange(val startOffset: Int, val endOffset: Int)

object Pagination {

    /**
     * 按真实行高把一次 StaticLayout 排版结果切成若干页。
     * 与原项目同名同签名，实现保持一致。
     */
    fun compute(layout: Layout, pageHeight: Int): List<PageLines> {
        if (layout.lineCount <= 0 || pageHeight <= 0) return emptyList()
        val tops = IntArray(layout.lineCount) { layout.getLineTop(it) }
        val bottoms = IntArray(layout.lineCount) { layout.getLineBottom(it) }
        return computeRanges(tops, bottoms, pageHeight)
    }

    /**
     * 逐行累加高度，超出 `pageHeight` 就换页。
     * 单行高度超过一屏时仍让它独占一页（end = start + 1），避免死循环。
     */
    fun computeRanges(lineTops: IntArray, lineBottoms: IntArray, pageHeight: Int): List<PageLines> {
        if (lineTops.isEmpty() || lineTops.size != lineBottoms.size || pageHeight <= 0) {
            return emptyList()
        }
        val pages = mutableListOf<PageLines>()
        var start = 0
        while (start < lineTops.size) {
            var end = start
            while (end < lineTops.size &&
                lineBottoms[end] - lineTops[start] <= pageHeight
            ) {
                end++
            }
            if (end == start) end = start + 1
            pages.add(PageLines(start, end))
            start = end
        }
        return pages
    }

    /**
     * 把「行区间」换算成「字符区间」。
     *
     * `Layout.getLineStart(line)` 给出该行首字符在全文中的下标，正好可以直接
     * 与 TTS 回调的 `onRangeStart` 字符偏移对齐，无需再维护一张页码表。
     */
    fun toCharRanges(layout: Layout, pages: List<PageLines>, textLength: Int): List<PageRange> {
        if (pages.isEmpty() || layout.lineCount == 0) return emptyList()
        return pages.map { p ->
            val startLine = p.startLine.coerceIn(0, layout.lineCount - 1)
            val endLine = p.endLine.coerceIn(startLine, layout.lineCount)
            val start = layout.getLineStart(startLine).coerceIn(0, textLength)
            val end = if (endLine >= layout.lineCount) {
                textLength
            } else {
                layout.getLineStart(endLine).coerceIn(start, textLength)
            }
            PageRange(start, end)
        }
    }

    /**
     * 二分查找某个字符偏移落在第几页。越界自动收敛到首 / 末页。
     * 复杂度 O(log n)，适合 `onRangeStart` 这种高频回调。
     */
    fun pageIndexForOffset(ranges: List<PageRange>, offset: Int): Int {
        if (ranges.isEmpty()) return 0
        if (offset <= 0) return 0
        var lo = 0
        var hi = ranges.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (ranges[mid].startOffset <= offset) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}
