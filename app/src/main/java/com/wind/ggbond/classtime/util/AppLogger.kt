package com.wind.ggbond.classtime.util

import android.util.Log
import com.wind.ggbond.classtime.BuildConfig

/**
 * 📋 统一日志工具类
 *
 * ✅ 功能：
 * - 统一日志格式（带模块前缀）
 * - Release 版本自动过滤 DEBUG 日志
 * - **敏感数据自动脱敏**（核心日志管道集成）
 * - 支持多种敏感数据类型的智能识别和脱敏
 *
 * 使用示例：
 * ```kotlin
 * AppLogger.d("Import", "导入成功")
 * AppLogger.e("Network", "请求失败", exception)
 * AppLogger.sensitive("Auth", "Cookie", cookieString)
 * // 自动脱敏：检测到 password/cookie/token 等关键词会自动脱敏
 * AppLogger.d("Login", "password=123456, token=abc")  // 输出: password=***, token=abc****
 * ```
 */
object AppLogger {

    private const val TAG_PREFIX = "KE"

    /**
     * 是否启用自动脱敏（默认开启）
     * 可通过 setDesensitizeEnabled(false) 关闭（仅用于调试场景）
     */
    @Volatile
    private var desensitizeEnabled = true

    /**
     * 设置是否启用自动脱敏
     *
     * @param enabled true 启用（默认），false 禁用
     */
    fun setDesensitizeEnabled(enabled: Boolean) {
        desensitizeEnabled = enabled
    }

    /**
     * 🔒 对日志消息进行自动脱敏处理
     *
     * 扫描消息中的敏感信息并自动替换
     * 检测格式：key=value 或 key: value
     */
    private fun processMessage(message: String): String {
        return if (desensitizeEnabled) {
            DesensitizeUtils.desensitizeMessage(message)
        } else {
            message
        }
    }

    /**
     * DEBUG 级别日志（Release 版本不输出，自动脱敏）
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("$TAG_PREFIX/$tag", processMessage(message))
        }
    }

    /**
     * INFO 级别日志（自动脱敏）
     */
    fun i(tag: String, message: String) {
        Log.i("$TAG_PREFIX/$tag", processMessage(message))
    }

    /**
     * WARN 级别日志（自动脱敏）
     */
    fun w(tag: String, message: String) {
        Log.w("$TAG_PREFIX/$tag", processMessage(message))
    }

    /**
     * ERROR 级别日志（自动脱敏）
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val processedMsg = processMessage(message)
        if (throwable != null) {
            Log.e("$TAG_PREFIX/$tag", processedMsg, throwable)
        } else {
            Log.e("$TAG_PREFIX/$tag", processedMsg)
        }
    }

    /**
     * 🔒 安全日志：显式脱敏敏感数据（推荐使用）
     *
     * 根据数据类型智能选择脱敏策略：
     * - password/pwd → 完全隐藏 ***
     * - cookie → 显示前4位+****
     * - studentId/学号 → 前2位+**+后2位
     * - phone → 前3位+****+后4位
     * - token/secret → 显示前8位+***+后4位
     * - 其他 → 通用脱敏
     *
     * @param tag 日志标签
     * @param key 数据名称（如 "Cookie", "Token", "Password"）
     * @param value 敏感数据值
     */
    fun sensitive(tag: String, key: String, value: String?) {
        if (BuildConfig.DEBUG) {
            val masked = DesensitizeUtils.maskByKey(key, value)
            d(tag, "$key: $masked ${if (value != null) "[length=${value.length}]" else ""}")
        }
    }

    /**
     * 🔒 安全日志：显式脱敏（带自定义mask方法）
     *
     * @param tag 日志标签
     * @param key 数据名称
     * @param value 原始值
     * @param maskFunction 自定义脱敏函数
     */
    fun sensitive(tag: String, key: String, value: String?, maskFunction: (String?) -> String) {
        if (BuildConfig.DEBUG) {
            val masked = maskFunction(value)
            d(tag, "$key: $masked ${if (value != null) "[length=${value.length}]" else ""}")
        }
    }

    /**
     * 带上下文的 DEBUG 日志（自动脱敏）
     * 自动包含调用位置信息
     */
    internal inline fun d(tag: String, lazyMessage: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d("$TAG_PREFIX/$tag", processMessage(lazyMessage()))
        }
    }
}
