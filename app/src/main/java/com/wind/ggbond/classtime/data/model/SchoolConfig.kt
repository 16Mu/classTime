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



