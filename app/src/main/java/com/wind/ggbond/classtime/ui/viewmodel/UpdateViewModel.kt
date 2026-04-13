package com.wind.ggbond.classtime.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.edit
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.service.ApkDownloadManager
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.AnnouncementChecker
import com.wind.ggbond.classtime.util.AnnouncementInfo
import com.wind.ggbond.classtime.util.UpdateChecker
import com.wind.ggbond.classtime.util.VersionInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.lifecycle.asFlow
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val announcementChecker: AnnouncementChecker,
    private val application: Application
) : ViewModel() {
    
    companion object {
        private const val TAG = "UpdateViewModel"
        private const val KEY_LAST_CHECK_TIME = "last_update_check_time"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
    
    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class UpdateAvailable(val versionInfo: VersionInfo) : UpdateState()
        object NoUpdate : UpdateState()
        data class Error(val message: String) : UpdateState()
    }
    
    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Int) : DownloadState()
        data class Success(val file: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }
    
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    private val _currentVersion = MutableStateFlow("")
    val currentVersion: StateFlow<String> = _currentVersion.asStateFlow()

    private val _announcements = MutableStateFlow<List<AnnouncementInfo>?>(null)
    val announcements: StateFlow<List<AnnouncementInfo>?> = _announcements.asStateFlow()

    private val _hasUpdate = MutableStateFlow(false)
    val hasUpdate: StateFlow<Boolean> = _hasUpdate.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var cachedVersionInfo: VersionInfo? = null
    
    private var lastCheckTime: Long = 0
    
    init {
        _currentVersion.value = updateChecker.getCurrentVersion(application)
    }
    
    fun checkUpdate(force: Boolean = false) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            
            if (!force && now - lastCheckTime < 60_000L) {
                AppLogger.d(TAG, "1分钟内已检查过，跳过")
                return@launch
            }
            
            lastCheckTime = now
            _updateState.value = UpdateState.Checking
            
            updateChecker.checkUpdate(application).fold(
                onSuccess = { versionInfo ->
                    saveLastCheckTime(now)
                    if (versionInfo != null && versionInfo.needUpdate(_currentVersion.value)) {
                        _updateState.value = UpdateState.UpdateAvailable(versionInfo)
                        _hasUpdate.value = true
                        cachedVersionInfo = versionInfo
                    } else {
                        _updateState.value = UpdateState.NoUpdate
                        _hasUpdate.value = false
                        cachedVersionInfo = null
                    }
                },
                onFailure = { e ->
                    _updateState.value = UpdateState.Error(e.message ?: "检查更新失败")
                }
            )
        }
    }

    fun silentCheckUpdate() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            if (now - lastCheckTime < 60_000L) return@launch

            lastCheckTime = now

            updateChecker.checkUpdate(application).fold(
                onSuccess = { versionInfo ->
                    saveLastCheckTime(now)
                    if (versionInfo != null && versionInfo.needUpdate(_currentVersion.value)) {
                        _hasUpdate.value = true
                        cachedVersionInfo = versionInfo
                        AppLogger.d(TAG, "静默检查发现新版本: ${versionInfo.latestVersion}")
                    } else {
                        _hasUpdate.value = false
                        cachedVersionInfo = null
                    }
                },
                onFailure = { e ->
                    AppLogger.w(TAG, "静默检查更新失败: ${e.message}")
                }
            )
        }
    }

    fun showCachedUpdate() {
        cachedVersionInfo?.let {
            _updateState.value = UpdateState.UpdateAvailable(it)
        }
    }

    fun downloadApk(url: String, sha256: String? = null) {
        ApkDownloadManager.downloadApk(application, url, sha256)
    }

    fun observeDownloadState() {
        viewModelScope.launch {
            ApkDownloadManager.downloadState.asFlow().collect { state ->
                _downloadState.value = when (state) {
                    is ApkDownloadManager.DownloadState.Idle -> DownloadState.Idle
                    is ApkDownloadManager.DownloadState.Downloading -> DownloadState.Downloading(state.progress)
                    is ApkDownloadManager.DownloadState.Success -> DownloadState.Success(state.file)
                    is ApkDownloadManager.DownloadState.Error -> DownloadState.Error(state.message)
                }
            }
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
        ApkDownloadManager.resetState()
    }

    fun fetchAnnouncements() {
        viewModelScope.launch {
            val currentVersion = _currentVersion.value
            val lastReadVersion = getLastReadAnnouncementVersion()
            
            if (currentVersion == lastReadVersion) {
                AppLogger.d(TAG, "当前版本 $currentVersion 的公告已读，跳过")
                return@launch
            }
            
            announcementChecker.fetchAnnouncements(application).fold(
                onSuccess = { list ->
                    if (list.isNotEmpty()) {
                        _announcements.value = list
                    }
                },
                onFailure = { e ->
                    AppLogger.w(TAG, "获取公告失败: ${e.message}")
                }
            )
        }
    }
    
    fun markAnnouncementAsRead() {
        viewModelScope.launch {
            saveLastReadAnnouncementVersion(_currentVersion.value)
        }
    }
    
    fun autoCheckIfNeeded() {
        viewModelScope.launch {
            val lastCheck = getLastCheckTime()
            val now = System.currentTimeMillis()
            
            if (now - lastCheck >= CHECK_INTERVAL_MS) {
                AppLogger.d(TAG, "超过24小时未检查，自动检查更新")
                checkUpdate(force = true)
            } else {
                AppLogger.d(TAG, "未到检查间隔，跳过自动检查")
            }
        }
    }

    fun dismissAnnouncements() {
        _announcements.value = null
    }
    
    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }
    
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
    
    private suspend fun getLastCheckTime(): Long {
        return try {
            val dataStore = DataStoreManager.getSettingsDataStore(application)
            dataStore.data
                .map { it[DataStoreManager.SettingsKeys.LAST_UPDATE_CHECK_TIME_KEY] ?: 0L }
                .first()
        } catch (e: Exception) {
            0L
        }
    }
    
    private suspend fun getLastReadAnnouncementVersion(): String {
        return try {
            val dataStore = DataStoreManager.getSettingsDataStore(application)
            dataStore.data
                .map { it[DataStoreManager.SettingsKeys.LAST_READ_ANNOUNCEMENT_VERSION_KEY] ?: "" }
                .first()
        } catch (e: Exception) {
            ""
        }
    }
    
    private suspend fun saveLastReadAnnouncementVersion(version: String) {
        try {
            val dataStore = DataStoreManager.getSettingsDataStore(application)
            dataStore.edit { prefs ->
                prefs[DataStoreManager.SettingsKeys.LAST_READ_ANNOUNCEMENT_VERSION_KEY] = version
            }
            AppLogger.d(TAG, "已记录公告已读版本: $version")
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存公告已读版本失败: ${e.message}")
        }
    }
    
    private suspend fun saveLastCheckTime(time: Long) {
        try {
            val dataStore = DataStoreManager.getSettingsDataStore(application)
            dataStore.edit { prefs ->
                prefs[DataStoreManager.SettingsKeys.LAST_UPDATE_CHECK_TIME_KEY] = time
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存检查时间失败: ${e.message}")
        }
    }
}
