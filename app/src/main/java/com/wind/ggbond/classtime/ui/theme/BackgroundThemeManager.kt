package com.wind.ggbond.classtime.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.util.MonetColorPalette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 背景主题管理器 - 管理自定义背景和动态配色方案（增强版）
 * 
 * 功能：
 * 1. ✅ 支持多套背景方案（最多10套）
 * 2. ✅ 背景模糊程度调节 (0-100)
 * 3. ✅ 背景暗化程度调节 (0-100)
 * 4. ✅ 支持图片/视频/GIF 三种类型
 * 5. ✅ 从图片中提取种子颜色（使用 Palette API）
 * 6. ✅ 使用 MaterialKolor 生成动态 Material 3 配色方案
 * 7. ✅ 实时主题切换能力
 */
@Singleton
class BackgroundThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BackgroundThemeManager"
        
        // 默认种子颜色（温暖的米色调，与原主题保持一致）
        const val DEFAULT_SEED_COLOR = 0xFFD4A574.toInt()
        
        // 默认是否使用动态主题
        const val DEFAULT_USE_DYNAMIC_THEME = false
    }
    
    private val dataStore = DataStoreManager.getSettingsDataStore(context)
    
    // ==================== 多背景方案管理 ====================
    
    /**
     * 获取所有背景方案列表
     */
    fun getAllBackgroundSchemes(): Flow<List<BackgroundScheme>> {
        return dataStore.data.map { preferences ->
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            BackgroundScheme.fromJsonArray(json)
        }
    }
    
    /**
     * 获取当前激活的背景方案
     */
    fun getActiveBackgroundScheme(): Flow<BackgroundScheme?> {
        return dataStore.data.map { preferences ->
            val index = preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
            
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val schemes = BackgroundScheme.fromJsonArray(json)
            
            if (index in schemes.indices) {
                schemes[index]
            } else if (schemes.isNotEmpty()) {
                schemes.first()
            } else {
                null
            }
        }
    }
    
    /**
     * 获取当前激活的背景索引
     */
    fun getActiveBackgroundIndex(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
        }
    }
    
    /**
     * 添加新的背景方案
     */
    suspend fun addBackgroundScheme(scheme: BackgroundScheme): Boolean {
        var result = false
        dataStore.edit { preferences ->
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val schemes = BackgroundScheme.fromJsonArray(json).toMutableList()
            
            if (schemes.size >= DataStoreManager.SettingsKeys.MAX_BACKGROUNDS_COUNT) {
                return@edit
            }
            
            schemes.add(scheme)
            preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY] = 
                BackgroundScheme.toJsonArray(schemes)
            
            if (schemes.size == 1) {
                preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] = 0
                preferences[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] = true
            }
            
            result = true
        }
        return result
    }
    
    /**
     * 删除指定索引的背景方案
     */
    suspend fun removeBackgroundScheme(index: Int) {
        dataStore.edit { preferences ->
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val schemes = BackgroundScheme.fromJsonArray(json).toMutableList()
            
            if (index in schemes.indices) {
                schemes.removeAt(index)
                
                preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY] = 
                    BackgroundScheme.toJsonArray(schemes)
                
                // 调整激活索引
                val currentIndex = preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY]
                    ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
                
                when {
                    schemes.isEmpty() -> {
                        // 没有背景了，恢复默认
                        preferences.remove(DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY)
                        preferences[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] = false
                    }
                    currentIndex > index -> {
                        // 删除的是前面的，索引减1
                        preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] = currentIndex - 1
                    }
                    currentIndex == index -> {
                        // 删除的是当前的，激活第一个或最后一个
                        preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] = 
                            minOf(0, (schemes.size - 1).coerceAtLeast(0))
                    }
                }
            }
        }
    }
    
    /**
     * 切换到指定索引的背景方案
     */
    suspend fun setActiveBackgroundIndex(index: Int) {
        dataStore.edit { preferences ->
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val schemes = BackgroundScheme.fromJsonArray(json)
            
            if (index in schemes.indices) {
                preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] = index
                
                // 更新种子颜色为当前方案的种子颜色
                val scheme = schemes[index]
                preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] = scheme.seedColor
                preferences[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] = true
            }
        }
    }
    
    // ==================== 效果参数管理 ====================
    
    /**
     * 获取模糊半径
     */
    fun getBlurRadius(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.BLUR_RADIUS_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_BLUR_RADIUS
        }
    }
    
    /**
     * 设置模糊半径
     */
    suspend fun setBlurRadius(radius: Int) {
        val clampedRadius = radius.coerceIn(0, 100)
        dataStore.edit { preferences ->
            preferences[DataStoreManager.SettingsKeys.BLUR_RADIUS_KEY] = clampedRadius
        }
        
        // 同时更新当前激活的方案
        updateActiveScheme { it.copy(blurRadius = clampedRadius) }
    }
    
    /**
     * 获取暗化程度
     */
    fun getDimAmount(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.DIM_AMOUNT_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_DIM_AMOUNT
        }
    }
    
    /**
     * 设置暗化程度
     */
    suspend fun setDimAmount(amount: Int) {
        val clampedAmount = amount.coerceIn(0, 100)
        dataStore.edit { preferences ->
            preferences[DataStoreManager.SettingsKeys.DIM_AMOUNT_KEY] = clampedAmount
        }
        
        // 同时更新当前激活的方案
        updateActiveScheme { it.copy(dimAmount = clampedAmount) }
    }
    
    /**
     * 获取背景类型
     */
    fun getBackgroundType(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.BACKGROUND_TYPE_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_BACKGROUND_TYPE
        }
    }
    
    // ==================== 兼容旧版 API ====================
    
    /**
     * 获取当前背景图片 URI（兼容旧版）
     */
    fun getBackgroundUri(): Flow<String?> {
        return getActiveBackgroundScheme().map { scheme -> scheme?.uri }
    }
    
    /**
     * 获取种子颜色
     */
    fun getSeedColor(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] ?: DEFAULT_SEED_COLOR
        }
    }
    
    /**
     * 是否使用动态主题
     */
    fun isDynamicThemeEnabled(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] ?: DEFAULT_USE_DYNAMIC_THEME
        }
    }
    
    /**
     * 保存背景（兼容旧版 API，内部转换为新的多背景系统）
     */
    suspend fun setBackground(uri: String, seedColor: Int) {
        val scheme = BackgroundScheme(
            uri = uri,
            type = BackgroundType.IMAGE,
            seedColor = seedColor,
            blurRadius = DataStoreManager.SettingsKeys.DEFAULT_BLUR_RADIUS,
            dimAmount = DataStoreManager.SettingsKeys.DEFAULT_DIM_AMOUNT
        )
        addBackgroundScheme(scheme)
    }
    
    /**
     * 只更新种子颜色（用于手动选择颜色）
     */
    suspend fun setSeedColor(seedColor: Int) {
        dataStore.edit { preferences ->
            preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] = seedColor
            preferences[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] = true
        }
        
        // 更新当前激活的方案
        updateActiveScheme { it.copy(seedColor = seedColor) }
    }
    
    /**
     * 清除自定义背景，恢复默认主题
     */
    suspend fun clearBackground() {
        dataStore.edit { preferences ->
            preferences.remove(DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY)
            preferences.remove(DataStoreManager.SettingsKeys.CUSTOM_BACKGROUND_URI_KEY)
            preferences.remove(DataStoreManager.SettingsKeys.SEED_COLOR_KEY)
            preferences.remove(DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY)
            preferences.remove(DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY)
            preferences.remove(DataStoreManager.SettingsKeys.BLUR_RADIUS_KEY)
            preferences.remove(DataStoreManager.SettingsKeys.DIM_AMOUNT_KEY)
            preferences.remove(DataStoreManager.SettingsKeys.BACKGROUND_TYPE_KEY)
        }
    }
    
    // ==================== 颜色提取与生成 ====================
    
    /**
     * 从 Bitmap 中提取种子颜色
     * 使用 Android Palette API 提取主要颜色
     */
    fun extractSeedColorFromBitmap(bitmap: Bitmap): Int {
        return try {
            val palette = androidx.palette.graphics.Palette.from(bitmap)
                .maximumColorCount(16)
                .generate()
            
            palette.vibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: DEFAULT_SEED_COLOR
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to extract color from bitmap", e)
            DEFAULT_SEED_COLOR
        }
    }
    
    /**
     * 从 Uri 加载 Bitmap 并提取种子颜色
     */
    suspend fun extractSeedColorFromUri(uri: Uri): Int {
        return try {
            Log.d(TAG, "Extracting seed color from URI: $uri")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode bitmap from URI: $uri")
                    return DEFAULT_SEED_COLOR
                }
                val color = extractSeedColorFromBitmap(bitmap)
                Log.d(TAG, "Extracted seed color: #${Integer.toHexString(color)}")
                color
            } ?: run {
                Log.e(TAG, "Failed to open input stream for URI: $uri")
                DEFAULT_SEED_COLOR
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception extracting seed color from URI: $uri", e)
            DEFAULT_SEED_COLOR
        }
    }
    
    /**
     * 生成动态配色方案（亮色）
     */
    fun generateLightColorScheme(seedColor: Int, style: PaletteStyle = PaletteStyle.TonalSpot): androidx.compose.material3.ColorScheme {
        val color = Color(seedColor)
        return dynamicColorScheme(
            seedColor = color,
            isDark = false,
            isAmoled = false,
            style = style
        )
    }

    /**
     * 生成动态配色方案（暗色）
     */
    fun generateDarkColorScheme(seedColor: Int, style: PaletteStyle = PaletteStyle.TonalSpot): androidx.compose.material3.ColorScheme {
        val color = Color(seedColor)
        return dynamicColorScheme(
            seedColor = color,
            isDark = true,
            isAmoled = false,
            style = style
        )
    }
    
    /**
     * 从视频第一帧提取种子颜色
     */
    suspend fun extractSeedColorFromVideoFirstFrame(uri: Uri): Int {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            Log.d(TAG, "Extracting seed color from video URI: $uri")
            retriever.setDataSource(context, uri)
            val bitmap = retriever.frameAtTime
            if (bitmap == null) {
                Log.e(TAG, "Failed to extract frame from video: $uri")
                return DEFAULT_SEED_COLOR
            }
            val color = extractSeedColorFromBitmap(bitmap)
            Log.d(TAG, "Extracted seed color from video: #${Integer.toHexString(color)}")
            color
        } catch (e: Exception) {
            Log.e(TAG, "Exception extracting frame from video: $uri", e)
            DEFAULT_SEED_COLOR
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release MediaMetadataRetriever", e)
            }
        }
    }

    // ==================== 私有辅助方法 ====================
    
    /**
     * 更新当前激活的方案
     */
    private suspend fun updateActiveScheme(transform: (BackgroundScheme) -> BackgroundScheme) {
        dataStore.edit { preferences ->
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val index = preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
            
            val schemes = BackgroundScheme.fromJsonArray(json).toMutableList()
            
            if (index in schemes.indices) {
                schemes[index] = transform(schemes[index])
                preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY] = 
                    BackgroundScheme.toJsonArray(schemes)
            }
        }
    }

    /**
     * 获取当前种子颜色（同步版本，从缓存读取）
     * 
     * @return ARGB格式的种子颜色整数，最坏情况返回DEFAULT_SEED_COLOR
     */
    suspend fun getCurrentSeedColor(): Int {
        return try {
            dataStore.data.first()[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] 
                ?: DEFAULT_SEED_COLOR
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read seed color", e)
            DEFAULT_SEED_COLOR
        }
    }

    /**
     * 生成当前的课程颜色调色板
     * 
     * @param saturationLevel 饱和度等级
     * @return 24种颜色的列表（16进制格式）
     */
    suspend fun generateCurrentCourseColorPalette(
        saturationLevel: MonetColorPalette.SaturationLevel = MonetColorPalette.SaturationLevel.STANDARD
    ): List<String> {
        val seedColor = getCurrentSeedColor()
        return MonetColorPalette.generatePalette(
            seedColor = seedColor,
            saturationLevel = saturationLevel,
            isDarkMode = false
        )
    }

    /**
     * 监听课程颜色变化（响应式）
     * 
     * @param saturationLevel 饱和度等级
     * @return 响应式的颜色列表数据流
     */
    fun observeCourseColors(
        saturationLevel: MonetColorPalette.SaturationLevel
    ): Flow<List<String>> {
        return dataStore.data.map { preferences ->
            val seedColor = preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] ?: DEFAULT_SEED_COLOR
            MonetColorPalette.generatePalette(
                seedColor = seedColor,
                saturationLevel = saturationLevel,
                isDarkMode = false
            )
        }
    }
}
