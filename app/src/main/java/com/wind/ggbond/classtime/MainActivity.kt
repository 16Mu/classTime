package com.wind.ggbond.classtime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.initializer.AppInitializer
import com.wind.ggbond.classtime.initializer.InitializationResult
import com.wind.ggbond.classtime.service.contract.IUpdateManager
import com.wind.ggbond.classtime.ui.components.MainContent
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 初始化状态
 */
sealed class InitializationState {
    object Loading : InitializationState()
    object Success : InitializationState()
    data class Error(val message: String, val throwable: Throwable) : InitializationState()
}

/**
 * 主Activity
 * 
 * ✅ 正确的启动流程：
 * 1. 安装SplashScreen
 * 2. 等待初始化完成
 * 3. 显示UI或错误界面
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appInitializer: AppInitializer

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var backgroundThemeManager: BackgroundThemeManager

    @Inject
    lateinit var updateOrchestrator: IUpdateManager
    
    // 初始化状态
    private var initState by mutableStateOf<InitializationState>(InitializationState.Loading)

    // 通知权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 权限已授予
        } else {
            // 权限被拒绝
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // ✅ 第一步：安装SplashScreen（必须在super.onCreate之前）
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // ✅ 第二步：保持SplashScreen直到初始化完成
        splashScreen.setKeepOnScreenCondition {
            initState is InitializationState.Loading
        }
        
        // 启用边到边显示和预测性返回手势
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 请求通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // ✅ 第三步：在后台执行初始化和预加载（带超时保护）
        lifecycleScope.launch {
            try {
                val initResult = appInitializer.initialize()

                initState = when (initResult) {
                    is InitializationResult.Success -> InitializationState.Success
                    is InitializationResult.Timeout -> InitializationState.Error(
                        message = "初始化超时（10秒），请重试",
                        throwable = java.util.concurrent.TimeoutException("初始化操作在10秒内未完成")
                    )
                    is InitializationResult.Error -> InitializationState.Error(
                        message = "初始化失败：${initResult.message}",
                        throwable = initResult.throwable
                    )
                }

                // ✅ 刷新小组件数据，确保从小组件进入应用时数据同步
                appInitializer.refreshWidgets()

                // ✅ 检查是否需要自动更新课表
                checkAndTriggerAutoUpdate()

            } catch (e: Exception) {
                e.printStackTrace()
                initState = InitializationState.Error(
                    message = "初始化失败：${e.message ?: "未知错误"}",
                    throwable = e
                )
            }
        }
        
        // ✅ 第四步：根据初始化状态显示UI
        setContent {
            MainContent(
                initState = initState,
                appInitializer = appInitializer,
                backgroundThemeManager = backgroundThemeManager,
                intent = intent,
                onRetry = {
                    initState = InitializationState.Loading
                    lifecycleScope.launch {
                        try {
                            val retryResult = appInitializer.initialize()
                            initState = when (retryResult) {
                                is InitializationResult.Success -> InitializationState.Success
                                is InitializationResult.Timeout -> InitializationState.Error(
                                    message = "初始化超时（10秒），请重试",
                                    throwable = java.util.concurrent.TimeoutException("初始化操作在10秒内未完成")
                                )
                                is InitializationResult.Error -> InitializationState.Error(
                                    message = "初始化失败：${retryResult.message}",
                                    throwable = retryResult.throwable
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            initState = InitializationState.Error(
                                message = "初始化失败：${e.message ?: "未知错误"}",
                                throwable = e
                            )
                        }
                    }
                }
            )
        }
    }
    
    private suspend fun checkAndTriggerAutoUpdate() {
        try {
            val decision = updateOrchestrator.checkAndTriggerAutoUpdate()
            if (decision.shouldUpdate) {
                Log.d("MainActivity", "✅ 自动更新已触发: ${decision.reason}")
            } else {
                Log.d("MainActivity", "跳过自动更新: ${decision.reason}")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "检查自动更新失败", e)
        }
    }
}

