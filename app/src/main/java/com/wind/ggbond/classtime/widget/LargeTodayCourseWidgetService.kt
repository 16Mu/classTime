package com.wind.ggbond.classtime.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.widget.data.WidgetCourseItem
import kotlinx.coroutines.Dispatchers
import com.wind.ggbond.classtime.util.AppLogger
import kotlinx.coroutines.runBlocking

class LargeTodayCourseWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return LargeTodayCourseRemoteViewsFactory(applicationContext)
    }
}

class LargeTodayCourseRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var courseItems: List<WidgetCourseItem> = emptyList()
    private var loadError: String? = null

    override fun onCreate() {}

    override fun onDataSetChanged() {
        courseItems = emptyList()
        loadError = null
        try {
            courseItems = runBlocking(Dispatchers.IO) {
                try {
                    val displayData = WidgetDataProvider.getTodayCourses(context)
                    displayData.courseItems
                } catch (e: Exception) {
                    AppLogger.e("LargeWidgetFactory", "数据加载失败", e)
                    loadError = "数据加载失败"
                    emptyList()
                }
            }
        } catch (e: Exception) {
            AppLogger.e("LargeWidgetFactory", "onDataSetChanged 异常", e)
            loadError = "加载异常"
        }
    }

    override fun onDestroy() {
        courseItems = emptyList()
    }

    override fun getCount(): Int = courseItems.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= courseItems.size) {
            return createErrorView()
        }

        val course = courseItems[position]
        return try {
            createCourseItemView(course)
        } catch (e: Exception) {
            AppLogger.e("LargeWidgetFactory", "getViewAt 异常 pos=$position", e)
            createErrorView()
        }
    }

    private fun createCourseItemView(course: WidgetCourseItem): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_large_course_item)

        val courseColor = try {
            Color.parseColor(course.color)
        } catch (e: Exception) {
            Color.parseColor("#5C6BC0")
        }

        views.setInt(R.id.view_color_bar, "setBackgroundColor", courseColor)

        val endSection = course.startSection + course.sectionCount - 1
        views.setTextViewText(R.id.tv_section, "${course.startSection}-${endSection}节")

        views.setTextViewText(R.id.tv_time, course.startTimeText)
        views.setTextViewText(R.id.tv_course_name, course.courseName)

        if (course.classroom.isNotEmpty()) {
            views.setTextViewText(R.id.tv_classroom, course.classroom)
            views.setViewVisibility(R.id.tv_classroom, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.tv_classroom, android.view.View.GONE)
        }

        if (course.teacher.isNotEmpty()) {
            views.setTextViewText(R.id.tv_teacher, course.teacher)
            views.setViewVisibility(R.id.tv_teacher, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.tv_teacher, android.view.View.GONE)
        }

        views.setViewVisibility(
            R.id.tv_ongoing,
            if (course.isOngoing) android.view.View.VISIBLE else android.view.View.GONE
        )

        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.course_item_container, fillInIntent)

        return views
    }

    private fun createErrorView(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_large_course_item)
        views.setTextViewText(R.id.tv_course_name, "-")
        views.setTextViewText(R.id.tv_section, "")
        views.setTextViewText(R.id.tv_time, "")
        views.setViewVisibility(R.id.tv_classroom, android.view.View.GONE)
        views.setViewVisibility(R.id.tv_teacher, android.view.View.GONE)
        views.setViewVisibility(R.id.tv_ongoing, android.view.View.GONE)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
