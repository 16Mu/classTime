package com.wind.ggbond.classtime.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val ds = DataStoreManager.getSettingsDataStore(context)
    private val K = DataStoreManager.SettingsKeys

    // ==================== 通用读写方法 ====================

    private suspend fun <T> get(key: androidx.datastore.preferences.core.Preferences.Key<T>, default: T): T =
        ds.data.first()[key] ?: default

    private suspend fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        ds.edit { it[key] = value }
    }

    private fun <T> observe(key: androidx.datastore.preferences.core.Preferences.Key<T>, default: T): Flow<T> =
        ds.data.map { it[key] ?: default }

    private suspend fun getBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, default: Boolean = false): Boolean = get(key, default)
    private suspend fun setBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) = set(key, value)
    private fun observeBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, default: Boolean = false): Flow<Boolean> = observe(key, default)
    private suspend fun getInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, default: Int): Int = get(key, default)
    private suspend fun setInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) = set(key, value)
    private fun observeInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, default: Int): Flow<Int> = observe(key, default)
    private suspend fun getStr(key: androidx.datastore.preferences.core.Preferences.Key<String>, default: String = ""): String = get(key, default)
    private suspend fun setStr(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String?) {
        ds.edit { if (value != null) it[key] = value else it.remove(key) }
    }
    private suspend fun getLong(key: androidx.datastore.preferences.core.Preferences.Key<Long>, default: Long = 0L): Long = get(key, default)
    private suspend fun setLong(key: androidx.datastore.preferences.core.Preferences.Key<Long>, value: Long) = set(key, value)

    // ==================== 引导相关 ====================

    override suspend fun isDisclaimerAccepted(): Boolean = getBool(K.DISCLAIMER_ACCEPTED_KEY)
    override suspend fun setDisclaimerAccepted(accepted: Boolean) = setBool(K.DISCLAIMER_ACCEPTED_KEY, accepted)
    override fun observeDisclaimerAccepted(): Flow<Boolean> = observeBool(K.DISCLAIMER_ACCEPTED_KEY)

    override suspend fun isOnboardingCompleted(): Boolean = getBool(K.ONBOARDING_COMPLETED_KEY)
    override suspend fun setOnboardingCompleted(completed: Boolean) = setBool(K.ONBOARDING_COMPLETED_KEY, completed)
    override fun observeOnboardingCompleted(): Flow<Boolean> = observeBool(K.ONBOARDING_COMPLETED_KEY)

    // ==================== 提醒相关 ====================

    override suspend fun isReminderEnabled(): Boolean = getBool(K.REMINDER_ENABLED_KEY)
    override suspend fun setReminderEnabled(enabled: Boolean) = setBool(K.REMINDER_ENABLED_KEY, enabled)
    override fun observeReminderEnabled(): Flow<Boolean> = observeBool(K.REMINDER_ENABLED_KEY)

    override suspend fun getDefaultReminderMinutes(): Int = getInt(K.DEFAULT_REMINDER_MINUTES_KEY, 10)
    override suspend fun setDefaultReminderMinutes(minutes: Int) = setInt(K.DEFAULT_REMINDER_MINUTES_KEY, minutes)
    override fun observeDefaultReminderMinutes(): Flow<Int> = observeInt(K.DEFAULT_REMINDER_MINUTES_KEY, 10)

    override suspend fun isHeadsUpNotificationEnabled(): Boolean = getBool(K.HEADS_UP_NOTIFICATION_ENABLED_KEY, true)
    override suspend fun setHeadsUpNotificationEnabled(enabled: Boolean) = setBool(K.HEADS_UP_NOTIFICATION_ENABLED_KEY, enabled)
    override fun observeHeadsUpNotificationEnabled(): Flow<Boolean> = observeBool(K.HEADS_UP_NOTIFICATION_ENABLED_KEY, true)

    // ==================== 显示模式 ====================

    override suspend fun isCompactModeEnabled(): Boolean = getBool(K.COMPACT_MODE_ENABLED_KEY)
    override suspend fun setCompactModeEnabled(enabled: Boolean) = setBool(K.COMPACT_MODE_ENABLED_KEY, enabled)
    override fun observeCompactModeEnabled(): Flow<Boolean> = observeBool(K.COMPACT_MODE_ENABLED_KEY)

    override suspend fun isShowWeekendEnabled(): Boolean = getBool(K.SHOW_WEEKEND_KEY, true)
    override suspend fun setShowWeekendEnabled(enabled: Boolean) = setBool(K.SHOW_WEEKEND_KEY, enabled)
    override fun observeShowWeekendEnabled(): Flow<Boolean> = observeBool(K.SHOW_WEEKEND_KEY, true)

    override suspend fun isGlassEffectEnabled(): Boolean = getBool(K.GLASS_EFFECT_ENABLED_KEY, K.DEFAULT_GLASS_EFFECT_ENABLED)
    override suspend fun setGlassEffectEnabled(enabled: Boolean) = setBool(K.GLASS_EFFECT_ENABLED_KEY, enabled)
    override fun observeGlassEffectEnabled(): Flow<Boolean> = observeBool(K.GLASS_EFFECT_ENABLED_KEY, K.DEFAULT_GLASS_EFFECT_ENABLED)

    // ==================== 自动更新相关 ====================

    override suspend fun isAutoUpdateEnabled(): Boolean = getBool(K.AUTO_UPDATE_ENABLED_KEY, K.DEFAULT_AUTO_UPDATE_ENABLED)
    override suspend fun setAutoUpdateEnabled(enabled: Boolean) = setBool(K.AUTO_UPDATE_ENABLED_KEY, enabled)

    override suspend fun isIntervalUpdateEnabled(): Boolean = getBool(K.INTERVAL_UPDATE_ENABLED_KEY, K.DEFAULT_INTERVAL_UPDATE_ENABLED)
    override suspend fun setIntervalUpdateEnabled(enabled: Boolean) = setBool(K.INTERVAL_UPDATE_ENABLED_KEY, enabled)

    override suspend fun getAutoUpdateIntervalHours(): Int = getInt(K.AUTO_UPDATE_INTERVAL_HOURS_KEY, K.DEFAULT_AUTO_UPDATE_INTERVAL_HOURS)
    override suspend fun setAutoUpdateIntervalHours(hours: Int) = setInt(K.AUTO_UPDATE_INTERVAL_HOURS_KEY, hours)

    override suspend fun isScheduledUpdateEnabled(): Boolean = getBool(K.SCHEDULED_UPDATE_ENABLED_KEY, K.DEFAULT_SCHEDULED_UPDATE_ENABLED)
    override suspend fun setScheduledUpdateEnabled(enabled: Boolean) = setBool(K.SCHEDULED_UPDATE_ENABLED_KEY, enabled)

    override suspend fun getScheduledUpdateTime(): String = getStr(K.SCHEDULED_UPDATE_TIME_KEY) ?: K.DEFAULT_SCHEDULED_UPDATE_TIME
    override suspend fun setScheduledUpdateTime(time: String) = setStr(K.SCHEDULED_UPDATE_TIME_KEY, time)

    override suspend fun getLastAutoUpdateTime(): Long = getLong(K.LAST_AUTO_UPDATE_TIME_KEY)
    override suspend fun setLastAutoUpdateTime(time: Long) = setLong(K.LAST_AUTO_UPDATE_TIME_KEY, time)

    // ==================== 背景主题相关 ====================

    override fun observeSeedColor(): Flow<Int> = observeInt(K.SEED_COLOR_KEY, BackgroundThemeManager.Companion.DEFAULT_SEED_COLOR)
    override suspend fun getSeedColor(): Int = getInt(K.SEED_COLOR_KEY, BackgroundThemeManager.Companion.DEFAULT_SEED_COLOR)
    override suspend fun setSeedColor(color: Int) = setInt(K.SEED_COLOR_KEY, color)

    override fun observeUseDynamicTheme(): Flow<Boolean> = observeBool(K.USE_DYNAMIC_THEME_KEY, BackgroundThemeManager.Companion.DEFAULT_USE_DYNAMIC_THEME)
    override suspend fun isUseDynamicTheme(): Boolean = getBool(K.USE_DYNAMIC_THEME_KEY, BackgroundThemeManager.Companion.DEFAULT_USE_DYNAMIC_THEME)
    override suspend fun setUseDynamicTheme(enabled: Boolean) = setBool(K.USE_DYNAMIC_THEME_KEY, enabled)

    override fun observeActiveBackgroundIndex(): Flow<Int> = observeInt(K.ACTIVE_BACKGROUND_INDEX_KEY, K.DEFAULT_ACTIVE_BACKGROUND_INDEX)
    override suspend fun getActiveBackgroundIndex(): Int = getInt(K.ACTIVE_BACKGROUND_INDEX_KEY, K.DEFAULT_ACTIVE_BACKGROUND_INDEX)
    override suspend fun setActiveBackgroundIndex(index: Int) = setInt(K.ACTIVE_BACKGROUND_INDEX_KEY, index)

    override suspend fun getBackgroundsJson(): String? = getStr(K.BACKGROUNDS_JSON_KEY)
    override suspend fun setBackgroundsJson(json: String?) = setStr(K.BACKGROUNDS_JSON_KEY, json)

    override suspend fun getBlurRadius(): Int = getInt(K.BLUR_RADIUS_KEY, K.DEFAULT_BLUR_RADIUS)
    override suspend fun setBlurRadius(radius: Int) = setInt(K.BLUR_RADIUS_KEY, radius)

    override suspend fun getDimAmount(): Int = getInt(K.DIM_AMOUNT_KEY, K.DEFAULT_DIM_AMOUNT)
    override suspend fun setDimAmount(amount: Int) = setInt(K.DIM_AMOUNT_KEY, amount)

    override suspend fun getBackgroundType(): String = getStr(K.BACKGROUND_TYPE_KEY) ?: K.DEFAULT_BACKGROUND_TYPE
    override suspend fun setBackgroundType(type: String) = setStr(K.BACKGROUND_TYPE_KEY, type)

    override suspend fun getCustomBackgroundUri(): String? = getStr(K.CUSTOM_BACKGROUND_URI_KEY)
    override suspend fun setCustomBackgroundUri(uri: String?) = setStr(K.CUSTOM_BACKGROUND_URI_KEY, uri)

    override suspend fun getLastUpdateCheckTime(): Long = getLong(K.LAST_UPDATE_CHECK_TIME_KEY)
    override suspend fun setLastUpdateCheckTime(time: Long) = setLong(K.LAST_UPDATE_CHECK_TIME_KEY, time)

    // ==================== 其他设置 ====================

    override suspend fun getRecentSchools(): String? = getStr(K.RECENT_SCHOOLS_KEY)
    override suspend fun setRecentSchools(schools: String?) = setStr(K.RECENT_SCHOOLS_KEY, schools)

    // ==================== 莫奈课程颜色 ====================

    override suspend fun isMonetCourseColorsEnabled(): Boolean = getBool(K.MONET_COURSE_COLORS_ENABLED_KEY, K.DEFAULT_MONET_COURSE_COLORS_ENABLED)
    override suspend fun setMonetCourseColorsEnabled(enabled: Boolean) = setBool(K.MONET_COURSE_COLORS_ENABLED_KEY, enabled)
    override fun observeMonetCourseColorsEnabled(): Flow<Boolean> = observeBool(K.MONET_COURSE_COLORS_ENABLED_KEY, K.DEFAULT_MONET_COURSE_COLORS_ENABLED)

    override suspend fun getCourseColorSaturation(): Int = getInt(K.COURSE_COLOR_SATURATION_KEY, K.DEFAULT_COURSE_COLOR_SATURATION)
    override suspend fun setCourseColorSaturation(saturation: Int) = setInt(K.COURSE_COLOR_SATURATION_KEY, saturation)
    override fun observeCourseColorSaturation(): Flow<Int> = observeInt(K.COURSE_COLOR_SATURATION_KEY, K.DEFAULT_COURSE_COLOR_SATURATION)

    // ==================== 桌面模式 ====================

    override suspend fun isDesktopModeEnabled(): Boolean = getBool(K.DESKTOP_MODE_ENABLED_KEY, K.DEFAULT_DESKTOP_MODE_ENABLED)
    override suspend fun setDesktopModeEnabled(enabled: Boolean) = setBool(K.DESKTOP_MODE_ENABLED_KEY, enabled)
    override fun observeDesktopModeEnabled(): Flow<Boolean> = observeBool(K.DESKTOP_MODE_ENABLED_KEY, K.DEFAULT_DESKTOP_MODE_ENABLED)
}
