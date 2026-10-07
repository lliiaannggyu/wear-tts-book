package com.tengwear.ttsbookm3e

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
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

class CountdownActivity : AppCompatActivity(), TtsBookApplication.CountdownListener {

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var displayRunnable: Runnable? = null

    private var isRunning by mutableStateOf(false)
    private var totalSeconds by mutableIntStateOf(10)
    private var remainingDisplay by mutableIntStateOf(0)
    private var autoPageEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ttsbook_prefs", MODE_PRIVATE)
        totalSeconds = prefs.getInt("countdown_interval", 10)
        autoPageEnabled = prefs.getBoolean("countdown_autopage", true)

        val app = TtsBookApplication.instance
        if (app.isCountdownActive()) {
            isRunning = true
            totalSeconds = app.getCountdownTotalSeconds()
            remainingDisplay = app.getRemainingSeconds()
            startDisplayUpdate()
        }

        setContent { TtsbookTheme { CountdownScreen() } }
    }

    @Composable
    private fun CountdownScreen() {
        var seconds by remember { mutableFloatStateOf(totalSeconds.toFloat()) }
        val secondsInt = seconds.roundToInt().coerceIn(1, 600)
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()

        DisposableEffect(Unit) {
            TtsBookApplication.instance.addCountdownListener(this@CountdownActivity)
            onDispose {
                TtsBookApplication.instance.removeCountdownListener(this@CountdownActivity)
                stopDisplayUpdate()
            }
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
                                text = "倒计时翻页",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    // 状态徽标：运行中用 tertiary 强调，等待中用次级文字色
                    item {
                        Text(
                            text = if (isRunning) "运行中" else "等待开始",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isRunning) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 剩余时间：大号数字，用 primary 强调
                    item {
                        Text(
                            text = String.format(
                                "%02d:%02d",
                                remainingDisplay / 60,
                                remainingDisplay % 60,
                            ),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 间隔秒数：M3E Slider（旋转表冠即可调节）
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "间隔 $secondsInt 秒",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Slider(
                                value = seconds,
                                onValueChange = { seconds = it },
                                valueRange = 1f..120f,
                                steps = 118,
                                enabled = !isRunning,
                                modifier = Modifier.fillMaxWidth(0.92f),
                            )
                        }
                    }

                    // 自动翻页开关：M3E SwitchButton
                    item {
                        SwitchButton(
                            checked = autoPageEnabled,
                            onCheckedChange = {
                                autoPageEnabled = it
                                prefs.edit().putBoolean("countdown_autopage", it).apply()
                            },
                            label = {
                                Text(
                                    text = "自动翻页",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                            },
                            secondaryLabel = {
                                Text(
                                    text = if (autoPageEnabled) "归零后自动翻页" else "仅计时不翻页",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }

                    // 开始 / 停止
                    item {
                        TtsbookButtonPair(
                            leftLabel = "开始",
                            onLeft = {
                                prefs.edit().putInt("countdown_interval", secondsInt).apply()
                                TtsBookApplication.instance.startCountdown(secondsInt)
                                isRunning = true
                                totalSeconds = secondsInt
                                remainingDisplay = secondsInt
                                startDisplayUpdate()
                            },
                            rightLabel = "停止",
                            onRight = {
                                TtsBookApplication.instance.stopCountdown()
                                stopDisplayUpdate()
                                isRunning = false
                                remainingDisplay = 0
                            },
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }

                    item {
                        Text(
                            text = "自动翻页不打断朗读",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
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

    override fun onCountdownTick(remainingSeconds: Int) {
        if (isRunning) remainingDisplay = remainingSeconds
    }

    override fun onCountdownFinish() {
        // 由 MainActivity 的监听器负责实际翻页
    }

    private fun startDisplayUpdate() {
        displayRunnable?.let { handler.removeCallbacks(it) }
        displayRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                remainingDisplay--
                if (remainingDisplay < 0) remainingDisplay = totalSeconds - 1
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(displayRunnable!!, 1000)
    }

    private fun stopDisplayUpdate() {
        displayRunnable?.let { handler.removeCallbacks(it) }
        displayRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDisplayUpdate()
        handler.removeCallbacksAndMessages(null)
        TtsBookApplication.instance.removeCountdownListener(this)
    }
}
