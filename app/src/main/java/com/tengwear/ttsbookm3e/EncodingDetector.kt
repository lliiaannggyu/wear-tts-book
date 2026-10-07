package com.tengwear.ttsbookm3e

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object EncodingDetector {

    private val SUPPORTED_ENCODINGS = listOf(
        StandardCharsets.UTF_8,
        Charset.forName("GBK"),
        Charset.forName("GB2312"),
        Charset.forName("GB18030"),
        Charset.forName("Big5"),
        Charset.forName("Shift_JIS"),
        Charset.forName("EUC-KR"),
        StandardCharsets.ISO_8859_1,
        StandardCharsets.UTF_16LE,
        StandardCharsets.UTF_16BE,
        Charset.forName("Windows-1252")
    )

    /**
     * 自动检测文件编码
     */
    fun detectEncoding(file: File): Charset {
        val bytes = file.readBytes()
        // 1. BOM 检测
        val bomCharset = detectBOM(bytes)
        if (bomCharset != null) return bomCharset

        // 2. 采样检测（取前 8KB）
        val sampleSize = minOf(bytes.size, 8192)
        val sample = bytes.copyOfRange(0, sampleSize)

        var bestCharset: Charset? = null
        var bestScore = 0.0

        for (charset in SUPPORTED_ENCODINGS) {
            try {
                val decoded = String(sample, charset)
                val score = calculateScore(decoded)
                if (score > bestScore) {
                    bestScore = score
                    bestCharset = charset
                }
            } catch (_: Exception) {
                // 忽略解码异常
            }
        }

        // 如果最佳得分太低（<0.5），可能不是文本文件，默认 UTF-8
        return if (bestScore > 0.5) bestCharset ?: StandardCharsets.UTF_8 else StandardCharsets.UTF_8
    }

    /**
     * 检测 BOM 头
     */
    private fun detectBOM(bytes: ByteArray): Charset? {
        if (bytes.size < 3) return null
        return when {
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                StandardCharsets.UTF_8
            bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                StandardCharsets.UTF_16BE
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                StandardCharsets.UTF_16LE
            else -> null
        }
    }

    /**
     * 计算文本中可打印字符的比例（用于评估解码质量）
     */
    fun calculateScore(text: String): Double {
        if (text.isEmpty()) return 0.0
        var printable = 0
        var total = 0
        for (ch in text) {
            total++
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' ||
                ch in '\u4e00'..'\u9fff' || ch in '\u3000'..'\u303f' ||
                ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' ||
                ch in '，'..'～' || ch in '！'..'￥') {
                printable++
            }
        }
        return printable.toDouble() / total
    }

    fun getSupportedEncodingNames(): List<String> = SUPPORTED_ENCODINGS.map { it.name() }

    fun getDisplayName(charset: Charset): String {
        return when (charset.name()) {
            "UTF-8" -> "UTF-8"
            "GBK" -> "GBK (简体中文)"
            "GB2312" -> "GB2312 (简体中文)"
            "GB18030" -> "GB18030 (中文)"
            "Big5" -> "Big5 (繁体中文)"
            "Shift_JIS" -> "Shift-JIS (日文)"
            "EUC-KR" -> "EUC-KR (韩文)"
            "ISO-8859-1" -> "ISO-8859-1 (西欧)"
            "Windows-1252" -> "Windows-1252 (西欧)"
            "UTF-16LE" -> "UTF-16LE (Unicode)"
            "UTF-16BE" -> "UTF-16BE (Unicode)"
            else -> charset.name()
        }
    }

    fun getCharsetByName(name: String): Charset? = try { Charset.forName(name) } catch (_: Exception) { null }

    /**
     * ★★★ 关键修复：白名单加入 "pdf" ★★★
     */
    fun isSupportedFile(file: File): Boolean =
        file.extension.lowercase() in listOf(
            "txt", "epub", "fb2", "html", "htm", "json", "pdf"
        )
}