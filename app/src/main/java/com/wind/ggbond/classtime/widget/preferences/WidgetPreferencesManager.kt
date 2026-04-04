package com.wind.ggbond.classtime.widget.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wind.ggbond.classtime.widget.WidgetPinHelper.WidgetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "widget_preferences"
)

class WidgetPreferencesManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: WidgetPreferencesManager? = null

        fun getInstance(context: Context): WidgetPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WidgetPreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Preferences Keys
        val KEY_DEFAULT_WIDGET_TYPE = stringPreferencesKey("default_widget_type")
        val KEY_LAST_USED_WIDGET_TYPE = stringPreferencesKey("last_used_widget_type")
        val KEY_PREFERRED_WIDGET_SIZE = stringPreferencesKey("preferred_widget_size")
        val KEY_TUTORIAL_SHOWN_COUNT = stringPreferencesKey("tutorial_shown_count")
        val KEY_LAST_MANUFACTURER = stringPreferencesKey("last_manufacturer")
        val KEY_USER_FEEDBACK = stringPreferencesKey("user_feedback_on_add_method")
        val KEY_WIDGET_ADD_TIMESTAMP = stringPreferencesKey("last_widget_add_timestamp")
        val KEY_FAVORITE_WIDGET_TYPES = stringPreferencesKey("favorite_widget_types")
    }

    /**
     * 保存默认 Widget 类型
     */
    suspend fun saveDefaultWidgetType(widgetType: WidgetType) {
        context.widgetPreferences.edit { preferences ->
            preferences[KEY_DEFAULT_WIDGET_TYPE] = widgetType.name.lowercase()
        }
    }

    /**
     * 获取默认 Widget 类型
     */
    fun getDefaultWidgetType(): Flow<String> {
        return context.widgetPreferences.data.map { preferences ->
            preferences[KEY_DEFAULT_WIDGET_TYPE] ?: "next_class"
        }
    }

    /**
     * 获取默认 Widget 类型（同步版本）
     */
    suspend fun getDefaultWidgetTypeSync(): String {
        return getDefaultWidgetType().first()
    }

    /**
     * 记录最后使用的 Widget 类型（用于智能推荐）
     */
    suspend fun recordLastUsedWidgetType(widgetType: WidgetType) {
        context.widgetPreferences.edit { preferences ->
            preferences[KEY_LAST_USED_WIDGET_TYPE] = widgetType.name.lowercase()
            preferences[KEY_WIDGET_ADD_TIMESTAMP] = System.currentTimeMillis().toString()
        }
    }

    /**
     * 获取最后使用的 Widget 类型
     */
    fun getLastUsedWidgetType(): Flow<String> {
        return context.widgetPreferences.data.map { preferences ->
            preferences[KEY_LAST_USED_WIDGET_TYPE] ?: "next_class"
        }
    }

    /**
     * 保存偏好的 Widget 尺寸
     * 
     * 可选值：small, medium, large, extra_large
     */
    suspend fun savePreferredWidgetSize(size: String) {
        context.widgetPreferences.edit { preferences ->
            preferences[KEY_PREFERRED_WIDGET_SIZE] = size
        }
    }

    /**
     * 获取偏好的 Widget 尺寸
     */
    fun getPreferredWidgetSize(): Flow<String> {
        return context.widgetPreferences.data.map { preferences ->
            preferences[KEY_PREFERRED_WIDGET_SIZE] ?: "medium"
        }
    }

    /**
     * 增加教程显示次数
     */
    suspend fun incrementTutorialShownCount() {
        context.widgetPreferences.edit { preferences ->
            val currentCount = (preferences[KEY_TUTORIAL_SHOWN_COUNT]?.toIntOrNull() ?: 0) + 1
            preferences[KEY_TUTORIAL_SHOWN_COUNT] = currentCount.toString()
        }
    }

    /**
     * 获取教程显示次数
     */
    fun getTutorialShownCount(): Flow<Int> {
        return context.widgetPreferences.data.map { preferences ->
            preferences[KEY_TUTORIAL_SHOWN_COUNT]?.toIntOrNull() ?: 0
        }
    }

    /**
     * 检查是否应该再次显示教程（超过3次后不再自动显示）
     */
    suspend fun shouldShowTutorial(): Boolean {
        val count = getTutorialShownCount().first()
        return count < 3
    }

    /**
     * 记录设备制造商信息
     */
    suspend fun recordManufacturer(manufacturer: String) {
        context.widgetPreferences.edit { preferences ->
            preferences[KEY_LAST_MANUFACTURER] = manufacturer
        }
    }

    /**
     * 获取记录的设备制造商
     */
    fun getRecordedManufacturer(): Flow<String> {
        return context.widgetPreferences.data.map { preferences ->
            preferences[KEY_LAST_MANUFACTURER] ?: ""
        }
    }

    /**
     * 记录用户对添加方式的反馈
     * 
     * feedback: "success", "failed", "manual", "cancelled"
     */
    suspend fun recordUserFeedback(feedback: String) {
        context.widgetPreferences.edit { preferences ->
            preferences[KEY_USER_FEEDBACK] = feedback
        }
    }

    /**
     * 添加到收藏的 Widget 类型列表
     */
    suspend fun addToFavoriteWidgetTypes(widgetType: WidgetType) {
        context.widgetPreferences.edit { preferences ->
            val currentFavorites = preferences[KEY_FAVORITE_WIDGET_TYPES]?.split(",")?.toMutableList()
                ?: mutableListOf()
            
            if (!currentFavorites.contains(widgetType.name.lowercase())) {
                currentFavorites.add(widgetType.name.lowercase())
                preferences[KEY_FAVORITE_WIDGET_TYPES] = currentFavorites.joinToString(",")
            }
        }
    }

    /**
     * 从收藏列表中移除
     */
    suspend fun removeFromFavoriteWidgetTypes(widgetType: WidgetType) {
        context.widgetPreferences.edit { preferences ->
            val currentFavorites = preferences[KEY_FAVORITE_WIDGET_TYPES]?.split(",")?.toMutableList()
                ?: mutableListOf()
            
            currentFavorites.remove(widgetType.name.lowercase())
            preferences[KEY_FAVORITE_WIDGET_TYPES] = currentFavorites.joinToString(",")
        }
    }

    /**
     * 获取收藏的 Widget 类型列表
     */
    fun getFavoriteWidgetTypes(): Flow<List<String>> {
        return context.widgetPreferences.data.map { preferences ->
            val favorites = preferences[KEY_FAVORITE_WIDGET_TYPES]
            if (favorites.isNullOrEmpty()) {
                emptyList()
            } else {
                favorites.split(",").filter { it.isNotEmpty() }
            }
        }
    }

    /**
     * 获取智能推荐的 Widget 类型
     * 
     * 推荐逻辑：
     * 1. 如果有明确设置的首选类型 → 返回首选类型
     * 2. 否则返回最近使用的类型
     * 3. 都没有则返回默认值（next_class）
     */
    suspend fun getRecommendedWidgetType(): WidgetType {
        val defaultType = getDefaultWidgetTypeSync()
        
        return if (defaultType.isNotEmpty()) {
            try {
                WidgetType.valueOf(defaultType.uppercase())
            } catch (e: Exception) {
                WidgetType.NEXT_CLASS
            }
        } else {
            val lastUsed = getLastUsedWidgetType().first()
            try {
                if (lastUsed.isNotEmpty()) {
                    WidgetType.valueOf(lastUsed.uppercase())
                } else {
                    WidgetType.NEXT_CLASS
                }
            } catch (e: Exception) {
                WidgetType.NEXT_CLASS
            }
        }
    }

    /**
     * 清除所有偏好设置（用于重置或隐私清理）
     */
    suspend fun clearAllPreferences() {
        context.widgetPreferences.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * 导出偏好设置（用于调试或迁移）
     */
    suspend fun exportPreferences(): Map<String, String?> {
        val prefs = context.widgetPreferences.data.first()
        return mapOf(
            "default_widget_type" to prefs[KEY_DEFAULT_WIDGET_TYPE]?.toString(),
            "last_used_widget_type" to prefs[KEY_LAST_USED_WIDGET_TYPE]?.toString(),
            "preferred_widget_size" to prefs[KEY_PREFERRED_WIDGET_SIZE]?.toString(),
            "tutorial_shown_count" to prefs[KEY_TUTORIAL_SHOWN_COUNT]?.toString(),
            "last_manufacturer" to prefs[KEY_LAST_MANUFACTURER]?.toString(),
            "user_feedback" to prefs[KEY_USER_FEEDBACK]?.toString(),
            "favorite_widget_types" to prefs[KEY_FAVORITE_WIDGET_TYPES]?.toString()
        )
    }
}

/**
 * 扩展函数：快速获取推荐 Widget 并执行操作
 */
suspend inline fun <T> Context.withRecommendedWidgetType(
    crossinline action: (WidgetType) -> T
): T {
    val prefsManager = WidgetPreferencesManager.getInstance(this)
    val recommendedType = prefsManager.getRecommendedWidgetType()
    
    // 记录使用情况
    prefsManager.recordLastUsedWidgetType(recommendedType)
    
    return action(recommendedType)
}
