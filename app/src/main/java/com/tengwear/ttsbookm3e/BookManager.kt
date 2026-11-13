package com.tengwear.ttsbookm3e

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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

    /**
     * 惰性兜底初始化。以前这里直接抛 UninitializedPropertyAccessException，
     * 只要系统直接恢复某个非主 Activity（手表上很常见）就会闪退。
     * 现在任何入口都能安全拿到同一个 SharedPreferences。
     */
    private fun storage(): SharedPreferences {
        if (!::prefs.isInitialized) {
            prefs = TtsBookApplication.instance.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            initialized = true
        }
        return prefs
    }

    fun getAllBooks(): List<Book> {
        // 书架 JSON 可能因旧版本格式 / 写入中断而损坏，解析失败时退回空书架，
        // 不能让 Gson 的 JsonSyntaxException 把应用带崩。
        return try {
            val json = storage().getString(KEY_BOOKS, "[]")
            val type = object : TypeToken<List<Book>>() {}.type
            gson.fromJson<List<Book>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("BookManager", "书架数据解析失败，返回空书架", e)
            emptyList()
        }
    }

    fun saveBooks(books: List<Book>) {
        try {
            val json = gson.toJson(books)
            storage().edit().putString(KEY_BOOKS, json).apply()
        } catch (e: Exception) {
            Log.e("BookManager", "保存书架失败", e)
        }
    }

    fun addBook(book: Book) {
        val list = getAllBooks().toMutableList()
        list.add(book)
        saveBooks(list)
    }

    fun deleteBook(path: String) {
        val list = getAllBooks().filter { it.path != path }
        saveBooks(list)
    }

    fun getBookByPath(path: String): Book? {
        return getAllBooks().firstOrNull { it.path == path }
    }

    fun updateBook(book: Book) {
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

        // ★ 自定义章节关键词必须先转义：用户若输入了 "(" "[" "*" 等正则元字符，
        // 直接拼进 Regex 会抛 PatternSyntaxException，导入书籍时直接闪退。
        val pattern = try {
            if (keywords.isNotEmpty()) {
                when (keywords.size) {
                    1 -> """^${Regex.escape(keywords[0])}.*$""".toRegex(RegexOption.MULTILINE)
                    2 -> """${Regex.escape(keywords[0])}.*${Regex.escape(keywords[1])}"""
                        .toRegex(RegexOption.MULTILINE)
                    else -> keywords.joinToString("|") { """^${Regex.escape(it)}.*$""" }
                        .toRegex(RegexOption.MULTILINE)
                }
            } else {
                buildDefaultChapterRegex()
            }
        } catch (e: Exception) {
            Log.e("BookManager", "自定义章节规则非法，回退默认规则", e)
            buildDefaultChapterRegex()
        }
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