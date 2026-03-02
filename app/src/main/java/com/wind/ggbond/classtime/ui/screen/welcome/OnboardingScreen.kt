package com.wind.ggbond.classtime.ui.screen.welcome

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.wind.ggbond.classtime.ui.navigation.Screen

/**
 * 完整的引导流程
 * 
 * 用户首次打开应用时的完整体验流程：
 * 1. 法律合规（免责声明）- WelcomeDisclaimerDialog
 * 2. 功能介绍 - FeatureIntroductionScreen
 * 3. 快速上手 - QuickStartGuideScreen  
 * 4. 权限引导 - PermissionGuideScreen
 */
@Composable
fun OnboardingScreen(
    navController: NavController,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    
    // ✅ 使用 Box 包装，确保全屏显示
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300, easing = EaseInOut)
                ) + fadeIn() togetherWith
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(300, easing = EaseInOut)
                ) + fadeOut()
            },
            label = "onboarding_transition"
        ) { step ->
        when (step) {
            0 -> {
                // 步骤1: 功能介绍
                FeatureIntroductionScreen(
                    onComplete = { currentStep = 1 },
                    onSkip = { currentStep = 1 }
                )
            }
            1 -> {
                // 步骤2: 快速上手指南
                QuickStartGuideScreen(
                    onComplete = { currentStep = 2 },
                    onSkip = { currentStep = 2 },
                    onSelectSchool = {
                        // 跳转到学校选择页面
                        onComplete()
                        navController.navigate(Screen.SchoolSelection.route) {
                            popUpTo(Screen.Main.route) { inclusive = false }
                        }
                    },
                    onImportSchedule = {
                        // 跳转到导入页面
                        onComplete()
                        navController.navigate(Screen.ImportSchedule.route) {
                            popUpTo(Screen.Main.route) { inclusive = false }
                        }
                    },
                    onSetupReminders = {
                        // 跳转到设置页面
                        onComplete()
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(Screen.Main.route) { inclusive = false }
                        }
                    }
                )
            }
            2 -> {
                // 步骤3: 权限引导
                PermissionGuideScreen(
                    onComplete = {
                        // 完成所有引导
                        onComplete()
                    },
                    onSkip = {
                        // 跳过权限引导
                        onComplete()
                    }
                )
            }
        }
        }
    }
}

