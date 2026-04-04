package com.wind.ggbond.classtime.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val settingsDataStore = DataStoreManager.getSettingsDataStore(context)

    // ==================== 引导相关 ====================

    override suspend fun isDisclaimerAccepted(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.DISCLAIMER_ACCEPTED_KEY] ?: false
    }

    override suspend fun setDisclaimerAccepted(accepted: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.DISCLAIMER_ACCEPTED_KEY] = accepted
        }
    }

    override fun observeDisclaimerAccepted(): Flow<Boolean> {
        return settingsDataStore.data.map { it[DataStoreManager.SettingsKeys.DISCLAIMER_ACCEPTED_KEY] ?: false }
    }

    override suspend fun isOnboardingCompleted(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.ONBOARDING_COMPLETED_KEY] ?: false
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    override fun observeOnboardingCompleted(): Flow<Boolean> {
        return settingsDataStore.data.map { it[DataStoreManager.SettingsKeys.ONBOARDING_COMPLETED_KEY] ?: false }
    }

    // ==================== 提醒相关 ====================

    override suspend fun isReminderEnabled(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.REMINDER_ENABLED_KEY] ?: false
    }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.REMINDER_ENABLED_KEY] = enabled
        }
    }

    override fun observeReminderEnabled(): Flow<Boolean> {
        return settingsDataStore.data.map { it[DataStoreManager.SettingsKeys.REMINDER_ENABLED_KEY] ?: false }
    }

    override suspend fun getDefaultReminderMinutes(): Int {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.DEFAULT_REMINDER_MINUTES_KEY] ?: 10
    }

    override suspend fun setDefaultReminderMinutes(minutes: Int) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.DEFAULT_REMINDER_MINUTES_KEY] = minutes
        }
    }

    override fun observeDefaultReminderMinutes(): Flow<Int> {
        return settingsDataStore.data.map { it[DataStoreManager.SettingsKeys.DEFAULT_REMINDER_MINUTES_KEY] ?: 10 }
    }

    override suspend fun isHeadsUpNotificationEnabled(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.HEADS_UP_NOTIFICATION_ENABLED_KEY] ?: true
    }

    override suspend fun setHeadsUpNotificationEnabled(enabled: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.HEADS_UP_NOTIFICATION_ENABLED_KEY] = enabled
        }
    }

    override fun observeHeadsUpNotificationEnabled(): Flow<Boolean> {
        return settingsDataStore.data.map { it[DataStoreManager.SettingsKeys.HEADS_UP_NOTIFICATION_ENABLED_KEY] ?: true }
    }

    // ==================== 显示模式 ====================

    override suspend fun isCompactModeEnabled(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.COMPACT_MODE_ENABLED_KEY] ?: false
    }

    override suspend fun setCompactModeEnabled(enabled: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.COMPACT_MODE_ENABLED_KEY] = enabled
        }
    }

    override fun observeCompactModeEnabled(): Flow<Boolean> {
        return settingsDataStore.data.map { it[DataStoreManager.SettingsKeys.COMPACT_MODE_ENABLED_KEY] ?: false }
    }

    override suspend fun isShowWeekendEnabled(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.SHOW_WEEKEND_KEY] ?: true
    }

    override suspend fun setShowWeekendEnabled(enabled: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.SHOW_WEEKEND_KEY] = enabled
        }
    }

    override fun observeShowWeekendEnabled(): Flow<Boolean> {
        return settingsDataStore.data.map { it[DataStoreManager.SettingsKeys.SHOW_WEEKEND_KEY] ?: true }
    }

    override suspend fun isBottomBarBlurEnabled(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.BOTTOM_BAR_BLUR_ENABLED_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_BOTTOM_BAR_BLUR_ENABLED
    }

    override suspend fun setBottomBarBlurEnabled(enabled: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.BOTTOM_BAR_BLUR_ENABLED_KEY] = enabled
        }
    }

    override fun observeBottomBarBlurEnabled(): Flow<Boolean> {
        return settingsDataStore.data.map {
            it[DataStoreManager.SettingsKeys.BOTTOM_BAR_BLUR_ENABLED_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_BOTTOM_BAR_BLUR_ENABLED
        }
    }

    // ==================== 自动更新相关 ====================

    override suspend fun isAutoUpdateEnabled(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.AUTO_UPDATE_ENABLED_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_ENABLED
    }

    override suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.AUTO_UPDATE_ENABLED_KEY] = enabled
        }
    }

    override suspend fun isIntervalUpdateEnabled(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.INTERVAL_UPDATE_ENABLED_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_INTERVAL_UPDATE_ENABLED
    }

    override suspend fun setIntervalUpdateEnabled(enabled: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.INTERVAL_UPDATE_ENABLED_KEY] = enabled
        }
    }

    override suspend fun getAutoUpdateIntervalHours(): Int {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.AUTO_UPDATE_INTERVAL_HOURS_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_AUTO_UPDATE_INTERVAL_HOURS
    }

    override suspend fun setAutoUpdateIntervalHours(hours: Int) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.AUTO_UPDATE_INTERVAL_HOURS_KEY] = hours
        }
    }

    override suspend fun isScheduledUpdateEnabled(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_ENABLED_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_SCHEDULED_UPDATE_ENABLED
    }

    override suspend fun setScheduledUpdateEnabled(enabled: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_ENABLED_KEY] = enabled
        }
    }

    override suspend fun getScheduledUpdateTime(): String {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_TIME_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_SCHEDULED_UPDATE_TIME
    }

    override suspend fun setScheduledUpdateTime(time: String) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.SCHEDULED_UPDATE_TIME_KEY] = time
        }
    }

    override suspend fun getLastAutoUpdateTime(): Long {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.LAST_AUTO_UPDATE_TIME_KEY] ?: 0L
    }

    override suspend fun setLastAutoUpdateTime(time: Long) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.LAST_AUTO_UPDATE_TIME_KEY] = time
        }
    }

    // ==================== 背景主题相关 ====================

    override fun observeSeedColor(): Flow<Int> {
        return settingsDataStore.data.map {
            it[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] ?: BackgroundThemeManager.Companion.DEFAULT_SEED_COLOR
        }
    }

    override suspend fun getSeedColor(): Int {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.SEED_COLOR_KEY]
            ?: BackgroundThemeManager.Companion.DEFAULT_SEED_COLOR
    }

    override suspend fun setSeedColor(color: Int) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] = color
        }
    }

    override fun observeUseDynamicTheme(): Flow<Boolean> {
        return settingsDataStore.data.map {
            it[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY]
                ?: BackgroundThemeManager.Companion.DEFAULT_USE_DYNAMIC_THEME
        }
    }

    override suspend fun isUseDynamicTheme(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY]
            ?: BackgroundThemeManager.Companion.DEFAULT_USE_DYNAMIC_THEME
    }

    override suspend fun setUseDynamicTheme(enabled: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] = enabled
        }
    }

    override fun observeActiveBackgroundIndex(): Flow<Int> {
        return settingsDataStore.data.map {
            it[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
        }
    }

    override suspend fun getActiveBackgroundIndex(): Int {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
    }

    override suspend fun setActiveBackgroundIndex(index: Int) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] = index
        }
    }

    override suspend fun getBackgroundsJson(): String? {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
    }

    override suspend fun setBackgroundsJson(json: String?) {
        settingsDataStore.edit { prefs ->
            if (json != null) {
                prefs[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY] = json
            } else {
                prefs.remove(DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY)
            }
        }
    }

    override suspend fun getBlurRadius(): Int {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.BLUR_RADIUS_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_BLUR_RADIUS
    }

    override suspend fun setBlurRadius(radius: Int) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.BLUR_RADIUS_KEY] = radius
        }
    }

    override suspend fun getDimAmount(): Int {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.DIM_AMOUNT_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_DIM_AMOUNT
    }

    override suspend fun setDimAmount(amount: Int) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.DIM_AMOUNT_KEY] = amount
        }
    }

    override suspend fun getBackgroundType(): String {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.BACKGROUND_TYPE_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_BACKGROUND_TYPE
    }

    override suspend fun setBackgroundType(type: String) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.BACKGROUND_TYPE_KEY] = type
        }
    }

    override suspend fun getCustomBackgroundUri(): String? {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.CUSTOM_BACKGROUND_URI_KEY]
    }

    override suspend fun setCustomBackgroundUri(uri: String?) {
        settingsDataStore.edit { prefs ->
            if (uri != null) {
                prefs[DataStoreManager.SettingsKeys.CUSTOM_BACKGROUND_URI_KEY] = uri
            } else {
                prefs.remove(DataStoreManager.SettingsKeys.CUSTOM_BACKGROUND_URI_KEY)
            }
        }
    }

    override suspend fun getLastUpdateCheckTime(): Long {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.LAST_UPDATE_CHECK_TIME_KEY] ?: 0L
    }

    override suspend fun setLastUpdateCheckTime(time: Long) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.LAST_UPDATE_CHECK_TIME_KEY] = time
        }
    }

    // ==================== 其他设置 ====================

    override suspend fun getRecentSchools(): String? {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.RECENT_SCHOOLS_KEY]
    }

    override suspend fun setRecentSchools(schools: String?) {
        settingsDataStore.edit { prefs ->
            if (schools != null) {
                prefs[DataStoreManager.SettingsKeys.RECENT_SCHOOLS_KEY] = schools
            } else {
                prefs.remove(DataStoreManager.SettingsKeys.RECENT_SCHOOLS_KEY)
            }
        }
    }

    // ==================== 莫奈课程颜色 ====================

    override suspend fun isMonetCourseColorsEnabled(): Boolean {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.MONET_COURSE_COLORS_ENABLED_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_MONET_COURSE_COLORS_ENABLED
    }

    override suspend fun setMonetCourseColorsEnabled(enabled: Boolean) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.MONET_COURSE_COLORS_ENABLED_KEY] = enabled
        }
    }

    override fun observeMonetCourseColorsEnabled(): Flow<Boolean> {
        return settingsDataStore.data.map {
            it[DataStoreManager.SettingsKeys.MONET_COURSE_COLORS_ENABLED_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_MONET_COURSE_COLORS_ENABLED
        }
    }

    override suspend fun getCourseColorSaturation(): Int {
        return settingsDataStore.data.first()[DataStoreManager.SettingsKeys.COURSE_COLOR_SATURATION_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_COURSE_COLOR_SATURATION
    }

    override suspend fun setCourseColorSaturation(saturation: Int) {
        settingsDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.COURSE_COLOR_SATURATION_KEY] = saturation
        }
    }

    override fun observeCourseColorSaturation(): Flow<Int> {
        return settingsDataStore.data.map {
            it[DataStoreManager.SettingsKeys.COURSE_COLOR_SATURATION_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_COURSE_COLOR_SATURATION
        }
    }
}
