package com.tengwear.ttsbookm3e

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tengwear.ttsbookm3e.ui.TtsbookActionButton
import com.tengwear.ttsbookm3e.ui.TtsbookButtonPair
import com.tengwear.ttsbookm3e.ui.theme.TtsbookTheme
import kotlin.math.roundToInt

class ControlPanelActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var bookPath: String? = null
    private var currentBook: Book? = null
    private var currentOffsetInChapter: Int = 0

    private var bookNameState by mutableStateOf<String?>(null)
    private var chapterNameState by mutableStateOf<String?>(null)
    private var chapterIndexState by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ttsbook_prefs", Context.MODE_PRIVATE)
        // 尽早同步主题状态，避免首帧闪现默认紫色
        com.tengwear.ttsbookm3e.ui.theme.SeedColorState.ensureLoaded(this)
        bookPath = intent.getStringExtra("book_path")
        bookNameState = intent.getStringExtra("book_name")
        chapterNameState = intent.getStringExtra("chapter_name")
        chapterIndexState = intent.getIntExtra("chapter_index", 0)
        currentBook = bookPath?.let { BookManager.getBookByPath(it) }
        currentOffsetInChapter = currentBook?.currentOffsetInChapter ?: 0
        setContent { TtsbookTheme { ControlPanelScreen() } }
    }

    override fun onResume() {
        super.onResume()
        // 回到本页时从磁盘重新同步一次主题：
        // 若上次的"实时预览"未点「应用」就离开了页面（如息屏、被系统回收），
        // 这里会把内存中的预览色回滚为已保存的正式配色。
        com.tengwear.ttsbookm3e.ui.theme.SeedColorState.reset()
        com.tengwear.ttsbookm3e.ui.theme.SeedColorState.ensureLoaded(this)
        bookPath?.let {
            currentBook = BookManager.getBookByPath(it)
            val book = currentBook
            if (book != null) {
                chapterIndexState = book.currentChapterIndex
                currentOffsetInChapter = book.currentOffsetInChapter
                val chapter = book.chapters.getOrNull(chapterIndexState)
                if (chapter != null) {
                    chapterNameState =
                        if (chapter.title.isBlank()) "第${chapterIndexState + 1}章" else chapter.title
                }
            }
        }
    }

    @Composable
    private fun ControlPanelScreen() {
        val context = LocalContext.current
        var showSettings by remember { mutableStateOf(false) }
        var showBookmarkDialog by remember { mutableStateOf(false) }
        var showEngineDialog by remember { mutableStateOf(false) }
        var showThemeDialog by remember { mutableStateOf(false) }

        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()

        // ---------- 阅读设置（M3E Slider + AlertDialog）----------
        if (showSettings) {
            SettingsDialog(
                onDismiss = { showSettings = false },
                onSave = { rows, seq, fSize ->
                    prefs.edit().putInt("rows_per_page", rows)
                        .putString("chars_sequence", seq.joinToString(","))
                        .putFloat("font_size", fSize).apply()
                    sendBroadcast(Intent("ACTION_REFRESH_SETTINGS"))
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_LONG).show()
                    showSettings = false
                },
            )
        }

        // ---------- 添加书签（M3E AlertDialog）----------
        if (showBookmarkDialog) {
            var text by remember {
                mutableStateOf(chapterNameState ?: "第${chapterIndexState + 1}章")
            }
            AlertDialog(
                visible = true,
                onDismissRequest = { showBookmarkDialog = false },
                title = {
                    Text(
                        text = "添加书签",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TtsbookActionButton(
                            label = text.ifBlank { "（空标签）" },
                            onClick = {
                                // 循环切换候选标签，避免在手表上弹软键盘
                                val all = bookmarkLabelChoices()
                                val idx = all.indexOf(text)
                                text = all[(idx + 1).mod(all.size)]
                            },
                            secondary = "点击切换标签",
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val book = currentBook
                            if (text.isNotBlank() && bookPath != null && book != null) {
                                currentOffsetInChapter = book.currentOffsetInChapter
                                BookmarkManager(context, bookPath!!).addBookmark(
                                    Bookmark(chapterIndexState, currentOffsetInChapter, text.trim())
                                )
                                Toast.makeText(
                                    context,
                                    "已添加书签：${text.trim()}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            showBookmarkDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text("保存", style = MaterialTheme.typography.labelMedium)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showBookmarkDialog = false },
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

        // ---------- 语音引擎（M3E AlertDialog）----------
        if (showEngineDialog) {
            TtsEngineDialog(
                onDismiss = { showEngineDialog = false },
                onEngineSelected = { packageName ->
                    TtsEngineHelper.saveEnginePackage(context, packageName)
                    Toast.makeText(
                        context,
                        "已选择: ${TtsEngineHelper.getEngineLabel(context, packageName)}\n重启应用后生效",
                        Toast.LENGTH_LONG,
                    ).show()
                    showEngineDialog = false
                },
            )
        }

        // ---------- 主题种子色（M3E Slider 实时预览 + SwitchButton）----------
        if (showThemeDialog) {
            ThemeSeedDialog(
                onDismiss = { showThemeDialog = false },
                onSeedSelected = { index ->
                    // 应用：落盘 + 生效（setSeedColorByIndex 同时更新全局状态）
                    com.tengwear.ttsbookm3e.ui.theme.setSeedColorByIndex(context, index)
                    sendBroadcast(Intent("ACTION_REFRESH_SETTINGS"))
                    Toast.makeText(
                        context,
                        "已应用：${com.tengwear.ttsbookm3e.ui.theme.SeedPaletteChoices.all[index].first}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    showThemeDialog = false
                },
                onSeedPreview = { index ->
                    // 预览：只改内存态，不落盘，取消时可无损回滚
                    val choices = com.tengwear.ttsbookm3e.ui.theme.SeedPaletteChoices.all
                    com.tengwear.ttsbookm3e.ui.theme.SeedColorState.update(
                        choices[index.coerceIn(0, choices.size - 1)].second,
                    )
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
                    // 顶部：书名 / 章节
                    item {
                        ListHeader(
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = bookNameState ?: "未选择书籍",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = chapterNameState.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    // 上一章 / 下一章
                    item {
                        TtsbookButtonPair(
                            leftLabel = "上一章",
                            onLeft = { switchChapter(-1) },
                            rightLabel = "下一章",
                            onRight = { switchChapter(1) },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }

                    item {
                        TtsbookActionButton(
                            label = "添加书签",
                            onClick = { showBookmarkDialog = true },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "书签列表",
                            onClick = {
                                openIfBook {
                                    startActivity(
                                        Intent(context, BookmarkListActivity::class.java)
                                            .putExtra("book_path", it)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "章节列表",
                            onClick = {
                                openIfBook {
                                    startActivity(
                                        Intent(context, ChapterListActivity::class.java)
                                            .putExtra("book_path", it)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "全局搜索",
                            onClick = {
                                startActivity(Intent(context, GlobalSearchActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "倒计时翻页",
                            onClick = {
                                startActivity(Intent(context, CountdownActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "语音引擎",
                            onClick = { showEngineDialog = true },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "阅读设置",
                            onClick = { showSettings = true },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "主题配色",
                            onClick = { showThemeDialog = true },
                            secondary = "种子色：${currentSeedName()}",
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "书架",
                            onClick = {
                                startActivity(Intent(context, BookShelfActivity::class.java))
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "教程",
                            onClick = {
                                startActivity(Intent(context, TutorialActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "关于",
                            onClick = { startActivity(Intent(context, AboutActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item {
                        TtsbookActionButton(
                            label = "返回阅读",
                            onClick = { finish() },
                            secondary = null,
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    // ==================== 语音引擎对话框 ====================

    @Composable
    private fun TtsEngineDialog(
        onDismiss: () -> Unit,
        onEngineSelected: (String?) -> Unit,
    ) {
        val context = LocalContext.current
        val engines = remember { TtsEngineHelper.getAvailableEngines(context) }
        val currentEngine = remember { TtsEngineHelper.getCurrentEnginePackage(context) }

        AlertDialog(
            visible = true,
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "选择语音引擎",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("关闭", style = MaterialTheme.typography.labelMedium)
                }
            },
        ) {
            item {
                EngineOption(
                    label = "系统默认引擎",
                    selected = currentEngine == null,
                    onClick = { onEngineSelected(null) },
                )
            }
            engines.filter { it.packageName.isNotEmpty() }.forEach { engine ->
                item {
                    EngineOption(
                        label = engine.label + if (engine.isSystemDefault) " (默认)" else "",
                        selected = currentEngine == engine.packageName,
                        onClick = { onEngineSelected(engine.packageName) },
                    )
                }
            }
        }
    }

    @Composable
    private fun EngineOption(label: String, selected: Boolean, onClick: () -> Unit) {
        // 选中项用 primaryContainer 高亮，未选中用默认 surface，完全走 colorScheme
        SwitchButton(
            checked = selected,
            onCheckedChange = { onClick() },
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // ==================== 主题种子色对话框 ====================

    @Composable
    private fun ThemeSeedDialog(
        onDismiss: () -> Unit,
        onSeedSelected: (Int) -> Unit,
        onSeedPreview: (Int) -> Unit,
    ) {
        val choices = com.tengwear.ttsbookm3e.ui.theme.SeedPaletteChoices.all
        // 进入对话框时的原始选择，用于「取消」时回滚
        val originalIndex = remember { currentSeedIndex() }
        var index by remember { mutableFloatStateOf(originalIndex.toFloat()) }
        val i = index.roundToInt().coerceIn(0, choices.size - 1)

        AlertDialog(
            visible = true,
            onDismissRequest = {
                onSeedPreview(originalIndex)
                onDismiss()
            },
            title = {
                Text(
                    text = "主题配色",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = choices[i].first,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "旋转表冠实时预览",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            content = {
                item {
                    Slider(
                        value = index,
                        onValueChange = {
                            index = it
                            // 拖动即预览：整机界面立刻跟随换色
                            onSeedPreview(it.roundToInt().coerceIn(0, choices.size - 1))
                        },
                        valueRange = 0f..(choices.size - 1).toFloat(),
                        steps = (choices.size - 2).coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth(0.95f),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSeedSelected(i) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text("应用", style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        onSeedPreview(originalIndex)
                        onDismiss()
                    },
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

    // ==================== 阅读设置对话框（全部使用 M3E Slider）====================

    @Composable
    private fun SettingsDialog(
        onDismiss: () -> Unit,
        onSave: (Int, List<Int>, Float) -> Unit,
    ) {
        var rows by remember { mutableFloatStateOf(prefs.getInt("rows_per_page", 6).toFloat()) }
        var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 18f)) }
        // 每行字符数：用「基础字数 + 首尾缩减」两个滑块表达，避免手输字符串
        var baseChars by remember {
            mutableFloatStateOf(parseCharsBase(prefs.getString("chars_sequence", null)).toFloat())
        }
        var edgeTrim by remember {
            mutableFloatStateOf(parseCharsTrim(prefs.getString("chars_sequence", null)).toFloat())
        }

        val rowsInt = rows.roundToInt().coerceIn(1, 12)
        val fontInt = fontSize.roundToInt().coerceIn(10, 30)
        val baseInt = baseChars.roundToInt().coerceIn(4, 20)
        val trimInt = edgeTrim.roundToInt().coerceIn(0, 4)
        val previewSeq = buildCharsSequence(rowsInt, baseInt, trimInt)

        AlertDialog(
            visible = true,
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "阅读设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    text = "字号 $fontInt · 每页 $rowsInt 行 · 每行 $baseInt" +
                            if (trimInt > 0) "（首尾 −$trimInt）" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            content = {
                item {
                    SettingSlider(
                        label = "字号",
                        value = fontSize,
                        valueRange = 10f..30f,
                        steps = 19,
                        onValueChange = { fontSize = it },
                    )
                }
                item {
                    SettingSlider(
                        label = "每页行数",
                        value = rows,
                        valueRange = 1f..12f,
                        steps = 10,
                        onValueChange = { rows = it },
                    )
                }
                item {
                    SettingSlider(
                        label = "每行字数",
                        value = baseChars,
                        valueRange = 4f..20f,
                        steps = 15,
                        onValueChange = { baseChars = it },
                    )
                }
                item {
                    SettingSlider(
                        label = "首尾缩字",
                        value = edgeTrim,
                        valueRange = 0f..4f,
                        steps = 3,
                        onValueChange = { edgeTrim = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSave(rowsInt, previewSeq, fontInt.toFloat()) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text("确定", style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                Button(
                    onClick = onDismiss,
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

    /** 带标签的 M3E 滑块；标签用 colorScheme 的次级文字色。 */
    @Composable
    private fun SettingSlider(
        label: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        onValueChange: (Float) -> Unit,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth(0.95f),
            )
        }
    }

    // ==================== 业务逻辑 ====================

    private fun sendCommand(command: String) {
        sendBroadcast(Intent("ACTION_CONTROL_COMMAND").putExtra("command", command))
    }

    private fun openIfBook(action: (String) -> Unit) {
        val path = bookPath
        if (path.isNullOrEmpty()) {
            Toast.makeText(this, "请先打开书籍", Toast.LENGTH_SHORT).show()
        } else {
            action(path)
        }
    }

    private fun switchChapter(delta: Int) {
        val path = bookPath
        if (path.isNullOrEmpty()) {
            Toast.makeText(this, "请先打开书籍", Toast.LENGTH_SHORT).show()
            return
        }
        val book = BookManager.getBookByPath(path)
        if (book == null) {
            Toast.makeText(this, "书籍不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val newIndex = chapterIndexState + delta
        if (newIndex < 0) {
            Toast.makeText(this, "已是第一章", Toast.LENGTH_SHORT).show()
            return
        }
        if (newIndex >= book.chapters.size) {
            Toast.makeText(this, "已是最后一章", Toast.LENGTH_SHORT).show()
            return
        }
        chapterIndexState = newIndex
        val chapter = book.chapters[chapterIndexState]
        sendBroadcast(
            Intent("ACTION_CHAPTER_CHANGED").apply {
                putExtra("book_path", book.path)
                putExtra("chapter_index", chapterIndexState)
                putExtra("offset", 0)
            }
        )
        chapterNameState =
            if (chapter.title.isBlank()) "第${chapterIndexState + 1}章" else chapter.title
        currentOffsetInChapter = 0
        Toast.makeText(this, "切换到: ${book.name} - $chapterNameState", Toast.LENGTH_SHORT).show()
    }

    // ---- 书签标签候选（手表上不便输入，改为循环切换）----

    private fun bookmarkLabelChoices(): List<String> = listOf(
        chapterNameState ?: "第${chapterIndexState + 1}章",
        "精彩片段",
        "重要内容",
        "待回顾",
        "笔记",
    )

    // ---- 字符序列 <-> 滑块参数 的互转 ----

    private fun currentSeedIndex(): Int {
        val choices = com.tengwear.ttsbookm3e.ui.theme.SeedPaletteChoices.all
        val seed = com.tengwear.ttsbookm3e.ui.theme.readSeedColor(this)
        val idx = choices.indexOfFirst { it.second == seed }
        return if (idx >= 0) idx else 0
    }

    private fun currentSeedName(): String =
        com.tengwear.ttsbookm3e.ui.theme.SeedPaletteChoices.all[currentSeedIndex()].first

    private fun parseCharsBase(seq: String?): Int {
        val list = parseSeq(seq)
        return list.maxOrNull() ?: 10
    }

    private fun parseCharsTrim(seq: String?): Int {
        val list = parseSeq(seq)
        val base = list.maxOrNull() ?: 10
        val min = list.minOrNull() ?: 8
        return (base - min).coerceIn(0, 4)
    }

    private fun parseSeq(seq: String?): List<Int> {
        val list = seq?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        return list.ifEmpty { listOf(8, 10, 10, 10, 10, 8) }
    }

    /** 依据「行数 / 每行字数 / 首尾缩字」构造 chars_sequence。 */
    private fun buildCharsSequence(rows: Int, base: Int, trim: Int): List<Int> =
        List(rows) { i ->
            val isEdge = i == 0 || i == rows - 1
            (if (isEdge) base - trim else base).coerceAtLeast(1)
        }
}
