package com.tengwear.ttsbookm3e

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tengwear.ttsbookm3e.ui.TtsbookActionButton
import com.tengwear.ttsbookm3e.ui.TtsbookHint
import com.tengwear.ttsbookm3e.ui.TtsbookListItem
import com.tengwear.ttsbookm3e.ui.theme.TtsbookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/**
 * 全局文字搜索：在所有书籍的章节标题和正文中搜索关键字。
 *
 * 手表无软键盘，因此关键字从一份「常用词 + 书内词汇」候选表中循环选取，
 * 完全使用 M3E 组件呈现，颜色全部来自 colorScheme。
 */
class GlobalSearchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TtsbookTheme { SearchScreen() } }
    }

    @Composable
    private fun SearchScreen() {
        val context = LocalContext.current
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()

        var keywordIndex by remember { mutableStateOf(0) }
        var committedQuery by remember { mutableStateOf("") }
        val results = mutableStateListOf<SearchResult>()
        var isSearching by remember { mutableStateOf(false) }

        // 候选关键词从已导入书籍的书名/章节名中提取，更贴合实际内容
        val candidates = remember { buildKeywordCandidates() }

        LaunchedEffect(committedQuery) {
            if (committedQuery.isBlank()) {
                results.clear()
                isSearching = false
                return@LaunchedEffect
            }
            isSearching = true
            val found = withContext(Dispatchers.IO) { doSearch(committedQuery) }
            results.clear()
            results.addAll(found)
            isSearching = false
        }

        AppScaffold {
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        ListHeader(
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                        ) {
                            Text(
                                text = "全局搜索",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    // 关键词选择器：点击循环切换候选词
                    item {
                        val current = candidates.getOrElse(keywordIndex) { "" }
                        TtsbookActionButton(
                            label = current.ifBlank { "无可用关键词" },
                            onClick = {
                                if (candidates.isNotEmpty()) {
                                    keywordIndex = (keywordIndex + 1) % candidates.size
                                }
                            },
                            secondary = "点击切换关键词 · 第 ${keywordIndex + 1}/${candidates.size}",
                            enabled = candidates.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .padding(vertical = 2.dp),
                        )
                    }

                    item {
                        TtsbookActionButton(
                            label = "开始搜索",
                            onClick = {
                                val kw = candidates.getOrNull(keywordIndex).orEmpty().trim()
                                if (kw.isBlank()) {
                                    Toast.makeText(context, "请输入关键字", Toast.LENGTH_SHORT).show()
                                } else {
                                    committedQuery = kw
                                }
                            },
                            enabled = candidates.isNotEmpty() &&
                                    !committedQuery.equals(
                                        candidates.getOrNull(keywordIndex),
                                        ignoreCase = true,
                                    ),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .padding(vertical = 2.dp),
                        )
                    }

                    if (committedQuery.isNotBlank()) {
                        item {
                            Text(
                                text = "「$committedQuery」 · ${results.size} 条结果",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }

                    when {
                        isSearching -> item {
                            TtsbookHint("搜索中…", modifier = Modifier.padding(top = 8.dp))
                        }

                        committedQuery.isNotBlank() && results.isEmpty() -> item {
                            TtsbookHint("未找到匹配结果", modifier = Modifier.padding(top = 8.dp))
                        }

                        results.isNotEmpty() -> items(results) { result ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                TtsbookListItem(
                                    title = result.book.name,
                                    subtitle = "第${result.chapterIndex + 1}章 · " +
                                            result.chapterTitle,
                                    onClick = {
                                        // 统一通过 MainActivity 打开（PDF / 文本自动识别）
                                        val intent = Intent(context, MainActivity::class.java)
                                            .apply {
                                                putExtra("book_path", result.book.path)
                                                putExtra("chapter_index", result.chapterIndex)
                                                putExtra(
                                                    "offset_in_chapter",
                                                    result.matchOffset,
                                                )
                                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            }
                                        startActivity(intent)
                                        finish()
                                    },
                                    modifier = Modifier.fillMaxWidth(0.95f),
                                )
                                if (result.snippet.isNotBlank()) {
                                    Text(
                                        text = result.snippet,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .padding(horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        TtsbookActionButton(
                            label = "返回",
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth(0.8f),
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    /**
     * 构造候选关键词：优先取书名的字与章节标题中的词，最后补上常用词。
     * 这样在无键盘场景下也能贴近真实内容。
     */
    private fun buildKeywordCandidates(): List<String> {
        val out = LinkedHashSet<String>()
        val books = try {
            BookManager.getAllBooks()
        } catch (_: Exception) {
            emptyList()
        }
        books.take(6).forEach { book ->
            if (book.name.length >= 2) out.add(book.name.take(4))
            book.chapters.take(12).forEach { ch ->
                val t = ch.title.trim()
                if (t.length in 2..6) out.add(t)
            }
        }
        out.addAll(listOf("我", "的", "他", "你", "说", "是"))
        return out.filter { it.isNotBlank() }.take(30)
    }

    /**
     * 执行全局搜索（后台线程）。
     */
    private fun doSearch(keyword: String): List<SearchResult> {
        val out = mutableListOf<SearchResult>()
        val books = BookManager.getAllBooks()
        val lowerKeyword = keyword.lowercase()
        val maxResultsPerBook = 50
        val maxTotal = 300

        for (book in books) {
            if (out.size >= maxTotal) break

            // 1. 章节标题搜索
            book.chapters.forEachIndexed { idx, ch ->
                if (out.size >= maxTotal) return@forEachIndexed
                val title = ch.title.ifBlank { "第${idx + 1}章" }
                if (title.lowercase().contains(lowerKeyword)) {
                    out.add(
                        SearchResult(
                            book = book,
                            chapterIndex = idx,
                            chapterTitle = title,
                            snippet = title,
                            matchOffset = 0,
                        )
                    )
                }
            }

            // 2. 正文搜索（PDF 跳过）
            if (out.size >= maxTotal) break
            if (book.path.lowercase().endsWith(".pdf")) continue

            val file = File(book.path)
            if (!file.exists()) continue

            try {
                val charset: Charset = try {
                    Charset.forName(book.encoding)
                } catch (_: Exception) {
                    Charsets.UTF_8
                }
                val content = file.readText(charset)
                var found = 0
                for ((idx, ch) in book.chapters.withIndex()) {
                    if (found >= maxResultsPerBook || out.size >= maxTotal) break
                    val start = ch.startOffset.coerceIn(0, content.length)
                    val end = ch.endOffset.coerceIn(start, content.length)
                    val chapterText = content.substring(start, end)

                    var searchFrom = 0
                    while (found < maxResultsPerBook && out.size < maxTotal) {
                        val matchIdx = chapterText.indexOf(keyword, searchFrom, ignoreCase = true)
                        if (matchIdx < 0) break
                        val snipStart = (matchIdx - 20).coerceAtLeast(0)
                        val snipEnd = (matchIdx + keyword.length + 20)
                            .coerceAtMost(chapterText.length)
                        val snippet = buildString {
                            if (snipStart > 0) append("…")
                            append(chapterText.substring(snipStart, snipEnd))
                            if (snipEnd < chapterText.length) append("…")
                        }
                        out.add(
                            SearchResult(
                                book = book,
                                chapterIndex = idx,
                                chapterTitle = ch.title.ifBlank { "第${idx + 1}章" },
                                snippet = snippet,
                                matchOffset = matchIdx,
                            )
                        )
                        found++
                        searchFrom = matchIdx + keyword.length
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    data class SearchResult(
        val book: Book,
        val chapterIndex: Int,
        val chapterTitle: String,
        val snippet: String,
        val matchOffset: Int,
    )
}
