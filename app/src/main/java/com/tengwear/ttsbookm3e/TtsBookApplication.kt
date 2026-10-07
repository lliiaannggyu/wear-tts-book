package com.tengwear.ttsbookm3e

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class TtsBookApplication : Application() {

    interface CountdownListener {
        fun onCountdownTick(remainingSeconds: Int)
        fun onCountdownFinish()
    }

    companion object {
        lateinit var instance: TtsBookApplication
            private set
    }

    private var countdownInterval: Int = 10
    private var countdownTotalSeconds: Int = 10
    private var isCountdownRunning = false
    private var isCountdownPaused = false
    private var remainingSeconds = 0
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private val listeners = mutableListOf<CountdownListener>()
    private var isFinishNotified = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 进程启动即载入用户种子色：所有页面首帧即为正确配色，切换主题后全局实时生效
        com.tengwear.ttsbookm3e.ui.theme.SeedColorState.ensureLoaded(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "reader_channel",
                "听书服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "后台朗读通知" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun startCountdown(seconds: Int) {
        stopCountdown()
        countdownInterval = seconds
        countdownTotalSeconds = seconds
        remainingSeconds = seconds
        isCountdownRunning = true
        isCountdownPaused = false
        isFinishNotified = false
        Log.d("AppCountdown", "启动倒计时，间隔 $seconds 秒")
        notifyTick(remainingSeconds)
        startTick()
    }

    private fun startTick() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = object : Runnable {
            override fun run() {
                if (!isCountdownRunning) return
                if (isCountdownPaused) {
                    handler.postDelayed(this, 500)
                    return
                }

                remainingSeconds--
                Log.d("AppCountdown", "倒计时剩余 $remainingSeconds 秒")

                if (remainingSeconds <= 0 && !isFinishNotified) {
                    isFinishNotified = true
                    Log.d("AppCountdown", "触发翻页")
                    notifyFinish()
                    remainingSeconds = countdownInterval
                    isFinishNotified = false
                }

                notifyTick(remainingSeconds)

                if (isCountdownRunning && !isCountdownPaused) {
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(countdownRunnable!!, 1000)
    }

    fun pauseCountdown() {
        if (!isCountdownRunning) return
        isCountdownPaused = true
        Log.d("AppCountdown", "倒计时已暂停，剩余 ${remainingSeconds} 秒")
    }

    fun resumeCountdown() {
        if (!isCountdownRunning) return
        isCountdownPaused = false
        isFinishNotified = false
        Log.d("AppCountdown", "倒计时已恢复，剩余 ${remainingSeconds} 秒")
        startTick()
    }

    fun stopCountdown() {
        isCountdownRunning = false
        isCountdownPaused = false
        isFinishNotified = false
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        remainingSeconds = 0
        countdownTotalSeconds = 0
        Log.d("AppCountdown", "停止倒计时")
    }

    fun isCountdownActive(): Boolean = isCountdownRunning && !isCountdownPaused
    fun isCountdownPaused(): Boolean = isCountdownPaused
    fun getRemainingSeconds(): Int = remainingSeconds
    fun getCountdownTotalSeconds(): Int = countdownTotalSeconds

    fun addCountdownListener(listener: CountdownListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            if (isCountdownRunning && !isCountdownPaused) {
                listener.onCountdownTick(remainingSeconds)
            }
        }
    }

    fun removeCountdownListener(listener: CountdownListener) {
        listeners.remove(listener)
    }

    private fun notifyTick(seconds: Int) {
        listeners.forEach { it.onCountdownTick(seconds) }
    }

    private fun notifyFinish() {
        listeners.forEach { it.onCountdownFinish() }
    }
}