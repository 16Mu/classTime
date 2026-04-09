package com.wind.ggbond.classtime.util

import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

/**
 * 纯文本课程信息解析结果
 */
data class ParsedTextCourse(
    val courseName: String = "",           // 课程名称
    val teacher: String = "",              // 教师
    val classroom: String = "",            // 教室
    val weekExpression: String = "",       // 周次表达式（原始）
    val weeks: List<Int> = emptyList(),    // 解析后的周次列表
    val sectionExpression: String = "",    // 节次表达式（如"1-2节"）
    val startSection: Int = 0,             // 起始节次（0表示未解析）
    val endSection: Int = 0,               // 结束节次
    val credit: Float = 0f,                // 学分
    val rawText: String = ""               // 原始文本
)

/**
 * 纯文本课程信息解析器
 * 
 * 支持解析从教务系统复制的课程信息格式，例如：
 * ```
 * 体育4★
 * (1-2节)1-5周,7-9周,11-13周
 * (单),14-16周,19周/校区:潼南
 * 校区/场地:未排地点/教师
 * :李兢/教学班组成:中德项目
 * 2024;信息24501/教学班人数:87/考核方式:考查/课程学时
 * 组成:理论:32/学分:2
 * ```
 */
@Singleton
class TextCourseParser @Inject constructor() {
    
    companion object {
        private const val TAG = "TextCourseParser"
    }
    
    /**
     * 解析纯文本课程信息
     * @param text 从剪贴板粘贴的课程信息文本
     * @return 解析结果列表（支持多门课程）
     */
    fun parse(text: String): List<ParsedTextCourse> {
        AppLogger.d(TAG, "开始解析文本: ${text.take(100)}...")
        
        // 清理文本：去除多余空白，保留换行
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            return emptyList()
        }
        
        // 尝试分割多门课程
        val courseTexts = splitMultipleCourses(cleanText)
        AppLogger.d(TAG, "识别到 ${courseTexts.size} 门课程")
        
        return courseTexts.mapNotNull { courseText ->
            parseSingleCourse(courseText)
        }
    }
    
    /**
     * 分割多门课程
     * 规则：当遇到新的课程名称行（以非括号开头，包含中文且不含":"的行）时，认为是新课程开始
     */
    private fun splitMultipleCourses(text: String): List<String> {
        val lines = text.lines()
        val courses = mutableListOf<String>()
        val currentCourse = StringBuilder()
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // 判断是否为新课程开始
            // 规则：行首不是"("，包含中文字符，不包含":"，且长度较短（课程名称通常不超过30字）
            val isNewCourse = trimmedLine.isNotEmpty() &&
                    !trimmedLine.startsWith("(") &&
                    !trimmedLine.startsWith("/") &&
                    !trimmedLine.contains(":") &&
                    !trimmedLine.contains("：") &&
                    containsChinese(trimmedLine) &&
                    trimmedLine.length <= 30 &&
                    // 排除一些常见的非课程名称行
                    !trimmedLine.matches(Regex("^(校区|场地|教师|教学班|考核|课程|学分|理论|实践).*"))
            
            if (isNewCourse && currentCourse.isNotEmpty()) {
                // 保存当前课程，开始新课程
                courses.add(currentCourse.toString().trim())
                currentCourse.clear()
            }
            
            if (currentCourse.isNotEmpty()) {
                currentCourse.append("\n")
            }
            currentCourse.append(trimmedLine)
        }
        
        // 添加最后一门课程
        if (currentCourse.isNotEmpty()) {
            courses.add(currentCourse.toString().trim())
        }
        
        return courses
    }
    
    /**
     * 解析单门课程信息
     */
    private fun parseSingleCourse(text: String): ParsedTextCourse? {
        val lines = text.lines()
        if (lines.isEmpty()) return null
        
        var courseName = ""
        var teacher = ""
        var classroom = ""
        var weekExpression = ""
        var sectionExpression = ""
        var credit = 0f
        
        // 先合并所有周次相关的行（去除换行符）
        val allWeekLines = mutableListOf<String>()
        val otherLines = mutableListOf<String>()
        
        for (line in lines) {
            val trimmedLine = line.trim()
            // 判断是否包含周次信息
            if (trimmedLine.contains("周") || 
                trimmedLine.matches(Regex(".*\\d+[,，].*周.*")) ||
                trimmedLine.contains("(单)") || trimmedLine.contains("（单）") ||
                trimmedLine.contains("(双)") || trimmedLine.contains("（双）") ||
                trimmedLine.matches(Regex(".*\\d+-\\d+.*"))) {
                allWeekLines.add(trimmedLine)
            } else {
                otherLines.add(trimmedLine)
            }
        }
        
        // 合并周次信息，去除换行符
        val combinedWeekText = allWeekLines.joinToString("").replace("\n", "")
        
        for ((index, line) in otherLines.withIndex()) {
            val trimmedLine = line
            
            // 第一行通常是课程名称
            if (index == 0) {
                courseName = extractCourseName(trimmedLine)
                continue
            }
            
            // 提取节次 (1-2节)
            if (trimmedLine.contains(Regex("\\(\\d+-\\d+节\\)"))) {
                val sectionMatch = Regex("\\((\\d+)-(\\d+)节\\)").find(trimmedLine)
                if (sectionMatch != null) {
                    sectionExpression = "${sectionMatch.groupValues[1]}-${sectionMatch.groupValues[2]}节"
                }
            }
            
            // 提取教室 场地:xxx 或 场地：xxx
            if (trimmedLine.contains("场地:") || trimmedLine.contains("场地：")) {
                classroom = extractField(trimmedLine, "场地")
            }
            
            // 提取教师 教师:xxx 或 教师：xxx
            if (trimmedLine.contains("教师:") || trimmedLine.contains("教师：")) {
                teacher = extractField(trimmedLine, "教师")
            }
            
            // 提取学分 学分:xxx 或 学分：xxx
            if (trimmedLine.contains("学分:") || trimmedLine.contains("学分：")) {
                val creditStr = extractField(trimmedLine, "学分")
                credit = creditStr.toFloatOrNull() ?: 0f
            }
        }
        
        // 使用合并后的周次文本
        weekExpression = if (combinedWeekText.isNotEmpty()) {
            extractWeekPart(combinedWeekText)
        } else {
            ""
        }
        
        // 解析周次列表
        val weeks = if (weekExpression.isNotEmpty()) {
            WeekParser.parseWeekExpression(weekExpression)
        } else {
            emptyList()
        }
        
        // 解析节次
        val (startSection, endSection) = parseSection(sectionExpression)
        
        val result = ParsedTextCourse(
            courseName = courseName,
            teacher = teacher,
            classroom = classroom,
            weekExpression = weekExpression,
            weeks = weeks,
            sectionExpression = sectionExpression,
            startSection = startSection,
            endSection = endSection,
            credit = credit,
            rawText = text
        )
        
        AppLogger.d(TAG, "解析结果: $result")
        return result
    }
    
    /**
     * 提取课程名称
     * 去除★等特殊符号
     */
    private fun extractCourseName(line: String): String {
        return line
            .replace("★", "")
            .replace("☆", "")
            .replace("〇", "")
            .replace(Regex("【[^】]*】"), "")
            .replace(Regex("\\[[^\\]]*\\]"), "")
            .trim()
    }
    
    /**
     * 提取周次部分
     * 从行中提取周次信息，处理可能跨行的情况
     */
    private fun extractWeekPart(line: String): String {
        // 周次通常在"/"之前，或者整行都是周次
        var text = line
        
        // 如果行以"/"结尾，去掉它
        if (text.endsWith("/")) {
            text = text.dropLast(1)
        }
        
        // 如果行包含"/"，取前面的部分
        if (text.contains("/")) {
            text = text.substringBefore("/")
        }
        
        // 先去除节次信息，保留周次部分
        text = text.replace(Regex("\\(\\d+-\\d+节\\)"), "")
        text = text.replace(Regex("^\\d+-\\d+节"), "")
        
        // 提取周次相关的所有内容
        val weekPattern = Regex("[\\d,，\\-周()（）单双]+")
        val matches = weekPattern.findAll(text)
        val weekText = matches.joinToString("") { it.value }
        
        // 清理多余空格
        val cleaned = weekText.trim()
        
        AppLogger.d(TAG, "原始文本: $line")
        AppLogger.d(TAG, "处理后周次: $cleaned")
        
        return cleaned
    }
    
    /**
     * 提取指定字段值
     * 例如：extractField("场地:4304机房", "场地") -> "4304机房"
     */
    private fun extractField(line: String, fieldName: String): String {
        // 支持全角和半角冒号
        val patterns = listOf(
            Regex("$fieldName[:：]\\s*(.+?)(?=/|$)"),
            Regex("$fieldName[:：]\\s*(.+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(line)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return ""
    }
    
    /**
     * 解析节次表达式
     * 例如："1-2节" -> (1, 2)
     */
    private fun parseSection(sectionExpression: String): Pair<Int, Int> {
        if (sectionExpression.isEmpty()) return Pair(0, 0)
        
        val match = Regex("(\\d+)-(\\d+)").find(sectionExpression)
        if (match != null) {
            val start = match.groupValues[1].toIntOrNull() ?: 0
            val end = match.groupValues[2].toIntOrNull() ?: 0
            return Pair(start, end)
        }
        return Pair(0, 0)
    }
    
    /**
     * 判断字符串是否包含中文字符
     */
    private fun containsChinese(text: String): Boolean {
        return text.any { it.code in 0x4E00..0x9FFF }
    }
}
