package com.wind.ggbond.classtime.data.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    // ==================== 引导相关 ====================

    suspend fun isDisclaimerAccepted(): Boolean
    suspend fun setDisclaimerAccepted(accepted: Boolean)
    fun observeDisclaimerAccepted(): Flow<Boolean>

    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean)
    fun observeOnboardingCompleted(): Flow<Boolean>

    // ==================== 提醒相关 ====================

    suspend fun isReminderEnabled(): Boolean
    suspend fun setReminderEnabled(enabled: Boolean)
    fun observeReminderEnabled(): Flow<Boolean>

    suspend fun getDefaultReminderMinutes(): Int
    suspend fun setDefaultReminderMinutes(minutes: Int)
    fun observeDefaultReminderMinutes(): Flow<Int>

    suspend fun isHeadsUpNotificationEnabled(): Boolean
    suspend fun setHeadsUpNotificationEnabled(enabled: Boolean)
    fun observeHeadsUpNotificationEnabled(): Flow<Boolean>

    // ==================== 显示模式 ====================

    suspend fun isCompactModeEnabled(): Boolean
    suspend fun setCompactModeEnabled(enabled: Boolean)
    fun observeCompactModeEnabled(): Flow<Boolean>

    suspend fun isShowWeekendEnabled(): Boolean
    suspend fun setShowWeekendEnabled(enabled: Boolean)
    fun observeShowWeekendEnabled(): Flow<Boolean>

    suspend fun isGlassEffectEnabled(): Boolean
    suspend fun setGlassEffectEnabled(enabled: Boolean)
    fun observeGlassEffectEnabled(): Flow<Boolean>

    // ==================== 自动更新相关 ====================

    suspend fun isAutoUpdateEnabled(): Boolean
    suspend fun setAutoUpdateEnabled(enabled: Boolean)

    suspend fun isIntervalUpdateEnabled(): Boolean
    suspend fun setIntervalUpdateEnabled(enabled: Boolean)

    suspend fun getAutoUpdateIntervalHours(): Int
    suspend fun setAutoUpdateIntervalHours(hours: Int)

    suspend fun isScheduledUpdateEnabled(): Boolean
    suspend fun setScheduledUpdateEnabled(enabled: Boolean)

    suspend fun getScheduledUpdateTime(): String
    suspend fun setScheduledUpdateTime(time: String)

    suspend fun getLastAutoUpdateTime(): Long
    suspend fun setLastAutoUpdateTime(time: Long)

    // ==================== 背景主题相关 ====================

    fun observeSeedColor(): Flow<Int>
    suspend fun getSeedColor(): Int
    suspend fun setSeedColor(color: Int)

    fun observeUseDynamicTheme(): Flow<Boolean>
    suspend fun isUseDynamicTheme(): Boolean
    suspend fun setUseDynamicTheme(enabled: Boolean)

    fun observeActiveBackgroundIndex(): Flow<Int>
    suspend fun getActiveBackgroundIndex(): Int
    suspend fun setActiveBackgroundIndex(index: Int)

    suspend fun getBackgroundsJson(): String?
    suspend fun setBackgroundsJson(json: String?)

    suspend fun getBlurRadius(): Int
    suspend fun setBlurRadius(radius: Int)

    suspend fun getDimAmount(): Int
    suspend fun setDimAmount(amount: Int)

    suspend fun getBackgroundType(): String
    suspend fun setBackgroundType(type: String)

    suspend fun getCustomBackgroundUri(): String?
    suspend fun setCustomBackgroundUri(uri: String?)

    suspend fun getLastUpdateCheckTime(): Long
    suspend fun setLastUpdateCheckTime(time: Long)

    // ==================== 其他设置 ====================

    suspend fun getRecentSchools(): String?
    suspend fun setRecentSchools(schools: String?)

    // ==================== 莫奈课程颜色 ====================

    suspend fun isMonetCourseColorsEnabled(): Boolean
    suspend fun setMonetCourseColorsEnabled(enabled: Boolean)
    fun observeMonetCourseColorsEnabled(): Flow<Boolean>

    suspend fun getCourseColorSaturation(): Int
    suspend fun setCourseColorSaturation(saturation: Int)
    fun observeCourseColorSaturation(): Flow<Int>
}
