package com.wind.ggbond.classtime.widget

import android.content.Context
import com.wind.ggbond.classtime.widget.data.NextClassDataProvider
import com.wind.ggbond.classtime.widget.data.TodayCourseDataProvider
import com.wind.ggbond.classtime.widget.data.TomorrowCourseDataProvider
import com.wind.ggbond.classtime.widget.data.WeekOverviewDataProvider
import com.wind.ggbond.classtime.widget.data.WeekGridDataProvider
import com.wind.ggbond.classtime.widget.data.WidgetDisplayData
import com.wind.ggbond.classtime.widget.data.NextClassDisplayData
import com.wind.ggbond.classtime.widget.data.TomorrowCourseDisplayData
import com.wind.ggbond.classtime.widget.data.WeekOverviewData
import com.wind.ggbond.classtime.widget.data.WeekGridData

object WidgetDataProvider {

    private val lock = Any()

    @Volatile
    private var todayProvider: TodayCourseDataProvider? = null
    @Volatile
    private var nextClassProvider: NextClassDataProvider? = null
    @Volatile
    private var weekOverviewProvider: WeekOverviewDataProvider? = null
    @Volatile
    private var tomorrowProvider: TomorrowCourseDataProvider? = null
    @Volatile
    private var weekGridProvider: WeekGridDataProvider? = null

    suspend fun getTodayCourses(context: Context): WidgetDisplayData {
        val appContext = context.applicationContext
        val provider = todayProvider ?: synchronized(lock) {
            todayProvider ?: TodayCourseDataProvider(appContext).also { todayProvider = it }
        }
        return provider.getTodayCourses()
    }

    suspend fun getNextClass(context: Context): NextClassDisplayData {
        val appContext = context.applicationContext
        val provider = nextClassProvider ?: synchronized(lock) {
            nextClassProvider ?: NextClassDataProvider(appContext).also { nextClassProvider = it }
        }
        return provider.getNextClass()
    }

    suspend fun getWeekOverview(context: Context): WeekOverviewData {
        val appContext = context.applicationContext
        val provider = weekOverviewProvider ?: synchronized(lock) {
            weekOverviewProvider ?: WeekOverviewDataProvider(appContext).also { weekOverviewProvider = it }
        }
        return provider.getWeekOverview()
    }

    suspend fun getSmartCourses(context: Context): TomorrowCourseDisplayData {
        val appContext = context.applicationContext
        val provider = tomorrowProvider ?: synchronized(lock) {
            tomorrowProvider ?: TomorrowCourseDataProvider(appContext).also { tomorrowProvider = it }
        }
        return provider.getSmartCourses()
    }

    suspend fun getWeekGridView(context: Context): WeekGridData {
        val appContext = context.applicationContext
        val provider = weekGridProvider ?: synchronized(lock) {
            weekGridProvider ?: WeekGridDataProvider(appContext).also { weekGridProvider = it }
        }
        return provider.getWeekGrid()
    }

    fun clearAllProviders() {
        synchronized(lock) {
            todayProvider = null
            nextClassProvider = null
            weekOverviewProvider = null
            tomorrowProvider = null
            weekGridProvider = null
        }
    }
}
