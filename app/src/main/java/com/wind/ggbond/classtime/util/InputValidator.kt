package com.wind.ggbond.classtime.util

import org.json.JSONArray
import org.json.JSONObject
import com.wind.ggbond.classtime.util.Constants.Course.MAX_COURSE_NAME_LENGTH
import com.wind.ggbond.classtime.util.Constants.Course.MAX_TEACHER_NAME_LENGTH
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.Constants.Course.MAX_CLASSROOM_NAME_LENGTH

/**
 * 输入验证和XSS过滤工具
 * 
 * 用于防止XSS攻击和SQL注入等安全问题
 */
object InputValidator {
    
    private const val TAG = "InputValidator"
    
    // XSS危险字符和模式
    private val XSS_PATTERNS = listOf(
        "<script", "</script>", "javascript:", "onerror=", "onload=",
        "<iframe", "</iframe>", "eval(", "expression(", "vbscript:",
        "<object", "<embed", "<applet", "onclick=", "onmouseover="
    )
    
    // SQL注入模式
    private val SQL_INJECTION_PATTERNS = listOf(
        "' OR '1'='1", "'; DROP TABLE", "'; DELETE FROM", 
        "' OR 1=1--", "' UNION SELECT", "'; --"
    )
    
    /**
     * 过滤XSS攻击字符
     * 
     * @param input 输入字符串
     * @return 过滤后的安全字符串
     */
    fun sanitizeXSS(input: String): String {
        var result = input
        
        // 转义HTML特殊字符
        result = result
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
        
        // 检测并记录可疑模式
        for (pattern in XSS_PATTERNS) {
            if (input.contains(pattern, ignoreCase = true)) {
                AppLogger.w(TAG, "XSS pattern detected: $pattern in input: ${input.take(50)}")
            }
        }
        
        return result
    }
    
    private fun validateField(value: String, maxLength: Int, fieldName: String): Boolean {
        if (value.length > maxLength) return false
        if (containsXSSPattern(value)) {
            AppLogger.w(TAG, "$fieldName contains suspicious XSS pattern: ${value.take(50)}")
            return false
        }
        return true
    }

    private fun containsXSSPattern(input: String): Boolean {
        val lower = input.lowercase()
        return XSS_PATTERNS.any { pattern -> lower.contains(pattern.lowercase()) }
    }
    
    /**
     * 验证课程名称
     * 
     * @param name 课程名称
     * @return 是否有效
     */
    fun validateCourseName(name: String): Boolean {
        if (name.isBlank()) return false
        return validateField(name, MAX_COURSE_NAME_LENGTH, "Course name")
    }
    
    /**
     * 验证教师姓名
     * 
     * @param teacher 教师姓名
     * @return 是否有效
     */
    fun validateTeacher(teacher: String): Boolean {
        return validateField(teacher, MAX_TEACHER_NAME_LENGTH, "Teacher name")
    }
    
    /**
     * 验证教室名称
     * 
     * @param classroom 教室名称
     * @return 是否有效
     */
    fun validateClassroom(classroom: String): Boolean {
        return validateField(classroom, MAX_CLASSROOM_NAME_LENGTH, "Classroom name")
    }
    
    /**
     * 验证JSON结构（从WebView返回的课程数据）
     * 
     * @param jsonString JSON字符串
     * @return 验证结果，包含是否有效和错误消息
     */
    fun validateScheduleJson(jsonString: String): ValidationResult {
        try {
            // 移除可能的JavaScript包装
            val cleanJson = jsonString
                .trim()
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("\\r", "")
            
            if (cleanJson.isBlank()) {
                return ValidationResult(false, "JSON字符串为空")
            }
            
            // 尝试解析为JSONArray
            val jsonArray = try {
                JSONArray(cleanJson)
            } catch (e: Exception) {
                // 如果不是数组，可能是对象，尝试解析
                val jsonObject = JSONObject(cleanJson)
                if (jsonObject.has("courses")) {
                    jsonObject.getJSONArray("courses")
                } else {
                    return ValidationResult(false, "JSON格式不正确：缺少courses字段")
                }
            }
            
            if (jsonArray.length() == 0) {
                return ValidationResult(false, "课程列表为空")
            }
            
            // 验证每个课程对象
            for (i in 0 until jsonArray.length()) {
                val course = jsonArray.getJSONObject(i)
                
                // 必需字段检查（支持两种格式：startSection 或 sectionRow）
                if (!course.has("courseName")) {
                    return ValidationResult(false, "课程 $i 缺少必需字段: courseName")
                }
                if (!course.has("dayOfWeek")) {
                    return ValidationResult(false, "课程 $i 缺少必需字段: dayOfWeek")
                }
                
                // 支持两种格式：startSection（直接格式）或 sectionRow（JavaScript提取格式）
                val hasStartSection = course.has("startSection")
                val hasSectionRow = course.has("sectionRow")
                if (!hasStartSection && !hasSectionRow) {
                    return ValidationResult(false, "课程 $i 缺少必需字段: startSection 或 sectionRow")
                }
                
                // 验证字段类型和范围
                val courseName = course.getString("courseName")
                // 注意：在验证阶段，课程名称可能包含未解析的完整信息
                // 实际验证会在解析后由parseCourseNameFromText处理
                // 这里只做基本检查：不为空，长度不超过300（允许包含完整信息）
                if (courseName.isBlank()) {
                    return ValidationResult(false, "课程名称不能为空")
                }
                if (courseName.length > 300) {
                    return ValidationResult(false, "课程名称过长: ${courseName.length}字符")
                }
                
                val dayOfWeek = course.getInt("dayOfWeek")
                // 如果星期值超出范围，自动修正（而不是验证失败）
                val validDayOfWeek = when {
                    dayOfWeek < Constants.Course.MIN_DAY_OF_WEEK -> Constants.Course.MIN_DAY_OF_WEEK
                    dayOfWeek > Constants.Course.MAX_DAY_OF_WEEK -> {
                        // 如果大于7，取模（可能是表格列数计算错误）
                        ((dayOfWeek - 1) % 7) + 1
                    }
                    else -> dayOfWeek
                }
                
                // 记录修正信息（但不阻止验证）
                if (validDayOfWeek != dayOfWeek) {
                    AppLogger.w(TAG, "星期值已修正: $dayOfWeek -> $validDayOfWeek")
                }
                
                // 验证节次（如果有startSection，验证它；如果有sectionRow，转换为startSection后验证）
                if (hasStartSection) {
                    val startSection = course.getInt("startSection")
                    if (startSection !in Constants.Course.MIN_SECTION_NUMBER..Constants.Course.MAX_SECTION_NUMBER) {
                        return ValidationResult(
                            false, 
                            "起始节次无效: $startSection (应在${Constants.Course.MIN_SECTION_NUMBER}-${Constants.Course.MAX_SECTION_NUMBER}之间)"
                        )
                    }
                    
                    // 验证sectionCount（如果存在）
                    if (course.has("sectionCount")) {
                        val sectionCount = course.getInt("sectionCount")
                        if (sectionCount !in Constants.Course.MIN_SECTION_COUNT..Constants.Course.MAX_SECTION_COUNT) {
                            return ValidationResult(
                                false, 
                                "节次数量无效: $sectionCount (应在${Constants.Course.MIN_SECTION_COUNT}-${Constants.Course.MAX_SECTION_COUNT}之间)"
                            )
                        }
                    }
                } else if (hasSectionRow) {
                    // sectionRow格式：验证sectionRow是否合理
                    val sectionRow = course.getInt("sectionRow")
                    if (sectionRow < 1 || sectionRow > 12) {
                        return ValidationResult(
                            false, 
                            "节次行号无效: $sectionRow (应在1-12之间)"
                        )
                    }
                }
                
                // 验证可选字段
                if (course.has("teacher")) {
                    val teacher = course.getString("teacher")
                    if (!validateTeacher(teacher)) {
                        return ValidationResult(
                            false, 
                            "教师姓名无效: $teacher"
                        )
                    }
                }
                
                if (course.has("classroom")) {
                    val classroom = course.getString("classroom")
                    if (!validateClassroom(classroom)) {
                        return ValidationResult(
                            false, 
                            "教室名称无效: $classroom"
                        )
                    }
                }
            }
            
            AppLogger.i(TAG, "JSON validation passed: ${jsonArray.length()} courses")
            return ValidationResult(true, "验证成功")
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "JSON validation failed", e)
            return ValidationResult(false, "JSON解析失败: ${e.message}")
        }
    }
    
    /**
     * 检测SQL注入
     * 
     * @param input 输入字符串
     * @return 是否包含SQL注入模式
     */
    fun detectSqlInjection(input: String): Boolean {
        for (pattern in SQL_INJECTION_PATTERNS) {
            if (input.contains(pattern, ignoreCase = true)) {
                AppLogger.w(TAG, "SQL injection pattern detected: $pattern")
                return true
            }
        }
        return false
    }
    
    /**
     * 验证URL
     * 
     * @param url URL字符串
     * @return 是否有效
     */
    fun validateUrl(url: String): Boolean {
        if (url.isBlank()) return false
        
        // 只允许http和https协议
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            AppLogger.w(TAG, "Invalid URL protocol: $url")
            return false
        }
        
        // 检查是否包含可疑字符
        if (url.contains("javascript:", ignoreCase = true) ||
            url.contains("data:", ignoreCase = true) ||
            url.contains("file:", ignoreCase = true)) {
            AppLogger.w(TAG, "Suspicious URL detected: $url")
            return false
        }
        
        return true
    }
}

/**
 * 验证结果
 */
data class ValidationResult(
    val isValid: Boolean,
    val message: String
)
