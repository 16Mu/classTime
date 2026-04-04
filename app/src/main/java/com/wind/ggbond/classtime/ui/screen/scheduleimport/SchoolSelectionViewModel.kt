package com.wind.ggbond.classtime.ui.screen.scheduleimport

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.local.entity.SchoolEntity
import com.wind.ggbond.classtime.data.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/**
 * 学校分组数据类
 */
data class SchoolGroup(
    val province: String,
    val schools: List<SchoolEntity>
) {
    val schoolCount: Int get() = schools.size
}

/**
 * 学校选择 ViewModel
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SchoolSelectionViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : ViewModel() {
    
    private val dataStore: DataStore<Preferences> = DataStoreManager.getSettingsDataStore(context)
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _selectedSchool = MutableStateFlow<SchoolEntity?>(null)
    val selectedSchool: StateFlow<SchoolEntity?> = _selectedSchool.asStateFlow()
    
    // 搜索模式：扁平列表
    val schools: StateFlow<List<SchoolEntity>> = searchQuery
        .debounce(300) // 防抖，300ms 后执行搜索
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                flowOf(emptyList()) // 搜索为空时返回空列表，使用分组列表
            } else {
                schoolRepository.searchSchools(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 分组模式：按省份分组
    // Room的Flow会自动在数据变化时发出新值，所以直接订阅即可
    val schoolGroups: StateFlow<List<SchoolGroup>> = schoolRepository
        .getAllProvinces()
        .onEach { provinces ->
            android.util.Log.d("SchoolSelectionViewModel", "getAllProvinces 更新，省份数量：${provinces.size}")
        }
        .flatMapLatest { provinces ->
            android.util.Log.d("SchoolSelectionViewModel", "flatMapLatest 处理，省份：$provinces")
            if (provinces.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    provinces.map { province ->
                        schoolRepository.getSchoolsByProvince(province)
                            .onEach { schools ->
                                android.util.Log.d("SchoolSelectionViewModel", "省份 $province 的学校数量：${schools.size}")
                            }
                            .map { schools -> province to schools }
                    }
                ) { provinceSchoolsArray ->
                    val groups = provinceSchoolsArray
                        .map { (province, schools) -> SchoolGroup(province, schools) }
                        .sortedWith(compareBy(Collator.getInstance(Locale.CHINESE)) { it.province })
                    android.util.Log.d("SchoolSelectionViewModel", "schoolGroups 更新，分组数量：${groups.size}")
                    groups
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 省份索引列表（用于右侧字母索引）
    val provinceIndex: StateFlow<List<String>> = schoolGroups
        .map { groups -> groups.map { it.province } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 最近使用的学校（最多5个）
    val recentSchools: StateFlow<List<SchoolEntity>> = dataStore.data
        .map { prefs ->
            val recentIdsJson = prefs[DataStoreManager.SettingsKeys.RECENT_SCHOOLS_KEY] ?: "[]"
            try {
                val recentIds: List<String> = gson.fromJson(recentIdsJson, Array<String>::class.java).toList()
                recentIds.take(DataStoreManager.SettingsKeys.MAX_RECENT_SCHOOLS)
            } catch (e: Exception) {
                emptyList()
            }
        }
        .flatMapLatest { recentIds ->
            if (recentIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(recentIds.map { id ->
                    flow {
                        emit(schoolRepository.getSchoolById(id))
                    }
                }) { schools ->
                    schools.filterNotNull()
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        // 初始化学校数据
        viewModelScope.launch {
            android.util.Log.d("SchoolSelectionViewModel", "开始初始化学校数据...")
            _isLoading.value = true
            try {
                // 检查数据库中是否已有数据
                val initialCount = schoolRepository.getAllSchools().first().size
                android.util.Log.d("SchoolSelectionViewModel", "初始化前数据库中的学校数量：$initialCount")
                
                // 从 assets 加载数据
                android.util.Log.d("SchoolSelectionViewModel", "开始从 assets 加载学校数据...")
                schoolRepository.initializeSchoolsFromAssets()
                
                // 等待一小段时间确保数据库写入完成
                kotlinx.coroutines.delay(200)
                
                // 验证数据是否加载成功
                val schoolCount = schoolRepository.getAllSchools().first().size
                android.util.Log.d("SchoolSelectionViewModel", "数据加载完成，学校数量：$schoolCount")
                
                // 检查省份列表
                val provinces = schoolRepository.getAllProvinces().first()
                android.util.Log.d("SchoolSelectionViewModel", "省份列表：$provinces")
                
                // 检查是否有启用的学校
                val enabledSchools = schoolRepository.getAllSchools().first()
                android.util.Log.d("SchoolSelectionViewModel", "启用的学校数量：${enabledSchools.size}")
                
                if (enabledSchools.isNotEmpty()) {
                    android.util.Log.d("SchoolSelectionViewModel", "第一个启用的学校：${enabledSchools.first().name}")
                } else {
                    android.util.Log.w("SchoolSelectionViewModel", "⚠️ 没有启用的学校！")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SchoolSelectionViewModel", "数据加载失败", e)
            } finally {
                _isLoading.value = false
                android.util.Log.d("SchoolSelectionViewModel", "初始化完成，loading状态：${_isLoading.value}")
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
    }
    
    fun selectSchool(school: SchoolEntity) {
        _selectedSchool.value = school
        // 保存到最近使用列表
        saveRecentSchool(school.id)
    }
    
    /**
     * 保存最近使用的学校
     */
    private fun saveRecentSchool(schoolId: String) {
        viewModelScope.launch {
            try {
                dataStore.edit { prefs ->
                    val currentJson = prefs[DataStoreManager.SettingsKeys.RECENT_SCHOOLS_KEY] ?: "[]"
                    val currentIds: MutableList<String> = try {
                        gson.fromJson(currentJson, Array<String>::class.java).toMutableList()
                    } catch (e: Exception) {
                        mutableListOf()
                    }
                    
                    // 移除已存在的（如果存在）
                    currentIds.remove(schoolId)
                    // 添加到最前面
                    currentIds.add(0, schoolId)
                    // 只保留最多MAX_RECENT_SCHOOLS个
                    val trimmedIds = currentIds.take(DataStoreManager.SettingsKeys.MAX_RECENT_SCHOOLS)
                    
                    prefs[DataStoreManager.SettingsKeys.RECENT_SCHOOLS_KEY] = gson.toJson(trimmedIds)
                }
            } catch (e: Exception) {
                android.util.Log.e("SchoolSelectionViewModel", "保存最近使用学校失败", e)
            }
        }
    }
}
