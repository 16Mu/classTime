package com.wind.ggbond.classtime.domain.usecase

import android.content.Context
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.local.entity.UpdateResult
import com.wind.ggbond.classtime.data.repository.AutoUpdateLogRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.UnifiedScheduleUpdateService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

@Singleton
class AutoUpdateUseCase @Inject constructor(
    private val unifiedScheduleUpdateService: UnifiedScheduleUpdateService,
    private val autoUpdateLogRepository: AutoUpdateLogRepository,
    private val scheduleRepository: ScheduleRepository,
    @ApplicationContext private val context: Context,
    private val autoLoginManager: com.wind.ggbond.classtime.util.AutoLoginManager
) {
    
    companion object {
        private const val TAG = "AutoUpdateUseCase"
    }
    
    suspend fun checkAndPerformAutoUpdate(): Pair<Boolean, String> {
        val startTime = System.currentTimeMillis()
        try {
            AppLogger.d(TAG, "🔍 检查是否需要自动更新")
            
            val skipReason = getSkipReason()
            if (skipReason != null) {
                AppLogger.d(TAG, "无需更新: $skipReason")
                
                val durationMs = System.currentTimeMillis() - startTime
                autoUpdateLogRepository.logUpdate(
                    triggerEvent = "进入软件",
                    result = UpdateResult.SKIPPED,
                    successMessage = null,
                    failureReason = skipReason,
                    scheduleId = scheduleRepository.getCurrentSchedule()?.id,
                    durationMs = durationMs
                )
                
                return Pair(false, skipReason)
            }
            
            AppLogger.d(TAG, "🚀 开始自动更新（Cookie模式）")
            
            val result = unifiedScheduleUpdateService.performSimpleUpdate()
            
            val durationMs = System.currentTimeMillis() - startTime
            if (result.first) {
                AppLogger.d(TAG, "✅ 更新成功: ${result.second}")
                autoUpdateLogRepository.logUpdate(
                    triggerEvent = "进入软件",
                    result = UpdateResult.SUCCESS,
                    successMessage = result.second,
                    failureReason = null,
                    scheduleId = scheduleRepository.getCurrentSchedule()?.id,
                    durationMs = durationMs
                )
            } else {
                AppLogger.d(TAG, "❌ 更新失败: ${result.second}")
                autoUpdateLogRepository.logUpdate(
                    triggerEvent = "进入软件",
                    result = UpdateResult.FAILED,
                    successMessage = null,
                    failureReason = result.second,
                    scheduleId = scheduleRepository.getCurrentSchedule()?.id,
                    durationMs = durationMs
                )
            }
            
            return result
        } catch (e: Exception) {
            AppLogger.e(TAG, "自动更新异常", e)
            
            val durationMs = System.currentTimeMillis() - startTime
            autoUpdateLogRepository.logUpdate(
                triggerEvent = "进入软件",
                result = UpdateResult.FAILED,
                successMessage = null,
                failureReason = e.message ?: "未知错误",
                scheduleId = scheduleRepository.getCurrentSchedule()?.id,
                durationMs = durationMs
            )
            
            return Pair(false, e.message ?: "未知错误")
        }
    }
    
    private suspend fun getSkipReason(): String? {
        val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
        val preferences = settingsDataStore.data.first()
        val autoUpdateEnabled = preferences[DataStoreManager.SettingsKeys.AUTO_UPDATE_ENABLED_KEY] 
            ?: DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_ENABLED
        
        if (!autoUpdateEnabled) {
            return "用户未开启自动更新功能"
        }
        
        val currentSchedule = scheduleRepository.getCurrentSchedule()
        if (currentSchedule == null) {
            return "尚未导入课表，无法自动更新"
        }
        
        val schoolId = currentSchedule.schoolName ?: ""
        if (schoolId.isEmpty()) {
            return "课表缺少学校配置信息"
        }
        
        if (!unifiedScheduleUpdateService.shouldUpdate()) {
            if (autoLoginManager.isAutoLoginEnabled() && autoLoginManager.hasCredentials()) {
                AppLogger.d(TAG, "Cookie不可用但已配置自动登录凭据，允许尝试更新")
            } else {
                val lastUpdateTime = currentSchedule.updatedAt
                val now = System.currentTimeMillis()
                val hoursSinceUpdate = (now - lastUpdateTime) / (1000 * 60 * 60)

                if (hoursSinceUpdate < 6) {
                    return "距上次更新仅${hoursSinceUpdate}小时，最小间隔为6小时"
                } else {
                    return "无有效登录凭证(Cookie)，请先手动登录导入课表"
                }
            }
        }
        
        return null
    }
}