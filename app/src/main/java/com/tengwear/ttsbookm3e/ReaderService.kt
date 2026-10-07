package com.tengwear.ttsbookm3e

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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

class ReaderService : Service() {

    private val binder = LocalBinder()
    private var tts: TextToSpeech? = null

    // 当前章节全文及当前朗读偏移（全局偏移，相对于章节开头）
    private var chapterText: String = ""
    private var currentOffset: Int = 0

    private var isPaused = false
    private var isReading = false

    // 当前使用的引擎包名，null 表示系统默认
    private var currentEnginePackage: String? = null

    // 回调接口
    var onHighlight: ((offset: Int) -> Unit)? = null   // 每读到一个词回调当前全局偏移
    var onChapterDone: (() -> Unit)? = null            // 全章朗读完成回调

    inner class LocalBinder : Binder() {
        fun getService(): ReaderService = this@ReaderService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 读取用户选择的引擎
        val prefs = getSharedPreferences("ttsbook_prefs", Context.MODE_PRIVATE)
        currentEnginePackage = prefs.getString("tts_engine_package", null)
        initTts()
        startForeground(1, createNotification("初始化中"))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "reader_channel",
                "听书服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "后台朗读通知"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun initTts() {
        val enginePackage = currentEnginePackage
        Log.d("ReaderService", "初始化 TTS，引擎: ${enginePackage ?: "系统默认"}")

        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupTtsLanguage()
            } else {
                Log.e("ReaderService", "TTS 初始化失败，状态码: $status")
                // 如果是用户指定的引擎失败，尝试回退到系统默认
                if (currentEnginePackage != null) {
                    Log.w("ReaderService", "尝试回退到系统默认引擎")
                    currentEnginePackage = null
                    initTts()
                } else {
                    Toast.makeText(this, "TTS 引擎初始化失败，请检查语音引擎设置", Toast.LENGTH_LONG).show()
                }
            }
        }

        tts = if (enginePackage != null) {
            TextToSpeech(this, listener, enginePackage)
        } else {
            TextToSpeech(this, listener)
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
            val result = tts?.setLanguage(locale)
            if (result != null &&
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                languageSet = true
                Log.d("ReaderService", "TTS 语言设置成功: ${locale.displayName}")
                break
            }
        }
        if (!languageSet) {
            val enResult = tts?.setLanguage(Locale.ENGLISH)
            if (enResult != null &&
                enResult != TextToSpeech.LANG_MISSING_DATA &&
                enResult != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.w("ReaderService", "中文不支持，回退到英语")
            } else {
                Log.e("ReaderService", "所有语言设置失败")
                Toast.makeText(this, "当前引擎不支持中文，请在设置中更换引擎", Toast.LENGTH_LONG).show()
            }
        }

        setupUtteranceListener()
    }

    private fun setupUtteranceListener() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("ReaderService", "TTS 开始朗读")
                    isReading = true
                    isPaused = false
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("ReaderService", "TTS 朗读完成（整章）")
                    isReading = false
                    // 整章朗读完成，触发回调
                    onChapterDone?.invoke()
                }

                override fun onError(utteranceId: String?) {
                    Log.e("ReaderService", "TTS 朗读错误")
                    isReading = false
                }

                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int
                ) {
                    // start/end 是相对于整章全文的全局偏移
                    currentOffset = start
                    // 通知外部当前偏移（用于高亮和翻页判断）
                    onHighlight?.invoke(currentOffset)
                }
            })
        } else {
            Log.w("ReaderService", "系统版本过低，不支持逐字高亮，将使用定时轮询模拟（可能不精确）")
        }
    }

    /**
     * 设置章节全文（朗读内容）
     */
    fun setChapter(text: String) {
        chapterText = text
        currentOffset = 0
        if (chapterText.isEmpty()) {
            stopReading()
        }
        updateNotification("已加载章节")
        Log.d("ReaderService", "setChapter: 长度=${chapterText.length}")
    }

    /**
     * 跳转到指定全局偏移（相对于章节开头）
     * 注意：调用此方法会中断当前朗读并重新从新偏移开始
     */
    fun seekToOffset(offset: Int) {
        if (chapterText.isEmpty()) return
        currentOffset = offset.coerceIn(0, chapterText.length)
        if (isReading) {
            tts?.stop()
            speakFromOffset(currentOffset)
        }
        updateNotification("进度 ${currentOffset}/${chapterText.length}")
    }

    fun getProgressPercent(): Int {
        if (chapterText.isEmpty()) return 0
        return (currentOffset * 100 / chapterText.length)
    }

    fun isPlaying(): Boolean = isReading && !isPaused

    fun pause() {
        tts?.stop()
        isPaused = true
        isReading = false
        updateNotification("已暂停")
    }

    fun resume() {
        if (chapterText.isEmpty()) {
            Log.w("ReaderService", "resume: 章节内容为空")
            return
        }
        if (tts == null) {
            Log.w("ReaderService", "resume: TTS 未初始化，重新初始化")
            initTts()
        }
        isPaused = false
        isReading = true
        speakFromOffset(currentOffset)
    }

    fun stopReading() {
        tts?.stop()
        isReading = false
        isPaused = false
        currentOffset = 0
        updateNotification("已停止")
    }

    /**
     * 从指定偏移处开始朗读（内部方法，会立即播放）
     */
    private fun speakFromOffset(offset: Int) {
        if (chapterText.isEmpty() || offset >= chapterText.length) {
            Log.w("ReaderService", "无法朗读：内容为空或偏移超出")
            isReading = false
            if (offset >= chapterText.length && chapterText.isNotEmpty()) {
                onChapterDone?.invoke()
            }
            return
        }

        val textToSpeak = chapterText.substring(offset)
        if (textToSpeak.isEmpty()) {
            isReading = false
            onChapterDone?.invoke()
            return
        }

        val engine = tts
        if (engine == null) {
            Log.e("ReaderService", "TTS 引擎未初始化，尝试重新初始化")
            initTts()
            return
        }

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "reading")
        val result = engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "reading")
        if (result == TextToSpeech.ERROR) {
            Log.e("ReaderService", "tts.speak 返回 ERROR")
            isReading = false
            tts?.shutdown()
            tts = null
            initTts()
        } else {
            isReading = true
            isPaused = false
            Log.d("ReaderService", "朗读成功启动，偏移=$offset, 长度=${textToSpeak.length}")
        }
    }

    // ========== 通知 ==========
    private fun createNotification(subtitle: String): Notification {
        val builder = NotificationCompat.Builder(this, "reader_channel")
            .setContentTitle("📖 离线听书")
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
        return builder.build()
    }

    private fun updateNotification(subtitle: String) {
        val notification = createNotification(subtitle)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, notification)
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        onHighlight = null
        onChapterDone = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}