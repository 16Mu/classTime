package com.wind.ggbond.classtime.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore 管理类 - 统一管理所有 DataStore 实例
 * 
 * ✅ 解决多个 DataStore 实例冲突问题
 * 使用委托属性确保每个 DataStore 文件只有一个实例
 */

// ✅ 课程时间配置 DataStore（单例）
private val Context.classTimeDataStore by preferencesDataStore(name = "class_time_settings")

// ✅ 应用设置 DataStore（单例）
private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * DataStore 管理器 - 提供统一的访问接口
 */
object DataStoreManager {
    
    /**
     * 获取课程时间配置 DataStore
     */
    fun getClassTimeDataStore(context: Context) = context.classTimeDataStore
    
    /**
     * 获取应用设置 DataStore
     */
    fun getSettingsDataStore(context: Context) = context.settingsDataStore
    
    // ==================== 课程时间配置相关的 Key ====================
    object ClassTimeKeys {
        val BREAK_DURATION_KEY = intPreferencesKey("break_duration_minutes")
        val CLASS_DURATION_KEY = intPreferencesKey("class_duration_minutes")
        val MORNING_SECTIONS_KEY = intPreferencesKey("morning_sections")
        val AFTERNOON_SECTIONS_KEY = intPreferencesKey("afternoon_sections")
        
        // 默认值
        const val DEFAULT_BREAK_DURATION = 10 // 默认课间10分钟
        const val DEFAULT_CLASS_DURATION = 40 // 默认课程40分钟
        const val DEFAULT_MORNING_SECTIONS = 4 // 默认上午4节
        const val DEFAULT_AFTERNOON_SECTIONS = 8 // 默认下午8节
    }
    
    // ==================== 应用设置相关的 Key ====================
    object SettingsKeys {
        val REMINDER_ENABLED_KEY = booleanPreferencesKey("reminder_enabled")
        val DEFAULT_REMINDER_MINUTES_KEY = intPreferencesKey("default_reminder_minutes")
        val COMPACT_MODE_ENABLED_KEY = booleanPreferencesKey("compact_mode_enabled")
        val DISCLAIMER_ACCEPTED_KEY = booleanPreferencesKey("disclaimer_accepted")
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")  // 功能引导是否完成
        val HEADS_UP_NOTIFICATION_ENABLED_KEY = booleanPreferencesKey("heads_up_notification_enabled")
        val SHOW_WEEKEND_KEY = booleanPreferencesKey("show_weekend")
        
        // 课表自动更新相关配置
        val AUTO_UPDATE_ENABLED_KEY = booleanPreferencesKey("auto_update_enabled")
        
        // 间隔更新配置
        val INTERVAL_UPDATE_ENABLED_KEY = booleanPreferencesKey("interval_update_enabled")
        val AUTO_UPDATE_INTERVAL_HOURS_KEY = intPreferencesKey("auto_update_interval_hours")
        
        // 定时更新配置
        val SCHEDULED_UPDATE_ENABLED_KEY = booleanPreferencesKey("scheduled_update_enabled")
        val SCHEDULED_UPDATE_TIME_KEY = stringPreferencesKey("scheduled_update_time")  // 格式: "HH:mm"
        
        // 最后更新时间（用于去重）
        val LAST_AUTO_UPDATE_TIME_KEY = longPreferencesKey("last_auto_update_time") // 上次自动更新时间（毫秒时间戳）
        
        // 底部导航栏高斯模糊开关
        val BOTTOM_BAR_BLUR_ENABLED_KEY = booleanPreferencesKey("bottom_bar_blur_enabled")
        
        // 最近使用的学校（JSON格式：["schoolId1", "schoolId2", ...]，最多保存5个）
        val RECENT_SCHOOLS_KEY = stringPreferencesKey("recent_schools")
        
        // 默认值
        const val DEFAULT_AUTO_UPDATE_ENABLED = true  // 默认开启自动更新
        const val DEFAULT_INTERVAL_UPDATE_ENABLED = true  // 默认开启间隔更新
        const val DEFAULT_AUTO_UPDATE_INTERVAL_HOURS = 6  // 默认6小时更新一次
        const val DEFAULT_SCHEDULED_UPDATE_ENABLED = false  // 默认关闭定时更新
        const val DEFAULT_SCHEDULED_UPDATE_TIME = "07:00"  // 默认早上7点
        const val MAX_RECENT_SCHOOLS = 5  // 最多保存5个最近使用的学校
        const val DEFAULT_BOTTOM_BAR_BLUR_ENABLED = true  // 默认开启底部栏高斯模糊
        const val UPDATE_DEDUP_INTERVAL_MS = 5 * 60 * 1000  // 防重复间隔：5分钟
    }
}



