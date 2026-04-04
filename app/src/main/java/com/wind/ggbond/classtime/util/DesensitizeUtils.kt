package com.wind.ggbond.classtime.util

/**
 * 🔒 日志脱敏工具类
 *
 * ✅ 功能：
 * - 提供多种敏感数据类型的脱敏方法
 * - 支持密码、Cookie、学号、手机号、Token等常见敏感数据
 * - 支持Debug/Release模式下的不同脱敏级别
 * - 自动检测并脱敏包含敏感关键词的键值对
 *
 * 使用示例：
 * ```kotlin
 * DesensitizeUtils.maskPassword("123456")           // "***"
 * DesensitizeUtils.maskCookie("session=abc123")      // "sess****"
 * DesensitizeUtils.maskStudentId("20210001")         // "20****01"
 * DesensitizeUtils.maskPhone("13812345678")          // "138****5678"
 * ```
 */
object DesensitizeUtils {

    private const val MASK_ALL = "***"
    private const val MASK_PARTIAL = "****"

    /**
     * 敏感关键词列表（英文，不区分大小写）
     */
    val SENSITIVE_KEYWORDS_EN = listOf(
        "password", "passwd", "pwd",
        "cookie", "cookies",
        "token", "accesstoken", "refreshtoken",
        "secret", "apikey", "api_key",
        "credential", "auth",
        "studentid", "student_id", "学号",
        "phone", "mobile", "tel"
    )

    /**
     * 敏感关键词列表（中文）
     */
    val SENSITIVE_KEYWORDS_CN = listOf(
        "密码", "口令",
        "学号",
        "手机", "电话",
        "凭证", "令牌"
    )

    /**
     * 🔒 脱敏密码（完全隐藏）
     */
    fun maskPassword(value: String?): String {
        return if (value.isNullOrEmpty()) "" else MASK_ALL
    }

    /**
     * 🔒 脱敏Cookie（显示前4位+****）
     */
    fun maskCookie(value: String?): String {
        return if (value.isNullOrEmpty()) ""
        else if (value.length <= 4) MASK_PARTIAL
        else value.take(4) + MASK_PARTIAL
    }

    /**
     * 🔒 脱敏学号（前2位+**+后2位）
     */
    fun maskStudentId(value: String?): String {
        return if (value.isNullOrEmpty() || value.length <= 4) MASK_PARTIAL
        else value.take(2) + "**" + value.takeLast(2)
    }

    /**
     * 🔒 脱敏手机号（前3位+****+后4位）
     */
    fun maskPhone(value: String?): String {
        return if (value.isNullOrEmpty() || value.length <= 7) MASK_PARTIAL
        else value.take(3) + MASK_PARTIAL + value.takeLast(4)
    }

    /**
     * 🔒 脱敏Token/Secret（显示前8位+***+后4位）
     */
    fun maskToken(value: String?): String {
        return if (value.isNullOrEmpty()) ""
        else if (value.length > 12) value.take(8) + "***" + value.takeLast(4)
        else if (value.isNotEmpty()) MASK_ALL
        else "(empty)"
    }

    /**
     * 🔒 脱敏通用敏感值（显示前8位+***+后4位）
     */
    fun maskSensitive(value: String?): String {
        return if (value.isNullOrEmpty()) ""
        else if (value.length > 12) value.take(8) + "***" + value.takeLast(4)
        else if (value.isNotEmpty()) MASK_ALL
        else "(empty)"
    }

    /**
     * 🔒 根据key名称智能选择脱敏策略
     *
     * @param key 键名（如 "password", "cookie", "studentId"）
     * @param value 值
     * @return 脱敏后的字符串
     */
    fun maskByKey(key: String, value: String?): String {
        if (value == null) return "null"
        if (value.isEmpty()) return "(empty)"

        val lowerKey = key.lowercase()

        return when {
            lowerKey.contains("password") || lowerKey.contains("passwd") || lowerKey.contains("pwd") -> maskPassword(value)
            lowerKey.contains("cookie") -> maskCookie(value)
            lowerKey.contains("token") || lowerKey.contains("secret") || lowerKey.contains("apikey") || lowerKey.contains("api_key") -> maskToken(value)
            lowerKey.contains("studentid") || lowerKey.contains("student_id") || key.contains("学号") -> maskStudentId(value)
            lowerKey.contains("phone") || lowerKey.contains("mobile") || lowerKey.contains("tel") -> maskPhone(value)
            lowerKey.contains("credential") || lowerKey.contains("auth") -> maskSensitive(value)
            else -> value
        }
    }

    /**
     * 🔒 扫描消息中的敏感信息并自动脱敏
     *
     * 检测格式：key=value 或 key: value
     * 自动识别敏感key并对其value进行脱敏处理
     *
     * @param message 原始日志消息
     * @return 脱敏后的日志消息
     */
    fun desensitizeMessage(message: String): String {
        var result = message

        // 匹配 key=value 格式（支持空格）
        val keyValuePatterns = listOf(
            Regex("""(\w[\w]*[Pp]assword\w*|cookie[s]?\w*)\s*[=:]\s*(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""(\w[\w]*[Tt]oken\w*|\w[\w]*[Ss]ecret\w*|\w[\w]*[Aa]pi[_-]?[Kk]ey\w*)\s*[=:]\s*(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""(\w[\w]*[Ss]tudent[Ii][Dd]\w*|学号)\s*[=:]\s*(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""(\w[\w]*[Pp]hone\w*|\w[\w]*[Mm]obile\w*|\w[\w]*[Tt]el\w*)\s*[=:]\s*(\S+)""", RegexOption.IGNORE_CASE),
            Regex("""(\w[\w]*[Cc]redential\w*|\w[\w]*[Aa]uth\w*)\s*[=:]\s*(\S+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in keyValuePatterns) {
            result = pattern.replace(result) { matchResult ->
                val key = matchResult.groupValues[1]
                val value = matchResult.groupValues[2]
                "$key=${maskByKey(key, value)}"
            }
        }

        return result
    }

    /**
     * 🔒 检查是否为敏感关键词
     */
    fun isSensitiveKeyword(key: String): Boolean {
        val lowerKey = key.lowercase()
        return SENSITIVE_KEYWORDS_EN.any { lowerKey.contains(it) } ||
               SENSITIVE_KEYWORDS_CN.any { key.contains(it) }
    }
}
