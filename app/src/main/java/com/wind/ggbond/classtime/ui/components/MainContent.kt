// [Monet] 已排查：该文件不涉及课程颜色渲染，无需适配
package com.wind.ggbond.classtime.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.wind.ggbond.classtime.InitializationState
import com.wind.ggbond.classtime.initializer.AppInitializer
import com.wind.ggbond.classtime.ui.navigation.BottomNavItem
import com.wind.ggbond.classtime.ui.navigation.NavGraph
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.theme.BackgroundScheme
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import com.wind.ggbond.classtime.ui.theme.BackgroundType
import com.wind.ggbond.classtime.ui.theme.CourseScheduleTheme
import androidx.hilt.navigation.compose.hiltViewModel
import com.wind.ggbond.classtime.ui.viewmodel.MainViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainContent(
    initState: InitializationState,
    appInitializer: AppInitializer,
    backgroundThemeManager: BackgroundThemeManager,
    intent: android.content.Intent?,
    onRetry: () -> Unit,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val isDynamicThemeEnabled by backgroundThemeManager.isDynamicThemeEnabled().collectAsState(initial = false)
    val seedColor by backgroundThemeManager.getSeedColor().collectAsState(initial = BackgroundThemeManager.DEFAULT_SEED_COLOR)
    val activeScheme by backgroundThemeManager.getActiveBackgroundScheme().collectAsState(initial = null)
    val blurRadius by backgroundThemeManager.getBlurRadius().collectAsState(initial = 0)
    val dimAmount by backgroundThemeManager.getDimAmount().collectAsState(initial = 40)
    val darkTheme = isSystemInDarkTheme()

    // Add logging for activeScheme changes
    LaunchedEffect(activeScheme) {
        android.util.Log.d("MainContent", "[activeScheme] Changed: ${activeScheme?.id ?: "null"}")
        if (activeScheme == null) {
            android.util.Log.w("MainContent", "[activeScheme] activeScheme is null")
        }
    }

    val dynamicColorScheme = if (isDynamicThemeEnabled) {
        if (darkTheme) backgroundThemeManager.generateDarkColorScheme(seedColor)
        else backgroundThemeManager.generateLightColorScheme(seedColor)
    } else {
        null
    }

    CourseScheduleTheme(
        darkTheme = darkTheme,
        customDynamicColorScheme = dynamicColorScheme
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Render wallpaper layer first (bottom layer)
            if (isDynamicThemeEnabled && activeScheme != null) {
                when (activeScheme!!.type) {
                    BackgroundType.IMAGE, BackgroundType.GIF -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(activeScheme!!.uri)
                                .build(),
                            contentDescription = "背景",
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (blurRadius > 0) Modifier.blur(blurRadius.dp / 10f)
                                    else Modifier
                                ),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = dimAmount / 100f))
                        )
                    }
                    BackgroundType.VIDEO -> {}
                }
            } else {
                // When no wallpaper is configured, show default background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                )
            }
            
            // Render app content on top of wallpaper
            when (val state = initState) {
                is InitializationState.Loading -> {
                    LoadingScreen()
                }
                is InitializationState.Success -> {
                    SuccessContent(
                        appInitializer = appInitializer,
                        backgroundThemeManager = backgroundThemeManager,
                        intent = intent,
                        mainViewModel = mainViewModel
                    )
                }
                is InitializationState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SuccessContent(
    appInitializer: AppInitializer,
    backgroundThemeManager: BackgroundThemeManager,
    intent: android.content.Intent?,
    mainViewModel: MainViewModel
) {
    // 从 ViewModel 获取引导状态（通过 Repository 访问）
    val uiState by mainViewModel.uiState.collectAsState()

    // 处理引导页状态
    when (val state = uiState) {
        is MainViewModel.UiState.OnboardingRequired -> {
            com.wind.ggbond.classtime.ui.screen.welcome.UnifiedOnboardingScreen(
                onComplete = {
                    mainViewModel.markOnboardingComplete(true)
                },
                onAcceptDisclaimer = {
                    mainViewModel.markOnboardingComplete(disclaimerAccepted = true)
                },
                disclaimerAccepted = state.disclaimerAccepted ?: false
            )
            return
        }
        is MainViewModel.UiState.Loading -> {
            // 仍在加载中，显示加载界面或返回
            return
        }
        is MainViewModel.UiState.Error -> {
            // 错误状态，由外层处理
            return
        }
        is MainViewModel.UiState.Success -> {
            // 继续显示主内容
        }
    }

    SharedTransitionLayout {
        val navController = rememberNavController()

        LaunchedEffect(Unit) {
            val openAdjustmentManagement = intent?.getBooleanExtra("openAdjustmentManagement", false) ?: false
            if (openAdjustmentManagement) {
                navController.navigate(Screen.AdjustmentManagement.route)
            }
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val showBottomBar = currentRoute in BottomNavItem.routes

        // 从 ViewModel 获取底部栏模糊设置（通过 Repository 访问）
        val bottomBarBlurEnabled by mainViewModel.bottomBarBlurEnabled.collectAsState()

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    BottomNavigationBar(
                        navController = navController,
                        currentRoute = currentRoute,
                        blurEnabled = bottomBarBlurEnabled
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                NavGraph(
                    navController = navController,
                    sharedTransitionScope = this@SharedTransitionLayout
                )
            }
        }
    }
}
