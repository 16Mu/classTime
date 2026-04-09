package com.wind.ggbond.classtime.util

import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.data.local.entity.Course

object CourseChangeDetector {

    private const val TAG = "CourseChangeDetector"

    private val DAY_NAMES = mapOf(1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四", 5 to "周五", 6 to "周六", 7 to "周日")

    fun detectChanges(localCourses: List<Course>, remoteCourses: List<Course>): CourseChangeResult {
        AppLogger.d(TAG, "开始检测课程变更... 本地:${localCourses.size}, 远程:${remoteCourses.size}")

        val localCourseMap = localCourses.associateBy { it.getFullKey() }
        val remoteCourseMap = remoteCourses.associateBy { it.getFullKey() }
        val localGrouped = localCourses.groupBy { it.getCourseIdentity() }
        val remoteGrouped = remoteCourses.groupBy { it.getCourseIdentity() }

        val addedCourses = mutableListOf<Course>()
        val removedCourses = mutableListOf<Course>()
        val adjustedCourses = mutableListOf<CourseTimeAdjustment>()

        for (identity in (localGrouped.keys + remoteGrouped.keys).distinct()) {
            val localList = localGrouped[identity] ?: emptyList()
            val remoteList = remoteGrouped[identity] ?: emptyList()

            when {
                localList.isEmpty() -> {
                    addedCourses.addAll(remoteList)
                    AppLogger.d(TAG, "新增课程: $identity (${remoteList.size}个时间段)")
                }
                remoteList.isEmpty() -> {
                    removedCourses.addAll(localList)
                    AppLogger.d(TAG, "删除课程: $identity (${localList.size}个时间段)")
                }
                else -> {
                    val localKeys = localList.map { it.getFullKey() }.toSet()
                    val remoteKeys = remoteList.map { it.getFullKey() }.toSet()
                    if (localKeys == remoteKeys) continue

                    val adjustmentsDetected = detectWeekMigrations(localList, remoteList, identity)
                    adjustedCourses.addAll(adjustmentsDetected)

                    remoteList.filter { !localKeys.contains(it.getFullKey()) }
                        .filterNot { r -> adjustmentsDetected.any { it.newTime.dayOfWeek == r.dayOfWeek && it.newTime.startSection == r.startSection && it.newTime.weeks.containsAll(r.weeks) } }
                        .let { addedCourses.addAll(it) }

                    localList.filter { !remoteKeys.contains(it.getFullKey()) }
                        .filterNot { l -> adjustmentsDetected.any { it.oldTime.dayOfWeek == l.dayOfWeek && it.oldTime.startSection == l.startSection && it.oldTime.weeks.containsAll(l.weeks) } }
                        .let { removedCourses.addAll(it) }

                    if (adjustmentsDetected.isNotEmpty()) AppLogger.d(TAG, "检测到 ${adjustmentsDetected.size} 次调课: $identity")
                }
            }
        }

        AppLogger.d(TAG, "检测完成 - 新增: ${addedCourses.size}, 删除: ${removedCourses.size}, 调课: ${adjustedCourses.size}")
        return CourseChangeResult(addedCourses, removedCourses, adjustedCourses)
    }

    private fun detectWeekMigrations(localList: List<Course>, remoteList: List<Course>, identity: String): List<CourseTimeAdjustment> {
        val localWeekToTimeSlot = mutableMapOf<Int, Course>().also { m ->
            localList.forEach { c -> c.weeks.forEach { w -> m[w] = c } }
        }
        val remoteWeekToTimeSlot = mutableMapOf<Int, Course>().also { m ->
            remoteList.forEach { c -> c.weeks.forEach { w -> m[w] = c } }
        }

        val migrations = mutableMapOf<Pair<Course, Course>, MutableList<Int>>()
        (localWeekToTimeSlot.keys + remoteWeekToTimeSlot.keys).distinct().sorted().forEach { week ->
            val lc = localWeekToTimeSlot[week]
            val rc = remoteWeekToTimeSlot[week]
            if (lc != null && rc != null && hasTimeOrLocationChanged(lc, rc)) {
                migrations.getOrPut(Pair(lc, rc)) { mutableListOf() }.add(week)
            }
        }

        return migrations.map { (courses, migratedWeeks) ->
            val (oldCourse, newCourse) = courses
            val timeChanged = oldCourse.dayOfWeek != newCourse.dayOfWeek || oldCourse.startSection != newCourse.startSection || oldCourse.sectionCount != newCourse.sectionCount
            val locationChanged = oldCourse.classroom != newCourse.classroom
            val changeType = when {
                timeChanged && locationChanged -> CourseChangeType.TIME_AND_LOCATION
                locationChanged -> CourseChangeType.LOCATION_ONLY
                else -> CourseChangeType.TIME_ONLY
            }
            AppLogger.d(TAG, "检测到周次迁移: ${oldCourse.courseName}, 第${migratedWeeks}周 $changeType")
            CourseTimeAdjustment(oldCourse.courseName, oldCourse.teacher,
                CourseTimeInfo(oldCourse.dayOfWeek, oldCourse.startSection, oldCourse.sectionCount, migratedWeeks, oldCourse.classroom),
                CourseTimeInfo(newCourse.dayOfWeek, newCourse.startSection, newCourse.sectionCount, migratedWeeks, newCourse.classroom),
                oldCourse, changeType)
        }
    }

    private fun Course.getFullKey(): String = "${courseName}_${teacher}_${dayOfWeek}_${startSection}_${sectionCount}_${weeks.sorted().joinToString(",")}"

    private fun Course.getCourseIdentity(): String = "${courseName}_${teacher}"

    private fun hasTimeOrLocationChanged(local: Course, remote: Course): Boolean =
        local.dayOfWeek != remote.dayOfWeek || local.startSection != remote.startSection ||
        local.sectionCount != remote.sectionCount || local.classroom != remote.classroom

    private fun formatDaySection(day: Int, start: Int, count: Int): String {
        val d = DAY_NAMES[day] ?: "周$day"
        return "$d 第$start-${start + count - 1}节"
    }

    data class CourseChangeResult(
        val addedCourses: List<Course>,
        val removedCourses: List<Course>,
        val adjustedCourses: List<CourseTimeAdjustment>
    ) {
        fun hasChanges(): Boolean = addedCourses.isNotEmpty() || removedCourses.isNotEmpty() || adjustedCourses.isNotEmpty()

        fun getSummary(): String = buildList {
            if (addedCourses.isNotEmpty()) add("新增 ${addedCourses.size} 门")
            if (removedCourses.isNotEmpty()) add("删除 ${removedCourses.size} 门")
            if (adjustedCourses.isNotEmpty()) add("调课 ${adjustedCourses.size} 门")
        }.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "无课程更新"

        fun getDetailedMessage(): String = buildString {
            if (!hasChanges()) return "✅ 课表无变化，无需更新"
            appendLine("📋 课表更新检测结果\n")
            appendCourseList("➕ 新增课程", addedCourses)
            appendCourseList("➖ 删除课程", removedCourses)
            if (adjustedCourses.isNotEmpty()) {
                appendLine("🔄 调课 (${adjustedCourses.size}门)：")
                adjustedCourses.take(3).forEach { adj ->
                    appendLine("  · ${adj.courseName} (${adj.teacher})")
                    appendLine("    ${adj.getChangeDescription()}")
                }
                if (adjustedCourses.size > 3) appendLine("  ...等 ${adjustedCourses.size - 3} 门课程")
            }
        }.trim()

        private fun StringBuilder.appendCourseList(prefix: String, courses: List<Course>) {
            if (courses.isEmpty()) return
            appendLine("$prefix (${courses.size}门)：")
            courses.take(3).forEach { appendLine("  · ${it.courseName} (${it.teacher})") }
            if (courses.size > 3) appendLine("  ...等 ${courses.size - 3} 门课程")
            appendLine()
        }
    }

    data class CourseTimeAdjustment(
        val courseName: String, val teacher: String,
        val oldTime: CourseTimeInfo, val newTime: CourseTimeInfo,
        val originalCourse: Course, val changeType: CourseChangeType
    ) {
        fun getChangeDescription(): String = when (changeType) {
            CourseChangeType.TIME_ONLY -> "时间调整：${formatDaySection(oldTime.dayOfWeek, oldTime.startSection, oldTime.sectionCount)} → ${formatDaySection(newTime.dayOfWeek, newTime.startSection, newTime.sectionCount)}"
            CourseChangeType.LOCATION_ONLY -> "地点变更：${oldTime.classroom} → ${newTime.classroom}"
            CourseChangeType.TIME_AND_LOCATION -> "时间+地点：${formatDaySection(oldTime.dayOfWeek, oldTime.startSection, oldTime.sectionCount)}/${oldTime.classroom} → ${formatDaySection(newTime.dayOfWeek, newTime.startSection, newTime.sectionCount)}/${newTime.classroom}"
        }
    }

    enum class CourseChangeType { TIME_ONLY, LOCATION_ONLY, TIME_AND_LOCATION }

    data class CourseTimeInfo(val dayOfWeek: Int, val startSection: Int, val sectionCount: Int, val weeks: List<Int>, val classroom: String)
}
