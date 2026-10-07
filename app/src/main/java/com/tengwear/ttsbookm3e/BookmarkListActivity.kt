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
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tengwear.ttsbookm3e.ui.TtsbookActionButton
import com.tengwear.ttsbookm3e.ui.TtsbookHint
import com.tengwear.ttsbookm3e.ui.TtsbookListItem
import com.tengwear.ttsbookm3e.ui.theme.TtsbookTheme
import kotlin.math.roundToInt

class BookmarkListActivity : AppCompatActivity() {
    private var bookPath: String = ""
    private lateinit var bookmarkManager: BookmarkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookPath = intent.getStringExtra("book_path") ?: ""
        if (bookPath.isEmpty()) {
            Toast.makeText(this, "未指定书籍", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        bookmarkManager = BookmarkManager(this, bookPath)
        setContent { TtsbookTheme { BookmarkListScreen() } }
    }

    @Composable
    private fun BookmarkListScreen() {
        val context = LocalContext.current
        var allBookmarks by remember { mutableStateOf(bookmarkManager.getBookmarks()) }
        var showPicker by remember { mutableStateOf(false) }
        var selectedIndex by remember { mutableStateOf(-1) }
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()

        val count = allBookmarks.size

        // 书签操作：用 M3E 对话框代替系统 AlertDialog.setItems
        if (selectedIndex in allBookmarks.indices) {
            val bookmark = allBookmarks[selectedIndex]
            AlertDialog(
                visible = true,
                onDismissRequest = { selectedIndex = -1 },
                title = {
                    Text(
                        text = bookmark.label.ifEmpty { "书签" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                },
                text = {
                    Text(
                        text = "第 ${bookmark.chapterIndex + 1} 章",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            startActivity(
                                Intent(context, MainActivity::class.java).apply {
                                    putExtra("book_path", bookPath)
                                    putExtra("chapter_index", bookmark.chapterIndex)
                                    putExtra("offset_in_chapter", bookmark.offsetInChapter)
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                            )
                            selectedIndex = -1
                            finish()
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
                        onClick = {
                            bookmarkManager.removeBookmark(selectedIndex)
                            allBookmarks = bookmarkManager.getBookmarks()
                            selectedIndex = -1
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text("删除", style = MaterialTheme.typography.labelMedium)
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
                        ListHeader(
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                        ) {
                            Text(
                                text = "书签列表 · $count",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    if (count > 0) {
                        item {
                            TtsbookActionButton(
                                label = "按位置快速定位",
                                onClick = { showPicker = true },
                                secondary = "共 $count 条书签",
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .padding(vertical = 2.dp),
                            )
                        }
                    }

                    if (allBookmarks.isEmpty()) {
                        item { TtsbookHint("暂无书签", modifier = Modifier.padding(top = 12.dp)) }
                    } else {
                        itemsIndexed(allBookmarks) { index, bookmark ->
                            TtsbookListItem(
                                title = bookmark.label.ifEmpty { "书签" },
                                subtitle = "第 ${bookmark.chapterIndex + 1} 章",
                                onClick = { selectedIndex = index },
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
}
