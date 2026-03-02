package com.wind.ggbond.classtime.data.model

/**
 * 学校配置数据模型
 */
data class SchoolConfig(
    val id: String,
    val name: String,
    val loginUrl: String,
    val scheduleUrl: String,
    val scheduleMethod: String = "GET", // GET or POST
    val scheduleParams: Map<String, String> = emptyMap(),
    val dataFormat: DataFormat = DataFormat.JSON,
    val jsonPaths: Map<String, String> = emptyMap(), // 字段映射
    val htmlSelectors: Map<String, String> = emptyMap(), // HTML选择器
    val needCsrfToken: Boolean = false, // 是否需要CSRF Token
    val csrfTokenName: String = "_csrf" // CSRF Token的字段名
)

enum class DataFormat {
    JSON, HTML, XML
}

/**
 * 解析后的课程数据
 */
data class ParsedCourse(
    val courseName: String,
    val teacher: String = "",
    val classroom: String = "",
    val dayOfWeek: Int,
    val startSection: Int,
    val sectionCount: Int = 1,
    val weekExpression: String = "",
    val weeks: List<Int> = emptyList(),
    val credit: Float = 0f  // 学分，默认0（可在导入后手动编辑）
)

/**
 * 导入时提取到的学期信息
 * 用于存储从课表页面提取的学期开始日期、结束日期和总周数
 * 如果提取到了这些信息，导入时就不需要用户手动设置
 */
data class ImportedSemesterInfo(
    val startDate: java.time.LocalDate? = null,  // 学期开始日期
    val endDate: java.time.LocalDate? = null,    // 学期结束日期
    val totalWeeks: Int? = null                   // 总周数
) {
    /**
     * 检查是否有完整的学期日期信息
     * 如果有开始日期和（结束日期或总周数），则认为有完整信息
     */
    fun hasCompleteDateInfo(): Boolean {
        return startDate != null && (endDate != null || totalWeeks != null)
    }
    
    /**
     * 计算总周数（如果有开始和结束日期）
     */
    fun calculateTotalWeeks(): Int {
        if (totalWeeks != null) return totalWeeks
        if (startDate != null && endDate != null) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)
            return ((days + 1) / 7).toInt().coerceAtLeast(1)
        }
        return 20 // 默认20周
    }
    
    /**
     * 计算结束日期（如果有开始日期和总周数）
     */
    fun calculateEndDate(): java.time.LocalDate? {
        if (endDate != null) return endDate
        if (startDate != null && totalWeeks != null) {
            return startDate.plusWeeks(totalWeeks.toLong()).minusDays(1)
        }
        return null
    }
}



