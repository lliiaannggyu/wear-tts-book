package com.tengwear.ttsbookm3e

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.tengwear.ttsbookm3e.ui.TtsbookActionButton
import com.tengwear.ttsbookm3e.ui.TtsbookHint
import com.tengwear.ttsbookm3e.ui.TtsbookListItem
import com.tengwear.ttsbookm3e.ui.theme.TtsbookTheme
import java.io.File

class FilePickerActivity : AppCompatActivity() {

    private var currentDir = File(Environment.getExternalStorageDirectory().absolutePath)
    private val fileList = mutableStateListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "请先在主界面授予“所有文件访问权限”", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23–29：用 READ_EXTERNAL_STORAGE 运行时权限代替 MANAGE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "请先在主界面授予存储权限", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
        refreshList()
        setContent { TtsbookTheme { FilePickerScreen() } }
    }

    @Composable
    private fun FilePickerScreen() {
        val context = LocalContext.current
        val dirState = androidx.compose.runtime.remember { mutableStateOf(currentDir) }
        val listState = rememberTransformingLazyColumnState()

        AppScaffold {
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        ListHeader {
                            Text(
                                text = dirState.value.absolutePath.substringAfterLast('/')
                                    .ifBlank { "/" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                            )
                        }
                    }

                    if (fileList.isEmpty()) {
                        item {
                            TtsbookHint("该目录为空", modifier = Modifier.padding(top = 12.dp))
                        }
                    } else {
                        items(fileList) { file ->
                            TtsbookListItem(
                                title = file.name,
                                subtitle = fileKindLabel(file),
                                subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = {
                                    if (file.isDirectory) {
                                        currentDir = file
                                        dirState.value = file
                                        refreshList()
                                    } else {
                                        if (EncodingDetector.isSupportedFile(file)) {
                                            val intent = Intent()
                                            intent.putExtra(
                                                "selected_file_path",
                                                file.absolutePath,
                                            )
                                            setResult(RESULT_OK, intent)
                                            finish()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "不支持的格式: ${file.extension}",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .padding(vertical = 1.dp),
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        TtsbookActionButton(
                            label = "返回上级",
                            onClick = {
                                val parent = currentDir.parentFile
                                if (parent != null) {
                                    currentDir = parent
                                    dirState.value = parent
                                    refreshList()
                                } else {
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.8f),
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    /** 用中文类别名代替 emoji 图标，符合 M3E 纯文字排版风格。 */
    private fun fileKindLabel(file: File): String = when {
        file.isDirectory -> "文件夹"
        else -> when (file.extension.lowercase()) {
            "txt" -> "文本文件"
            "epub" -> "EPUB 电子书"
            "fb2" -> "FB2 电子书"
            "html", "htm" -> "网页文件"
            "json" -> "JSON 文件"
            "pdf" -> "PDF 文档"
            else -> "文件"
        }
    }

    private fun refreshList() {
        fileList.clear()
        currentDir.listFiles()?.let {
            fileList.addAll(it.sortedWith(compareBy({ !it.isDirectory }, { it.name })))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val parent = currentDir.parentFile
        if (parent != null) {
            currentDir = parent
            refreshList()
        } else {
            super.onBackPressed()
        }
    }
}
