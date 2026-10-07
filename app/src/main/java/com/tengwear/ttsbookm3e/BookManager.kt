package com.tengwear.ttsbookm3e

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.charset.Charset

data class Chapter(val title: String, val startOffset: Int, val endOffset: Int)
data class Book(
    val name: String,
    val path: String,
    var chapters: List<Chapter>,
    val encoding: String = "UTF-8",
    var currentChapterIndex: Int = 0,
    var currentOffsetInChapter: Int = 0
)

object BookManager {
    private const val PREFS_NAME = "books_prefs"
    private const val KEY_BOOKS = "books_list"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            initialized = true
        }
    }

    private fun ensureInitialized() {
        if (!::prefs.isInitialized) {
            throw UninitializedPropertyAccessException("BookManager has not been initialized. Call BookManager.init(context) first.")
        }
    }

    fun getAllBooks(): List<Book> {
        ensureInitialized()
        val json = prefs.getString(KEY_BOOKS, "[]")
        val type = object : TypeToken<List<Book>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveBooks(books: List<Book>) {
        ensureInitialized()
        val json = gson.toJson(books)
        prefs.edit().putString(KEY_BOOKS, json).apply()
    }

    fun addBook(book: Book) {
        ensureInitialized()
        val list = getAllBooks().toMutableList()
        list.add(book)
        saveBooks(list)
    }

    fun deleteBook(path: String) {
        ensureInitialized()
        val list = getAllBooks().filter { it.path != path }
        saveBooks(list)
    }

    fun getBookByPath(path: String): Book? {
        ensureInitialized()
        return getAllBooks().firstOrNull { it.path == path }
    }

    fun updateBook(book: Book) {
        ensureInitialized()
        val list = getAllBooks().toMutableList()
        val index = list.indexOfFirst { it.path == book.path }
        if (index >= 0) {
            list[index] = book
            saveBooks(list)
        }
    }

    fun readFileContent(file: File, charset: Charset): String {
        return file.readText(charset)
    }

    fun parseChaptersWithProgress(
        content: String,
        keywords: List<String> = emptyList(),
        onProgress: (progress: Int, chapterCount: Int) -> Unit
    ): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        if (content.isEmpty()) return chapters

        val patternStr = if (keywords.isNotEmpty()) {
            when (keywords.size) {
                1 -> """^${keywords[0]}.*$""".toRegex(RegexOption.MULTILINE)
                2 -> """${keywords[0]}.*${keywords[1]}""".toRegex(RegexOption.MULTILINE)
                else -> keywords.joinToString("|") { """^$it.*$""" }.toRegex(RegexOption.MULTILINE)
            }
        } else {
            buildDefaultChapterRegex()
        }

        val pattern = patternStr
        var lastMatchEnd = 0
        var matchResult = pattern.find(content)
        var lastTitle = "开始"
        var lastStart = 0
        var chapterCount = 0
        val totalLength = content.length

        while (matchResult != null) {
            val title = matchResult.value.trim()
            val start = matchResult.range.first
            if (lastStart < start) {
                chapters.add(Chapter(lastTitle, lastStart, start))
                chapterCount++
                val progress = if (totalLength > 0) {
                    ((start.toFloat() / totalLength) * 100).toInt().coerceIn(0, 100)
                } else 0
                onProgress(progress, chapterCount)
            }
            lastTitle = title
            lastStart = start
            lastMatchEnd = matchResult.range.last + 1
            matchResult = pattern.find(content, lastMatchEnd)
        }
        if (lastStart < content.length) {
            chapters.add(Chapter(lastTitle, lastStart, content.length))
            chapterCount++
        }
        if (chapters.isEmpty()) {
            chapters.add(Chapter("全书", 0, content.length))
        }
        onProgress(100, chapters.size)
        return chapters
    }

    private fun buildDefaultChapterRegex(): Regex {
        val patterns = listOf(
            """第[一二三四五六七八九十百千万0-9]+[章节回卷部篇集][\s　]*[^\n]*""",
            """第[一二三四五六七八九十百千万0-9]+部分[\s　]*[^\n]*""",
            """第[一二三四五六七八九十百千万0-9]+节[\s　]*[^\n]*""",
            """[一二三四五六七八九十百千万]+[章回节][\s　]*[^\n]*""",
            """楔子[\s　]*[^\n]*""",
            """序言[\s　]*[^\n]*""",
            """前言[\s　]*[^\n]*""",
            """引子[\s　]*[^\n]*""",
            """尾声[\s　]*[^\n]*""",
            """后记[\s　]*[^\n]*""",
            """附录[\s　]*[^\n]*""",
            """(?:Chapter|Ch\.|Part|Section)\s+[0-9IVX]+[\s　]*[^\n]*""",
            """^\d+\.\d*[\s　]*[^\n]*""",
            """^\d+\.[\s　]*[^\n]*"""
        )
        val combined = patterns.joinToString("|")
        return Regex(combined, RegexOption.MULTILINE)
    }

    fun parseChapters(content: String, keywords: List<String> = emptyList()): List<Chapter> {
        return parseChaptersWithProgress(content, keywords) { _, _ -> }
    }
}