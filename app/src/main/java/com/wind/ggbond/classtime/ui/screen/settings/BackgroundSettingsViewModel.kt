package com.wind.ggbond.classtime.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.ui.theme.BackgroundScheme
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.ui.theme.BackgroundType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BgSettingsVM"

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class BackgroundSettingsViewModel @Inject constructor(
    private val backgroundThemeManager: BackgroundThemeManager,
    private val backgroundExportManager: BackgroundExportManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val mediaFileManager = com.wind.ggbond.classtime.util.MediaFileManager(context)
    
    private var settingsCollectionJob: Job? = null
    
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
    
    private val _blurRadiusDebounce = MutableStateFlow(-1)
    private val _dimAmountDebounce = MutableStateFlow(-1)

    init {
        _blurRadiusDebounce
            .debounce(300)
            .filter { it >= 0 }
            .onEach { backgroundThemeManager.setBlurRadius(it) }
            .launchIn(viewModelScope)

        _dimAmountDebounce
            .debounce(300)
            .filter { it >= 0 }
            .onEach { backgroundThemeManager.setDimAmount(it) }
            .launchIn(viewModelScope)

        loadCurrentSettings()
    }

    fun updateBlurRadius(radius: Float) {
        _uiState.update { it.copy(blurRadius = radius.toInt()) }
        _blurRadiusDebounce.value = radius.toInt()
    }
    
    fun updateDimAmount(amount: Float) {
        _uiState.update { it.copy(dimAmount = amount.toInt()) }
        _dimAmountDebounce.value = amount.toInt()
    }
    
    private fun loadCurrentSettings() {
        settingsCollectionJob?.cancel()
        settingsCollectionJob = viewModelScope.launch {
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
                _uiState.update { current ->
                    current.copy(
                        backgroundSchemes = newState.backgroundSchemes,
                        activeBackgroundIndex = newState.activeBackgroundIndex,
                        activeScheme = newState.activeScheme,
                        blurRadius = newState.blurRadius,
                        dimAmount = newState.dimAmount,
                        isDynamicThemeEnabled = newState.isDynamicThemeEnabled,
                        seedColor = newState.seedColor
                    )
                }
            }
        }
    }
    
    fun showImagePicker() {
        AppLogger.d(TAG, "[showImagePicker]")
        _uiState.update { it.copy(showImagePicker = true) }
    }
    
    fun hideImagePicker() {
        AppLogger.d(TAG, "[hideImagePicker]")
        _uiState.update { it.copy(showImagePicker = false) }
    }
    
    fun onImageSelected(uri: Uri) {
        AppLogger.d(TAG, "图片处理开始: uri=$uri")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val localUri = mediaFileManager.copyMediaToPrivateStorage(uri, "jpg")
                if (localUri == null) {
                    AppLogger.e(TAG, "图片处理失败: 文件复制失败")
                    _uiState.update { it.copy(isLoading = false, showImagePicker = false) }
                    return@launch
                }

                val seedColor = backgroundThemeManager.extractSeedColorFromUri(localUri)

                val scheme = BackgroundScheme(
                    uri = localUri.toString(),
                    type = BackgroundType.IMAGE,
                    seedColor = seedColor,
                    blurRadius = _uiState.value.blurRadius,
                    dimAmount = _uiState.value.dimAmount,
                    name = "图片背景 ${_uiState.value.backgroundSchemes.size + 1}"
                )

                val success = backgroundThemeManager.addAndActivateBackgroundScheme(scheme)

                if (!success) {
                    AppLogger.e(TAG, "图片处理失败: 添加方案失败")
                    _uiState.update { it.copy(isLoading = false, showImagePicker = false) }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showImagePicker = false,
                        seedColor = seedColor,
                        isDynamicThemeEnabled = true,
                        backgroundAppliedSuccess = true
                    )
                }
                AppLogger.d(TAG, "图片处理完成")
            } catch (e: Exception) {
                AppLogger.e(TAG, "图片处理异常", e)
                _uiState.update { it.copy(isLoading = false, showImagePicker = false) }
            }
        }
    }
    
    fun showVideoPicker() {
        AppLogger.d(TAG, "[showVideoPicker]")
        _uiState.update { it.copy(showVideoPicker = true) }
    }
    
    fun hideVideoPicker() {
        AppLogger.d(TAG, "[hideVideoPicker]")
        _uiState.update { it.copy(showVideoPicker = false) }
    }
    
    fun onVideoSelected(uri: Uri) {
        AppLogger.d(TAG, "视频处理开始: uri=$uri")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val localUri = mediaFileManager.copyMediaToPrivateStorage(uri, "mp4")
                if (localUri == null) {
                    AppLogger.e(TAG, "视频处理失败: 文件复制失败")
                    _uiState.update { it.copy(isLoading = false, showVideoPicker = false) }
                    return@launch
                }

                val seedColor = backgroundThemeManager.extractSeedColorFromVideoFirstFrame(localUri)

                val scheme = BackgroundScheme(
                    uri = localUri.toString(),
                    type = BackgroundType.VIDEO,
                    seedColor = seedColor,
                    blurRadius = _uiState.value.blurRadius,
                    dimAmount = _uiState.value.dimAmount,
                    name = "视频背景 ${_uiState.value.backgroundSchemes.size + 1}"
                )

                val success = backgroundThemeManager.addAndActivateBackgroundScheme(scheme)

                if (!success) {
                    AppLogger.e(TAG, "视频处理失败: 添加方案失败")
                    _uiState.update { it.copy(isLoading = false, showVideoPicker = false) }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showVideoPicker = false,
                        seedColor = seedColor,
                        isDynamicThemeEnabled = true,
                        backgroundAppliedSuccess = true
                    )
                }
                AppLogger.d(TAG, "视频处理完成")
            } catch (e: Exception) {
                AppLogger.e(TAG, "视频处理异常", e)
                _uiState.update { it.copy(isLoading = false, showVideoPicker = false) }
            }
        }
    }
    
    fun showGifPicker() {
        AppLogger.d(TAG, "[showGifPicker]")
        _uiState.update { it.copy(showGifPicker = true) }
    }
    
    fun hideGifPicker() {
        AppLogger.d(TAG, "[hideGifPicker]")
        _uiState.update { it.copy(showGifPicker = false) }
    }
    
    fun onGifSelected(uri: Uri) {
        AppLogger.d(TAG, "GIF处理开始: uri=$uri")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val localUri = mediaFileManager.copyMediaToPrivateStorage(uri, "gif")
                if (localUri == null) {
                    AppLogger.e(TAG, "GIF处理失败: 文件复制失败")
                    _uiState.update { it.copy(isLoading = false, showGifPicker = false) }
                    return@launch
                }

                val seedColor = backgroundThemeManager.extractSeedColorFromUri(localUri)

                val scheme = BackgroundScheme(
                    uri = localUri.toString(),
                    type = BackgroundType.GIF,
                    seedColor = seedColor,
                    blurRadius = _uiState.value.blurRadius,
                    dimAmount = _uiState.value.dimAmount,
                    name = "GIF 背景 ${_uiState.value.backgroundSchemes.size + 1}"
                )

                val success = backgroundThemeManager.addAndActivateBackgroundScheme(scheme)

                if (!success) {
                    AppLogger.e(TAG, "GIF处理失败: 添加方案失败")
                    _uiState.update { it.copy(isLoading = false, showGifPicker = false) }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showGifPicker = false,
                        seedColor = seedColor,
                        isDynamicThemeEnabled = true,
                        backgroundAppliedSuccess = true
                    )
                }
                AppLogger.d(TAG, "GIF处理完成")
            } catch (e: Exception) {
                AppLogger.e(TAG, "GIF处理异常", e)
                _uiState.update { it.copy(isLoading = false, showGifPicker = false) }
            }
        }
    }
    
    fun switchToBackground(index: Int) {
        AppLogger.d(TAG, "[switchToBackground] START: targetIndex=$index, currentSchemes=${_uiState.value.backgroundSchemes.size}")
        viewModelScope.launch {
            backgroundThemeManager.setActiveBackgroundIndex(index)
            _uiState.update { it.copy(backgroundAppliedSuccess = true) }
            AppLogger.d(TAG, "[switchToBackground] END: Switched to index $index")
        }
    }
    
    fun deleteBackground(index: Int) {
        AppLogger.d(TAG, "[deleteBackground] Showing delete confirm for index=$index")
        _uiState.update { it.copy(pendingDeleteIndex = index, showDeleteConfirmDialog = true) }
    }
    
    fun confirmDeleteBackground() {
        val index = _uiState.value.pendingDeleteIndex
        AppLogger.d(TAG, "[confirmDeleteBackground] START: index=$index")
        
        if (index >= 0) { 
            viewModelScope.launch { 
                val scheme = _uiState.value.backgroundSchemes.getOrNull(index)
                AppLogger.d(TAG, "[confirmDeleteBackground] Scheme to delete: ${scheme?.name}, uri=${scheme?.uri}")
                
                AppLogger.d(TAG, "[confirmDeleteBackground] Step 1: Removing from BackgroundThemeManager")
                backgroundThemeManager.removeBackgroundScheme(index)
                
                AppLogger.d(TAG, "[confirmDeleteBackground] Step 2: Deleting local file")
                scheme?.let {
                    try {
                        val uri = Uri.parse(it.uri)
                        val deleted = mediaFileManager.deleteBackgroundFile(uri)
                        AppLogger.d(TAG, "[confirmDeleteBackground] File deletion result: $deleted")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "[confirmDeleteBackground] EXCEPTION: Failed to delete background file", e)
                    }
                }
                
                _uiState.update { it.copy(showDeleteConfirmDialog = false, pendingDeleteIndex = -1) }
                AppLogger.d(TAG, "[confirmDeleteBackground] END: Background deleted successfully")
            } 
        } else {
            AppLogger.w(TAG, "[confirmDeleteBackground] Invalid index: $index")
            _uiState.update { it.copy(showDeleteConfirmDialog = false, pendingDeleteIndex = -1) }
        }
    }
    
    fun cancelDeleteBackground() {
        AppLogger.d(TAG, "[cancelDeleteBackground]")
        _uiState.update { it.copy(showDeleteConfirmDialog = false, pendingDeleteIndex = -1) }
    }
    
    fun consumeBackgroundAppliedSuccess() {
        AppLogger.d(TAG, "[consumeBackgroundAppliedSuccess]")
        _uiState.update { it.copy(backgroundAppliedSuccess = false) }
    }
    
    fun showRenameDialog(index: Int) {
        val scheme = _uiState.value.backgroundSchemes.getOrNull(index)
        AppLogger.d(TAG, "[showRenameDialog] index=$index, currentName=${scheme?.name}")
        if (scheme != null) { _uiState.update { it.copy(pendingRenameIndex = index, pendingRenameName = scheme.name, showRenameDialog = true) } }
    }
    
    fun hideRenameDialog() {
        AppLogger.d(TAG, "[hideRenameDialog]")
        _uiState.update { it.copy(showRenameDialog = false, pendingRenameIndex = -1) }
    }
    
    fun updateRenameName(name: String) {
        AppLogger.d(TAG, "[updateRenameName] name=$name")
        _uiState.update { it.copy(pendingRenameName = name) }
    }
    
    fun confirmRename() {
        val index = _uiState.value.pendingRenameIndex
        val newName = _uiState.value.pendingRenameName.trim()
        
        if (index >= 0 && newName.isNotEmpty()) {
            val isActive = index == _uiState.value.activeBackgroundIndex
            viewModelScope.launch {
                val scheme = _uiState.value.backgroundSchemes.getOrNull(index)
                    ?: return@launch
                
                val updatedScheme = scheme.copy(name = newName)
                backgroundThemeManager.renameBackgroundScheme(index, updatedScheme, isActive)
                
                _uiState.update { it.copy(showRenameDialog = false, pendingRenameIndex = -1) }
            }
        } else {
            _uiState.update { it.copy(showRenameDialog = false, pendingRenameIndex = -1) }
        }
    }
    
    fun showColorPicker() {
        _uiState.update { it.copy(showColorPicker = true) }
    }
    
    fun hideColorPicker() {
        AppLogger.d(TAG, "[hideColorPicker]")
        _uiState.update { it.copy(showColorPicker = false) }
    }
    
    fun onSeedColorSelected(color: Int) {
        AppLogger.d(TAG, "[onSeedColorSelected] START: color=#${Integer.toHexString(color)}")
        viewModelScope.launch {
            backgroundThemeManager.setSeedColor(color)
            _uiState.update { it.copy(seedColor = color, isDynamicThemeEnabled = true, showColorPicker = false) }
            AppLogger.d(TAG, "[onSeedColorSelected] END: Seed color updated successfully")
        }
    }
    
    fun clearAllBackgrounds() {
        AppLogger.d(TAG, "[clearAllBackgrounds] START: Clearing all backgrounds")
        viewModelScope.launch {
            AppLogger.d(TAG, "[clearAllBackgrounds] Step 1: Clearing local files")
            mediaFileManager.clearAllBackgrounds()
            
            AppLogger.d(TAG, "[clearAllBackgrounds] Step 2: Clearing BackgroundThemeManager data")
            backgroundThemeManager.clearBackground()
            
            _uiState.update {
                it.copy(
                    backgroundSchemes = emptyList(),
                    activeBackgroundIndex = 0,
                    activeScheme = null,
                    blurRadius = DataStoreManager.SettingsKeys.DEFAULT_BLUR_RADIUS,
                    dimAmount = DataStoreManager.SettingsKeys.DEFAULT_DIM_AMOUNT,
                    isDynamicThemeEnabled = false,
                    seedColor = 0
                )
            }
            AppLogger.d(TAG, "[clearAllBackgrounds] END: All backgrounds cleared successfully")
        }
    }
    
    fun toggleDynamicTheme(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                backgroundThemeManager.setUseDynamicTheme(false)
                _uiState.update { it.copy(isDynamicThemeEnabled = false) }
            } else if (_uiState.value.backgroundSchemes.isNotEmpty()) {
                backgroundThemeManager.setActiveBackgroundIndex(_uiState.value.activeBackgroundIndex)
                _uiState.update { it.copy(isDynamicThemeEnabled = true) }
            } else {
                AppLogger.w(TAG, "无法开启动态主题: 没有可用的背景方案")
            }
        }
    }
    
    suspend fun exportAndShare(): Intent? {
        AppLogger.d(TAG, "[exportAndShare] START")
        val schemes = _uiState.value.backgroundSchemes
        if (schemes.isEmpty()) {
            AppLogger.w(TAG, "[exportAndShare] No schemes to export")
            return null
        }
        
        AppLogger.d(TAG, "[exportAndShare] Exporting ${schemes.size} schemes")
        val uri = backgroundExportManager.exportSchemes(schemes) ?: run {
            AppLogger.e(TAG, "[exportAndShare] Export failed")
            return null
        }
        
        val intent = backgroundExportManager.createShareIntent(uri)
        AppLogger.d(TAG, "[exportAndShare] END: Share intent created")
        return intent
    }
    
    suspend fun importFromUri(uri: Uri): Int {
        AppLogger.d(TAG, "[importFromUri] START: uri=$uri")
        val schemes = backgroundExportManager.importSchemes(uri)
        if (schemes.isEmpty()) {
            AppLogger.w(TAG, "[importFromUri] No schemes imported")
            return 0
        }
        
        AppLogger.d(TAG, "[importFromUri] Imported ${schemes.size} schemes, current count=${_uiState.value.backgroundSchemes.size}/10")
        var addedCount = 0
        for ((i, scheme) in schemes.withIndex()) {
            if (_uiState.value.backgroundSchemes.size < 10) {
                AppLogger.d(TAG, "[importFromUri] Adding scheme $i/${schemes.size}: ${scheme.name}")
                backgroundThemeManager.addBackgroundScheme(scheme)
                addedCount++
            } else {
                AppLogger.w(TAG, "[importFromUri] Max backgrounds reached, skipping remaining schemes")
                break
            }
        }
        
        AppLogger.d(TAG, "[importFromUri] Reloading settings")
        loadCurrentSettings()
        AppLogger.d(TAG, "[importFromUri] END: Added $addedCount schemes")
        return addedCount
    }
    
    data class ValidationResult(val isValid: Boolean, val message: String = "")
    
    suspend fun validateImportFile(uri: Uri): ValidationResult {
        AppLogger.d(TAG, "[validateImportFile] uri=$uri")
        val result = backgroundExportManager.validateImportFile(uri)
        return ValidationResult(result.isValid, result.message)
    }
}
