package com.wind.ggbond.classtime.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.wind.ggbond.classtime.R
import kotlinx.coroutines.runBlocking

/**
 * 4x4 大尺寸今日课程小组件 - RemoteViewsService
 * 
 * 负责为 ListView 提供数据适配器（RemoteViewsFactory）
 * 系统会在需要更新 ListView 数据时调用此服务
 */
class LargeTodayCourseWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return LargeTodayCourseRemoteViewsFactory(applicationContext)
    }
}

/**
 * 4x4 大尺寸今日课程小组件 - RemoteViewsFactory
 * 
 * 负责：
 * - 从数据库加载今日课程数据
 * - 为 ListView 的每一项创建 RemoteViews
 * - 支持课程颜色、进行中状态等显示
 */
class LargeTodayCourseRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    /** 课程数据列表 */
    private var courseItems: List<WidgetCourseItem> = emptyList()

    /**
     * 初始化时调用，可用于初始化资源
     */
    override fun onCreate() {
        // 初始化时不加载数据，等待 onDataSetChanged 调用
    }

    /**
     * 数据集变更时调用
     * 从数据库重新加载今日课程数据
     */
    override fun onDataSetChanged() {
        // 使用 runBlocking 在后台线程同步获取数据
        // RemoteViewsFactory 的 onDataSetChanged 已经在 Binder 线程池中运行
        courseItems = runBlocking {
            try {
                val displayData = WidgetDataProvider.getTodayCourses(context)
                displayData.courseItems
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 销毁时调用，释放资源
     */
    override fun onDestroy() {
        courseItems = emptyList()
    }

    /**
     * 返回数据项数量
     */
    override fun getCount(): Int = courseItems.size

    /**
     * 为指定位置创建 RemoteViews
     * 
     * @param position 列表位置
     * @return 该位置的 RemoteViews
     */
    override fun getViewAt(position: Int): RemoteViews {
        // 边界检查
        if (position < 0 || position >= courseItems.size) {
            return RemoteViews(context.packageName, R.layout.widget_large_course_item)
        }

        val course = courseItems[position]
        val views = RemoteViews(context.packageName, R.layout.widget_large_course_item)

        // 解析课程颜色
        val courseColor = try {
            Color.parseColor(course.color)
        } catch (e: Exception) {
            Color.parseColor("#5C6BC0")
        }

        // 设置色条颜色
        views.setInt(R.id.view_color_bar, "setBackgroundColor", courseColor)

        // 设置节次信息
        val endSection = course.startSection + course.sectionCount - 1
        views.setTextViewText(R.id.tv_section, "${course.startSection}-${endSection}节")

        // 设置时间信息
        views.setTextViewText(R.id.tv_time, course.startTimeText)

        // 设置课程名称
        views.setTextViewText(R.id.tv_course_name, course.courseName)

        // 设置教室信息
        if (course.classroom.isNotEmpty()) {
            views.setTextViewText(R.id.tv_classroom, course.classroom)
            views.setViewVisibility(R.id.tv_classroom, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.tv_classroom, android.view.View.GONE)
        }

        // 设置教师信息
        if (course.teacher.isNotEmpty()) {
            views.setTextViewText(R.id.tv_teacher, course.teacher)
            views.setViewVisibility(R.id.tv_teacher, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.tv_teacher, android.view.View.GONE)
        }

        // 设置进行中标记
        if (course.isOngoing) {
            views.setViewVisibility(R.id.tv_ongoing, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.tv_ongoing, android.view.View.GONE)
        }

        // 设置点击填充 Intent（用于点击跳转）
        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.course_item_container, fillInIntent)

        return views
    }

    /**
     * 返回加载中视图
     * 在数据加载期间显示
     */
    override fun getLoadingView(): RemoteViews? = null

    /**
     * 返回视图类型数量
     * 所有项使用相同布局，返回 1
     */
    override fun getViewTypeCount(): Int = 1

    /**
     * 返回指定位置的稳定 ID
     */
    override fun getItemId(position: Int): Long = position.toLong()

    /**
     * 返回 ID 是否稳定
     */
    override fun hasStableIds(): Boolean = true
}
