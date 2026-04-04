package com.wind.ggbond.classtime.ui.navigation

/**
 * 应用导航屏幕
 */
sealed class Screen(val route: String) {
    object Main : Screen("main?refresh={refresh}") {
        fun createRoute(refresh: Boolean = false): String {
            return if (refresh) "main?refresh=true" else "main"
        }
    }
    object CourseEdit : Screen("course_edit?courseId={courseId}&dayOfWeek={dayOfWeek}&startSection={startSection}&sectionCount={sectionCount}&weekNumber={weekNumber}&courseName={courseName}") {
        fun createRoute(
            courseId: Long? = null,
            dayOfWeek: Int? = null,
            startSection: Int? = null,
            sectionCount: Int? = null,
            weekNumber: Int? = null,
            courseName: String? = null
        ): String {
            val params = mutableListOf<String>()
            if (courseId != null) {
                params += "courseId=$courseId"
            }
            if (dayOfWeek != null) {
                params += "dayOfWeek=$dayOfWeek"
            }
            if (startSection != null) {
                params += "startSection=$startSection"
            }
            if (sectionCount != null) {
                params += "sectionCount=$sectionCount"
            }
            if (weekNumber != null) {
                params += "weekNumber=$weekNumber"
            }
            if (courseName != null) {
                // URL编码课程名称，避免特殊字符问题
                params += "courseName=${java.net.URLEncoder.encode(courseName, "UTF-8")}"
            }
            return if (params.isEmpty()) {
                "course_edit"
            } else {
                "course_edit?" + params.joinToString("&")
            }
        }
    }
    object CourseInfoList : Screen("course_info_list")
    object Settings : Screen("settings")
    object SemesterManagement : Screen("semester_management?fromImport={fromImport}&fallSemesterStartDate={fallSemesterStartDate}&springSemesterStartDate={springSemesterStartDate}") {
        fun createRoute(
            fromImport: Boolean = false,
            fallSemesterStartDate: String? = null,
            springSemesterStartDate: String? = null
        ): String {
            val params = mutableListOf("fromImport=$fromImport")
            if (fallSemesterStartDate != null) {
                params += "fallSemesterStartDate=$fallSemesterStartDate"
            }
            if (springSemesterStartDate != null) {
                params += "springSemesterStartDate=$springSemesterStartDate"
            }
            return "semester_management?" + params.joinToString("&")
        }
    }
    object SectionCountConfig : Screen("section_count_config")
    object ClassTimeConfig : Screen("class_time_config?fromImport={fromImport}") {
        fun createRoute(fromImport: Boolean = false) = "class_time_config?fromImport=$fromImport"
    }
    object TimetableSettings : Screen("timetable_settings")
    object ImportSchedule : Screen("import_schedule")
    object SchoolSelection : Screen("school_selection")
    object SmartWebViewImport : Screen("smart_webview_import/{schoolId}") {
        fun createRoute(schoolId: String) = "smart_webview_import/$schoolId"
    }
    object WebViewLogin : Screen("webview_login/{schoolId}") {
        fun createRoute(schoolId: String) = "webview_login/$schoolId"
    }
    object AdjustmentManagement : Screen("adjustment_management")
    object AutoUpdateSettings : Screen("auto_update_settings")
    object BatchCourseCreate : Screen("batch_course_create")
    object ReminderSettings : Screen("reminder_settings")
    object ReminderManagement : Screen("reminder_management")
    object Onboarding : Screen("onboarding")
    object PermissionTutorial : Screen("permission_tutorial/{permissionType}") {
        fun createRoute(permissionType: String) = "permission_tutorial/$permissionType"
    }
    object BackgroundSettings : Screen("background_settings")
}

