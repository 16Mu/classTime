package com.wind.ggbond.classtime.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.ui.theme.BackgroundScheme
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.ui.theme.BackgroundType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackgroundSettingsViewModel @Inject constructor(
    private val backgroundThemeManager: BackgroundThemeManager,
    private val backgroundExportManager: BackgroundExportManager,
    private val unsplashWallpaperManager: UnsplashWallpaperManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val mediaFileManager = com.wind.ggbond.classtime.util.MediaFileManager(context)
    
    data class UiState(
        val backgroundSchemes: List<BackgroundScheme> = emptyList(),
        val activeBackgroundIndex: Int = 0,
        val activeScheme: BackgroundScheme? = null,
        val blurRadius: Int = DataStoreManager.SettingsKeys.DEFAULT_BLUR_RADIUS,
        val dimAmount: Int = DataStoreManager.SettingsKeys.DEFAULT_DIM_AMOUNT,
        val isDynamicThemeEnabled: Boolean = false,
        val seedColor: Int = 0,
        val isLoading: Boolean = false,
        val showColorPicker: Boolean = false,
        val showImagePicker: Boolean = false,
        val showVideoPicker: Boolean = false,
        val showGifPicker: Boolean = false,
        val showRenameDialog: Boolean = false,
        val showDeleteConfirmDialog: Boolean = false,
        val pendingRenameIndex: Int = -1,
        val pendingDeleteIndex: Int = -1,
        val pendingRenameName: String = "",
        val backgroundAppliedSuccess: Boolean = false
    )
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    init {
        loadCurrentSettings()
    }
    
    private fun loadCurrentSettings() {
        viewModelScope.launch {
            combine(
                backgroundThemeManager.getAllBackgroundSchemes(),
                backgroundThemeManager.getActiveBackgroundIndex(),
                backgroundThemeManager.getActiveBackgroundScheme(),
                backgroundThemeManager.getBlurRadius(),
                backgroundThemeManager.getDimAmount(),
                backgroundThemeManager.isDynamicThemeEnabled()
            ) { arr ->
                @Suppress("UNCHECKED_CAST")
                val schemes = arr[0] as List<BackgroundScheme>
                val index = arr[1] as Int
                val active = arr[2] as BackgroundScheme?
                val blur = arr[3] as Int
                val dim = arr[4] as Int
                val dynamic = arr[5] as Boolean
                UiState(
                    backgroundSchemes = schemes,
                    activeBackgroundIndex = if (index < schemes.size) index else 0,
                    activeScheme = active,
                    blurRadius = blur,
                    dimAmount = dim,
                    isDynamicThemeEnabled = dynamic,
                    seedColor = active?.seedColor ?: 0
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
    
    fun showImagePicker() { _uiState.update { it.copy(showImagePicker = true) } }
    fun hideImagePicker() { _uiState.update { it.copy(showImagePicker = false) } }
    
    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 将图片复制到应用私有存储
                val localUri = mediaFileManager.copyMediaToPrivateStorage(uri, "jpg")
                if (localUri == null) {
                    android.util.Log.e("BackgroundSettings", "Failed to copy image to private storage")
                    _uiState.update { it.copy(isLoading = false, showImagePicker = false) }
                    return@launch
                }
                
                // 从本地文件提取种子颜色
                val seedColor = backgroundThemeManager.extractSeedColorFromUri(localUri)
                val scheme = BackgroundScheme(
                    uri = localUri.toString(), 
                    type = BackgroundType.IMAGE, 
                    seedColor = seedColor,
                    blurRadius = _uiState.value.blurRadius, 
                    dimAmount = _uiState.value.dimAmount,
                    name = "图片背景 ${_uiState.value.backgroundSchemes.size + 1}"
                )
                
                // Wait for DataStore write to complete before proceeding
                val success = backgroundThemeManager.addBackgroundScheme(scheme)
                
                if (!success) {
                    android.util.Log.e("BackgroundSettings", "Failed to add background scheme to DataStore")
                    _uiState.update { it.copy(isLoading = false, showImagePicker = false) }
                    return@launch
                }

                // Wait for seed color write to complete
                backgroundThemeManager.setSeedColor(seedColor)

                // Calculate the new index (after the scheme is added)
                val newIndex = _uiState.value.backgroundSchemes.size
                
                // Wait for active index write to complete
                backgroundThemeManager.setActiveBackgroundIndex(newIndex)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showImagePicker = false,
                        seedColor = seedColor,
                        isDynamicThemeEnabled = true,
                        backgroundAppliedSuccess = true
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("BackgroundSettings", "Failed to process image", e)
                _uiState.update { it.copy(isLoading = false, showImagePicker = false) }
            }
        }
    }
    
    fun showVideoPicker() { _uiState.update { it.copy(showVideoPicker = true) } }
    fun hideVideoPicker() { _uiState.update { it.copy(showVideoPicker = false) } }
    
    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 将视频复制到应用私有存储
                val localUri = mediaFileManager.copyMediaToPrivateStorage(uri, "mp4")
                if (localUri == null) {
                    android.util.Log.e("BackgroundSettings", "Failed to copy video to private storage")
                    _uiState.update { it.copy(isLoading = false, showVideoPicker = false) }
                    return@launch
                }
                
                // 从本地视频文件提取种子颜色
                val seedColor = backgroundThemeManager.extractSeedColorFromVideoFirstFrame(localUri)
                
                val scheme = BackgroundScheme(
                    uri = localUri.toString(), 
                    type = BackgroundType.VIDEO,
                    seedColor = seedColor,
                    blurRadius = _uiState.value.blurRadius, 
                    dimAmount = _uiState.value.dimAmount,
                    name = "视频背景 ${_uiState.value.backgroundSchemes.size + 1}"
                )
                
                // Wait for DataStore write to complete before proceeding
                val success = backgroundThemeManager.addBackgroundScheme(scheme)
                
                if (!success) {
                    android.util.Log.e("BackgroundSettings", "Failed to add background scheme to DataStore")
                    _uiState.update { it.copy(isLoading = false, showVideoPicker = false) }
                    return@launch
                }
                
                // Wait for seed color write to complete
                backgroundThemeManager.setSeedColor(seedColor)
                
                // Calculate the new index (after the scheme is added)
                val newIndex = _uiState.value.backgroundSchemes.size
                
                // Wait for active index write to complete
                backgroundThemeManager.setActiveBackgroundIndex(newIndex)
                
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        showVideoPicker = false,
                        seedColor = seedColor,
                        isDynamicThemeEnabled = true,
                        backgroundAppliedSuccess = true
                    ) 
                }
            } catch (e: Exception) {
                android.util.Log.e("BackgroundSettings", "Failed to process video", e)
                _uiState.update { it.copy(isLoading = false, showVideoPicker = false) }
            }
        }
    }
    
    fun showGifPicker() { _uiState.update { it.copy(showGifPicker = true) } }
    fun hideGifPicker() { _uiState.update { it.copy(showGifPicker = false) } }
    
    fun onGifSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 将 GIF 复制到应用私有存储
                val localUri = mediaFileManager.copyMediaToPrivateStorage(uri, "gif")
                if (localUri == null) {
                    android.util.Log.e("BackgroundSettings", "Failed to copy GIF to private storage")
                    _uiState.update { it.copy(isLoading = false, showGifPicker = false) }
                    return@launch
                }
                
                // 从本地 GIF 文件提取种子颜色
                val seedColor = backgroundThemeManager.extractSeedColorFromUri(localUri)
                val scheme = BackgroundScheme(
                    uri = localUri.toString(), 
                    type = BackgroundType.GIF, 
                    seedColor = seedColor,
                    blurRadius = _uiState.value.blurRadius, 
                    dimAmount = _uiState.value.dimAmount,
                    name = "GIF 背景 ${_uiState.value.backgroundSchemes.size + 1}"
                )
                
                // Wait for DataStore write to complete before proceeding
                val success = backgroundThemeManager.addBackgroundScheme(scheme)
                
                if (!success) {
                    android.util.Log.e("BackgroundSettings", "Failed to add background scheme to DataStore")
                    _uiState.update { it.copy(isLoading = false, showGifPicker = false) }
                    return@launch
                }

                // Wait for seed color write to complete
                backgroundThemeManager.setSeedColor(seedColor)

                // Calculate the new index (after the scheme is added)
                val newIndex = _uiState.value.backgroundSchemes.size
                
                // Wait for active index write to complete
                backgroundThemeManager.setActiveBackgroundIndex(newIndex)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showGifPicker = false,
                        seedColor = seedColor,
                        isDynamicThemeEnabled = true,
                        backgroundAppliedSuccess = true
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("BackgroundSettings", "Failed to process GIF", e)
                _uiState.update { it.copy(isLoading = false, showGifPicker = false) }
            }
        }
    }
    
    fun switchToBackground(index: Int) {
        viewModelScope.launch {
            backgroundThemeManager.setActiveBackgroundIndex(index)
            _uiState.update { it.copy(backgroundAppliedSuccess = true) }
        }
    }
    
    fun deleteBackground(index: Int) { _uiState.update { it.copy(pendingDeleteIndex = index, showDeleteConfirmDialog = true) } }
    
    fun confirmDeleteBackground() {
        val index = _uiState.value.pendingDeleteIndex
        if (index >= 0) { 
            viewModelScope.launch { 
                // 获取要删除的方案
                val scheme = _uiState.value.backgroundSchemes.getOrNull(index)
                
                // 从 BackgroundThemeManager 中删除
                backgroundThemeManager.removeBackgroundScheme(index)
                
                // 删除本地文件
                scheme?.let {
                    try {
                        val uri = Uri.parse(it.uri)
                        mediaFileManager.deleteBackgroundFile(uri)
                    } catch (e: Exception) {
                        android.util.Log.e("BackgroundSettings", "Failed to delete background file", e)
                    }
                }
            } 
        }
        _uiState.update { it.copy(showDeleteConfirmDialog = false, pendingDeleteIndex = -1) }
    }
    
    fun cancelDeleteBackground() { _uiState.update { it.copy(showDeleteConfirmDialog = false, pendingDeleteIndex = -1) } }
    
    fun consumeBackgroundAppliedSuccess() {
        _uiState.update { it.copy(backgroundAppliedSuccess = false) }
    }
    
    fun showRenameDialog(index: Int) {
        val scheme = _uiState.value.backgroundSchemes.getOrNull(index)
        if (scheme != null) { _uiState.update { it.copy(pendingRenameIndex = index, pendingRenameName = scheme.name, showRenameDialog = true) } }
    }
    
    fun hideRenameDialog() { _uiState.update { it.copy(showRenameDialog = false, pendingRenameIndex = -1) } }
    fun updateRenameName(name: String) { _uiState.update { it.copy(pendingRenameName = name) } }
    
    fun confirmRename() {
        val index = _uiState.value.pendingRenameIndex
        val newName = _uiState.value.pendingRenameName.trim()
        if (index >= 0 && newName.isNotEmpty()) {
            viewModelScope.launch {
                val scheme = _uiState.value.backgroundSchemes[index]
                val updatedScheme = scheme.copy(name = newName)
                backgroundThemeManager.removeBackgroundScheme(index)
                backgroundThemeManager.addBackgroundScheme(updatedScheme)
                val newIndex = _uiState.value.backgroundSchemes.size - 1
                if (newIndex >= 0) { backgroundThemeManager.setActiveBackgroundIndex(newIndex) }
            }
        }
        _uiState.update { it.copy(showRenameDialog = false, pendingRenameIndex = -1) }
    }
    
    fun updateBlurRadius(radius: Float) {
        _uiState.update { it.copy(blurRadius = radius.toInt()) }
        viewModelScope.launch { backgroundThemeManager.setBlurRadius(radius.toInt()) }
    }
    
    fun updateDimAmount(amount: Float) {
        _uiState.update { it.copy(dimAmount = amount.toInt()) }
        viewModelScope.launch { backgroundThemeManager.setDimAmount(amount.toInt()) }
    }
    
    fun showColorPicker() { _uiState.update { it.copy(showColorPicker = true) } }
    fun hideColorPicker() { _uiState.update { it.copy(showColorPicker = false) } }
    
    fun onSeedColorSelected(color: Int) {
        viewModelScope.launch {
            backgroundThemeManager.setSeedColor(color)
            _uiState.update { it.copy(seedColor = color, isDynamicThemeEnabled = true, showColorPicker = false) }
        }
    }
    
    fun clearAllBackgrounds() {
        viewModelScope.launch {
            // 清除所有本地文件
            mediaFileManager.clearAllBackgrounds()
            
            // 清除 BackgroundThemeManager 中的数据
            backgroundThemeManager.clearBackground()
            
            _uiState.update { 
                UiState(
                    blurRadius = DataStoreManager.SettingsKeys.DEFAULT_BLUR_RADIUS, 
                    dimAmount = DataStoreManager.SettingsKeys.DEFAULT_DIM_AMOUNT
                ) 
            }
        }
    }
    
    fun toggleDynamicTheme(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                backgroundThemeManager.clearBackground()
                _uiState.update { it.copy(isDynamicThemeEnabled = false) }
            } else if (_uiState.value.backgroundSchemes.isNotEmpty()) {
                backgroundThemeManager.setActiveBackgroundIndex(_uiState.value.activeBackgroundIndex)
                _uiState.update { it.copy(isDynamicThemeEnabled = true) }
            }
        }
    }
    
    suspend fun exportAndShare(): Intent? {
        val schemes = _uiState.value.backgroundSchemes
        if (schemes.isEmpty()) return null
        
        val uri = backgroundExportManager.exportSchemes(schemes) ?: return null
        return backgroundExportManager.createShareIntent(uri)
    }
    
    suspend fun importFromUri(uri: Uri): Int {
        val schemes = backgroundExportManager.importSchemes(uri)
        if (schemes.isEmpty()) return 0
        
        var addedCount = 0
        for (scheme in schemes) {
            if (_uiState.value.backgroundSchemes.size < 10) {
                backgroundThemeManager.addBackgroundScheme(scheme)
                addedCount++
            }
        }
        
        loadCurrentSettings()
        return addedCount
    }
    
    data class ValidationResult(val isValid: Boolean, val message: String = "")
    suspend fun validateImportFile(uri: Uri): ValidationResult {
        val result = backgroundExportManager.validateImportFile(uri)
        return ValidationResult(result.isValid, result.message)
    }
    
    suspend fun searchUnsplashPhotos(query: String, count: Int = 10): List<Any> =
        unsplashWallpaperManager.searchUnsplashPhotos(query, count)
    
    suspend fun getRandomUnsplashWallpapers(count: Int = 5): List<Any> =
        unsplashWallpaperManager.getRandomUnsplashWallpapers(count)
    
    suspend fun getFeaturedWallpapers(count: Int = 10): List<Any> =
        unsplashWallpaperManager.getFeaturedWallpapers(count)
    
    suspend fun saveUnsplashAsBackground(photo: Any, name: String? = null): Boolean =
        unsplashWallpaperManager.saveUnsplashAsBackground(photo, name)
}