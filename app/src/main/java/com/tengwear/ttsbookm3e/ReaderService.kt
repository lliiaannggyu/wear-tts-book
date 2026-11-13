package com.tengwear.ttsbookm3e

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * 后台朗读服务。
 *
 * 对外的 API（setChapter / seekToOffset / pause / resume / onHighlight /
 * onChapterDone 等）保持不变，MainActivity 无需感知内部实现变化。
 *
 * 本次修复的要点：
 *  1. 先 startForeground 再初始化 TTS —— 原实现若 TTS 构造抛异常，
 *     startForeground 永远不执行，系统会以「前台服务未及时启动」杀掉应用（闪退）。
 *  2. 引擎包名做有效性校验并 try-catch 构造，失效时只回退一次，避免无限递归。
 *  3. 整章文本不再一次性 speak（引擎对单次文本长度有上限，会 ERROR / 抛异常），
 *     改为按句切成短块排队朗读。
 *  4. 修正朗读进度偏移：onRangeStart 给的是「相对当前朗读块」的偏移，
 *     原先被直接当作章节全局偏移，导致高亮/自动翻页错位；现在换算成全局偏移。
 *  5. 用播放代次（generation）作废旧队列的回调，避免切章/暂停时误触发
 *     onChapterDone 造成连跳章节。
 */
class ReaderService : Service() {

    private val binder = LocalBinder()
    private var tts: TextToSpeech? = null

    // 当前章节全文及当前朗读偏移（全局偏移，相对于章节开头）
    private var chapterText: String = ""
    private var currentOffset: Int = 0

    /** 按句切好的朗读块，startOffset 是块首在章节全文中的偏移。 */
    private var chunks: List<SpeechChunk> = emptyList()
    private var playbackGeneration = 0
    private var rangeStartSeen = false
    private var pendingResume = false

    private var isPaused = false
    private var isReading = false

    // 当前使用的引擎包名，null 表示系统默认
    private var currentEnginePackage: String? = null
    private var usingFallbackEngine = false

    // 回调接口
    var onHighlight: ((offset: Int) -> Unit)? = null   // 每读到一个词回调当前全局偏移
    var onChapterDone: (() -> Unit)? = null            // 全章朗读完成回调

    private data class SpeechChunk(val startOffset: Int, val text: String)

    inner class LocalBinder : Binder() {
        fun getService(): ReaderService = this@ReaderService
    }

    private companion object {
        const val TAG = "ReaderService"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "reader_channel"
        /**
         * 很多 TTS 引擎对单次 speak 的文本长度有上限（部分约 4000 字符），
         * 整章一次性送入会返回 ERROR 甚至直接抛异常。按句切成短块更稳妥，
         * 同时让 onRangeStart 的偏移量保持在可换算的短区间内。
         */
        const val MAX_CHUNK_LENGTH = 600
        const val ID_PREFIX = "ttsbook"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // ★ 关键修复：先进入前台，再做可能抛异常的 TTS 初始化。
        // startForegroundService 要求 5 秒内调用 startForeground，否则系统抛
        // ForegroundServiceDidNotStartInTimeException，表现为「一打开就闪退」。
        try {
            val notification = createNotification("初始化中")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
        }

        val prefs = getSharedPreferences("ttsbook_prefs", Context.MODE_PRIVATE)
        currentEnginePackage = prefs.getString("tts_engine_package", null)
        initTts()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "听书服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "后台朗读通知"
                setSound(null, null)
            }
            try {
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "创建通知渠道失败", e)
            }
        }
    }

    private fun initTts() {
        // 用户可能选过一个后来被卸载 / 为空的引擎包名，直接拿它构造
        // TextToSpeech 会失败甚至抛异常。这里先校验再决定用哪个引擎。
        val saved = currentEnginePackage?.takeIf { it.isNotBlank() }
        val usableEngine = saved?.let {
            if (TtsEngineHelper.isEngineInstalled(this, it)) it else null
        }
        if (saved != null && usableEngine == null) {
            Log.w(TAG, "已保存的 TTS 引擎不可用，回退系统默认: $saved")
            currentEnginePackage = null
        }
        if (usableEngine == null) usingFallbackEngine = true

        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null

        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupTtsLanguage()
            } else {
                Log.e(TAG, "TTS 初始化失败，状态码: $status")
                // 只回退一次，避免指定引擎反复失败导致无限递归初始化
                if (!usingFallbackEngine) {
                    usingFallbackEngine = true
                    currentEnginePackage = null
                    initTts()
                } else {
                    Toast.makeText(
                        this,
                        "TTS 引擎初始化失败，请检查语音引擎设置",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        try {
            tts = if (usableEngine != null) {
                TextToSpeech(this, listener, usableEngine)
            } else {
                TextToSpeech(this, listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS 构造异常", e)
            tts = null
            Toast.makeText(this, "语音引擎不可用，请检查 TTS 设置", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupTtsLanguage() {
        // 设置语言（中文优先）
        val locales = listOf(
            Locale.CHINESE,
            Locale.SIMPLIFIED_CHINESE,
            Locale.TRADITIONAL_CHINESE,
            Locale("zh", "CN"),
            Locale("zh", "TW"),
            Locale.getDefault()
        )
        var languageSet = false
        for (locale in locales) {
            val result = try {
                tts?.setLanguage(locale)
            } catch (_: Exception) {
                null
            }
            if (result != null &&
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                languageSet = true
                Log.d(TAG, "TTS 语言设置成功: ${locale.displayName}")
                break
            }
        }
        if (!languageSet) {
            val enResult = try {
                tts?.setLanguage(Locale.ENGLISH)
            } catch (_: Exception) {
                null
            }
            if (enResult != null &&
                enResult != TextToSpeech.LANG_MISSING_DATA &&
                enResult != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.w(TAG, "中文不支持，回退到英语")
            } else {
                Log.e(TAG, "所有语言设置失败")
                Toast.makeText(this, "当前引擎不支持中文，请在设置中更换引擎", Toast.LENGTH_LONG).show()
            }
        }

        setupUtteranceListener()
        if (pendingResume) {
            pendingResume = false
            speakFromOffset(currentOffset.coerceIn(0, chapterText.length))
        }
    }

    private fun setupUtteranceListener() {
        try {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isReading = true
                    isPaused = false
                    // 部分引擎不回调 onRangeStart：至少在块开始时同步一次进度，
                    // 保证低版本 / 简化引擎下自动翻页仍能工作。
                    if (!rangeStartSeen) {
                        val idx = chunkIndexOf(utteranceId)
                        if (idx >= 0) {
                            val global = chunks[idx].startOffset.coerceIn(0, chapterText.length)
                            currentOffset = global
                            onHighlight?.invoke(global)
                        }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    val idx = chunkIndexOf(utteranceId)
                    // 只有「最后一个朗读块」完成才算整章读完，触发自动翻章节。
                    if (idx >= 0 && idx == chunks.lastIndex && chunks.isNotEmpty()) {
                        Log.d(TAG, "整章朗读完成")
                        isReading = false
                        onChapterDone?.invoke()
                    }
                }

                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS 朗读错误: $utteranceId")
                    isReading = false
                }

                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int
                ) {
                    val idx = chunkIndexOf(utteranceId)
                    if (idx < 0) return
                    rangeStartSeen = true
                    // start 是相对于「当前朗读块」的偏移，必须加上块首在章节中的
                    // 全局偏移，才能得到分页引擎需要的章节内字符偏移。
                    val global = (chunks[idx].startOffset + start)
                        .coerceIn(0, chapterText.length)
                    currentOffset = global
                    onHighlight?.invoke(global)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "注册朗读进度监听失败", e)
        }
    }

    /** 解析 utteranceId（`ttsbook_<generation>_<index>`），并作废旧代次回调。 */
    private fun chunkIndexOf(utteranceId: String?): Int {
        if (utteranceId == null) return -1
        val parts = utteranceId.split("_")
        if (parts.size != 3 || parts[0] != ID_PREFIX) return -1
        if (parts[1].toIntOrNull() != playbackGeneration) return -1
        return parts[2].toIntOrNull()?.takeIf { it in chunks.indices } ?: -1
    }

    /**
     * 按句切块：优先在句末标点后断开，避免把一句话劈成两半。
     * 每个块记录它在章节全文中的起始偏移，用于把 TTS 进度换算成全局偏移。
     */
    private fun buildChunks(text: String, fromOffset: Int): List<SpeechChunk> {
        val result = mutableListOf<SpeechChunk>()
        var i = fromOffset.coerceIn(0, text.length)
        while (i < text.length) {
            val remaining = text.length - i
            var end = i + minOf(MAX_CHUNK_LENGTH, remaining)
            if (end < text.length) {
                val searchFrom = (i + (MAX_CHUNK_LENGTH * 0.6f).toInt()).coerceAtLeast(i)
                for (p in end downTo searchFrom) {
                    val c = text[p]
                    if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n' ||
                        c == '.' || c == '!' || c == '?'
                    ) {
                        end = p + 1
                        break
                    }
                }
            }
            val chunk = text.substring(i, end)
            if (chunk.isNotBlank()) result.add(SpeechChunk(i, chunk))
            i = end
        }
        return result
    }

    /**
     * 设置章节全文（朗读内容）
     */
    fun setChapter(text: String) {
        chapterText = text
        currentOffset = 0
        chunks = emptyList()
        if (chapterText.isEmpty()) {
            stopReading()
        }
        updateNotification("已加载章节")
        Log.d(TAG, "setChapter: 长度=${chapterText.length}")
    }

    /**
     * 跳转到指定全局偏移（相对于章节开头）
     * 注意：调用此方法会中断当前朗读并重新从新偏移开始
     */
    fun seekToOffset(offset: Int) {
        if (chapterText.isEmpty()) return
        currentOffset = offset.coerceIn(0, chapterText.length)
        if (isReading) {
            speakFromOffset(currentOffset)
        }
        updateNotification("进度 $currentOffset/${chapterText.length}")
    }

    fun getProgressPercent(): Int {
        if (chapterText.isEmpty()) return 0
        return ((currentOffset.toLong() * 100) / chapterText.length).toInt().coerceIn(0, 100)
    }

    fun isPlaying(): Boolean = isReading && !isPaused

    fun pause() {
        playbackGeneration++
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        chunks = emptyList()
        isPaused = true
        isReading = false
        updateNotification("已暂停")
    }

    fun resume() {
        if (chapterText.isEmpty()) {
            Log.w(TAG, "resume: 章节内容为空")
            return
        }
        if (tts == null) {
            Log.w(TAG, "resume: TTS 未初始化，重新初始化")
            pendingResume = true
            initTts()
            return
        }
        isPaused = false
        speakFromOffset(currentOffset.coerceIn(0, chapterText.length))
    }

    fun stopReading() {
        playbackGeneration++
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        chunks = emptyList()
        isReading = false
        isPaused = false
        currentOffset = 0
        updateNotification("已停止")
    }

    /**
     * 从指定偏移处开始朗读：分块排队，并把每个块与全局偏移绑定。
     */
    private fun speakFromOffset(offset: Int) {
        if (chapterText.isEmpty()) {
            isReading = false
            return
        }
        if (offset >= chapterText.length) {
            Log.d(TAG, "已到章节末尾")
            isReading = false
            onChapterDone?.invoke()
            return
        }

        val engine = tts ?: run {
            Log.e(TAG, "TTS 引擎未就绪，重新初始化")
            pendingResume = true
            initTts()
            return
        }

        val newChunks = buildChunks(chapterText, offset)
        if (newChunks.isEmpty()) {
            isReading = false
            onChapterDone?.invoke()
            return
        }

        try {
            // 先清空旧队列；递增代次后旧队列的 onDone/onRangeStart 会被自动忽略。
            engine.stop()
            playbackGeneration++
            chunks = newChunks
            rangeStartSeen = false

            var ok = true
            for ((idx, chunk) in chunks.withIndex()) {
                val id = "${ID_PREFIX}_${playbackGeneration}_$idx"
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
                }
                val mode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                if (engine.speak(chunk.text, mode, params, id) == TextToSpeech.ERROR) {
                    ok = false
                    break
                }
            }

            if (!ok) {
                Log.e(TAG, "TTS 排队失败，重建引擎")
                isReading = false
                isPaused = false
                try {
                    engine.shutdown()
                } catch (_: Exception) {
                }
                tts = null
                initTts()
                return
            }

            isReading = true
            isPaused = false
            currentOffset = offset
            Log.d(TAG, "朗读启动，偏移=$offset，块数=${chunks.size}")
            updateNotification("朗读中 ${getProgressPercent()}%")
        } catch (e: Exception) {
            // 引擎实现各异，speak / stop 都可能抛异常；兜住，绝不让服务崩溃。
            Log.e(TAG, "启动朗读异常", e)
            isReading = false
        }
    }

    // ========== 通知 ==========
    private fun createNotification(subtitle: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("离线听书")
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(subtitle: String) {
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, createNotification(subtitle))
        } catch (e: Exception) {
            Log.e(TAG, "更新通知失败", e)
        }
    }

    override fun onDestroy() {
        playbackGeneration++
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        chunks = emptyList()
        onHighlight = null
        onChapterDone = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
