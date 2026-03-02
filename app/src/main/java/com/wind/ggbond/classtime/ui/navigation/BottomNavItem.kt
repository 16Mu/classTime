package com.wind.ggbond.classtime.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航项定义
 * 定义三个顶级Tab页面的路由、图标和标签
 */
sealed class BottomNavItem(
    val route: String,      // 导航路由
    val icon: ImageVector,  // 图标
    val label: String       // 标签文字
) {
    // Tab1: 课表
    object Schedule : BottomNavItem(
        route = "tab_schedule",
        icon = Icons.Default.GridView,
        label = "课表"
    )

    // Tab2: 工具
    object Tools : BottomNavItem(
        route = "tab_tools",
        icon = Icons.Default.Build,
        label = "工具"
    )

    // Tab3: 我的
    object Profile : BottomNavItem(
        route = "tab_profile",
        icon = Icons.Default.Person,
        label = "我的"
    )

    companion object {
        // 所有Tab项列表
        val items = listOf(Schedule, Tools, Profile)

        // 所有Tab路由集合，用于判断是否显示底部栏
        val routes = items.map { it.route }.toSet()
    }
}
