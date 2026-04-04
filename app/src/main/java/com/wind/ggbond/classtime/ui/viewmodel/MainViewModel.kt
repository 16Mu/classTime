package com.wind.ggbond.classtime.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.repository.InitializationRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.repository.SettingsRepository
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.service.contract.IUpdateManager
import com.wind.ggbond.classtime.worker.ReminderCheckWorker
import com.wind.ggbond.classtime.util.ScheduledUpdateManager
import com.wind.ggbond.classtime.ui.screen.update.FloatingUpdateActivity
import com.wind.ggbond.classtime.service.KeepAliveService
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.widget.WidgetRefreshHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val initializationRepository: InitializationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduledUpdateManager: ScheduledUpdateManager,
    private val alarmReminderScheduler: IAlarmScheduler,
    private val updateOrchestrator: IUpdateManager,
    private val application: Application
) : ViewModel() {
    
    companion object {
        private const val TAG = "MainVM"
    }
    
    // ==================== UI 状态定义 ====================
    
    sealed class UiState {
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String, val throwable: Throwable? = null) : UiState()
        
        /**
         * 引导页状态（需要显示引导时）
         */
        data class OnboardingRequired(
            val disclaimerAccepted: Boolean?,
            val onboardingCompleted: Boolean?
        ) : UiState()
    }
    
    // ==================== 状态持有 ====================
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // 底部栏模糊设置（供 Composable 读取）
    private val _bottomBarBlurEnabled = MutableStateFlow(true)
    val bottomBarBlurEnabled: StateFlow<Boolean> = _bottomBarBlurEnabled.asStateFlow()

    init {
        AppLogger.d(TAG, "MainViewModel 初始化")
        
        // 观察 DataStore 中的底部栏模糊设置
        viewModelScope.launch {
            settingsRepository.observeBottomBarBlurEnabled()
                .distinctUntilChanged()
                .collect { value ->
                    _bottomBarBlurEnabled.value = value
                }
        }
        
        initializeApp()
    }
    
    // ==================== 初始化流程 ====================
    
    /**
     * 执行完整的应用初始化流程
     * 
     * 流程：
     * 1. 安装 SplashScreen（由 Activity 负责）
     * 2. 初始化默认数据
     * 3. 预加载核心数据
     * 4. 初始化定时更新
     * 5. 注册 WorkManager 兜底任务
     * 6. 启动保活服务
     * 7. 重新调度提醒
     * 8. 检查引导状态
     * 9. 触发自动更新检查
     */
    private fun initializeApp() {
        viewModelScope.launch {
            try {
                AppLogger.d(TAG, "开始应用初始化...")
                
                // Step 1-2: 初始化数据
                initializationRepository.initializeDefaultData()
                AppLogger.d(TAG, "✅ 默认数据初始化完成")
                
                // Step 3: 预加载核心数据
                initializationRepository.preloadCoreData()
                AppLogger.d(TAG, "✅ 核心数据预加载完成")
                
                // Step 4: 初始化定时更新任务
                initializeScheduledUpdate()
                
                // Step 5: 注册 WorkManager 兜底检查任务
                ReminderCheckWorker.enqueue(application)
                AppLogger.d(TAG, "✅ WorkManager 兜底任务已注册")
                
                // Step 6: 启动前台保活服务
                KeepAliveService.start(application)
                AppLogger.d(TAG, "✅ 保活服务已启动")
                
                // Step 7: 重新调度所有课程提醒
                alarmReminderScheduler.rescheduleAllReminders()
                AppLogger.d(TAG, "✅ 课程提醒调度完成")
                
                // Step 8: 检查引导页状态并决定显示什么
                checkOnboardingStatus()
                
                // Step 9: 后台刷新 Widget 和检查自动更新（不阻塞 UI）
                launch {
                    WidgetRefreshHelper.refreshAllWidgets(application)
                    AppLogger.d(TAG, "✅ Widget 已刷新")
                    
                    checkAndTriggerAutoUpdate()
                }
                
            } catch (e: Exception) {
                AppLogger.e(TAG, "初始化失败", e)
                _uiState.value = UiState.Error(
                    message = "初始化失败：${e.message ?: "未知错误"}",
                    throwable = e
                )
            }
        }
    }
    
    /**
     * 检查引导页状态
     * 决定是显示主界面还是引导页
     */
    private suspend fun checkOnboardingStatus() {
        try {
            // 使用 SettingsRepository 读取引导状态
            val disclaimerAccepted = settingsRepository.isDisclaimerAccepted()
            val onboardingCompleted = settingsRepository.isOnboardingCompleted()

            // 判断是否需要引导
            val needsOnboarding = !disclaimerAccepted || !onboardingCompleted

            if (needsOnboarding) {
                AppLogger.d(TAG, "需要显示引导页 (disclaimer=$disclaimerAccepted, onboarding=$onboardingCompleted)")
                _uiState.value = UiState.OnboardingRequired(disclaimerAccepted, onboardingCompleted)
            } else {
                AppLogger.d(TAG, "引导已完成，显示主界面")
                _uiState.value = UiState.Success
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "检查引导状态失败", e)
            // 出错时默认显示主界面
            _uiState.value = UiState.Success
        }
    }
    
    // ==================== 定时更新相关 ====================
    
    /**
     * 初始化定时更新任务
     */
    private suspend fun initializeScheduledUpdate() {
        try {
            val isEnabled = scheduledUpdateManager.isScheduledUpdateEnabled()
            if (isEnabled) {
                AppLogger.d(TAG, "✅ 定时更新已启用，初始化 WorkManager 任务")
                scheduledUpdateManager.enableScheduledUpdate()
            } else {
                AppLogger.d(TAG, "定时更新未启用")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "初始化定时更新失败", e)
        }
    }
    
    private suspend fun checkAndTriggerAutoUpdate() {
        try {
            val decision = updateOrchestrator.checkAndTriggerAutoUpdate()
            if (decision.shouldUpdate) {
                AppLogger.d(TAG, "✅ 自动更新已触发: ${decision.reason}")
            } else {
                AppLogger.d(TAG, "跳过自动更新: ${decision.reason}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查自动更新失败", e)
        }
    }
    
    // ==================== 公开方法 ====================
    
    /**
     * 重试初始化（用于错误恢复）
     */
    fun retry() {
        AppLogger.d(TAG, "用户请求重试初始化")
        _uiState.value = UiState.Loading
        initializeApp()
    }
    
    /**
     * 更新引导完成状态
     */
    fun markOnboardingComplete(disclaimerAccepted: Boolean = true) {
        viewModelScope.launch {
            try {
                // 使用 SettingsRepository 保存引导完成状态
                settingsRepository.setDisclaimerAccepted(disclaimerAccepted)
                settingsRepository.setOnboardingCompleted(true)
                AppLogger.d(TAG, "✅ 引导完成标记已保存")

                // 引导完成后切换到主界面
                _uiState.value = UiState.Success

            } catch (e: Exception) {
                AppLogger.e(TAG, "保存引导完成状态失败", e)
            }
        }
    }
}
