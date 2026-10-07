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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import com.tengwear.ttsbookm3e.ui.TtsbookActionButton
import com.tengwear.ttsbookm3e.ui.TtsbookHint
import com.tengwear.ttsbookm3e.ui.TtsbookListItem
import com.tengwear.ttsbookm3e.ui.TtsbookSubHeader
import com.tengwear.ttsbookm3e.ui.theme.TtsbookTheme
import kotlin.math.roundToInt

class ChapterListActivity : AppCompatActivity() {
    private var bookPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookPath = intent.getStringExtra("book_path") ?: ""
        if (bookPath.isEmpty()) {
            Toast.makeText(this, "未指定书籍", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent { TtsbookTheme { ChapterListScreen() } }
    }

    @Composable
    private fun ChapterListScreen() {
        val context = LocalContext.current
        val book = remember { BookManager.getBookByPath(bookPath) }

        if (book == null) {
            Toast.makeText(context, "书籍不存在", Toast.LENGTH_SHORT).show()
            LaunchedEffect(Unit) { finish() }
            return
        }

        val allTitles = remember(book) {
            book.chapters.mapIndexed { i, c ->
                if (c.title.isBlank()) "第${i + 1}章" else c.title
            }
        }
        // 用「章节索引区间」做筛选，避免同名章节在 indexOf 时定位错误
        var query by remember { mutableStateOf("") }
        var showPicker by remember { mutableStateOf(false) }
        val listState = rememberTransformingLazyColumnState()

        val filtered = remember(query, allTitles) {
            if (query.isEmpty()) {
                allTitles.mapIndexed { i, t -> i to t }
            } else {
                allTitles.mapIndexed { i, t -> i to t }
                    .filter { it.second.contains(query, ignoreCase = true) }
            }
        }

        // 章节快速定位：用滑块在全部章节中选取，代替软键盘输入
        if (showPicker) {
            var position by remember {
                mutableFloatStateOf(0f)
            }
            val maxIndex = (allTitles.size - 1).coerceAtLeast(0)
            val current = position.roundToInt().coerceIn(0, maxIndex)

            AlertDialog(
                visible = true,
                onDismissRequest = { showPicker = false },
                title = {
                    Text(
                        text = "跳转章节",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "第 ${current + 1} / ${allTitles.size} 章",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = allTitles.getOrNull(current).orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                content = {
                    item {
                        Slider(
                            value = position,
                            onValueChange = { position = it },
                            valueRange = 0f..maxIndex.toFloat().coerceAtLeast(1f),
                            steps = (maxIndex - 1).coerceAtLeast(0),
                            modifier = Modifier.fillMaxWidth(0.95f),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPicker = false
                            openChapter(current)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text("跳转", style = MaterialTheme.typography.labelMedium)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showPicker = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text("取消", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TtsbookSubHeader(book.name)
                            Text(
                                text = "章节列表 · 共 ${allTitles.size} 章",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    item {
                        TtsbookActionButton(
                            label = if (query.isEmpty()) "跳转 / 搜索章节" else "筛选：$query",
                            onClick = { showPicker = true },
                            secondary = if (query.isEmpty()) null else "点击清除",
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .padding(vertical = 2.dp),
                        )
                    }

                    if (filtered.isEmpty()) {
                        item { TtsbookHint("未匹配到章节", modifier = Modifier.padding(top = 12.dp)) }
                    } else {
                        items(filtered.size) { i ->
                            val (originalIndex, title) = filtered[i]
                            TtsbookListItem(
                                title = title,
                                subtitle = if (originalIndex == book.currentChapterIndex) {
                                    "当前阅读位置"
                                } else {
                                    "第 ${originalIndex + 1} 章"
                                },
                                subtitleColor = if (originalIndex == book.currentChapterIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                onClick = { openChapter(originalIndex) },
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .padding(vertical = 1.dp),
                            )
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

    private fun openChapter(index: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("book_path", bookPath)
            putExtra("chapter_index", index)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}
