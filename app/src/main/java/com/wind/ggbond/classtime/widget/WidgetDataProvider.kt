package com.wind.ggbond.classtime.widget

import android.content.Context
import com.wind.ggbond.classtime.widget.data.NextClassDataProvider
import com.wind.ggbond.classtime.widget.data.TodayCourseDataProvider
import com.wind.ggbond.classtime.widget.data.TomorrowCourseDataProvider
import com.wind.ggbond.classtime.widget.data.WeekOverviewDataProvider
import com.wind.ggbond.classtime.widget.data.WidgetDisplayData
import com.wind.ggbond.classtime.widget.data.NextClassDisplayData
import com.wind.ggbond.classtime.widget.data.TomorrowCourseDisplayData
import com.wind.ggbond.classtime.widget.data.WeekOverviewData

object WidgetDataProvider {

    suspend fun getTodayCourses(context: Context): WidgetDisplayData {
        return TodayCourseDataProvider(context).getTodayCourses()
    }

    suspend fun getNextClass(context: Context): NextClassDisplayData {
        return NextClassDataProvider(context).getNextClass()
    }

    suspend fun getWeekOverview(context: Context): WeekOverviewData {
        return WeekOverviewDataProvider(context).getWeekOverview()
    }

    suspend fun getSmartCourses(context: Context): TomorrowCourseDisplayData {
        return TomorrowCourseDataProvider(context).getSmartCourses()
    }
}
