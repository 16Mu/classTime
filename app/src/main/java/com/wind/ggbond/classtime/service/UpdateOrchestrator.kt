package com.wind.ggbond.classtime.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.contract.IUpdateManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.wind.ggbond.classtime.util.AppLogger
import java.util.concurrent.atomic.AtomicLong

@Singleton
class UpdateOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val dataStoreManager: DataStoreManager
) : IUpdateManager {

    companion object {
        private const val TAG = "UpdateOrchestrator"
        private const val MIN_CHECK_INTERVAL_MS = 60_000L
    }

    private val lastCheckTime = AtomicLong(0)

    data class UpdateConfig(
        val enabled: Boolean,
        val intervalEnabled: Boolean,
        val intervalHours: Int,
        val lastUpdateTime: Long,
        val dedupIntervalMs: Long
    )

    suspend override fun checkAndTriggerAutoUpdate(): IUpdateManager.UpdateDecision {
        if (!shouldPerformCheck()) {
            return IUpdateManager.UpdateDecision(false, "检查频率限制")
        }

        val config = loadUpdateConfig()

        if (!config.enabled) {
            AppLogger.d(TAG, "自动更新未启用")
            return IUpdateManager.UpdateDecision(false, "自动更新未启用")
        }

        val decision = evaluateUpdateNecessity(config)

        if (decision.shouldUpdate) {
            executeUpdate(config)
        }

        return decision
    }

    private fun shouldPerformCheck(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastCheckTime.get()
        if (now - last < MIN_CHECK_INTERVAL_MS) {
            AppLogger.d(TAG, "跳过检查（距离上次不足1分钟）")
            return false
        }
        return lastCheckTime.compareAndSet(last, now)
    }

    private suspend fun loadUpdateConfig(): UpdateConfig {
        val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
        val preferences = settingsDataStore.data.first()

        val enabled = preferences[DataStoreManager.SettingsKeys.AUTO_UPDATE_ENABLED_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_ENABLED

        val intervalEnabled = preferences[DataStoreManager.SettingsKeys.INTERVAL_UPDATE_ENABLED_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_INTERVAL_UPDATE_ENABLED

        val intervalHours = preferences[DataStoreManager.SettingsKeys.AUTO_UPDATE_INTERVAL_HOURS_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_INTERVAL_HOURS

        val lastUpdateTime = preferences[DataStoreManager.SettingsKeys.LAST_AUTO_UPDATE_TIME_KEY] ?: 0L

        val dedupIntervalMs = DataStoreManager.SettingsKeys.UPDATE_DEDUP_INTERVAL_MS.toLong()

        return UpdateConfig(
            enabled = enabled,
            intervalEnabled = intervalEnabled,
            intervalHours = intervalHours,
            lastUpdateTime = lastUpdateTime,
            dedupIntervalMs = dedupIntervalMs
        )
    }

    private suspend fun evaluateUpdateNecessity(config: UpdateConfig): IUpdateManager.UpdateDecision {
        val now = System.currentTimeMillis()
        val timeSinceLastUpdate = now - config.lastUpdateTime

        AppLogger.d(TAG, "自动更新检查:")
        AppLogger.d(TAG, "  启用状态: ${config.enabled}")
        AppLogger.d(TAG, "  间隔更新启用: ${config.intervalEnabled}")
        AppLogger.d(TAG, "  更新间隔: ${config.intervalHours}小时")
        AppLogger.d(TAG, "  距离上次: ${timeSinceLastUpdate / (60 * 1000)}分钟")

        if (timeSinceLastUpdate < config.dedupIntervalMs) {
            return IUpdateManager.UpdateDecision(false, "防重复：距离上次更新不足5分钟")
        }

        if (!config.intervalEnabled) {
            return IUpdateManager.UpdateDecision(false, "间隔更新未启用")
        }

        val intervalMillis = config.intervalHours * 60 * 60 * 1000L
        if (timeSinceLastUpdate < intervalMillis) {
            return IUpdateManager.UpdateDecision(false, "未达到更新间隔")
        }

        val currentSchedule = scheduleRepository.getCurrentSchedule()
        if (currentSchedule == null) {
            return IUpdateManager.UpdateDecision(false, "未导入课表")
        }

        val schoolId = currentSchedule.schoolName
        if (schoolId.isNullOrEmpty()) {
            return IUpdateManager.UpdateDecision(false, "课表缺少学校配置")
        }

        return IUpdateManager.UpdateDecision(true, "满足所有更新条件")
    }

    private suspend fun executeUpdate(config: UpdateConfig) {
        AppLogger.d(TAG, "✅ 触发自动更新")

        val now = System.currentTimeMillis()
        val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.LAST_AUTO_UPDATE_TIME_KEY] = now
        }

        delay(500)

        com.wind.ggbond.classtime.ui.screen.update.FloatingUpdateActivity.start(context)
    }

    override suspend fun isUpdateEnabled(): Boolean {
        val settingsDataStore = DataStoreManager.getSettingsDataStore(context)
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.AUTO_UPDATE_ENABLED_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_ENABLED
    }
}
