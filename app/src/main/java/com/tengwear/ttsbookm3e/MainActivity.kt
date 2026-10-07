package com.tengwear.ttsbookm3e

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.tengwear.ttsbookm3e.ui.theme.TtsbookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

@SuppressLint("BroadcastReceiver")
class MainActivity : AppCompatActivity(), TtsBookApplication.CountdownListener {

    // ==================== 服务 ====================
    private var readerService: ReaderService? = null
    private var bound = false
    private var isPlaying by mutableStateOf(false)

    // ==================== 通用 ====================
    private var currentBook: Book? = null
    private var isPdfMode by mutableStateOf(false)

    // ==================== 文本模式状态 ====================
    private var currentChapterIndex by mutableIntStateOf(0)
    private var currentChapterText by mutableStateOf("")
    private var currentOffsetInChapter by mutableIntStateOf(0)
    private var rowsPerPage by mutableIntStateOf(6)
    private var charsSequence by mutableStateOf(listOf(8, 10, 10, 10, 10, 8))
    private var currentPage by mutableIntStateOf(0)
    private var totalPages by mutableIntStateOf(1)
    private var fontSize by mutableStateOf(18f)
    private var lineStartOffsets = mutableListOf<Int>()
    private var lineEndOffsets = mutableListOf<Int>()
    private var isAutoPageTurning = false

    // ==================== PDF 模式状态 ====================
    private var pdfRenderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pdfPath by mutableStateOf("")
    private var pdfName by mutableStateOf("")
    private var pdfPageCount by mutableIntStateOf(0)
    private var pdfCurrentPage by mutableIntStateOf(0)
    private var pdfScale by mutableFloatStateOf(1f)
    private var pdfDefaultScale by mutableFloatStateOf(1f)
    private var pdfShowOverlay by mutableStateOf(true)
    private var pdfLoading by mutableStateOf(false)
    private var pdfBitmap by mutableStateOf<Bitmap?>(null)
    private var pdfLastKnownPage: Int = 0

    private val PDF_ZOOM_PRESETS = listOf(
        0.1f, 0.15f, 0.2f, 0.25f, 0.33f, 0.4f, 0.5f,
        0.67f, 0.75f, 0.9f, 1f,
        1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f,
        3.5f, 4f, 5f, 6f, 7f, 8f, 9f, 10f
    )

    // ==================== 通用状态 ====================
    private var isProgressRestored = false
    private val handler = Handler(Looper.getMainLooper())
    private val longPressThreshold = 500L
    private val doubleClickThreshold = 400L
    private var lastClickTime = 0L
    private lateinit var prefs: SharedPreferences
    private lateinit var progressPrefs: SharedPreferences
    private val FIRST_RUN_KEY = "first_run_done"

    private companion object {
        /** 连续空白折叠成单个空格，用于阅读与 TTS 分句。 */
        val WHITESPACE_REGEX = Regex("\\s+")

        /** API 23–29 的运行时存储权限请求码。 */
        const val REQUEST_STORAGE_PERMISSION = 100
    }

    // ==================== 广播接收器 ====================
    private val progressSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_SAVE_PROGRESS") {
                if (isPdfMode) return
                val offset = intent.getIntExtra("offset", currentOffsetInChapter)
                if (offset > currentOffsetInChapter) currentOffsetInChapter = offset
                saveCurrentProgress()
            }
        }
    }

    private val chapterChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_CHAPTER_CHANGED") {
                if (isPdfMode) return
                val path = intent.getStringExtra("book_path") ?: return
                if (path.lowercase().endsWith(".pdf")) return
                val chapterIdx = intent.getIntExtra("chapter_index", 0)
                val offset = intent.getIntExtra("offset", 0)
                val book = BookManager.getBookByPath(path) ?: return
                readerService?.pause()
                isPlaying = false
                // 广播携带的章节下标可能来自过期的控制面板状态，交由 loadBook 内部统一校验
                val safeIdx = chapterIdx.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
                loadBook(book, safeIdx, offset)
                if (isPlaying) readerService?.resume()
            }
        }
    }

    private val settingsRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_REFRESH_SETTINGS") {
                loadSettings()
                applyNewSettings()
                Toast.makeText(this@MainActivity, "设置已刷新", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val controlCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_CONTROL_COMMAND") {
                when (intent.getStringExtra("command")) {
                    "PLAY_PAUSE" -> togglePlayPause()
                }
            }
        }
    }

    private val addBookmarkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_ADD_BOOKMARK") {
                val label = intent.getStringExtra("label")
                if (label.isNullOrEmpty()) {
                    Toast.makeText(this@MainActivity, "书签标签为空", Toast.LENGTH_SHORT).show()
                    return
                }
                addBookmark(label)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ReaderService.LocalBinder
            readerService = binder.getService()
            bound = true
            readerService?.let {
                it.onHighlight = { offset ->
                    runOnUiThread {
                        if (isPdfMode) return@runOnUiThread
                        currentOffsetInChapter = offset
                        val cpp = getCharsPerPage()
                        if (cpp > 0 && currentPage < totalPages - 1) {
                            val pageEnd = (currentPage + 1) * cpp
                            if (offset >= pageEnd && !isAutoPageTurning) {
                                isAutoPageTurning = true
                                autoNextPage()
                                handler.postDelayed({ isAutoPageTurning = false }, 200)
                            }
                        }
                        if (offset >= currentChapterText.length && currentChapterText.isNotEmpty()) {
                            onChapterEnd()
                        }
                        highlightCurrentLine(offset)
                        saveCurrentProgress()
                    }
                }
                it.onChapterDone = {
                    runOnUiThread { if (!isPdfMode) onChapterEnd() }
                }
                if (!isProgressRestored) restoreProgressIfNeeded()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            readerService = null
        }
    }

    // ==================== 生命周期 ====================

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ttsbook_prefs", Context.MODE_PRIVATE)
        progressPrefs = getSharedPreferences("ttsbook_progress", Context.MODE_PRIVATE)
        BookManager.init(this)
        loadSettings()

        val showPermissionGuide = shouldShowFirstRunDialog()

        val saveFilter = IntentFilter("ACTION_SAVE_PROGRESS")
        val chapterFilter = IntentFilter("ACTION_CHAPTER_CHANGED")
        val refreshFilter = IntentFilter("ACTION_REFRESH_SETTINGS")
        val controlFilter = IntentFilter("ACTION_CONTROL_COMMAND")
        val addBookmarkFilter = IntentFilter("ACTION_ADD_BOOKMARK")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(progressSaveReceiver, saveFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(chapterChangedReceiver, chapterFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(settingsRefreshReceiver, refreshFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(controlCommandReceiver, controlFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(addBookmarkReceiver, addBookmarkFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(progressSaveReceiver, saveFilter)
            registerReceiver(chapterChangedReceiver, chapterFilter)
            registerReceiver(settingsRefreshReceiver, refreshFilter)
            registerReceiver(controlCommandReceiver, controlFilter)
            registerReceiver(addBookmarkReceiver, addBookmarkFilter)
        }

        startReaderService()
        checkStoragePermission()
        checkNotificationPermission()

        handleIntent(intent)
        restoreProgressIfNeeded()

        setContent {
            TtsbookTheme {
                if (isPdfMode) PdfReaderScreen() else TextReaderScreen()

                // 首次启动的存储权限引导：用 M3E AlertDialog，配色自动跟随种子色
                if (showPermissionGuide) {
                    var visible by remember { mutableStateOf(true) }
                    AlertDialog(
                        visible = visible,
                        onDismissRequest = { /* 强制阅读，不允许点外部关闭 */ },
                        title = {
                            Text(
                                text = "需要存储权限",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        text = {
                            Text(
                                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    "请先在电脑上执行以下命令授予存储权限：\n\n" +
                                            "adb shell appops set --uid " +
                                            "com.tengwear.ttsbookm3e MANAGE_EXTERNAL_STORAGE allow\n\n" +
                                            "授权后，点击「去教程」查看使用说明。"
                                } else {
                                    "需要授予存储权限才能读取本机书籍。\n\n" +
                                            "点击「授权」后允许「存储」权限即可。" +
                                            "授权后会自动打开上次阅读的书籍。"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    markFirstRunHandled()
                                    visible = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        startActivity(
                                            Intent(this@MainActivity, TutorialActivity::class.java)
                                        )
                                    } else {
                                        // API 25–29：直接弹运行时权限，授权后自动恢复阅读进度
                                        checkStoragePermission()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            ) {
                                Text(
                                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        "去教程"
                                    } else {
                                        "授权"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = {
                                    markFirstRunHandled()
                                    visible = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Text("跳过", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val path = intent.getStringExtra("book_path") ?: return
        val chapterIdx = intent.getIntExtra("chapter_index", -1)
        val offset = intent.getIntExtra("offset_in_chapter", -1)

        if (path.lowercase().endsWith(".pdf")) {
            val startPage = if (chapterIdx >= 0) chapterIdx else -1
            openPdf(path, startPage)
        } else {
            val book = BookManager.getBookByPath(path)
            if (book != null) {
                val index = if (chapterIdx >= 0 && chapterIdx < book.chapters.size) chapterIdx else 0
                val off = if (offset >= 0) offset else 0
                readerService?.pause()
                isPlaying = false
                isPdfMode = false
                closePdf()
                loadBook(book, index, off)
                // loadBook 内部可能因章节失效而重建章节列表，这里不能用旧的 index 直接取标题
                val title = book.chapters.getOrNull(currentChapterIndex)?.title ?: ""
                Toast.makeText(this, "${book.name} - $title", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TtsBookApplication.instance.addCountdownListener(this)
        TtsBookApplication.instance.resumeCountdown()
        loadSettings()
        applyNewSettings()
        if (!isProgressRestored) restoreProgressIfNeeded()
        readerService?.let { if (it.isPlaying()) isPlaying = true }
    }

    override fun onPause() {
        super.onPause()
        TtsBookApplication.instance.pauseCountdown()
        TtsBookApplication.instance.removeCountdownListener(this)
        if (isPdfMode) {
            if (pdfPath.isNotEmpty()) savePdfProgress(pdfPath, pdfLastKnownPage)
        } else {
            saveCurrentProgress()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isPdfMode) {
            if (pdfPath.isNotEmpty()) savePdfProgress(pdfPath, pdfLastKnownPage)
        } else {
            saveCurrentProgress()
        }
    }

    override fun onDestroy() {
        if (isPdfMode) {
            if (pdfPath.isNotEmpty()) savePdfProgress(pdfPath, pdfLastKnownPage)
        } else {
            saveCurrentProgress()
        }
        super.onDestroy()
        if (bound) {
            readerService?.onHighlight = null
            readerService?.onChapterDone = null
            unbindService(connection)
            bound = false
        }
        closePdf()
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(progressSaveReceiver)
            unregisterReceiver(chapterChangedReceiver)
            unregisterReceiver(settingsRefreshReceiver)
            unregisterReceiver(controlCommandReceiver)
            unregisterReceiver(addBookmarkReceiver)
        } catch (_: Exception) { }
    }

    override fun onCountdownTick(remainingSeconds: Int) {}
    override fun onCountdownFinish() {
        if (isPdfMode) return
        manualNextPage()
    }

    // ==================== 首次启动 ====================

    /**
     * 是否需要展示「存储权限」引导。
     *
     * 只做判断，不再直接弹系统 AlertDialog —— 那个对话框不是 Compose 组件、
     * 也无法使用 M3E 配色。真正的展示由 Compose 侧的 M3E AlertDialog 完成，
     * 这样在手表上排版一致、配色跟随种子色。
     */
    private fun shouldShowFirstRunDialog(): Boolean {
        if (prefs.getBoolean(FIRST_RUN_KEY, false)) return false
        // API 30+：需要用户在系统设置里开「所有文件访问权限」
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return !Environment.isExternalStorageManager()
        }
        // API 25–29：需要的是 READ_EXTERNAL_STORAGE 运行时权限
        return !hasRequiredStorageAccess()
    }

    private fun markFirstRunHandled() {
        prefs.edit().putBoolean(FIRST_RUN_KEY, true).apply()
    }

    // ==================== Compose 界面 ====================

    @Composable
    private fun TextReaderScreen() {
        val pageText = remember(currentPage, currentChapterText, rowsPerPage, charsSequence, fontSize) {
            buildPageText()
        }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }

        // 阅读正文配色全部取自 M3E colorScheme：
        //   背景 → background（tone 0），正文 → onSurface（tone 95）
        val backgroundColor = MaterialTheme.colorScheme.background
        val textColor = MaterialTheme.colorScheme.onSurface

        AppScaffold {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .focusRequester(focusRequester)
                    .focusable()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                val width = size.width
                                val isLeft = offset.x < width / 3f
                                val isRight = offset.x > width * 2f / 3f
                                val isMiddle = !isLeft && !isRight
                                val downTime = System.currentTimeMillis()
                                var consumed = false
                                val lpRunnable = Runnable {
                                    togglePlayPause()
                                    consumed = true
                                }
                                handler.postDelayed(lpRunnable, longPressThreshold)
                                tryAwaitRelease()
                                handler.removeCallbacks(lpRunnable)
                                if (consumed) return@detectTapGestures
                                if (System.currentTimeMillis() - downTime >= longPressThreshold) {
                                    return@detectTapGestures
                                }
                                if (isMiddle) {
                                    val now = System.currentTimeMillis()
                                    if ((now - lastClickTime) < doubleClickThreshold) {
                                        lastClickTime = 0
                                        openControlPanel()
                                        return@detectTapGestures
                                    }
                                    lastClickTime = now
                                } else if (isLeft) {
                                    manualPrevPage()
                                } else if (isRight) {
                                    manualNextPage()
                                }
                            }
                        )
                    }
                    .onRotaryScrollEvent { event ->
                        if (event.verticalScrollPixels > 0) {
                            manualNextPage()
                        } else if (event.verticalScrollPixels < 0) {
                            manualPrevPage()
                        }
                        true
                    }
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pageText,
                    style = TextStyle(
                        fontSize = fontSize.sp,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        lineHeight = (fontSize * 1.4).sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    @Composable
    private fun PdfReaderScreen() {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val scope = rememberCoroutineScope()

        // PDF 叠加层配色全部取自 colorScheme
        val cs = MaterialTheme.colorScheme
        val scrimColor = cs.surfaceContainerLow.copy(alpha = 0.85f)
        val chipColor = cs.primaryContainer
        val onChipColor = cs.onPrimaryContainer
        val accentColor = cs.primaryDim
        val onScrimColor = cs.onSurface
        val accentWash = cs.tertiaryContainer.copy(alpha = 0.8f)
        val onAccentWash = cs.onTertiaryContainer

        val animatedScale by animateFloatAsState(
            targetValue = pdfScale,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "pdfScale"
        )

        val focusRequester = remember { FocusRequester() }
        val verticalScrollState = rememberScrollState()
        val horizontalScrollState = rememberScrollState()

        var resetJob by remember { mutableStateOf<Job?>(null) }
        var tapCount by remember { mutableIntStateOf(0) }
        var lastTapTime by remember { mutableLongStateOf(0L) }

        // 加载当前页
        LaunchedEffect(pdfCurrentPage) {
            pdfLoading = true
            pdfScale = pdfDefaultScale
            pdfBitmap = withContext(Dispatchers.IO) { renderPdfPage(pdfCurrentPage) }
            scope.launch {
                verticalScrollState.scrollTo(0)
                horizontalScrollState.scrollTo(0)
            }
            pdfLoading = false
            pdfLastKnownPage = pdfCurrentPage
            savePdfProgress(pdfPath, pdfCurrentPage)
        }

        LaunchedEffect(Unit) {
            delay(120)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
            delay(2500)
            pdfShowOverlay = false
        }

        AppScaffold {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cs.background)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onRotaryScrollEvent { event ->
                        val delta = event.verticalScrollPixels.toInt()
                        val newValue = (verticalScrollState.value + delta)
                            .coerceIn(0, verticalScrollState.maxValue)
                        scope.launch { verticalScrollState.scrollTo(newValue) }
                        true
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            var isLongPress = false
                            var isScroll = false
                            var isPinching = false
                            var pinchInitialDistance = 0f
                            var pinchInitialScale = pdfScale

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                if (pressed.size >= 2) {
                                    event.changes.forEach { it.consume() }
                                    isPinching = true
                                    val p1 = pressed[0].position
                                    val p2 = pressed[1].position
                                    val dist = (p1 - p2).getDistance()
                                    if (pinchInitialDistance == 0f) {
                                        pinchInitialDistance = dist
                                        pinchInitialScale = pdfScale
                                    } else if (pinchInitialDistance > 1f) {
                                        val ratio = dist / pinchInitialDistance
                                        pdfScale = (pinchInitialScale * ratio).coerceIn(0.1f, 10f)
                                    }
                                    continue
                                }

                                if (isPinching) continue

                                val change = pressed.firstOrNull { it.id == down.id } ?: break
                                val moved = (change.position - change.previousPosition).getDistance()

                                if (!isLongPress && !isScroll) {
                                    if (moved > viewConfiguration.touchSlop) {
                                        isScroll = true
                                        break
                                    }
                                    if (System.currentTimeMillis() - downTime > 500) {
                                        isLongPress = true
                                        pdfShowOverlay = !pdfShowOverlay
                                    }
                                }
                            }

                            if (!isPinching && !isScroll && !isLongPress) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 400) tapCount++ else tapCount = 1
                                lastTapTime = now

                                resetJob?.cancel()
                                resetJob = scope.launch {
                                    delay(450)
                                    val count = tapCount
                                    tapCount = 0
                                    when (count) {
                                        1 -> { pdfShowOverlay = !pdfShowOverlay }
                                        2 -> {
                                            pdfScale = nextPdfPreset(pdfScale) ?: pdfDefaultScale
                                        }
                                        3 -> {
                                            pdfScale = pdfDefaultScale
                                            pdfShowOverlay = true
                                            delay(320)
                                            horizontalScrollState.scrollTo(0)
                                            verticalScrollState.scrollTo(0)
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(50.dp))

                    PdfChipButton(
                        label = "上一页",
                        enabled = pdfCurrentPage > 0,
                        container = chipColor,
                        content = onChipColor,
                        onClick = {
                            if (pdfCurrentPage > 0) pdfCurrentPage--
                            else Toast.makeText(context, "已是第一页", Toast.LENGTH_SHORT).show()
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = configuration.screenWidthDp.dp)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            pdfBitmap?.let { bmp ->
                                val aspect = bmp.height.toFloat() / bmp.width.toFloat()
                                val baseWidthDp = configuration.screenWidthDp.dp
                                val baseHeightDp = baseWidthDp * aspect

                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "PDF page ${pdfCurrentPage + 1}",
                                    modifier = Modifier
                                        .width(baseWidthDp * animatedScale)
                                        .height(baseHeightDp * animatedScale),
                                    contentScale = ContentScale.FillBounds
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    PdfChipButton(
                        label = "下一页",
                        enabled = pdfCurrentPage < pdfPageCount - 1,
                        container = chipColor,
                        content = onChipColor,
                        onClick = {
                            if (pdfCurrentPage < pdfPageCount - 1) pdfCurrentPage++
                            else Toast.makeText(context, "已是最后一页", Toast.LENGTH_SHORT).show()
                        },
                    )

                    Spacer(Modifier.height(50.dp))
                }

                if (pdfLoading) {
                    Text(
                        "加载中…",
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // 顶部信息
                if (pdfShowOverlay && !pdfLoading) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        OverlayChip(
                            text = pdfName,
                            container = scrimColor,
                            content = onScrimColor,
                        )
                        OverlayChip(
                            text = "${pdfCurrentPage + 1} / $pdfPageCount",
                            container = chipColor,
                            content = onChipColor,
                        )
                        OverlayChip(
                            text = "缩放 ${formatPdfScale(pdfScale)}" +
                                    if (kotlin.math.abs(pdfScale - pdfDefaultScale) < 0.005f) {
                                        " (默认)"
                                    } else {
                                        ""
                                    },
                            container = scrimColor,
                            content = onScrimColor,
                        )
                    }
                }

                // 底部控件
                if (pdfShowOverlay && !pdfLoading) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 行1：缩放 [−] [设为默认 X%] [+]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PdfMiniButton(
                                label = "−",
                                container = chipColor,
                                content = onChipColor,
                                onClick = {
                                    pdfScale = prevPdfPreset(pdfScale)
                                    pdfShowOverlay = true
                                },
                            )
                            PdfMiniButton(
                                label = "设为默认 ${formatPdfScale(pdfScale)}",
                                container = chipColor,
                                content = onChipColor,
                                width = 132.dp,
                                onClick = {
                                    pdfDefaultScale = pdfScale
                                    setPdfDefaultScale(pdfPath, pdfScale)
                                    Toast.makeText(
                                        context,
                                        "已保存默认缩放 ${formatPdfScale(pdfScale)}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                            )
                            PdfMiniButton(
                                label = "+",
                                container = chipColor,
                                content = onChipColor,
                                onClick = {
                                    pdfScale = nextPdfPreset(pdfScale) ?: 10f
                                    pdfShowOverlay = true
                                },
                            )
                        }

                        // 行2：翻页
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PdfMiniButton(
                                label = "← 上一页",
                                container = chipColor,
                                content = onChipColor,
                                width = 104.dp,
                                onClick = {
                                    if (pdfCurrentPage > 0) pdfCurrentPage--
                                    else Toast.makeText(context, "已是第一页", Toast.LENGTH_SHORT).show()
                                },
                            )
                            PdfMiniButton(
                                label = "下一页 →",
                                container = chipColor,
                                content = onChipColor,
                                width = 104.dp,
                                onClick = {
                                    if (pdfCurrentPage < pdfPageCount - 1) pdfCurrentPage++
                                    else Toast.makeText(context, "已是最后一页", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }

                        // 行3：书架 / 控制面板
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PdfMiniButton(
                                label = "书架",
                                container = accentWash,
                                content = onAccentWash,
                                width = 104.dp,
                                onClick = {
                                    if (pdfPath.isNotEmpty()) {
                                        savePdfProgress(pdfPath, pdfLastKnownPage)
                                    }
                                    startActivity(
                                        Intent(context, BookShelfActivity::class.java)
                                    )
                                    finish()
                                },
                            )
                            PdfMiniButton(
                                label = "控制面板",
                                container = accentWash,
                                content = onAccentWash,
                                width = 104.dp,
                                onClick = {
                                    val intent = Intent(context, ControlPanelActivity::class.java)
                                    intent.putExtra("book_path", pdfPath)
                                    intent.putExtra("book_name", pdfName)
                                    intent.putExtra(
                                        "chapter_name",
                                        "PDF · 第 ${pdfCurrentPage + 1} 页",
                                    )
                                    intent.putExtra("chapter_index", pdfCurrentPage)
                                    startActivity(intent)
                                },
                            )
                        }

                        // 行4：手势提示
                        OverlayChip(
                            text = "滑动浏览 · 双指缩放 0.1~10x · 双击切档 · 三击复位 · 长按呼出",
                            container = scrimColor,
                            content = onScrimColor,
                        )
                    }
                }
            }
        }
    }

    /** 半透明信息胶囊：颜色来自 colorScheme 的 surface / primary 系列。 */
    @Composable
    private fun OverlayChip(
        text: String,
        container: Color,
        content: Color,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .background(container, MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }

    /** PDF 阅读页的胶囊按钮（M3E Button + 自定义容器色）。 */
    @Composable
    private fun PdfChipButton(
        label: String,
        enabled: Boolean,
        container: Color,
        content: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
            ),
            modifier = modifier.padding(8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }

    /** PDF 底部工具的迷你按钮，支持固定宽度。 */
    @Composable
    private fun PdfMiniButton(
        label: String,
        container: Color,
        content: Color,
        onClick: () -> Unit,
        width: androidx.compose.ui.unit.Dp? = null,
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
            ),
            modifier = if (width != null) Modifier.width(width) else Modifier,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }

    // ==================== PDF 逻辑 ====================

    private fun openPdf(path: String, startPage: Int = -1) {
        closePdf()

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val savedPage = getSavedPdfPage(path)
        val initialPage = if (startPage >= 0) startPage else savedPage

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(pfd!!)
            pdfPageCount = pdfRenderer?.pageCount ?: 0
        } catch (e: Exception) {
            Toast.makeText(this, "PDF打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
            closePdf()
            return
        }

        if (pdfPageCount == 0) {
            Toast.makeText(this, "PDF无有效页面", Toast.LENGTH_SHORT).show()
            closePdf()
            return
        }

        readerService?.pause()
        isPlaying = false

        pdfPath = path
        pdfName = file.nameWithoutExtension
        pdfCurrentPage = initialPage.coerceIn(0, pdfPageCount - 1)
        pdfLastKnownPage = pdfCurrentPage
        pdfDefaultScale = getPdfDefaultScale(path)
        pdfScale = pdfDefaultScale
        pdfShowOverlay = true
        pdfBitmap = null

        currentBook = BookManager.getBookByPath(path)
        isPdfMode = true
    }

    private fun closePdf() {
        try { pdfRenderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        pdfRenderer = null
        pfd = null
        pdfPageCount = 0
        pdfBitmap = null
    }

    private fun renderPdfPage(pageIndex: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
        return try {
            val page = renderer.openPage(pageIndex)
            val targetWidth = 900
            val aspect = page.height.toFloat() / page.width.toFloat()
            var w = targetWidth
            var h = (targetWidth * aspect).toInt().coerceAtLeast(100)

            val maxPixels = 3_500_000L
            if (w.toLong() * h > maxPixels) {
                val s = Math.sqrt(maxPixels.toDouble() / (w.toLong() * h)).toFloat()
                w = (w * s).toInt()
                h = (h * s).toInt()
            }

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun nextPdfPreset(current: Float): Float? =
        PDF_ZOOM_PRESETS.firstOrNull { it > current + 0.005f }

    private fun prevPdfPreset(current: Float): Float =
        PDF_ZOOM_PRESETS.lastOrNull { it < current - 0.005f } ?: 0.1f

    private fun formatPdfScale(s: Float): String = "${(s * 100).toInt()}%"

    private fun getPdfDefaultScale(path: String): Float =
        getSharedPreferences("pdf_prefs", Context.MODE_PRIVATE)
            .getFloat("default_scale_$path", 1f).coerceIn(0.1f, 10f)

    private fun setPdfDefaultScale(path: String, scale: Float) {
        getSharedPreferences("pdf_prefs", Context.MODE_PRIVATE)
            .edit().putFloat("default_scale_$path", scale.coerceIn(0.1f, 10f)).apply()
    }

    private fun getSavedPdfPage(path: String): Int =
        getSharedPreferences("pdf_prefs", Context.MODE_PRIVATE)
            .getInt("last_page_$path", 0)

    private fun savePdfProgress(path: String, page: Int) {
        getSharedPreferences("pdf_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("last_page_$path", page)
            .putLong("last_page_time_$path", System.currentTimeMillis())
            .apply()

        progressPrefs.edit()
            .putString("book_path", path)
            .putInt("chapter_index", page)
            .putInt("offset_in_chapter", 0)
            .putLong("timestamp", System.currentTimeMillis())
            .apply()

        try {
            val book = BookManager.getBookByPath(path)
            if (book != null && book.currentChapterIndex != page) {
                book.currentChapterIndex = page
                book.currentOffsetInChapter = 0
                BookManager.updateBook(book)
            }
        } catch (_: Exception) {}
    }

    // ==================== 文本逻辑 ====================

    private fun buildPageText(): String {
        if (currentChapterText.isEmpty()) return "请从书架导入书籍\n点击进入书架"
        var cpp = getCharsPerPage()
        if (cpp <= 0) cpp = 100
        var charIndex = currentPage * cpp
        val endIndex = min(charIndex + cpp, currentChapterText.length)
        val lines = mutableListOf<String>()
        val seq = charsSequence
        lineStartOffsets.clear()
        lineEndOffsets.clear()
        if (charIndex >= currentChapterText.length) return "（已读至末尾）"
        for (i in 0 until rowsPerPage) {
            if (charIndex >= endIndex) break
            var charsThisLine = if (i < seq.size) seq[i] else seq.lastOrNull() ?: 10
            val remaining = endIndex - charIndex
            if (remaining < charsThisLine) charsThisLine = remaining
            if (remaining == 1 && i < rowsPerPage - 1 && lines.isNotEmpty()) {
                val extendChar = currentChapterText.substring(charIndex, charIndex + 1)
                lineEndOffsets[lineEndOffsets.size - 1] = charIndex + 1
                lines[lines.size - 1] = lines.last() + extendChar.replace(" ", "\u3000")
                charIndex++
                continue
            }
            val lineEnd = min(charIndex + charsThisLine, endIndex)
            var lineText = currentChapterText.substring(charIndex, lineEnd)
            if (lineText.isNotEmpty()) {
                lineText = lineText.replace(" ", "\u3000")
                lines.add(lineText)
                lineStartOffsets.add(charIndex)
                lineEndOffsets.add(lineEnd)
            }
            charIndex = lineEnd
        }
        if (lines.size > 1 && lines.last().length < 2) {
            val lastLine = lines.removeAt(lines.size - 1)
            val lastEnd = lineEndOffsets.removeAt(lineEndOffsets.size - 1)
            if (lines.isNotEmpty()) {
                lines[lines.size - 1] = lines.last() + lastLine
                lineEndOffsets[lines.size - 1] = lastEnd
            }
        }
        return lines.joinToString("\n")
    }

    private fun openControlPanel() {
        if (currentBook == null) {
            startActivity(Intent(this, BookShelfActivity::class.java))
        } else if (isPdfMode) {
            startActivity(Intent(this, BookShelfActivity::class.java))
        } else {
            val intent = Intent(this, ControlPanelActivity::class.java)
            intent.putExtra("book_path", currentBook!!.path)
            intent.putExtra("book_name", currentBook!!.name)
            val chapter = currentBook!!.chapters[currentChapterIndex]
            intent.putExtra("chapter_name", chapter.title)
            intent.putExtra("chapter_index", currentChapterIndex)
            startActivity(intent)
        }
    }

    private fun togglePlayPause() {
        if (isPdfMode) {
            Toast.makeText(this, "PDF 不支持朗读", Toast.LENGTH_SHORT).show()
            return
        }
        val service = readerService ?: run {
            Toast.makeText(this, "朗读服务未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentChapterText.isEmpty()) {
            Toast.makeText(this, "没有可朗读的内容", Toast.LENGTH_SHORT).show()
            return
        }
        if (service.isPlaying()) {
            service.pause()
            isPlaying = false
            Toast.makeText(this, "已暂停", Toast.LENGTH_SHORT).show()
        } else {
            if (currentOffsetInChapter >= currentChapterText.length) {
                currentOffsetInChapter = 0
                currentPage = 0
                service.seekToOffset(0)
            }
            service.resume()
            isPlaying = true
            Toast.makeText(this, "继续朗读", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addBookmark(label: String) {
        if (currentBook == null || isPdfMode) {
            Toast.makeText(this, "PDF 不支持书签", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val bookmark = Bookmark(currentChapterIndex, currentOffsetInChapter, label)
            BookmarkManager(this, currentBook!!.path).addBookmark(bookmark)
            Toast.makeText(this, "✅ 已添加书签：$label", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存书签失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autoNextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++
            saveCurrentProgress()
        } else {
            onChapterEnd()
        }
    }

    private fun manualNextPage() {
        if (isPdfMode) return
        if (currentPage < totalPages - 1) {
            currentPage++
            val cpp = getCharsPerPage()
            currentOffsetInChapter = (currentPage * cpp).coerceAtMost(currentChapterText.length)
            readerService?.seekToOffset(currentOffsetInChapter)
            saveCurrentProgress()
        } else {
            val book = currentBook ?: return
            if (currentChapterIndex < book.chapters.size - 1) {
                switchToChapter(currentChapterIndex + 1, 0)
            } else {
                Toast.makeText(this, "已是最后一章", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun manualPrevPage() {
        if (isPdfMode) return
        if (currentPage > 0) {
            currentPage--
            val cpp = getCharsPerPage()
            currentOffsetInChapter = (currentPage * cpp).coerceAtMost(currentChapterText.length)
            readerService?.seekToOffset(currentOffsetInChapter)
            saveCurrentProgress()
        } else {
            val book = currentBook ?: return
            if (currentChapterIndex > 0) {
                val prevChapter = currentChapterIndex - 1
                val prevBook = BookManager.getBookByPath(book.path) ?: return
                if (prevChapter >= prevBook.chapters.size) {
                    Toast.makeText(this, "已是第一章", Toast.LENGTH_SHORT).show()
                    return
                }
                val prevData = prevBook.chapters[prevChapter]
                val fullText = readBookText(prevBook)
                val prevText = sliceChapterText(fullText, prevData)
                val cpp = getCharsPerPage()
                val total = if (cpp > 0) ceil(prevText.length.toDouble() / cpp).toInt() else 1
                switchToChapter(prevChapter, (total - 1).coerceAtLeast(0))
            } else {
                Toast.makeText(this, "已是第一章", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onChapterEnd() {
        val book = currentBook ?: return
        if (currentChapterIndex < book.chapters.size - 1) {
            switchToChapter(currentChapterIndex + 1, 0)
        } else {
            readerService?.pause()
            isPlaying = false
            Toast.makeText(this, "全书朗读完毕", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchToChapter(newChapterIndex: Int, targetPage: Int) {
        val book = currentBook ?: return
        if (newChapterIndex < 0 || newChapterIndex >= book.chapters.size) return
        readerService?.pause()
        isPlaying = false
        val chapter = book.chapters[newChapterIndex]
        val file = File(book.path)
        if (!file.exists()) {
            Toast.makeText(this, "文件丢失", Toast.LENGTH_SHORT).show()
            return
        }
        val fullText = readBookText(book)
        currentChapterText = sliceChapterText(fullText, chapter)
        currentChapterIndex = newChapterIndex
        currentOffsetInChapter = 0
        val cpp = getCharsPerPage()
        totalPages = if (cpp > 0) ceil(currentChapterText.length.toDouble() / cpp).toInt() else 1
        if (totalPages < 1) totalPages = 1
        val page = targetPage.coerceIn(0, totalPages - 1)
        currentPage = page
        currentOffsetInChapter = (page * cpp).coerceIn(0, currentChapterText.length)
        readerService?.setChapter(currentChapterText)
        readerService?.seekToOffset(currentOffsetInChapter)
        saveCurrentProgress()
        readerService?.resume()
        isPlaying = true
    }

    /**
     * 按书本记录的编码读取全文。
     *
     * 解析章节时用的是 EncodingDetector 检测出的编码，读取时必须保持一致，
     * 否则同一份文件的字符长度会不同，章节偏移量随即失效。
     */
    private fun readBookText(book: Book): String {
        val file = File(book.path)
        val charset = EncodingDetector.getCharsetByName(book.encoding) ?: Charsets.UTF_8
        return BookManager.readFileContent(file, charset)
    }

    /**
     * 安全截取章节文本：偏移量越界时自动收敛到文件范围内，绝不抛异常。
     */
    private fun sliceChapterText(fullText: String, chapter: Chapter): String {
        val start = chapter.startOffset.coerceIn(0, fullText.length)
        val end = chapter.endOffset.coerceIn(start, fullText.length)
        return fullText.substring(start, end).replace(WHITESPACE_REGEX, " ")
    }

    /**
     * 校验持久化的章节信息是否仍然匹配磁盘上的文件。
     *
     * 返回值为 (可用章节列表, 是否发生了变更)。
     * 当所有章节偏移都落在当前文件长度内时，直接复用，避免每次打开都重新解析。
     */
    private fun ensureChaptersValid(book: Book, fullText: String): Pair<List<Chapter>, Boolean> {
        val fileLength = fullText.length
        val chapters = book.chapters

        if (chapters.isEmpty()) {
            val reparsed = BookManager.parseChapters(fullText)
            val fallback = if (reparsed.isEmpty()) listOf(Chapter("全书", 0, fileLength)) else reparsed
            return fallback to true
        }

        val offsetsValid = chapters.all {
            it.startOffset in 0..fileLength &&
                it.endOffset in it.startOffset..fileLength
        }
        if (offsetsValid && chapters.last().endOffset == fileLength) {
            return chapters to false
        }

        // 偏移量已失效（文件被替换 / 截断 / 编码变化），按当前内容重新解析章节
        val reparsed = BookManager.parseChapters(fullText)
        val fallback = if (reparsed.isEmpty()) listOf(Chapter("全书", 0, fileLength)) else reparsed
        return fallback to true
    }

    private fun loadBook(book: Book, chapterIndex: Int, offsetInChapter: Int = 0) {
        readerService?.stopReading()
        isPlaying = false

        val file = File(book.path)
        if (!file.exists()) {
            Toast.makeText(this, "文件丢失", Toast.LENGTH_SHORT).show()
            return
        }

        val fullText = readBookText(book)

        // 章节偏移量可能来自旧版本的文件（文件被替换 / 编码变化 / 截断），
        // 此时持久化的 offset 会超出当前文件长度，必须先校验再使用，否则 substring 越界闪退。
        val (resolvedChapters, chaptersChanged) = ensureChaptersValid(book, fullText)
        var targetChapterIndex = chapterIndex
        var targetOffset = offsetInChapter
        if (chaptersChanged) {
            book.chapters = resolvedChapters
            book.currentChapterIndex = 0
            book.currentOffsetInChapter = 0
            BookManager.updateBook(book)
            targetChapterIndex = 0
            targetOffset = 0
        }

        if (book.chapters.isEmpty()) {
            Toast.makeText(this, "无法解析章节", Toast.LENGTH_SHORT).show()
            return
        }

        val safeChapterIndex = targetChapterIndex.coerceIn(0, book.chapters.size - 1)
        currentBook = book
        currentChapterIndex = safeChapterIndex
        currentOffsetInChapter = targetOffset
        progressPrefs.edit().apply {
            putString("book_path", book.path)
            putInt("chapter_index", safeChapterIndex)
            putInt("offset_in_chapter", targetOffset)
            commit()
        }
        updateBookManagerProgress()

        val chapter = book.chapters[safeChapterIndex]
        currentChapterText = sliceChapterText(fullText, chapter)
        var offset = targetOffset
        if (offset < 0 || offset > currentChapterText.length) offset = 0
        currentOffsetInChapter = offset
        val cpp = getCharsPerPage()
        if (cpp > 0) {
            currentPage = offset / cpp
            totalPages = ceil(currentChapterText.length.toDouble() / cpp).toInt()
            if (totalPages < 1) totalPages = 1
            if (currentPage >= totalPages) currentPage = totalPages - 1
        } else {
            currentPage = 0
            totalPages = 1
        }
        readerService?.setChapter(currentChapterText)
        readerService?.seekToOffset(currentOffsetInChapter)
        if (isPlaying) readerService?.resume()
    }

    private fun updateBookManagerProgress() {
        val book = currentBook ?: return
        if (book.currentChapterIndex != currentChapterIndex || book.currentOffsetInChapter != currentOffsetInChapter) {
            book.currentChapterIndex = currentChapterIndex
            book.currentOffsetInChapter = currentOffsetInChapter
            BookManager.updateBook(book)
        }
    }

    private fun saveCurrentProgress() {
        if (currentBook == null) return
        if (isPdfMode) return
        progressPrefs.edit().apply {
            putString("book_path", currentBook!!.path)
            putInt("chapter_index", currentChapterIndex)
            putInt("offset_in_chapter", currentOffsetInChapter)
            putLong("timestamp", System.currentTimeMillis())
        }.commit()
        updateBookManagerProgress()
    }

    private fun restoreProgressIfNeeded() {
        if (isProgressRestored) return

        // API 23–29 缺少存储权限时读不到书，此时不要标记为已恢复，
        // 否则用户授权后 onRequestPermissionsResult 里的重试会被这个标志挡住。
        if (!hasRequiredStorageAccess()) {
            return
        }

        val savedPath = progressPrefs.getString("book_path", null)

        if (savedPath == null) {
            val books = BookManager.getAllBooks()
            if (books.isNotEmpty()) openBook(books[0].path, 0, 0)
            isProgressRestored = true
            return
        }

        val savedChapter = progressPrefs.getInt("chapter_index", 0)
        val savedOffset = progressPrefs.getInt("offset_in_chapter", 0)
        val book = BookManager.getBookByPath(savedPath)
        if (book == null) {
            val fileName = File(savedPath).name
            val allBooks = BookManager.getAllBooks()
            val matched = allBooks.find { File(it.path).name == fileName }
            if (matched != null) {
                progressPrefs.edit().putString("book_path", matched.path).commit()
                openBook(matched.path, savedChapter, savedOffset)
            } else if (allBooks.isNotEmpty()) {
                progressPrefs.edit().apply {
                    putString("book_path", allBooks[0].path)
                    putInt("chapter_index", 0)
                    putInt("offset_in_chapter", 0)
                }.commit()
                openBook(allBooks[0].path, 0, 0)
            }
            isProgressRestored = true
            return
        }
        openBook(savedPath, savedChapter, savedOffset)
        isProgressRestored = true
    }

    private fun openBook(path: String, chapterIndex: Int = 0, offsetInChapter: Int = 0) {
        if (path.lowercase().endsWith(".pdf")) {
            openPdf(path, chapterIndex)
        } else {
            val book = BookManager.getBookByPath(path) ?: return
            isPdfMode = false
            closePdf()
            loadBook(book, chapterIndex, offsetInChapter)
        }
    }

    private fun highlightCurrentLine(charOffset: Int) {
        if (lineStartOffsets.isEmpty()) return
        val cpp = getCharsPerPage()
        val pageStart = currentPage * cpp
        val pageEnd = min(pageStart + cpp, currentChapterText.length)
        if (charOffset < pageStart || charOffset >= pageEnd) return
        for (i in lineStartOffsets.indices) {
            if (charOffset >= lineStartOffsets[i] && charOffset < lineEndOffsets[i]) {
                return
            }
        }
    }

    private fun loadSettings() {
        rowsPerPage = prefs.getInt("rows_per_page", 6)
        val seqStr = prefs.getString("chars_sequence", "8,10,10,10,10,8")
        charsSequence = seqStr?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: listOf(8, 10, 10, 10, 10, 8)
        if (charsSequence.isEmpty()) charsSequence = listOf(8, 10, 10, 10, 10, 8)
        fontSize = prefs.getFloat("font_size", 18f)
    }

    private fun applyNewSettings() {
        if (currentChapterText.isNotEmpty()) {
            val cpp = getCharsPerPage()
            totalPages = if (cpp > 0) ceil(currentChapterText.length.toDouble() / cpp).toInt() else 1
        }
    }

    private fun getCharsPerPage(): Int {
        val total = charsSequence.take(rowsPerPage).sum()
        return if (total > 0) total else 100
    }

    /**
     * 当前是否已经拿到读取本地书所需的存储权限。
     *
     * - API 30+ ：MANAGE_EXTERNAL_STORAGE（走系统设置页，用 isExternalStorageManager 判断）
     * - API 23–29：READ_EXTERNAL_STORAGE 运行时权限
     * - API 25 以下（本项目不会出现）：视为已授权
     */
    private fun hasRequiredStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkStoragePermission() {
        // API 30+ 走 MANAGE_EXTERNAL_STORAGE（系统设置页授权），由首次运行引导负责；
        // API 23–29 没有 MANAGE_EXTERNAL_STORAGE，必须走运行时权限，否则读不到文件。
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M until Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION,
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Toast.makeText(
                    this,
                    "未授予存储权限，无法读取本地书籍",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                restoreProgressIfNeeded()
            }
        }
    }
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun startReaderService() {
        val intent = Intent(this, ReaderService::class.java)
        // startForegroundService 是 API 26 引入的：要求服务在 5 秒内调用 startForeground()。
        // API 25 及以下没有该方法，直接 startService 即可（ReaderService.onCreate 里本就会 startForeground）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
}