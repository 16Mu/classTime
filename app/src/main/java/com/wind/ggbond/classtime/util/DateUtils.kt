package com.wind.ggbond.classtime.util

import android.util.Log
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 日期工具类
 * 
 * ✅ 添加了完善的边界检查和错误处理
 */
object DateUtils {
    
    private const val TAG = "DateUtils"
    
    // ✅ 使用Constants中定义的范围
    private val MIN_WEEK_NUMBER = Constants.Semester.MIN_WEEK_NUMBER
    private val MAX_WEEK_NUMBER = Constants.Semester.MAX_WEEK_NUMBER
    
    /**
     * 计算当前是学期的第几周
     * 
     * ✅ 添加参数验证和边界检查
     * 
     * @param semesterStartDate 学期开始日期（不能为null）
     * @param currentDate 当前日期（默认为今天）
     * @return 周次（从1开始），如果日期无效返回1
     * @throws IllegalArgumentException 如果日期参数无效
     */
    fun calculateWeekNumber(
        semesterStartDate: LocalDate, 
        currentDate: LocalDate = LocalDate.now()
    ): Int {
        // ✅ 验证：currentDate不能早于semesterStartDate
        if (currentDate.isBefore(semesterStartDate)) {
            Log.w(TAG, "currentDate ($currentDate) is before semesterStartDate ($semesterStartDate), returning week 1")
            return 1  // 返回第1周而不是负数
        }
        
        val days = ChronoUnit.DAYS.between(semesterStartDate, currentDate)
        
        // ✅ 边界检查：防止计算结果异常
        if (days < 0) {
            Log.e(TAG, "Unexpected negative days: $days, returning week 1")
            return 1
        }
        
        val weekNumber = (days / 7 + 1).toInt()
        
        // ✅ 合理性检查：周次不应超过30周
        if (weekNumber > MAX_WEEK_NUMBER) {
            Log.w(TAG, "Week number $weekNumber exceeds maximum $MAX_WEEK_NUMBER, check semester configuration")
        }
        
        return weekNumber.coerceIn(MIN_WEEK_NUMBER, MAX_WEEK_NUMBER)
    }
    
    /**
     * 获取指定周次的周一日期
     * 
     * ✅ 添加参数验证
     * 
     * @param semesterStartDate 学期开始日期
     * @param weekNumber 周次（必须 >= 1）
     * @return 该周的周一日期
     * @throws IllegalArgumentException 如果weekNumber < 1
     */
    fun getMondayOfWeek(semesterStartDate: LocalDate, weekNumber: Int): LocalDate {
        // ✅ 验证周次
        require(weekNumber >= MIN_WEEK_NUMBER) {
            "Week number must be >= $MIN_WEEK_NUMBER, got: $weekNumber"
        }
        
        if (weekNumber > MAX_WEEK_NUMBER) {
            Log.w(TAG, "Week number $weekNumber is unusually large")
        }
        
        // 计算学期第一周的周一
        val firstMonday = semesterStartDate.minusDays((semesterStartDate.dayOfWeek.value - 1).toLong())
        // 加上周数偏移
        return firstMonday.plusWeeks((weekNumber - 1).toLong())
    }
    
    /**
     * 获取星期几的中文名称
     * 
     * ✅ 添加边界检查
     * 
     * @param dayOfWeek 星期数（1-7）
     * @return 中文名称，如果无效返回空字符串
     */
    fun getDayOfWeekName(dayOfWeek: Int): String {
        // ✅ 使用Constants验证范围
        if (dayOfWeek !in Constants.Course.MIN_DAY_OF_WEEK..Constants.Course.MAX_DAY_OF_WEEK) {
            Log.w(TAG, "Invalid dayOfWeek: $dayOfWeek, expected ${Constants.Course.MIN_DAY_OF_WEEK}-${Constants.Course.MAX_DAY_OF_WEEK}")
            return ""
        }
        
        return when (dayOfWeek) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            7 -> "周日"
            else -> ""
        }
    }
    
    /**
     * 获取星期几的简短名称
     * 
     * ✅ 添加边界检查
     * 
     * @param dayOfWeek 星期数（1-7）
     * @return 简短名称，如果无效返回空字符串
     */
    fun getDayOfWeekShortName(dayOfWeek: Int): String {
        // ✅ 使用Constants验证范围
        if (dayOfWeek !in Constants.Course.MIN_DAY_OF_WEEK..Constants.Course.MAX_DAY_OF_WEEK) {
            Log.w(TAG, "Invalid dayOfWeek: $dayOfWeek, expected ${Constants.Course.MIN_DAY_OF_WEEK}-${Constants.Course.MAX_DAY_OF_WEEK}")
            return ""
        }
        
        return when (dayOfWeek) {
            1 -> "一"
            2 -> "二"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            6 -> "六"
            7 -> "日"
            else -> ""
        }
    }
}
