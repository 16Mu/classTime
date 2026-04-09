// [Monet] 壁纸系统核心渲染层
package com.wind.ggbond.classtime.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wind.ggbond.classtime.InitializationState
import com.wind.ggbond.classtime.initializer.AppInitializer
import com.wind.ggbond.classtime.ui.navigation.BottomNavItem
import com.wind.ggbond.classtime.ui.navigation.NavGraph
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import com.wind.ggbond.classtime.ui.theme.BackgroundType
import com.wind.ggbond.classtime.ui.theme.CourseScheduleTheme
import com.wind.ggbond.classtime.ui.theme.LocalWallpaperAlpha
import com.wind.ggbond.classtime.ui.theme.LocalWallpaperEnabled
import com.wind.ggbond.classtime.ui.theme.OverlayConfig
import androidx.hilt.navigation.compose.hiltViewModel
import com.wind.ggbond.classtime.ui.screen.welcome.UnifiedOnboardingScreen
import com.wind.ggbond.classtime.ui.viewmodel.MainViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainContent(
    initState: InitializationState,
    appInitializer: AppInitializer,
    backgroundThemeManager: BackgroundThemeManager,
    intent: Intent?,
    onRetry: () -> Unit,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val isDynamicThemeEnabled by backgroundThemeManager.isDynamicThemeEnabled().collectAsState(initial = false)
    val seedColor by backgroundThemeManager.getSeedColor().collectAsState(initial = BackgroundThemeManager.DEFAULT_SEED_COLOR)
    val activeScheme by backgroundThemeManager.getActiveBackgroundScheme().collectAsState(initial = null)
    val blurRadius by backgroundThemeManager.getBlurRadius().collectAsState(initial = 0)
    val dimAmount by backgroundThemeManager.getDimAmount().collectAsState(initial = 40)
    val darkTheme = isSystemInDarkTheme()
    val courseColors by remember(darkTheme) {
        backgroundThemeManager.observeCourseColors(isDarkMode = darkTheme)
    }.collectAsState(initial = emptyList())
    val smartOverlayConfig by remember(activeScheme) {
        val uri = activeScheme?.uri?.let { Uri.parse(it) }
        backgroundThemeManager.getSmartOverlayConfig(uri)
    }.collectAsState(initial = OverlayConfig())

    val wallpaperEnabled = isDynamicThemeEnabled && activeScheme != null
    val wallpaperAlpha = if (wallpaperEnabled) 0.85f else 1.0f

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
        CompositionLocalProvider(
            LocalWallpaperEnabled provides wallpaperEnabled,
            LocalWallpaperAlpha provides wallpaperAlpha
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (wallpaperEnabled) {
                activeScheme?.let { scheme ->
                    val blurModifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (blurRadius > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(blurRadius.dp / 10f)
                            else Modifier
                        )
                    val overlayColor = smartOverlayConfig.overlayColor
                    val overlayModifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color(overlayColor).copy(
                                alpha = dimAmount / 100f
                            )
                        )

                    when (scheme.type) {
                        BackgroundType.IMAGE -> {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(scheme.uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "背景",
                                modifier = blurModifier,
                                contentScale = ContentScale.Crop
                            )
                            Box(modifier = overlayModifier)
                        }
                        BackgroundType.GIF -> {
                            GifBackgroundPlayer(
                                gifUri = Uri.parse(scheme.uri),
                                modifier = blurModifier,
                                dimAmount = 0f
                            )
                            Box(modifier = overlayModifier)
                        }
                        BackgroundType.VIDEO -> {
                            VideoBackgroundWithLoader(
                                videoUri = Uri.parse(scheme.uri),
                                isPlaying = true,
                                dimAmount = 0f,
                                modifier = blurModifier
                            )
                            Box(modifier = overlayModifier)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
            
            when (val state = initState) {
                is InitializationState.Loading -> {
                    LoadingScreen()
                }
                is InitializationState.Success -> {
                    SuccessContent(
                        appInitializer = appInitializer,
                        backgroundThemeManager = backgroundThemeManager,
                        intent = intent,
                        mainViewModel = mainViewModel,
                        courseColors = courseColors
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SuccessContent(
    appInitializer: AppInitializer,
    backgroundThemeManager: BackgroundThemeManager,
    intent: Intent?,
    mainViewModel: MainViewModel,
    courseColors: List<String> = emptyList()
) {
    // 从 ViewModel 获取引导状态（通过 Repository 访问）
    val uiState by mainViewModel.uiState.collectAsState()

    // 处理引导页状态
    when (val state = uiState) {
        is MainViewModel.UiState.OnboardingRequired -> {
            UnifiedOnboardingScreen(
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
        val bottomBarBlurEnabled by mainViewModel.glassEffectEnabled.collectAsState()

        val wallpaperEnabled = LocalWallpaperEnabled.current

        Scaffold(
            containerColor = if (wallpaperEnabled) Color.Transparent else MaterialTheme.colorScheme.background,
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
                    sharedTransitionScope = this@SharedTransitionLayout,
                    backgroundThemeManager = backgroundThemeManager,
                    courseColors = courseColors
                )
            }
        }
    }
}
