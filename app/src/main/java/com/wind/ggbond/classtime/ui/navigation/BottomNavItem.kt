package com.wind.ggbond.classtime.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航项定义
 * 定义三个顶级Tab页面的路由、图标和标签
 * 使用Outlined（线性）图标风格
 */
sealed class BottomNavItem(
    val route: String,      // 导航路由
    val icon: ImageVector,  // 图标
    val label: String       // 标签文字
) {
    // Tab1: 课表 - 使用日历图标
    object Schedule : BottomNavItem(
        route = "tab_schedule",
        icon = Icons.Outlined.CalendarMonth,
        label = "课表"
    )

    // Tab2: 工具 - 使用工具/设置图标
    object Tools : BottomNavItem(
        route = "tab_tools",
        icon = Icons.Outlined.Build,
        label = "工具"
    )

    // Tab3: 我的 - 使用人物图标
    object Profile : BottomNavItem(
        route = "tab_profile",
        icon = Icons.Outlined.Person,
        label = "我的"
    )

    companion object {
        // 所有Tab项列表
        val items = listOf(Schedule, Tools, Profile)

        // 所有Tab路由集合，用于判断是否显示底部栏
        val routes = items.map { it.route }.toSet()
    }
}
