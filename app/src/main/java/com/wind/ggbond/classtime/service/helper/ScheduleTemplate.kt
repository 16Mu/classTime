package com.wind.ggbond.classtime.service.helper

interface ScheduleTemplate {
    val name: String
    val displayName: String
    val headerPatterns: Map<String, List<String>>
    val defaultHeaderRow: Int
    val defaultDataStartRow: Int
    val identifyingKeywords: List<String>

    fun matchHeaders(headers: List<String>): TemplateMatchResult

    fun buildConfig(headerRowIndex: Int, fieldMapping: Map<String, Int>): ExcelImportConfig
}

abstract class BaseScheduleTemplate : ScheduleTemplate {

    override fun matchHeaders(headers: List<String>): TemplateMatchResult {
        val mapping = mutableMapOf<String, Int>()
        var matchCount = 0
        var totalScore = 0f

        for ((fieldName, patterns) in headerPatterns) {
            var bestIdx = -1
            var bestScore = 0f

            headers.forEachIndexed { idx, header ->
                val normalized = header.trim().lowercase()
                for (pattern in patterns) {
                    val np = pattern.lowercase()
                    val score = when {
                        normalized == np -> 1.0f
                        normalized.contains(np) -> 0.8f
                        np.contains(normalized) -> 0.6f
                        else -> 0f
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestIdx = idx
                    }
                }
            }

            if (bestIdx >= 0 && bestScore > 0.5f) {
                if (!mapping.containsValue(bestIdx)) {
                    mapping[fieldName] = bestIdx
                    matchCount++
                    totalScore += bestScore
                }
            }
        }

        val confidence = if (headerPatterns.isNotEmpty()) {
            totalScore / headerPatterns.size
        } else 0f

        return TemplateMatchResult(
            templateName = name,
            confidence = confidence,
            fieldMapping = mapping,
            headerRowIndex = defaultHeaderRow,
            dataStartRowIndex = defaultDataStartRow
        )
    }

    override fun buildConfig(headerRowIndex: Int, fieldMapping: Map<String, Int>): ExcelImportConfig {
        return ExcelImportConfig(
            headerRowIndex = headerRowIndex,
            dataStartRowIndex = headerRowIndex + 1,
            fieldMapping = fieldMapping
        )
    }
}

class ZhengfangTemplate : BaseScheduleTemplate() {
    override val name = "ZHENGFANG"
    override val displayName = "正方教务"
    override val headerPatterns = EducationalSystemTemplate.ZHENGFANG.headerPatterns
    override val defaultHeaderRow = 0
    override val defaultDataStartRow = 1
    override val identifyingKeywords = EducationalSystemTemplate.ZHENGFANG.keywords
}

class QingguoTemplate : BaseScheduleTemplate() {
    override val name = "QINGGUO"
    override val displayName = "青果教务"
    override val headerPatterns = EducationalSystemTemplate.QINGGUO.headerPatterns
    override val defaultHeaderRow = 0
    override val defaultDataStartRow = 1
    override val identifyingKeywords = EducationalSystemTemplate.QINGGUO.keywords
}

class UrpTemplate : BaseScheduleTemplate() {
    override val name = "URP"
    override val displayName = "URP教务"
    override val headerPatterns = EducationalSystemTemplate.URP.headerPatterns
    override val defaultHeaderRow = 0
    override val defaultDataStartRow = 1
    override val identifyingKeywords = EducationalSystemTemplate.URP.keywords
}
