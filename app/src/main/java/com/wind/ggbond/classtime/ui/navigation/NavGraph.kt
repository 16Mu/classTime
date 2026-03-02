package com.wind.ggbond.classtime.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wind.ggbond.classtime.ui.screen.main.MainScreen

/**
 * 应用导航图（支持共享元素转场 + 底部Tab导航）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    startDestination: String = BottomNavItem.Schedule.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { defaultEnterTransition() },
        exitTransition = { defaultExitTransition() },
        popEnterTransition = { defaultPopEnterTransition() },
        popExitTransition = { defaultPopExitTransition() }
    ) {
        // ==================== 底部Tab页面 ====================
        // 引导页已移至 MainActivity 顶层拦截，不再作为导航路由

        // Tab1: 课表（主界面）
        composable(
            route = BottomNavItem.Schedule.route
        ) {
            MainScreen(
                navController = navController
            )
        }

        // Tab2: 工具
        composable(
            route = BottomNavItem.Tools.route
        ) {
            com.wind.ggbond.classtime.ui.screen.tools.ToolsScreen(
                navController = navController
            )
        }

        // Tab3: 我的
        composable(
            route = BottomNavItem.Profile.route
        ) {
            com.wind.ggbond.classtime.ui.screen.profile.ProfileScreen(
                navController = navController
            )
        }

        // ==================== 二级页面 ====================

        // 主界面（带refresh参数的路由，用于导入后强制刷新）
        composable(
            route = Screen.Main.route,
            arguments = listOf(
                navArgument("refresh") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val refresh = backStackEntry.arguments?.getBoolean("refresh") ?: false
            MainScreen(
                navController = navController,
                forceRefresh = refresh
            )
        }
        
        // 课程编辑 - 从顶部滑入的全屏动画
        composable(
            route = Screen.CourseEdit.route,
            arguments = listOf(
                navArgument("courseId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("dayOfWeek") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("startSection") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("sectionCount") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("weekNumber") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            ),
            enterTransition = {
                // 从顶部向下滑入
                slideInVertically(
                    initialOffsetY = { -it }, // 从屏幕顶部外开始
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                ) + fadeIn(
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )
            },
            exitTransition = {
                // 保持原位淡出（当跳转到其他页面时）
                fadeOut(
                    animationSpec = tween(300, easing = FastOutLinearInEasing)
                )
            },
            popEnterTransition = {
                // 当从其他页面返回时淡入
                fadeIn(
                    animationSpec = tween(300, easing = LinearOutSlowInEasing)
                )
            },
            popExitTransition = {
                // 点击保存/返回时，向下滑出
                slideOutVertically(
                    targetOffsetY = { it }, // 向下滑出到屏幕底部外
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeOut(
                    animationSpec = tween(350, easing = FastOutLinearInEasing)
                )
            }
        ) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getLong("courseId")?.takeIf { it > 0 }
            val dayOfWeek = backStackEntry.arguments?.getInt("dayOfWeek")?.takeIf { it in 1..7 }
            val startSection = backStackEntry.arguments?.getInt("startSection")?.takeIf { it > 0 }
            val sectionCount = backStackEntry.arguments?.getInt("sectionCount")?.takeIf { it > 0 }
            val weekNumber = backStackEntry.arguments?.getInt("weekNumber")?.takeIf { it > 0 }
            com.wind.ggbond.classtime.ui.screen.course.CourseEditScreen(
                navController = navController,
                courseId = courseId,
                defaultDayOfWeek = dayOfWeek,
                defaultStartSection = startSection,
                defaultSectionCount = sectionCount,
                defaultWeekNumber = weekNumber
            )
        }
        
        // 课程信息列表
        composable(Screen.CourseInfoList.route) {
            com.wind.ggbond.classtime.ui.screen.course.CourseInfoListScreen(
                navController = navController
            )
        }
        
        // 设置
        composable(Screen.Settings.route) {
            com.wind.ggbond.classtime.ui.screen.settings.SettingsScreen(navController)
        }
        
        // 学期管理
        composable(
            route = Screen.SemesterManagement.route,
            arguments = listOf(
                navArgument("fromImport") { 
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("fallSemesterStartDate") {
                    type = NavType.StringType
                    defaultValue = null
                    nullable = true
                },
                navArgument("springSemesterStartDate") {
                    type = NavType.StringType
                    defaultValue = null
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val fromImport = backStackEntry.arguments?.getBoolean("fromImport") ?: false
            val fallSemesterStartDate = backStackEntry.arguments?.getString("fallSemesterStartDate")
            val springSemesterStartDate = backStackEntry.arguments?.getString("springSemesterStartDate")
            com.wind.ggbond.classtime.ui.screen.settings.SemesterManagementScreen(
                navController, 
                fromImport,
                fallSemesterStartDate,
                springSemesterStartDate
            )
        }
        
        // 课程数设置
        composable(Screen.SectionCountConfig.route) {
            com.wind.ggbond.classtime.ui.screen.settings.SectionCountConfigScreen(navController)
        }
        
        // 上下课时间配置
        composable(
            route = Screen.ClassTimeConfig.route,
            arguments = listOf(navArgument("fromImport") { 
                type = NavType.BoolType
                defaultValue = false
            })
        ) { backStackEntry ->
            val fromImport = backStackEntry.arguments?.getBoolean("fromImport") ?: false
            com.wind.ggbond.classtime.ui.screen.settings.ClassTimeConfigScreen(navController, fromImport)
        }
        
        // 课程表设置
        composable(Screen.TimetableSettings.route) {
            com.wind.ggbond.classtime.ui.screen.settings.TimetableSettingsScreen(navController)
        }
        
        // 导入课表
        composable(Screen.ImportSchedule.route) {
            com.wind.ggbond.classtime.ui.screen.scheduleimport.ImportScheduleScreen(navController)
        }
        
        // 学校选择
        composable(Screen.SchoolSelection.route) {
            com.wind.ggbond.classtime.ui.screen.scheduleimport.SchoolSelectionScreen(navController)
        }
        
        // 智能WebView导入（推荐方式）⭐
        composable(
            route = Screen.SmartWebViewImport.route,
            arguments = listOf(navArgument("schoolId") { type = NavType.StringType })
        ) { backStackEntry ->
            val schoolId = backStackEntry.arguments?.getString("schoolId") ?: ""
            com.wind.ggbond.classtime.ui.screen.scheduleimport.SmartWebViewImportScreen(navController, schoolId)
        }
        
        // WebView 登录（旧方式，保留）
        composable(
            route = Screen.WebViewLogin.route,
            arguments = listOf(navArgument("schoolId") { type = NavType.StringType })
        ) { backStackEntry ->
            val schoolId = backStackEntry.arguments?.getString("schoolId") ?: ""
            com.wind.ggbond.classtime.ui.screen.scheduleimport.WebViewLoginScreen(navController, schoolId)
        }
        
        // 调课记录管理
        composable(Screen.AdjustmentManagement.route) {
            com.wind.ggbond.classtime.ui.screen.adjustment.AdjustmentManagementScreen(navController)
        }
        
        // 自动更新设置
        composable(Screen.AutoUpdateSettings.route) {
            com.wind.ggbond.classtime.ui.screen.settings.AutoUpdateSettingsScreen(navController)
        }
        
        // 手动批量创建课程
        composable(Screen.BatchCourseCreate.route) {
            com.wind.ggbond.classtime.ui.screen.scheduleimport.BatchCourseCreateScreen(navController)
        }
        
        // 课程提醒设置（二级页面）
        composable(Screen.ReminderSettings.route) {
            com.wind.ggbond.classtime.ui.screen.reminder.ReminderSettingsScreen(navController)
        }
        
        // 提醒管理（查看所有提醒列表）
        composable(Screen.ReminderManagement.route) {
            com.wind.ggbond.classtime.ui.screen.reminder.ReminderManagementScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        
        // 权限教程页面（三级页面，按权限类型参数区分）
        composable(
            route = Screen.PermissionTutorial.route,
            arguments = listOf(navArgument("permissionType") { type = NavType.StringType })
        ) { backStackEntry ->
            val permissionType = backStackEntry.arguments?.getString("permissionType") ?: ""
            com.wind.ggbond.classtime.ui.screen.reminder.PermissionTutorialScreen(
                navController = navController,
                permissionTypeName = permissionType
            )
        }
    }
}

/**
 * 默认进入动画 - 一镜到底的淡入+上滑
 */
private fun defaultEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + slideInVertically(
        initialOffsetY = { it / 20 },
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    ) + scaleIn(
        initialScale = 0.95f,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )
}

/**
 * 默认退出动画 - 淡出+轻微缩放
 */
private fun defaultExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = tween(200, easing = FastOutLinearInEasing)
    ) + scaleOut(
        targetScale = 0.96f,
        animationSpec = tween(200, easing = FastOutLinearInEasing)
    )
}

/**
 * 返回进入动画 - 恢复原页面
 */
private fun defaultPopEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = tween(300, easing = LinearOutSlowInEasing)
    ) + scaleIn(
        initialScale = 0.96f,
        animationSpec = tween(300, easing = LinearOutSlowInEasing)
    )
}

/**
 * 返回退出动画 - 下滑退出
 */
private fun defaultPopExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = tween(250, easing = FastOutLinearInEasing)
    ) + slideOutVertically(
        targetOffsetY = { it / 20 },
        animationSpec = tween(350, easing = FastOutLinearInEasing)
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = tween(250, easing = FastOutLinearInEasing)
    )
}

