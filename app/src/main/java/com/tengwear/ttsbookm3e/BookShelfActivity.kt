package com.tengwear.ttsbookm3e

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tengwear.ttsbookm3e.ui.TtsbookActionButton
import com.tengwear.ttsbookm3e.ui.TtsbookHint
import com.tengwear.ttsbookm3e.ui.TtsbookListItem
import com.tengwear.ttsbookm3e.ui.theme.TtsbookTheme
import java.io.File

class BookShelfActivity : AppCompatActivity() {

    private val internalFilePickerRequest = 1001
    private val systemFilePickerRequest = 1002
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            BookManager.getAllBooks()
        } catch (e: UninitializedPropertyAccessException) {
            BookManager.init(this)
        }
        setContent { TtsbookTheme { BookShelfScreen() } }
    }

    @Composable
    private fun BookShelfScreen() {
        val context = LocalContext.current
        var books by remember { mutableStateOf(BookManager.getAllBooks()) }
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()

        // 两个 M3E 对话框的状态：导入来源选择 / 删除确认
        var showImportPicker by remember { mutableStateOf(false) }
        var pendingDelete by remember { mutableStateOf<Book?>(null) }

        // ---------- 导入来源选择（M3E AlertDialog）----------
        if (showImportPicker) {
            AlertDialog(
                visible = true,
                onDismissRequest = { showImportPicker = false },
                title = {
                    Text(
                        text = "选择文件管理器",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showImportPicker = false
                            openInternalFilePicker()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text("内置", style = MaterialTheme.typography.labelMedium)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showImportPicker = false
                            openSystemFilePicker()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text("系统", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        }

        // ---------- 删除确认（M3E AlertDialog）----------
        pendingDelete?.let { target ->
            AlertDialog(
                visible = true,
                onDismissRequest = { pendingDelete = null },
                title = {
                    Text(
                        text = "删除确认",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                text = {
                    Text(
                        text = "确定要删除「${target.name}」吗？",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            BookManager.deleteBook(target.path)
                            books = BookManager.getAllBooks()
                            pendingDelete = null
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
                dismissButton = {
                    Button(
                        onClick = { pendingDelete = null },
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
                        ListHeader(
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                        ) {
                            Text(
                                text = "我的书架",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    item {
                        TtsbookActionButton(
                            label = "导入书籍",
                            onClick = { showImportPicker = true },
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .padding(vertical = 2.dp),
                        )
                    }

                    if (books.isEmpty()) {
                        item {
                            TtsbookHint(
                                text = "书架为空\n点击「导入书籍」添加",
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    } else {
                        itemsIndexed(books) { _, book ->
                            val file = File(book.path)
                            val exists = file.exists()
                            val isPdf = book.path.lowercase().endsWith(".pdf")

                            val subtitle = when {
                                !exists -> "文件已丢失"
                                isPdf -> "PDF · 第 ${book.currentChapterIndex + 1} 页"
                                else -> {
                                    val totalChars = try {
                                        file.readText(Charsets.UTF_8).length
                                    } catch (_: Exception) {
                                        1
                                    }
                                    val progress = if (totalChars > 0) {
                                        (book.currentOffsetInChapter * 100 / totalChars)
                                            .coerceIn(0, 99)
                                    } else {
                                        0
                                    }
                                    "第${book.currentChapterIndex + 1}章 $progress%"
                                }
                            }

                            TtsbookListItem(
                                title = book.name,
                                subtitle = subtitle,
                                subtitleColor = if (!exists) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                onClick = {
                                    if (!exists) {
                                        Toast.makeText(context, "文件已丢失", Toast.LENGTH_SHORT).show()
                                        return@TtsbookListItem
                                    }
                                    // 统一通过 MainActivity 打开，PDF / 文本自动识别
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        putExtra("book_path", book.path)
                                        putExtra("chapter_index", book.currentChapterIndex)
                                        putExtra(
                                            "offset_in_chapter",
                                            book.currentOffsetInChapter,
                                        )
                                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    startActivity(intent)
                                    finish()
                                },
                                onLongClick = { pendingDelete = book },
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .padding(vertical = 2.dp),
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

    /** 打开内置文件选择器（供 M3E 导入对话框调用）。 */
    private fun openInternalFilePicker() {
        val intent = Intent(this, FilePickerActivity::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, internalFilePickerRequest)
    }

    private fun openSystemFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "text/plain",
                        "application/pdf",
                        "application/epub+zip",
                        "application/x-mobipocket-ebook",
                        "text/html",
                    ),
                )
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, systemFilePickerRequest)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开系统文件管理器: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            internalFilePickerRequest -> {
                val path = data?.getStringExtra("selected_file_path") ?: return
                processImportedPath(path)
            }
            systemFilePickerRequest -> {
                val uri = data?.data ?: return
                val path = copyUriToInternal(uri)
                if (path == null) {
                    Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show()
                    return
                }
                processImportedPath(path)
            }
        }
    }

    private fun copyUriToInternal(uri: Uri): String? {
        return try {
            val fileName = queryFileName(uri) ?: "imported_${System.currentTimeMillis()}"
            val targetDir = File(filesDir, "imported_books")
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            targetFile.absolutePath
        } catch (e: Exception) {
            Toast.makeText(this, "复制文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
            }
        } catch (_: Exception) {
        }
        return name
    }

    private fun processImportedPath(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val isPdf = file.extension.equals("pdf", ignoreCase = true)

        if (isPdf) {
            val existing = BookManager.getAllBooks().any { it.path == path }
            if (!existing) {
                val book = Book(
                    name = file.nameWithoutExtension,
                    path = path,
                    chapters = listOf(Chapter("PDF", 0, 0)),
                    encoding = "UTF-8",
                )
                BookManager.addBook(book)
            }
            Toast.makeText(this, "已添加 PDF: ${file.name}", Toast.LENGTH_LONG).show()

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("book_path", path)
                putExtra("chapter_index", 0)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setMessage("正在解析章节...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        Thread {
            try {
                val encoding = EncodingDetector.detectEncoding(file)
                val content = BookManager.readFileContent(file, encoding)

                val chapters = BookManager.parseChaptersWithProgress(
                    content, emptyList(),
                ) { progress, chapterCount ->
                    handler.post {
                        progressDialog.progress = progress
                        progressDialog.setMessage("已解析 $chapterCount 章")
                    }
                }

                handler.post {
                    progressDialog.dismiss()
                    if (chapters.isEmpty()) {
                        Toast.makeText(
                            this@BookShelfActivity,
                            "未解析出任何章节",
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@post
                    }

                    val book = Book(
                        name = file.nameWithoutExtension,
                        path = path,
                        chapters = chapters,
                        encoding = encoding.name(),
                    )
                    BookManager.addBook(book)

                    Toast.makeText(
                        this@BookShelfActivity,
                        "已导入: ${book.name} (${chapters.size}章)",
                        Toast.LENGTH_LONG,
                    ).show()

                    val intent = Intent(this@BookShelfActivity, MainActivity::class.java).apply {
                        putExtra("book_path", book.path)
                        putExtra("chapter_index", 0)
                        putExtra("offset_in_chapter", 0)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                handler.post {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@BookShelfActivity,
                        "导入失败: ${e.message}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }.start()
    }
}
