package com.wind.ggbond.classtime.service

sealed class ScheduleUpdateRequest {

    data class Manual(
        val schoolConfig: com.wind.ggbond.classtime.data.model.SchoolConfig,
        val scheduleId: Long,
        val showWebView: Boolean = false
    ) : ScheduleUpdateRequest()

    data class Auto(
        val schoolConfig: com.wind.ggbond.classtime.data.model.SchoolConfig,
        val scheduleId: Long
    ) : ScheduleUpdateRequest()

    data class CookieRefresh(
        val schoolConfig: com.wind.ggbond.classtime.data.model.SchoolConfig,
        val scheduleId: Long
    ) : ScheduleUpdateRequest()
}
