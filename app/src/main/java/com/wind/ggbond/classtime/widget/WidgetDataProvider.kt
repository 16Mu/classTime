package com.wind.ggbond.classtime.widget

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import com.wind.ggbond.classtime.data.local.database.Migrations
import com.wind.ggbond.classtime.util.DateUtils
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Widget 数据提供器
 * 
 * 负责从 Room 数据库中获取今日课程数据，
 * 供桌面小组件使用。由于 Widget 无法使用 Hilt 注入，
 * 此处通过单例模式复用 Room 数据库实例。
 */
object WidgetDataProvider {

    private const val TAG = "WidgetDataProvider"

    /** 日期格式化器：用于显示日期 */
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日")

    /** 时间格式化器：用于显示上下课时间 */
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

    /** 数据库单例：使用 volatile + 双重检查锁定，避免重复创建 */
    @Volatile
    private var databaseInstance: CourseDatabase? = null

    /**
     * 获取数据库单例
     * 
     * Widget 无法使用 Hilt，需直接构建 Room 实例。
     * 使用双重检查锁定确保线程安全且只创建一个实例。
     */
    private fun getDatabase(context: Context): CourseDatabase {
        return databaseInstance ?: synchronized(this) {
            databaseInstance ?: Room.databaseBuilder(
                context.applicationContext,
                CourseDatabase::class.java,
                CourseDatabase.DATABASE_NAME
            )
                .addMigrations(*Migrations.getAllMigrations())
                .fallbackToDestructiveMigration()
                .build()
                .also { databaseInstance = it }
        }
    }

    /**
     * 获取今日课程数据
     * 
     * 完整流程：
     * 1. 获取当前学期 → 计算当前周次
     * 2. 获取当前课表 → 加载该课表的所有课程
     * 3. 过滤出今日 + 本周的课程
     * 4. 应用调课记录（调走的课程移除，调入的课程添加）
     * 5. 关联上下课时间
     * 6. 按节次排序返回
     * 
     * @param context 应用上下文
     * @return 今日课程展示数据
     */
    suspend fun getTodayCourses(context: Context): WidgetDisplayData {
        val db = getDatabase(context)
        try {
            // 获取当前时间，用于计算课程进度
            val now = LocalTime.now()
            // 获取当前课表（包含学期时间信息）
            val schedule = db.scheduleDao().getCurrentSchedule()
            if (schedule == null) {
                Log.w(TAG, "未找到当前课表")
                return WidgetDisplayData.empty("未设置课表")
            }

            // 计算当前周次
            val today = LocalDate.now()
            val currentWeekNumber = DateUtils.calculateWeekNumber(schedule.startDate, today)

            // 今天是星期几 (1=周一, 7=周日)
            val todayDayOfWeek = today.dayOfWeek.value

            // 获取所有课程
            val allCourses = db.courseDao().getAllCourses()
                .filter { it.scheduleId == schedule.id }

            // 获取所有调课记录
            val adjustments = db.courseAdjustmentDao().getAdjustmentsByScheduleSync(schedule.id)

            // 构建调课映射：被调走的课程集合
            val movedAwayCourses = adjustments.filter {
                it.originalWeekNumber == currentWeekNumber &&
                it.originalDayOfWeek == todayDayOfWeek
            }.map { it.originalCourseId to it.originalStartSection }
                .toSet()

            // 构建调课映射：被调入今天的课程
            val movedInAdjustments = adjustments.filter {
                it.newWeekNumber == currentWeekNumber &&
                it.newDayOfWeek == todayDayOfWeek
            }

            // 过滤今日课程：属于今天、本周有课、且未被调走
            val todayCourses = allCourses.filter { course ->
                course.dayOfWeek == todayDayOfWeek &&
                currentWeekNumber in course.weeks &&
                (course.id to course.startSection) !in movedAwayCourses
            }.toMutableList()

            // 添加调入今天的课程（将原始课程信息复制，但使用调课后的节次）
            movedInAdjustments.forEach { adjustment ->
                val originalCourse = allCourses.find { it.id == adjustment.originalCourseId }
                if (originalCourse != null) {
                    todayCourses.add(
                        originalCourse.copy(
                            dayOfWeek = adjustment.newDayOfWeek,
                            startSection = adjustment.newStartSection,
                            sectionCount = adjustment.newSectionCount
                        )
                    )
                }
            }

            // 按开始节次排序
            todayCourses.sortBy { it.startSection }

            // 获取上下课时间配置
            val classTimes = db.classTimeDao().getClassTimesByConfigSync("default")
            val classTimeMap = classTimes.associateBy { it.sectionNumber }

            // 转换为 Widget 展示数据
            val courseItems = todayCourses.map { course ->
                val startTime = classTimeMap[course.startSection]?.startTime
                val endSection = course.startSection + course.sectionCount - 1
                val endTime = classTimeMap[endSection]?.endTime

                WidgetCourseItem(
                    courseName = course.courseName,
                    classroom = course.classroom,
                    teacher = course.teacher,
                    startSection = course.startSection,
                    sectionCount = course.sectionCount,
                    startTimeText = startTime?.format(TIME_FORMATTER) ?: "",
                    endTimeText = endTime?.format(TIME_FORMATTER) ?: "",
                    color = course.color,
                    isOngoing = isOngoing(startTime, endTime)
                )
            }

            // 计算课程进度：已结束的课程数量
            val finishedCount = courseItems.count { item ->
                if (item.endTimeText.isEmpty()) return@count false
                try {
                    val endTime = LocalTime.parse(item.endTimeText, TIME_FORMATTER)
                    now.isAfter(endTime)
                } catch (e: Exception) { false }
            }

            // 构建进度文本："已上2节/共5节"
            val progressText = if (courseItems.isNotEmpty()) {
                "已上${finishedCount}节/共${courseItems.size}节"
            } else ""

            return WidgetDisplayData(
                dateText = today.format(DATE_FORMATTER),
                dayOfWeekText = DateUtils.getDayOfWeekName(todayDayOfWeek),
                weekNumberText = "第${currentWeekNumber}周",
                courseItems = courseItems,
                emptyMessage = null,
                progressText = progressText
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取今日课程失败", e)
            return WidgetDisplayData.empty("数据加载失败")
        }
    }

    /**
     * 获取下节课数据
     * 
     * 查找今天剩余课程中最近的一节课，
     * 计算距离上课的剩余分钟数。
     * 
     * @param context 应用上下文
     * @return 下节课展示数据
     */
    suspend fun getNextClass(context: Context): NextClassDisplayData {
        try {
            // 获取今日课程数据（复用已有逻辑，内部自行管理数据库连接）
            val todayData = getTodayCourses(context)
            if (todayData.courseItems.isEmpty()) {
                return NextClassDisplayData(
                    hasNextClass = false,
                    dayOfWeekText = todayData.dayOfWeekText,
                    weekNumberText = todayData.weekNumberText,
                    message = "今日无课程"
                )
            }

            val now = LocalTime.now()

            // 查找正在进行的课程
            val ongoingCourse = todayData.courseItems.find { it.isOngoing }
            if (ongoingCourse != null) {
                // 计算距离下课的剩余分钟数
                val endTime = try {
                    LocalTime.parse(ongoingCourse.endTimeText, TIME_FORMATTER)
                } catch (e: Exception) { null }
                val minutesRemaining = if (endTime != null) {
                    java.time.Duration.between(now, endTime).toMinutes().toInt()
                } else { 0 }

                return NextClassDisplayData(
                    hasNextClass = true,
                    isOngoing = true,
                    courseName = ongoingCourse.courseName,
                    classroom = ongoingCourse.classroom,
                    teacher = ongoingCourse.teacher,
                    timeText = "${ongoingCourse.startTimeText} - ${ongoingCourse.endTimeText}",
                    sectionText = "${ongoingCourse.startSection}-${ongoingCourse.startSection + ongoingCourse.sectionCount - 1}节",
                    color = ongoingCourse.color,
                    minutesRemaining = minutesRemaining,
                    dayOfWeekText = todayData.dayOfWeekText,
                    weekNumberText = todayData.weekNumberText,
                    message = "正在上课"
                )
            }

            // 查找下一节即将开始的课程（开始时间在当前时间之后）
            val nextCourse = todayData.courseItems.firstOrNull { item ->
                if (item.startTimeText.isEmpty()) return@firstOrNull false
                try {
                    val startTime = LocalTime.parse(item.startTimeText, TIME_FORMATTER)
                    startTime.isAfter(now)
                } catch (e: Exception) { false }
            }

            if (nextCourse != null) {
                // 计算距离上课的剩余分钟数
                val startTime = try {
                    LocalTime.parse(nextCourse.startTimeText, TIME_FORMATTER)
                } catch (e: Exception) { null }
                val minutesRemaining = if (startTime != null) {
                    java.time.Duration.between(now, startTime).toMinutes().toInt()
                } else { 0 }

                return NextClassDisplayData(
                    hasNextClass = true,
                    isOngoing = false,
                    courseName = nextCourse.courseName,
                    classroom = nextCourse.classroom,
                    teacher = nextCourse.teacher,
                    timeText = "${nextCourse.startTimeText} - ${nextCourse.endTimeText}",
                    sectionText = "${nextCourse.startSection}-${nextCourse.startSection + nextCourse.sectionCount - 1}节",
                    color = nextCourse.color,
                    minutesRemaining = minutesRemaining,
                    dayOfWeekText = todayData.dayOfWeekText,
                    weekNumberText = todayData.weekNumberText,
                    message = null
                )
            }

            // 今日课程已全部结束，尝试显示明日课程预告
            val tomorrowPreview = getTomorrowPreview(context)
            return NextClassDisplayData(
                hasNextClass = false,
                dayOfWeekText = todayData.dayOfWeekText,
                weekNumberText = todayData.weekNumberText,
                message = tomorrowPreview
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取下节课数据失败", e)
            return NextClassDisplayData(
                hasNextClass = false,
                message = "数据加载失败"
            )
        }
    }

    /**
     * 获取明日课程预告
     * 
     * 当今日课程已全部结束时，显示明天的课程数量，
     * 提供友好的预告信息。
     * 
     * @param context 应用上下文
     * @return 明日预告文本
     */
    private suspend fun getTomorrowPreview(context: Context): String {
        try {
            val db = getDatabase(context)
            val schedule = db.scheduleDao().getCurrentSchedule() ?: return "今日课程已结束"

            // 计算明天的日期和周次
            val tomorrow = LocalDate.now().plusDays(1)
            val tomorrowWeek = DateUtils.calculateWeekNumber(schedule.startDate, tomorrow)
            val tomorrowDayOfWeek = tomorrow.dayOfWeek.value

            // 查询明天的课程数量
            val allCourses = db.courseDao().getAllCourses()
                .filter { it.scheduleId == schedule.id }
            val tomorrowCount = allCourses.count { course ->
                course.dayOfWeek == tomorrowDayOfWeek && tomorrowWeek in course.weeks
            }

            return if (tomorrowCount > 0) {
                "今日已结束 · 明日${tomorrowCount}节课"
            } else {
                "今日已结束 · 明日无课"
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取明日预告失败", e)
            return "今日课程已结束"
        }
    }

    /**
     * 判断课程是否正在进行中
     * 
     * @param startTime 课程开始时间
     * @param endTime 课程结束时间
     * @return 当前时间是否在课程时间范围内
     */
    private fun isOngoing(startTime: LocalTime?, endTime: LocalTime?): Boolean {
        if (startTime == null || endTime == null) return false
        val now = LocalTime.now()
        return now.isAfter(startTime) && now.isBefore(endTime)
    }
}

/**
 * Widget 展示数据
 * 
 * 包含今日课程列表和头部显示信息
 */
data class WidgetDisplayData(
    val dateText: String,           // 日期文本，如 "2月13日"
    val dayOfWeekText: String,      // 星期文本，如 "周四"
    val weekNumberText: String,     // 周次文本，如 "第10周"
    val courseItems: List<WidgetCourseItem>,  // 课程列表
    val emptyMessage: String?,      // 无课程时的提示信息
    val progressText: String = ""   // 课程进度文本，如 "已上2节/共5节"
) {
    companion object {
        /** 创建空数据（无课程或异常时使用） */
        fun empty(message: String) = WidgetDisplayData(
            dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日")),
            dayOfWeekText = DateUtils.getDayOfWeekName(LocalDate.now().dayOfWeek.value),
            weekNumberText = "",
            courseItems = emptyList(),
            emptyMessage = message,
            progressText = ""
        )
    }
}

/**
 * 单个课程的展示数据
 */
data class WidgetCourseItem(
    val courseName: String,     // 课程名称
    val classroom: String,      // 教室
    val teacher: String,        // 教师
    val startSection: Int,      // 开始节次
    val sectionCount: Int,      // 持续节数
    val startTimeText: String,  // 开始时间文本，如 "08:00"
    val endTimeText: String,    // 结束时间文本，如 "09:40"
    val color: String,          // 课程颜色（16进制）
    val isOngoing: Boolean      // 是否正在进行中
)

/**
 * 下节课倒计时展示数据
 */
data class NextClassDisplayData(
    val hasNextClass: Boolean,          // 是否有下节课
    val isOngoing: Boolean = false,     // 当前是否正在上课
    val courseName: String = "",        // 课程名称
    val classroom: String = "",         // 教室
    val teacher: String = "",           // 教师
    val timeText: String = "",          // 时间文本，如 "08:00 - 09:40"
    val sectionText: String = "",       // 节次文本，如 "1-2节"
    val color: String = "",             // 课程颜色
    val minutesRemaining: Int = 0,      // 剩余分钟数（上课前/下课前）
    val dayOfWeekText: String = "",     // 星期文本
    val weekNumberText: String = "",    // 周次文本
    val message: String? = null         // 状态提示信息
)
