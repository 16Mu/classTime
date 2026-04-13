package com.wind.ggbond.classtime.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.MonetColorPalette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class OverlayConfig(val overlayColor: Int = android.graphics.Color.BLACK, val alpha: Float = 0.4f)

@Singleton
class BackgroundThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BgThemeManager"
        
        const val DEFAULT_SEED_COLOR = BackgroundScheme.DEFAULT_SEED_COLOR
        const val DEFAULT_USE_DYNAMIC_THEME = false
        val DEFAULT_OVERLAY_CONFIG = OverlayConfig()
    }
    
    private val dataStore = DataStoreManager.getSettingsDataStore(context)
    
    fun getAllBackgroundSchemes(): Flow<List<BackgroundScheme>> {
        return dataStore.data.map { preferences ->
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            AppLogger.d(TAG, "getAllSchemes: json=${json?.length ?: 0} chars")
            val schemes = BackgroundScheme.fromJsonArray(json)
            AppLogger.d(TAG, "getAllSchemes: ${schemes.size} schemes")
            schemes
        }
    }
    
    fun getActiveBackgroundScheme(): Flow<BackgroundScheme?> {
        return dataStore.data.map { preferences ->
            val index = preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
            
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val schemes = BackgroundScheme.fromJsonArray(json)
            
            val result = if (index in schemes.indices) {
                schemes[index]
            } else if (schemes.isNotEmpty()) {
                AppLogger.w(TAG, "activeScheme index out of bounds, using first")
                schemes.first()
            } else {
                AppLogger.w(TAG, "activeScheme no schemes available")
                null
            }
            
            result
        }
    }
    
    fun getActiveBackgroundIndex(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
        }
    }
    
    suspend fun addBackgroundScheme(scheme: BackgroundScheme, setActive: Boolean = false): Boolean {
        var result = false
        dataStore.edit { preferences ->
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val schemes = BackgroundScheme.fromJsonArray(json).toMutableList()

            if (schemes.size >= DataStoreManager.SettingsKeys.MAX_BACKGROUNDS_COUNT) {
                AppLogger.w(TAG, "addScheme: max limit reached (${DataStoreManager.SettingsKeys.MAX_BACKGROUNDS_COUNT})")
                return@edit
            }

            if (schemes.any { it.uri == scheme.uri && it.uri.isNotEmpty() }) {
                AppLogger.w(TAG, "addScheme: duplicate URI, skipping")
                return@edit
            }

            schemes.add(scheme)

            val newJson = BackgroundScheme.toJsonArray(schemes)
            preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY] = newJson

            if (setActive || schemes.size == 1) {
                preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] = if (setActive) schemes.size - 1 else 0
                preferences[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] = true
                preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] = scheme.seedColor
            }

            result = true
        }
        return result
    }

    suspend fun addAndActivateBackgroundScheme(
        scheme: BackgroundScheme,
        activate: Boolean = true
    ): Boolean = addBackgroundScheme(scheme, setActive = activate)
    
    suspend fun removeBackgroundScheme(index: Int) {
        dataStore.edit { preferences ->
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val schemes = BackgroundScheme.fromJsonArray(json).toMutableList()
            
            if (index in schemes.indices) {
                schemes.removeAt(index)
                
                preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY] = 
                    BackgroundScheme.toJsonArray(schemes)
                
                val currentIndex = preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY]
                    ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
                
                when {
                    schemes.isEmpty() -> {
                        preferences.remove(DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY)
                        preferences.remove(DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY)
                        preferences.remove(DataStoreManager.SettingsKeys.SEED_COLOR_KEY)
                    }
                    currentIndex > index -> {
                        preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] = currentIndex - 1
                        val newActive = schemes[(currentIndex - 1).coerceAtMost(schemes.size - 1).coerceAtLeast(0)]
                        preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] = newActive.seedColor
                    }
                    currentIndex == index -> {
                        val newIndex = currentIndex.coerceAtMost(schemes.size - 1).coerceAtLeast(0)
                        preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] = newIndex
                        preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] = schemes[newIndex].seedColor
                    }
                }
            } else {
                AppLogger.w(TAG, "removeScheme: index $index out of bounds (size=${schemes.size})")
            }
        }
    }
    
    suspend fun renameBackgroundScheme(index: Int, updatedScheme: BackgroundScheme, isActive: Boolean) {
        dataStore.edit { preferences ->
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val schemes = BackgroundScheme.fromJsonArray(json).toMutableList()
            
            if (index in schemes.indices) {
                schemes[index] = updatedScheme
                preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY] =
                    BackgroundScheme.toJsonArray(schemes)
                
                if (isActive) {
                    preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] = updatedScheme.seedColor
                }
            }
        }
    }
    
    suspend fun setActiveBackgroundIndex(index: Int) {
        dataStore.edit { preferences ->
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val schemes = BackgroundScheme.fromJsonArray(json)
            
            if (index in schemes.indices) {
                val scheme = schemes[index]
                preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] = index
                preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] = scheme.seedColor
                preferences[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] = true
            } else {
                AppLogger.w(TAG, "setActiveIndex: $index out of bounds (size=${schemes.size})")
            }
        }
    }
    
    fun getBlurRadius(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.BLUR_RADIUS_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_BLUR_RADIUS
        }
    }
    
    suspend fun setBlurRadius(radius: Int) {
        val clampedRadius = radius.coerceIn(0, 100)
        
        dataStore.edit { preferences ->
            preferences[DataStoreManager.SettingsKeys.BLUR_RADIUS_KEY] = clampedRadius
            
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val index = preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
            val schemes = BackgroundScheme.fromJsonArray(json).toMutableList()
            if (index in schemes.indices) {
                schemes[index] = schemes[index].copy(blurRadius = clampedRadius)
                preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY] = 
                    BackgroundScheme.toJsonArray(schemes)
            }
        }
    }
    
    fun getDimAmount(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.DIM_AMOUNT_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_DIM_AMOUNT
        }
    }
    
    suspend fun setDimAmount(amount: Int) {
        val clampedAmount = amount.coerceIn(0, 100)
        
        dataStore.edit { preferences ->
            preferences[DataStoreManager.SettingsKeys.DIM_AMOUNT_KEY] = clampedAmount
            
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val index = preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
            val schemes = BackgroundScheme.fromJsonArray(json).toMutableList()
            if (index in schemes.indices) {
                schemes[index] = schemes[index].copy(dimAmount = clampedAmount)
                preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY] = 
                    BackgroundScheme.toJsonArray(schemes)
            }
        }
    }
    
    fun getBackgroundType(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.BACKGROUND_TYPE_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_BACKGROUND_TYPE
        }
    }
    
    fun getBackgroundUri(): Flow<String?> {
        return getActiveBackgroundScheme().map { it?.uri }
    }
    
    fun getSeedColor(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] ?: DEFAULT_SEED_COLOR
        }
    }
    
    fun isDynamicThemeEnabled(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] ?: DEFAULT_USE_DYNAMIC_THEME
        }
    }

    fun isDesktopModeEnabled(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[DataStoreManager.SettingsKeys.DESKTOP_MODE_ENABLED_KEY]
                ?: DataStoreManager.SettingsKeys.DEFAULT_DESKTOP_MODE_ENABLED
        }
    }

    suspend fun setDesktopModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DataStoreManager.SettingsKeys.DESKTOP_MODE_ENABLED_KEY] = enabled
        }
    }
    
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
    
    suspend fun setSeedColor(seedColor: Int) {
        dataStore.edit { preferences ->
            preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] = seedColor
            preferences[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] = true
            
            val json = preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY]
            val index = preferences[DataStoreManager.SettingsKeys.ACTIVE_BACKGROUND_INDEX_KEY] 
                ?: DataStoreManager.SettingsKeys.DEFAULT_ACTIVE_BACKGROUND_INDEX
            val schemes = BackgroundScheme.fromJsonArray(json).toMutableList()
            if (index in schemes.indices) {
                schemes[index] = schemes[index].copy(seedColor = seedColor)
                preferences[DataStoreManager.SettingsKeys.BACKGROUNDS_JSON_KEY] = 
                    BackgroundScheme.toJsonArray(schemes)
            }
        }
    }

    suspend fun setUseDynamicTheme(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DataStoreManager.SettingsKeys.USE_DYNAMIC_THEME_KEY] = enabled
        }
    }

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
    
    fun extractSeedColorFromBitmap(bitmap: Bitmap): Int {
        return try {
            val palette = Palette.from(bitmap)
                .maximumColorCount(16)
                .generate()
            
            palette.vibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: DEFAULT_SEED_COLOR
        } catch (e: Exception) {
            AppLogger.e(TAG, "extractSeedColorFromBitmap failed", e)
            DEFAULT_SEED_COLOR
        }
    }
    
    suspend fun extractSeedColorFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                options.inSampleSize = calculateInSampleSize(options, 100, 100)
                options.inJustDecodeBounds = false

                context.contentResolver.openInputStream(uri)?.use { sampleStream ->
                    val bitmap = BitmapFactory.decodeStream(sampleStream, null, options)
                    if (bitmap == null) {
                        AppLogger.e(TAG, "extractSeedColorFromUri: decode failed")
                        return@withContext DEFAULT_SEED_COLOR
                    }
                    try {
                        extractSeedColorFromBitmap(bitmap)
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                } ?: DEFAULT_SEED_COLOR
            } ?: run {
                AppLogger.e(TAG, "extractSeedColorFromUri: cannot open stream")
                DEFAULT_SEED_COLOR
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "extractSeedColorFromUri exception", e)
            DEFAULT_SEED_COLOR
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    
    fun generateLightColorScheme(seedColor: Int, style: PaletteStyle = PaletteStyle.TonalSpot): androidx.compose.material3.ColorScheme {
        return dynamicColorScheme(
            seedColor = Color(seedColor),
            isDark = false,
            isAmoled = false,
            style = style
        )
    }

    fun generateDarkColorScheme(seedColor: Int, style: PaletteStyle = PaletteStyle.TonalSpot): androidx.compose.material3.ColorScheme {
        return dynamicColorScheme(
            seedColor = Color(seedColor),
            isDark = true,
            isAmoled = false,
            style = style
        )
    }
    
    suspend fun extractSeedColorFromVideoFirstFrame(uri: Uri): Int = withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri)

            val bitmap = retriever.frameAtTime
            if (bitmap == null) {
                AppLogger.e(TAG, "extractSeedColorFromVideo: no frame")
                return@withContext DEFAULT_SEED_COLOR
            }

            try {
                extractSeedColorFromBitmap(bitmap)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "extractSeedColorFromVideo exception", e)
            DEFAULT_SEED_COLOR
        } finally {
            try { retriever.release() } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
        }
    }

    suspend fun getCurrentSeedColor(): Int {
        return try {
            dataStore.data.first()[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] 
                ?: DEFAULT_SEED_COLOR
        } catch (e: Exception) {
            AppLogger.e(TAG, "getCurrentSeedColor failed", e)
            DEFAULT_SEED_COLOR
        }
    }

    suspend fun generateCurrentCourseColorPalette(
        saturationLevel: MonetColorPalette.SaturationLevel = MonetColorPalette.SaturationLevel.STANDARD,
        isDarkMode: Boolean = false
    ): List<String> {
        val seedColor = getCurrentSeedColor()
        return MonetColorPalette.generatePalette(
            seedColor = seedColor,
            saturationLevel = saturationLevel,
            isDarkMode = isDarkMode
        )
    }

    fun observeCourseColors(
        saturationLevel: MonetColorPalette.SaturationLevel = MonetColorPalette.SaturationLevel.STANDARD,
        isDarkMode: Boolean = false
    ): Flow<List<String>> {
        return dataStore.data.map { preferences ->
            val seedColor = preferences[DataStoreManager.SettingsKeys.SEED_COLOR_KEY] ?: DEFAULT_SEED_COLOR
            MonetColorPalette.generatePalette(
                seedColor = seedColor,
                saturationLevel = saturationLevel,
                isDarkMode = isDarkMode
            )
        }.distinctUntilChanged()
    }

    fun calculateLuminance(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    fun getDominantColor(bitmap: Bitmap): Int {
        return try {
            val palette = Palette.from(bitmap)
                .maximumColorCount(16)
                .generate()

            palette.vibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: DEFAULT_SEED_COLOR
        } catch (e: Exception) {
            AppLogger.e(TAG, "getDominantColor failed", e)
            DEFAULT_SEED_COLOR
        }
    }

    fun calculateSmartOverlay(wallpaper: Bitmap): OverlayConfig {
        val dominantColor = getDominantColor(wallpaper)
        val luminance = calculateLuminance(dominantColor)

        return when {
            luminance > 0.7f -> OverlayConfig(android.graphics.Color.BLACK, 0.5f)
            luminance < 0.3f -> OverlayConfig(android.graphics.Color.WHITE, 0.35f)
            else -> OverlayConfig(android.graphics.Color.DKGRAY, 0.45f)
        }
    }

    fun getSmartOverlayConfig(uri: Uri?): Flow<OverlayConfig> {
        return kotlinx.coroutines.flow.flow {
            if (uri == null) {
                emit(DEFAULT_OVERLAY_CONFIG)
                return@flow
            }

            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    options.inSampleSize = calculateInSampleSize(options, 100, 100)
                    options.inJustDecodeBounds = false

                    context.contentResolver.openInputStream(uri)?.use { sampleStream ->
                        val bitmap = BitmapFactory.decodeStream(sampleStream, null, options)
                        if (bitmap == null) {
                            emit(DEFAULT_OVERLAY_CONFIG)
                            return@flow
                        }

                        try {
                            emit(calculateSmartOverlay(bitmap))
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    } ?: emit(DEFAULT_OVERLAY_CONFIG)
                } ?: emit(DEFAULT_OVERLAY_CONFIG)
            } catch (e: Exception) {
                AppLogger.e(TAG, "getSmartOverlayConfig exception", e)
                emit(DEFAULT_OVERLAY_CONFIG)
            }
        }.flowOn(Dispatchers.IO)
    }
}
