package com.tengwear.ttsbookm3e.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 移植过来的测量式分页算法的纯逻辑测试。
 * 不依赖 Android 运行时（只测 computeRanges / 偏移互转这类纯计算），
 * 覆盖正常切页、超高单行、非法输入、偏移越界收敛等边界。
 */
class PaginationTest {

    @Test
    fun splitByRealLineHeight() {
        // 5 行，每行 10px；一屏 25px → 每页最多 2 行
        val tops = intArrayOf(0, 10, 20, 30, 40)
        val bottoms = intArrayOf(10, 20, 30, 40, 50)
        val pages = Pagination.computeRanges(tops, bottoms, 25)
        assertEquals(
            listOf(PageLines(0, 2), PageLines(2, 4), PageLines(4, 5)),
            pages
        )
    }

    @Test
    fun oversizedLineStillGetsItsOwnPage() {
        // 单行超过一屏时不能死循环，必须独占一页
        val tops = intArrayOf(0, 100)
        val bottoms = intArrayOf(100, 200)
        val pages = Pagination.computeRanges(tops, bottoms, 10)
        assertEquals(2, pages.size)
        assertEquals(PageLines(0, 1), pages[0])
        assertEquals(PageLines(1, 2), pages[1])
    }

    @Test
    fun invalidInputReturnsEmpty() {
        assertTrue(Pagination.computeRanges(intArrayOf(), intArrayOf(), 100).isEmpty())
        assertTrue(Pagination.computeRanges(intArrayOf(0), intArrayOf(10, 20), 100).isEmpty())
        assertTrue(Pagination.computeRanges(intArrayOf(0), intArrayOf(10), 0).isEmpty())
    }

    @Test
    fun offsetToPageIndexIsBinarySearchedAndClamped() {
        val ranges = listOf(
            PageRange(0, 100),
            PageRange(100, 220),
            PageRange(220, 300)
        )
        assertEquals(0, Pagination.pageIndexForOffset(ranges, 0))
        assertEquals(0, Pagination.pageIndexForOffset(ranges, 99))
        assertEquals(1, Pagination.pageIndexForOffset(ranges, 100))
        assertEquals(1, Pagination.pageIndexForOffset(ranges, 219))
        assertEquals(2, Pagination.pageIndexForOffset(ranges, 220))
        // 越界一律收敛，绝不让 TTS 回调的异常偏移把页码算飞
        assertEquals(2, Pagination.pageIndexForOffset(ranges, 9999))
        assertEquals(0, Pagination.pageIndexForOffset(emptyList(), 50))
    }

    @Test
    fun pagedChapterMapsPageAndOffsetBothWays() {
        val chapter = PagedChapter(
            text = "0123456789",
            pages = listOf(
                ReaderPage(0, 5, listOf(LineRange(0, 5))),
                ReaderPage(5, 10, listOf(LineRange(5, 10)))
            ),
            lineHeightPx = 20f
        )
        assertEquals(2, chapter.pageCount)
        assertEquals(0, chapter.pageIndexForOffset(4))
        assertEquals(1, chapter.pageIndexForOffset(5))
        assertEquals(5, chapter.offsetForPage(1))
        assertEquals("56789", chapter.pageText(1))
        // 越界页码收敛到末页，不抛异常
        assertEquals("56789", chapter.pageText(99))
        assertEquals(0, chapter.offsetForPage(-5))
    }

    @Test
    fun emptyChapterIsSafe() {
        val chapter = PagedChapter.empty()
        assertEquals(1, chapter.pageCount)
        assertEquals("", chapter.pageText(0))
        assertEquals(0, chapter.pageIndexForOffset(123))
        assertEquals(-1, chapter.highlightedLine(0, 0))
    }
}
