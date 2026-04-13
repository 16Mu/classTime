package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormatDetector @Inject constructor() {

    enum class DetectedFormat {
        JSON, ICS, CSV, HTML, EXCEL, UNKNOWN
    }

    data class FormatDetectionResult(
        val format: DetectedFormat,
        val confidence: Float,
        val version: String?,
        val isExternalSource: Boolean
    )

    fun detectFormat(content: String, fileName: String? = null): FormatDetectionResult {
        val extensionResult = fileName?.let { detectByExtension(it) }
        val contentResult = detectByContent(content)

        if (extensionResult != null && extensionResult.format == contentResult.format) {
            return extensionResult.copy(confidence = 1.0f)
        }

        if (extensionResult != null && extensionResult.format == DetectedFormat.EXCEL) {
            return extensionResult
        }

        if (extensionResult != null && extensionResult.confidence > contentResult.confidence) {
            return extensionResult
        }

        return contentResult
    }

    private fun detectByExtension(fileName: String): FormatDetectionResult? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "json" -> FormatDetectionResult(DetectedFormat.JSON, 0.7f, null, false)
            "ics", "ical" -> FormatDetectionResult(DetectedFormat.ICS, 0.7f, null, false)
            "csv" -> FormatDetectionResult(DetectedFormat.CSV, 0.7f, null, false)
            "html", "htm" -> FormatDetectionResult(DetectedFormat.HTML, 0.7f, null, false)
            "xlsx", "xls" -> FormatDetectionResult(DetectedFormat.EXCEL, 0.9f, null, true)
            else -> null
        }
    }

    private fun detectByContent(content: String): FormatDetectionResult {
        val trimmed = content.trim()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val version = extractJsonVersion(trimmed)
            val isExternal = version == null && !trimmed.contains("CourseScheduleExport")
            return FormatDetectionResult(
                format = DetectedFormat.JSON,
                confidence = 0.9f,
                version = version,
                isExternalSource = isExternal
            )
        }

        if (trimmed.contains("BEGIN:VCALENDAR") || trimmed.contains("BEGIN:VEVENT")) {
            val version = extractIcsVersion(trimmed)
            val isExternal = !trimmed.contains("Course Schedule App")
            return FormatDetectionResult(
                format = DetectedFormat.ICS,
                confidence = 0.95f,
                version = version,
                isExternalSource = isExternal
            )
        }

        if (isCsvContent(trimmed)) {
            return FormatDetectionResult(
                format = DetectedFormat.CSV,
                confidence = 0.8f,
                version = extractCsvVersion(trimmed),
                isExternalSource = !trimmed.contains("课程表导出数据")
            )
        }

        if (trimmed.startsWith("<!DOCTYPE html") || trimmed.startsWith("<html") ||
            (trimmed.contains("<html") && trimmed.contains("</html>"))) {
            return FormatDetectionResult(
                format = DetectedFormat.HTML,
                confidence = 0.85f,
                version = extractHtmlVersion(trimmed),
                isExternalSource = !trimmed.contains("课程表 App")
            )
        }

        return FormatDetectionResult(DetectedFormat.UNKNOWN, 0f, null, false)
    }

    private fun extractJsonVersion(content: String): String? {
        return try {
            val versionMatch = Regex(""""version"\s*:\s*"([^"]+)"""").find(content)
            val exportVersionMatch = Regex(""""exportVersion"\s*:\s*"([^"]+)"""").find(content)
            exportVersionMatch?.groupValues?.get(1) ?: versionMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            AppLogger.w("FormatDetector", "提取JSON版本失败: ${e.message}")
            null
        }
    }

    private fun extractIcsVersion(content: String): String? {
        return try {
            val match = Regex("X-EXPORT-VERSION:(.+)").find(content)
            match?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractCsvVersion(content: String): String? {
        return try {
            val match = Regex("""^"# 导出格式版本","([^"]+)""", RegexOption.MULTILINE).find(content)
            match?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractHtmlVersion(content: String): String? {
        return try {
            val match = Regex("""name="export-version"\s+content="([^"]+)"""").find(content)
            match?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun isCsvContent(content: String): Boolean {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return false

        val firstDataLine = lines.first {
            val trimmed = it.trimStart()
            !trimmed.startsWith("#") && !trimmed.startsWith("\"#")
        }
        val commaCount = firstDataLine.count { it == ',' }
        val quoteCount = firstDataLine.count { it == '"' }

        return commaCount >= 3 || (quoteCount >= 4 && commaCount >= 2)
    }
}
