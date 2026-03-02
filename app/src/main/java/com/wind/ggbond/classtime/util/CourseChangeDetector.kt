package com.wind.ggbond.classtime.util

import android.util.Log
import com.wind.ggbond.classtime.data.local.entity.Course

/**
 * 课程变更检测器
 * 
 * 功能：对比本地课程和远程课程，检测变更
 * - 新增的课程
 * - 删除的课程
 * - 时间调整的课程（调课）
 * 
 * @author AI Assistant
 * @since 2025-11-05
 */
object CourseChangeDetector {
    
    private const val TAG = "CourseChangeDetector"
    
    /**
     * 检测课程变更
     * 
     * @param localCourses 本地现有课程
     * @param remoteCourses 远程新课程
     * @return 变更检测结果
     */
    fun detectChanges(
        localCourses: List<Course>,
        remoteCourses: List<Course>
    ): CourseChangeResult {
        Log.d(TAG, "开始检测课程变更...")
        Log.d(TAG, "本地课程数: ${localCourses.size}, 远程课程数: ${remoteCourses.size}")
        
        // 1. 使用完整key（包含时间信息）进行精确匹配
        val localCourseMap = localCourses.associateBy { it.getFullKey() }
        val remoteCourseMap = remoteCourses.associateBy { it.getFullKey() }
        
        // 2. 按课程名+教师分组，用于检测调课
        val localGrouped = localCourses.groupBy { it.getCourseIdentity() }
        val remoteGrouped = remoteCourses.groupBy { it.getCourseIdentity() }
        
        // 3. 检测三种变更
        val addedCourses = mutableListOf<Course>()
        val removedCourses = mutableListOf<Course>()
        val adjustedCourses = mutableListOf<CourseTimeAdjustment>()
        
        // 4. 遍历所有课程标识（课程名+教师）
        val allIdentities = (localGrouped.keys + remoteGrouped.keys).distinct()
        
        for (identity in allIdentities) {
            val localList = localGrouped[identity] ?: emptyList()
            val remoteList = remoteGrouped[identity] ?: emptyList()
            
            when {
                // 情况1：本地没有，远程有 -> 新增课程
                localList.isEmpty() -> {
                    addedCourses.addAll(remoteList)
                    Log.d(TAG, "新增课程: $identity (${remoteList.size}个时间段)")
                }
                
                // 情况2：本地有，远程没有 -> 删除课程
                remoteList.isEmpty() -> {
                    removedCourses.addAll(localList)
                    Log.d(TAG, "删除课程: $identity (${localList.size}个时间段)")
                }
                
                // 情况3：本地和远程都有 -> 检测是否有调课或周次调整
                else -> {
                    // 使用完整key进行精确对比
                    val localKeys = localList.map { it.getFullKey() }.toSet()
                    val remoteKeys = remoteList.map { it.getFullKey() }.toSet()
                    
                    // 完全一样，无变化
                    if (localKeys == remoteKeys) {
                        // 无变化，跳过
                        continue
                    }
                    
                    // ⚠️ 智能调课检测：检测周次在不同时间段之间的迁移
                    // 例如：第4周从"周二2-3节"移到"周四5-6节"
                    val adjustmentsDetected = detectWeekMigrations(localList, remoteList, identity)
                    adjustedCourses.addAll(adjustmentsDetected)
                    
                    // 检测纯新增和纯删除的时间段
                    // 新增：远程有但本地完全没有的时间段
                    remoteList.forEach { remote ->
                        if (!localKeys.contains(remote.getFullKey())) {
                            // 检查是否已经被调课检测覆盖了
                            val alreadyCovered = adjustmentsDetected.any { adj ->
                                adj.newTime.dayOfWeek == remote.dayOfWeek &&
                                adj.newTime.startSection == remote.startSection &&
                                adj.newTime.weeks.containsAll(remote.weeks)
                            }
                            if (!alreadyCovered) {
                                addedCourses.add(remote)
                            }
                        }
                    }
                    
                    // 删除：本地有但远程完全没有的时间段
                    localList.forEach { local ->
                        if (!remoteKeys.contains(local.getFullKey())) {
                            // 检查是否已经被调课检测覆盖了
                            val alreadyCovered = adjustmentsDetected.any { adj ->
                                adj.oldTime.dayOfWeek == local.dayOfWeek &&
                                adj.oldTime.startSection == local.startSection &&
                                adj.oldTime.weeks.containsAll(local.weeks)
                            }
                            if (!alreadyCovered) {
                                removedCourses.add(local)
                            }
                        }
                    }
                    
                    if (adjustmentsDetected.isNotEmpty()) {
                        Log.d(TAG, "检测到 ${adjustmentsDetected.size} 次调课: $identity")
                    }
                }
            }
        }
        
        Log.d(TAG, "检测完成 - 新增: ${addedCourses.size}, 删除: ${removedCourses.size}, 调课: ${adjustedCourses.size}")
        
        return CourseChangeResult(
            addedCourses = addedCourses,
            removedCourses = removedCourses,
            adjustedCourses = adjustedCourses
        )
    }
    
    /**
     * 检测周次在不同时间段之间的迁移（调课）
     * 
     * 例如：第4周从"周二2-3节"迁移到"周四5-6节"
     * 
     * @param localList 本地的课程时间段列表
     * @param remoteList 远程的课程时间段列表
     * @param identity 课程标识（用于日志）
     * @return 检测到的调课记录
     */
    private fun detectWeekMigrations(
        localList: List<Course>,
        remoteList: List<Course>,
        identity: String
    ): List<CourseTimeAdjustment> {
        val adjustments = mutableListOf<CourseTimeAdjustment>()
        
        // 1. 构建周次到时间段的映射
        // 本地：每个周次对应哪个时间段
        val localWeekToTimeSlot = mutableMapOf<Int, Course>()
        localList.forEach { course ->
            course.weeks.forEach { week ->
                localWeekToTimeSlot[week] = course
            }
        }
        
        // 远程：每个周次对应哪个时间段
        val remoteWeekToTimeSlot = mutableMapOf<Int, Course>()
        remoteList.forEach { course ->
            course.weeks.forEach { week ->
                remoteWeekToTimeSlot[week] = course
            }
        }
        
        // 2. 收集所有涉及的周次（本地和远程的并集）
        val allWeeks = (localWeekToTimeSlot.keys + remoteWeekToTimeSlot.keys).distinct().sorted()
        
        // 3. 按时间段分组，找出发生迁移的周次
        // 格式：(旧时间段, 新时间段) -> 迁移的周次列表
        val migrations = mutableMapOf<Pair<Course, Course>, MutableList<Int>>()
        
        for (week in allWeeks) {
            val localCourse = localWeekToTimeSlot[week]
            val remoteCourse = remoteWeekToTimeSlot[week]
            
            // 只有当周次在本地和远程都存在，且时间段不同时，才算调课
            if (localCourse != null && remoteCourse != null) {
                // 检查时间段是否发生变化
                if (hasTimeOrLocationChanged(localCourse, remoteCourse)) {
                    val key = Pair(localCourse, remoteCourse)
                    migrations.getOrPut(key) { mutableListOf() }.add(week)
                }
            }
        }
        
        // 4. 为每个迁移路径创建调课记录
        migrations.forEach { (courses, migratedWeeks) ->
            val (oldCourse, newCourse) = courses
            
            // 判断变更类型
            val timeChanged = oldCourse.dayOfWeek != newCourse.dayOfWeek || 
                            oldCourse.startSection != newCourse.startSection ||
                            oldCourse.sectionCount != newCourse.sectionCount
            val locationChanged = oldCourse.classroom != newCourse.classroom
            
            val changeType = when {
                timeChanged && locationChanged -> CourseChangeType.TIME_AND_LOCATION
                locationChanged -> CourseChangeType.LOCATION_ONLY
                else -> CourseChangeType.TIME_ONLY
            }
            
            adjustments.add(
                CourseTimeAdjustment(
                    courseName = oldCourse.courseName,
                    teacher = oldCourse.teacher,
                    oldTime = CourseTimeInfo(
                        dayOfWeek = oldCourse.dayOfWeek,
                        startSection = oldCourse.startSection,
                        sectionCount = oldCourse.sectionCount,
                        weeks = migratedWeeks,  // 只记录发生迁移的周次
                        classroom = oldCourse.classroom
                    ),
                    newTime = CourseTimeInfo(
                        dayOfWeek = newCourse.dayOfWeek,
                        startSection = newCourse.startSection,
                        sectionCount = newCourse.sectionCount,
                        weeks = migratedWeeks,  // 只记录发生迁移的周次
                        classroom = newCourse.classroom
                    ),
                    originalCourse = oldCourse,
                    changeType = changeType
                )
            )
            
            // 更详细的日志
            val changeDesc = when(changeType) {
                CourseChangeType.TIME_ONLY -> "时间调整"
                CourseChangeType.LOCATION_ONLY -> "地点变更"
                CourseChangeType.TIME_AND_LOCATION -> "时间+地点变更"
            }
            Log.d(TAG, "检测到周次迁移: ${oldCourse.courseName}, 第${migratedWeeks}周 $changeDesc")
        }
        
        return adjustments
    }
    
    /**
     * 生成课程的完整唯一标识（用于精确匹配）
     * 包含：课程名 + 教师 + 星期几 + 开始节次 + 节数 + 周次
     * 
     * 例如："高等数学_李老师_1_1_2_[1,2,3,4,5]" 
     * 表示周一第1节开始上2节，第1-5周的高等数学
     * 
     * ⚠️ 周次信息很重要，同样时间但不同周次的课应该被视为不同的课程时段
     */
    private fun Course.getFullKey(): String {
        // 将周次列表转换为字符串，确保格式统一
        val weeksString = weeks.sorted().joinToString(",")
        return "${courseName}_${teacher}_${dayOfWeek}_${startSection}_${sectionCount}_${weeksString}"
    }
    
    /**
     * 生成课程标识（不包含时间，用于分组）
     * 包含：课程名 + 教师
     * 
     * 用于将同一门课的不同时间段归为一组
     */
    private fun Course.getCourseIdentity(): String {
        return "${courseName}_${teacher}"
    }
    
    /**
     * 检查课程的时间或地点是否发生变化
     * 
     * ⚠️ 注意：周次变化不算调课，因为：
     * - 周次是课程的固定属性（如"单周"、"1-8周"）
     * - 调课是指临时性的时间调整（如"周一第1节调到周三第3节"）
     * - 只检查星期、节次、地点的变化
     */
    private fun hasTimeOrLocationChanged(local: Course, remote: Course): Boolean {
        return local.dayOfWeek != remote.dayOfWeek ||
                local.startSection != remote.startSection ||
                local.sectionCount != remote.sectionCount ||
                local.classroom != remote.classroom
    }
    
    /**
     * 判断是否有任何变更
     */
    fun CourseChangeResult.hasChanges(): Boolean {
        return addedCourses.isNotEmpty() || 
               removedCourses.isNotEmpty() || 
               adjustedCourses.isNotEmpty()
    }
    
    /**
     * 生成变更摘要信息
     */
    fun CourseChangeResult.getSummary(): String {
        val parts = mutableListOf<String>()
        
        if (addedCourses.isNotEmpty()) {
            parts.add("新增 ${addedCourses.size} 门")
        }
        if (removedCourses.isNotEmpty()) {
            parts.add("删除 ${removedCourses.size} 门")
        }
        if (adjustedCourses.isNotEmpty()) {
            parts.add("调课 ${adjustedCourses.size} 门")
        }
        
        return if (parts.isEmpty()) {
            "无课程更新"
        } else {
            parts.joinToString("、")
        }
    }
    
    /**
     * 生成详细的变更信息
     */
    fun CourseChangeResult.getDetailedMessage(): String {
        val sb = StringBuilder()
        
        if (!hasChanges()) {
            sb.append("✅ 课表无变化，无需更新")
            return sb.toString()
        }
        
        sb.append("📋 课表更新检测结果\n\n")
        
        if (addedCourses.isNotEmpty()) {
            sb.append("➕ 新增课程 (${addedCourses.size}门)：\n")
            addedCourses.take(3).forEach { course ->
                sb.append("  · ${course.courseName} (${course.teacher})\n")
            }
            if (addedCourses.size > 3) {
                sb.append("  ...等 ${addedCourses.size - 3} 门课程\n")
            }
            sb.append("\n")
        }
        
        if (removedCourses.isNotEmpty()) {
            sb.append("➖ 删除课程 (${removedCourses.size}门)：\n")
            removedCourses.take(3).forEach { course ->
                sb.append("  · ${course.courseName} (${course.teacher})\n")
            }
            if (removedCourses.size > 3) {
                sb.append("  ...等 ${removedCourses.size - 3} 门课程\n")
            }
            sb.append("\n")
        }
        
        if (adjustedCourses.isNotEmpty()) {
            sb.append("🔄 调课 (${adjustedCourses.size}门)：\n")
            adjustedCourses.take(3).forEach { adj ->
                sb.append("  · ${adj.courseName} (${adj.teacher})\n")
                sb.append("    ${adj.getChangeDescription()}\n")
            }
            if (adjustedCourses.size > 3) {
                sb.append("  ...等 ${adjustedCourses.size - 3} 门课程\n")
            }
        }
        
        return sb.toString().trim()
    }
    
    /**
     * 获取时间描述
     */
    private fun CourseTimeInfo.getTimeDescription(): String {
        val dayNames = mapOf(
            1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
            5 to "周五", 6 to "周六", 7 to "周日"
        )
        val dayName = dayNames[dayOfWeek] ?: "周$dayOfWeek"
        return "$dayName 第${startSection}-${startSection + sectionCount - 1}节 $classroom"
    }
    
    /**
     * 获取变更描述（根据变更类型显示不同的内容）
     */
    private fun CourseTimeAdjustment.getChangeDescription(): String {
        val dayNames = mapOf(
            1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
            5 to "周五", 6 to "周六", 7 to "周日"
        )
        
        return when (changeType) {
            CourseChangeType.TIME_ONLY -> {
                // 仅时间变更
                val oldDay = dayNames[oldTime.dayOfWeek] ?: "周${oldTime.dayOfWeek}"
                val newDay = dayNames[newTime.dayOfWeek] ?: "周${newTime.dayOfWeek}"
                val oldSection = "${oldTime.startSection}-${oldTime.startSection + oldTime.sectionCount - 1}节"
                val newSection = "${newTime.startSection}-${newTime.startSection + newTime.sectionCount - 1}节"
                "时间调整：$oldDay $oldSection → $newDay $newSection"
            }
            CourseChangeType.LOCATION_ONLY -> {
                // 仅地点变更
                "地点变更：${oldTime.classroom} → ${newTime.classroom}"
            }
            CourseChangeType.TIME_AND_LOCATION -> {
                // 时间和地点都变更
                val oldDay = dayNames[oldTime.dayOfWeek] ?: "周${oldTime.dayOfWeek}"
                val newDay = dayNames[newTime.dayOfWeek] ?: "周${newTime.dayOfWeek}"
                val oldSection = "${oldTime.startSection}-${oldTime.startSection + oldTime.sectionCount - 1}节"
                val newSection = "${newTime.startSection}-${newTime.startSection + newTime.sectionCount - 1}节"
                "时间+地点：$oldDay $oldSection/${oldTime.classroom} → $newDay $newSection/${newTime.classroom}"
            }
        }
    }
}

/**
 * 课程变更结果
 */
data class CourseChangeResult(
    val addedCourses: List<Course>,           // 新增的课程
    val removedCourses: List<Course>,         // 删除的课程
    val adjustedCourses: List<CourseTimeAdjustment>  // 时间调整的课程
)

/**
 * 课程时间调整信息
 */
data class CourseTimeAdjustment(
    val courseName: String,
    val teacher: String,
    val oldTime: CourseTimeInfo,
    val newTime: CourseTimeInfo,
    val originalCourse: Course,  // 原始课程对象（用于生成调课记录）
    val changeType: CourseChangeType  // 变更类型
)

/**
 * 课程变更类型
 */
enum class CourseChangeType {
    TIME_ONLY,      // 仅时间变更（星期或节次变化，教室不变）
    LOCATION_ONLY,  // 仅地点变更（教室变化，时间不变）
    TIME_AND_LOCATION  // 时间和地点都变更
}

/**
 * 课程时间信息
 */
data class CourseTimeInfo(
    val dayOfWeek: Int,
    val startSection: Int,
    val sectionCount: Int,
    val weeks: List<Int>,
    val classroom: String
)


