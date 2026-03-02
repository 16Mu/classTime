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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.repository.InitializationRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.AlarmReminderScheduler
import com.wind.ggbond.classtime.ui.navigation.NavGraph
import com.wind.ggbond.classtime.ui.theme.CourseScheduleTheme
import com.wind.ggbond.classtime.util.ScheduledUpdateManager
import com.wind.ggbond.classtime.widget.WidgetRefreshHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    lateinit var initializationRepository: InitializationRepository
    
    @Inject
    lateinit var scheduleRepository: ScheduleRepository
    
    @Inject
    lateinit var scheduledUpdateManager: ScheduledUpdateManager
    
    @Inject
    lateinit var alarmReminderScheduler: AlarmReminderScheduler
    
    // 初始化状态
    private var initState by mutableStateOf<InitializationState>(InitializationState.Loading)
    
    // 上次自动检查更新的时间（避免重复检查）
    private var lastAutoCheckTime: Long = 0
    
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
        
        // ✅ 第三步：在后台执行初始化和预加载
        lifecycleScope.launch {
            try {
                // 初始化默认数据
                initializationRepository.initializeDefaultData()
                
                // ✅ 预加载核心数据到内存（避免进入主界面时看到加载过程）
                initializationRepository.preloadCoreData()
                
                // ✅ 初始化定时更新任务
                initializeScheduledUpdate()
                
                // ✅ 注册WorkManager兜底检查任务（每15分钟检查到期提醒，即使AlarmManager被清除也能触发）
                com.wind.ggbond.classtime.worker.ReminderCheckWorker.enqueue(this@MainActivity)
                
                // ✅ 启动前台保活服务（保持最小进程存活，到时间唤醒提醒）
                com.wind.ggbond.classtime.service.KeepAliveService.start(this@MainActivity)
                
                // ✅ 重新调度所有AlarmManager课程提醒（防止App被杀死后提醒丢失）
                rescheduleRemindersOnStartup()
                
                initState = InitializationState.Success
                
                // ✅ 刷新小组件数据，确保从小组件进入应用时数据同步
                WidgetRefreshHelper.refreshAllWidgets(this@MainActivity)
                
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
            CourseScheduleTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                    when (val state = initState) {
                        is InitializationState.Loading -> {
                            // SplashScreen会自动显示，这里可以留空
                            // 或者显示一个简单的加载指示器作为备用
                            LoadingScreen()
                        }
                        is InitializationState.Success -> {
                            // ==================== 引导页顶层拦截 ====================
                            // 从 DataStore 读取引导完成状态，在 Scaffold/底部Tab 之前拦截
                            val onboardingContext = LocalContext.current
                            val settingsDataStore = DataStoreManager.getSettingsDataStore(onboardingContext)
                            val disclaimerAccepted by settingsDataStore.data
                                .map { it[DataStoreManager.SettingsKeys.DISCLAIMER_ACCEPTED_KEY] ?: false }
                                .collectAsState(initial = null)
                            val onboardingCompleted by settingsDataStore.data
                                .map { it[DataStoreManager.SettingsKeys.ONBOARDING_COMPLETED_KEY] ?: false }
                                .collectAsState(initial = null)

                            // 状态未加载完成时显示空白（避免闪屏）
                            if (disclaimerAccepted == null || onboardingCompleted == null) {
                                return@Surface
                            }

                            // 需要引导时：渲染引导页，完全阻断底部Tab
                            val shouldShowOnboarding = (disclaimerAccepted == false || onboardingCompleted == false)
                            if (shouldShowOnboarding) {
                                com.wind.ggbond.classtime.ui.screen.welcome.UnifiedOnboardingScreen(
                                    onComplete = {
                                        // 标记引导完成 + 免责声明已接受
                                        lifecycleScope.launch {
                                            settingsDataStore.edit { prefs ->
                                                prefs[DataStoreManager.SettingsKeys.DISCLAIMER_ACCEPTED_KEY] = true
                                                prefs[DataStoreManager.SettingsKeys.ONBOARDING_COMPLETED_KEY] = true
                                            }
                                        }
                                    },
                                    onAcceptDisclaimer = {
                                        lifecycleScope.launch {
                                            settingsDataStore.edit { prefs ->
                                                prefs[DataStoreManager.SettingsKeys.DISCLAIMER_ACCEPTED_KEY] = true
                                            }
                                        }
                                    },
                                    disclaimerAccepted = disclaimerAccepted ?: false
                                )
                                return@Surface
                            }

                            // ==================== 正常应用界面 ====================
                            // 使用 SharedTransitionLayout 包裹整个导航，实现一镜到底
                            @OptIn(ExperimentalSharedTransitionApi::class)
                            SharedTransitionLayout {
                        val navController = rememberNavController()
                        
                        // 处理从通知启动的情况
                        LaunchedEffect(Unit) {
                            val openAdjustmentManagement = intent?.getBooleanExtra("openAdjustmentManagement", false) ?: false
                            if (openAdjustmentManagement) {
                                navController.navigate(com.wind.ggbond.classtime.ui.navigation.Screen.AdjustmentManagement.route)
                            }
                        }
                        
                        // 获取当前路由，判断是否需要显示底部导航栏
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        // 只在三个Tab顶级页面显示底部导航栏
                        val showBottomBar = currentRoute in com.wind.ggbond.classtime.ui.navigation.BottomNavItem.routes
                        
                        // 读取底部栏高斯模糊设置
                        val blurContext = LocalContext.current
                        val bottomBarBlurEnabled by DataStoreManager.getSettingsDataStore(blurContext)
                            .data
                            .map { it[DataStoreManager.SettingsKeys.BOTTOM_BAR_BLUR_ENABLED_KEY] ?: true }
                            .collectAsState(initial = true)
                        
                        Scaffold(
                            // 底部栏使用自定义实现，禁用 Scaffold 默认的底部栏 padding
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
                        is InitializationState.Error -> {
                            ErrorScreen(
                                message = state.message,
                                onRetry = {
                                    // 重试初始化
                                    initState = InitializationState.Loading
                                    lifecycleScope.launch {
                                        try {
                                            initializationRepository.initializeDefaultData()
                                            initState = InitializationState.Success
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
                }
            }
        }
    }
    
    /**
     * 检查并触发自动更新
     * 
     * 方案 B：支持间隔更新和定时更新两种方式
     * 逻辑：
     * 1. 检查是否启用自动更新（总开关）
     * 2. 检查间隔更新：距离上次更新时间 >= 设定间隔
     * 3. 防重复：如果距离上次更新 < 5分钟，跳过此次更新
     * 4. 检查前置条件：课表和学校配置
     * 5. 触发更新
     */
    private suspend fun checkAndTriggerAutoUpdate() {
        try {
            // ✅ 避免短时间内重复检查（例如用户快速切换应用）
            val now = System.currentTimeMillis()
            if (now - lastAutoCheckTime < 60_000) { // 1分钟内只检查一次
                android.util.Log.d("MainActivity", "跳过自动更新检查（距离上次检查不足1分钟）")
                return
            }
            lastAutoCheckTime = now
            
            // 读取设置
            val settingsDataStore = com.wind.ggbond.classtime.data.datastore.DataStoreManager.getSettingsDataStore(this)
            val preferences = settingsDataStore.data.first()
            
            val autoUpdateEnabled = preferences[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.AUTO_UPDATE_ENABLED_KEY] 
                ?: com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_ENABLED
            
            if (!autoUpdateEnabled) {
                android.util.Log.d("MainActivity", "自动更新未启用，跳过检查")
                return
            }
            
            // 检查间隔更新是否启用
            val intervalUpdateEnabled = preferences[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.INTERVAL_UPDATE_ENABLED_KEY]
                ?: com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.DEFAULT_INTERVAL_UPDATE_ENABLED
            
            val updateIntervalHours = preferences[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.AUTO_UPDATE_INTERVAL_HOURS_KEY]
                ?: com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_INTERVAL_HOURS
            
            val lastUpdateTime = preferences[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.LAST_AUTO_UPDATE_TIME_KEY] ?: 0L
            
            val intervalMillis = updateIntervalHours * 60 * 60 * 1000L
            val timeSinceLastUpdate = now - lastUpdateTime
            val dedupIntervalMs = com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.UPDATE_DEDUP_INTERVAL_MS
            
            android.util.Log.d("MainActivity", "自动更新检查:")
            android.util.Log.d("MainActivity", "  启用状态: $autoUpdateEnabled")
            android.util.Log.d("MainActivity", "  间隔更新启用: $intervalUpdateEnabled")
            android.util.Log.d("MainActivity", "  更新间隔: ${updateIntervalHours}小时")
            android.util.Log.d("MainActivity", "  距离上次: ${timeSinceLastUpdate / (60 * 1000)}分钟")
            
            // ✅ 防重复检查：如果距离上次更新 < 5分钟，跳过
            if (timeSinceLastUpdate < dedupIntervalMs) {
                android.util.Log.d("MainActivity", "⏭️ 防重复：距离上次更新不足5分钟，跳过")
                return
            }
            
            // 检查是否应该触发间隔更新
            val shouldTriggerIntervalUpdate = intervalUpdateEnabled && timeSinceLastUpdate >= intervalMillis
            
            if (!shouldTriggerIntervalUpdate) {
                android.util.Log.d("MainActivity", "未达到更新间隔，跳过更新")
                return
            }
            
            android.util.Log.d("MainActivity", "✅ 达到更新间隔，检查前置条件...")
            
            // ✅ 检查前置条件：课表和学校配置
            val currentSchedule = scheduleRepository.getCurrentSchedule()
            if (currentSchedule == null) {
                android.util.Log.d("MainActivity", "⚠️ 未导入课表，跳过自动更新")
                return
            }
            
            val schoolId = currentSchedule.schoolName
            if (schoolId.isNullOrEmpty()) {
                android.util.Log.d("MainActivity", "⚠️ 课表缺少学校配置，跳过自动更新")
                return
            }
            
            android.util.Log.d("MainActivity", "✅ 前置条件满足 (课表: ${currentSchedule.name}, 学校: $schoolId)，触发自动更新")
            
            // 更新最后更新时间（避免重复触发）
            settingsDataStore.edit { prefs ->
                prefs[com.wind.ggbond.classtime.data.datastore.DataStoreManager.SettingsKeys.LAST_AUTO_UPDATE_TIME_KEY] = now
            }
            
            // 延迟500ms启动更新（确保UI已经准备好）
            delay(500)
            
            // 触发更新
            com.wind.ggbond.classtime.ui.screen.update.FloatingUpdateActivity.start(this)
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "检查自动更新失败", e)
        }
    }
    
    /**
     * 初始化定时更新任务
     * 
     * 在应用启动时检查定时更新是否启用，如果启用则启动 WorkManager 任务
     */
    private suspend fun initializeScheduledUpdate() {
        try {
            val isEnabled = scheduledUpdateManager.isScheduledUpdateEnabled()
            if (isEnabled) {
                Log.d("MainActivity", "✅ 定时更新已启用，初始化 WorkManager 任务")
                scheduledUpdateManager.enableScheduledUpdate()
            } else {
                Log.d("MainActivity", "定时更新未启用")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "初始化定时更新失败", e)
        }
    }
    
    /**
     * 应用启动时重新调度所有课程提醒
     * 
     * AlarmManager在App被杀死或系统重启后可能丢失，
     * 因此每次应用启动时都需要重新调度所有未来的提醒。
     */
    private suspend fun rescheduleRemindersOnStartup() {
        try {
            Log.d("MainActivity", "开始重新调度课程提醒...")
            alarmReminderScheduler.rescheduleAllReminders()
            Log.d("MainActivity", "✅ 课程提醒调度完成")
        } catch (e: Exception) {
            Log.e("MainActivity", "重新调度课程提醒失败", e)
        }
    }
}

/**
 * 加载界面（备用）
 * ✅ 优化：显示预加载提示，提升用户体验
 */
@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在加载课表数据...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 底部导航栏
 * 减小高度以获得更紧凑的布局，支持高斯模糊（半透明）背景效果
 * 
 * @param navController 导航控制器
 * @param currentRoute 当前路由
 * @param blurEnabled 是否启用高斯模糊背景（半透明效果）
 */
@Composable
private fun BottomNavigationBar(
    navController: androidx.navigation.NavController,
    currentRoute: String?,
    blurEnabled: Boolean = true
) {
    // 底部栏背景色：模糊模式使用半透明表面色，否则使用不透明表面色
    val containerColor = if (blurEnabled) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    // 使用 Material3 NavigationBar 组件，减小高度并自定义背景色
    androidx.compose.material3.NavigationBar(
        modifier = Modifier.height(64.dp),
        containerColor = containerColor,
        tonalElevation = if (blurEnabled) 0.dp else 3.dp
    ) {
        com.wind.ggbond.classtime.ui.navigation.BottomNavItem.items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 11.sp
                    )
                },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

/**
 * 错误界面
 */
@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "初始化失败",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

