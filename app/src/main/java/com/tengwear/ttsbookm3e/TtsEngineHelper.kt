package com.tengwear.ttsbookm3e

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.util.Log

/**
 * TTS 引擎辅助类：获取设备上可用的 TTS 引擎列表
 */
object TtsEngineHelper {

    data class TtsEngineInfo(
        val packageName: String,
        val label: String,
        val isSystemDefault: Boolean
    )

    /**
     * 获取设备上所有可用的 TTS 引擎
     */
    fun getAvailableEngines(context: Context): List<TtsEngineInfo> {
        val engines = mutableListOf<TtsEngineInfo>()

        try {
            // 方法1：通过 TextToSpeech 获取
            val tts = TextToSpeech(context, null)
            val defaultEngine = tts.defaultEngine
            val engineList = tts.engines

            for (engine in engineList) {
                engines.add(
                    TtsEngineInfo(
                        packageName = engine.name,
                        label = engine.label?.toString() ?: engine.name,
                        isSystemDefault = engine.name == defaultEngine
                    )
                )
            }
            tts.shutdown()
        } catch (e: Exception) {
            Log.e("TtsEngineHelper", "获取引擎列表失败", e)
        }

        // 如果没有获取到，至少添加系统默认选项
        if (engines.isEmpty()) {
            engines.add(TtsEngineInfo("", "系统默认引擎", true))
        }

        return engines
    }

    /**
     * 获取当前设置的引擎包名（null 表示系统默认）
     */
    fun getCurrentEnginePackage(context: Context): String? {
        val prefs = context.getSharedPreferences("ttsbook_prefs", Context.MODE_PRIVATE)
        return prefs.getString("tts_engine_package", null)
    }

    /**
     * 保存引擎设置
     */
    fun saveEnginePackage(context: Context, packageName: String?) {
        val prefs = context.getSharedPreferences("ttsbook_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("tts_engine_package", packageName).apply()
    }

    /**
     * 检查指定引擎是否已安装
     */
    fun isEngineInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 获取引擎的显示名称
     */
    fun getEngineLabel(context: Context, packageName: String?): String {
        if (packageName.isNullOrEmpty()) return "系统默认引擎"

        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}