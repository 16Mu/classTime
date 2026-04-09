package com.wind.ggbond.classtime.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String
) {
    object Schedule : BottomNavItem(
        route = "tab_schedule",
        icon = Icons.Outlined.CalendarMonth,
        selectedIcon = Icons.Filled.CalendarMonth,
        label = "课表"
    )

    object Tools : BottomNavItem(
        route = "tab_tools",
        icon = Icons.Outlined.Build,
        selectedIcon = Icons.Filled.Build,
        label = "工具"
    )

    object Profile : BottomNavItem(
        route = "tab_profile",
        icon = Icons.Outlined.Person,
        selectedIcon = Icons.Filled.Person,
        label = "我的"
    )

    companion object {
        val items = listOf(Schedule, Tools, Profile)
        val routes = items.map { it.route }.toSet()
    }
}
