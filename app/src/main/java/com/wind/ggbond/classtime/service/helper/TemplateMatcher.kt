package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateMatcher @Inject constructor() {

    companion object {
        private const val TAG = "TemplateMatcher"
        private const val MATCH_THRESHOLD = 0.4f
    }

    private val templates: List<ScheduleTemplate> = listOf(
        ZhengfangTemplate(),
        QingguoTemplate(),
        UrpTemplate()
    )

    fun matchTemplate(headers: List<String>): TemplateMatchResult? {
        val results = templates.map { template ->
            template.matchHeaders(headers)
        }.filter { it.confidence >= MATCH_THRESHOLD }

        if (results.isEmpty()) {
            AppLogger.d(TAG, "没有匹配到任何模板")
            return null
        }

        val best = results.maxByOrNull { it.confidence }
        AppLogger.d(TAG, "最佳模板匹配: ${best?.templateName}, 置信度: ${best?.confidence}")
        return best
    }

    fun matchAllTemplates(headers: List<String>): List<TemplateMatchResult> {
        return templates.map { template ->
            template.matchHeaders(headers)
        }.filter { it.confidence >= MATCH_THRESHOLD }
            .sortedByDescending { it.confidence }
    }

    fun matchByKeywords(sheetData: List<List<String>>): TemplateMatchResult? {
        val allText = sheetData.flatten().joinToString(" ").lowercase()

        var bestTemplate: ScheduleTemplate? = null
        var bestScore = 0

        for (template in templates) {
            var score = 0
            for (keyword in template.identifyingKeywords) {
                if (allText.contains(keyword.lowercase())) {
                    score += 3
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestTemplate = template
            }
        }

        if (bestTemplate == null || bestScore == 0) {
            return null
        }

        val headerRowIndex = bestTemplate.defaultHeaderRow
        if (headerRowIndex >= sheetData.size) {
            return null
        }

        val headers = sheetData[headerRowIndex]
        val matchResult = bestTemplate.matchHeaders(headers)

        AppLogger.d(TAG, "关键词匹配模板: ${bestTemplate.displayName}, 得分: $bestScore, 置信度: ${matchResult.confidence}")
        return matchResult
    }

    fun getTemplateByName(name: String): ScheduleTemplate? {
        return templates.find { it.name.equals(name, ignoreCase = true) }
    }

    fun getAllTemplates(): List<ScheduleTemplate> = templates
}
