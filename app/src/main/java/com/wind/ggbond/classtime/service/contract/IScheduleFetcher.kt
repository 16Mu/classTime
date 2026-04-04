package com.wind.ggbond.classtime.service.contract

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.data.model.SchoolConfig

interface IScheduleFetcher {

    suspend fun fetchSchedule(
        schoolConfig: SchoolConfig,
        showWebView: Boolean = false
    ): Result<Pair<List<ParsedCourse>, String>>
}
