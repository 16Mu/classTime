package com.wind.ggbond.classtime.ui.screen.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * 上下课时间配置 ViewModel
 */
@HiltViewModel
class ClassTimeConfigViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val classTimeRepository: ClassTimeRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {
    
    companion object {
        private val BREAK_DURATION_KEY = DataStoreManager.ClassTimeKeys.BREAK_DURATION_KEY
        private val CLASS_DURATION_KEY = DataStoreManager.ClassTimeKeys.CLASS_DURATION_KEY
        private val MORNING_SECTIONS_KEY = DataStoreManager.ClassTimeKeys.MORNING_SECTIONS_KEY
        private val AFTERNOON_SECTIONS_KEY = DataStoreManager.ClassTimeKeys.AFTERNOON_SECTIONS_KEY
        private const val DEFAULT_BREAK_DURATION = DataStoreManager.ClassTimeKeys.DEFAULT_BREAK_DURATION
        private const val DEFAULT_CLASS_DURATION = DataStoreManager.ClassTimeKeys.DEFAULT_CLASS_DURATION
        private const val DEFAULT_MORNING_SECTIONS = DataStoreManager.ClassTimeKeys.DEFAULT_MORNING_SECTIONS
        private const val DEFAULT_AFTERNOON_SECTIONS = DataStoreManager.ClassTimeKeys.DEFAULT_AFTERNOON_SECTIONS
    }
    
    private val classTimeDataStore = DataStoreManager.getClassTimeDataStore(context)
    
    private val _classTimes = MutableStateFlow<List<ClassTime>>(emptyList())
    val classTimes: StateFlow<List<ClassTime>> = _classTimes.asStateFlow()
    
    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()
    
    private val _editingClassTime = MutableStateFlow<ClassTime?>(null)
    val editingClassTime: StateFlow<ClassTime?> = _editingClassTime.asStateFlow()
    
    private val _uniformDuration = MutableStateFlow(false)
    val uniformDuration: StateFlow<Boolean> = _uniformDuration.asStateFlow()
    
    private val _breakDuration = MutableStateFlow(DEFAULT_BREAK_DURATION)
    val breakDuration: StateFlow<Int> = _breakDuration.asStateFlow()
    
    private val _classDuration = MutableStateFlow(DEFAULT_CLASS_DURATION)
    val classDuration: StateFlow<Int> = _classDuration.asStateFlow()
    
    private val _showBreakDurationDialog = MutableStateFlow(false)
    val showBreakDurationDialog: StateFlow<Boolean> = _showBreakDurationDialog.asStateFlow()
    
    private val _showClassDurationDialog = MutableStateFlow(false)
    val showClassDurationDialog: StateFlow<Boolean> = _showClassDurationDialog.asStateFlow()
    
    private val _morningSections = MutableStateFlow(DEFAULT_MORNING_SECTIONS)
    val morningSections: StateFlow<Int> = _morningSections.asStateFlow()
    
    private val _afternoonSections = MutableStateFlow(DEFAULT_AFTERNOON_SECTIONS)
    val afternoonSections: StateFlow<Int> = _afternoonSections.asStateFlow()
    
    private val _showMorningSectionsDialog = MutableStateFlow(false)
    val showMorningSectionsDialog: StateFlow<Boolean> = _showMorningSectionsDialog.asStateFlow()
    
    private val _showAfternoonSectionsDialog = MutableStateFlow(false)
    val showAfternoonSectionsDialog: StateFlow<Boolean> = _showAfternoonSectionsDialog.asStateFlow()
    
    // 当前课表状态
    private val _currentSchedule = MutableStateFlow<Schedule?>(null)
    val currentSchedule: StateFlow<Schedule?> = _currentSchedule.asStateFlow()
    
    // 当前时间配置名称
    private val _currentConfigName = MutableStateFlow("default")
    val currentConfigName: StateFlow<String> = _currentConfigName.asStateFlow()
    
    init {
        loadCurrentSchedule()
        loadClassTimes()
        loadBreakDuration()
        loadClassDuration()
        loadSectionCounts()
    }
    
    /**
     * 加载当前课表
     */
    private fun loadCurrentSchedule() {
        viewModelScope.launch {
            scheduleRepository.getCurrentScheduleFlow().collect { schedule ->
                _currentSchedule.value = schedule
                _currentConfigName.value = schedule?.classTimeConfigName ?: "default"
                // 重新加载时间配置
                loadClassTimes()
            }
        }
    }
    
    private fun loadBreakDuration() {
        viewModelScope.launch {
            val preferences = classTimeDataStore.data.first()
            _breakDuration.value = preferences[BREAK_DURATION_KEY] ?: DEFAULT_BREAK_DURATION
        }
    }
    
    private fun loadClassDuration() {
        viewModelScope.launch {
            val preferences = classTimeDataStore.data.first()
            _classDuration.value = preferences[CLASS_DURATION_KEY] ?: DEFAULT_CLASS_DURATION
        }
    }
    
    private fun loadSectionCounts() {
        viewModelScope.launch {
            val preferences = classTimeDataStore.data.first()
            _morningSections.value = preferences[MORNING_SECTIONS_KEY] ?: DEFAULT_MORNING_SECTIONS
            _afternoonSections.value = preferences[AFTERNOON_SECTIONS_KEY] ?: DEFAULT_AFTERNOON_SECTIONS
        }
    }
    
    private fun loadClassTimes() {
        viewModelScope.launch {
            val configName = _currentConfigName.value
            classTimeRepository.getClassTimesByConfig(configName).collect { times ->
                _classTimes.value = times.sortedBy { it.sectionNumber }
            }
        }
    }
    
    /**
     * 为当前课表设置时间配置
     */
    suspend fun setConfigForCurrentSchedule(configName: String) {
        val currentSchedule = _currentSchedule.value ?: return
        classTimeRepository.setClassTimeConfigForSchedule(currentSchedule.id, configName)
        _currentConfigName.value = configName
        loadClassTimes()
    }
    
    /**
     * 创建默认12节课时间配置模板
     * 根据用户提供的时间安排：
     * 上午8:30-9:10 9:20-10:00 
     * 10:30-11:10  11:20-12:00
     * 12:20-13:00  13:10-13:50
     * 2:30-3:10 3:20-4:00
     * 4:20-5:00 5:10-5:50
     * 7:00-7:40 7:50-8:30
     */
    suspend fun createDefault12SectionConfig() {
        val configName = "default_12_sections"
        val defaultTimes = listOf(
            // 上午
            ClassTime(sectionNumber = 1, startTime = LocalTime.of(8, 30), endTime = LocalTime.of(9, 10), configName = configName),
            ClassTime(sectionNumber = 2, startTime = LocalTime.of(9, 20), endTime = LocalTime.of(10, 0), configName = configName),
            ClassTime(sectionNumber = 3, startTime = LocalTime.of(10, 30), endTime = LocalTime.of(11, 10), configName = configName),
            ClassTime(sectionNumber = 4, startTime = LocalTime.of(11, 20), endTime = LocalTime.of(12, 0), configName = configName),
            ClassTime(sectionNumber = 5, startTime = LocalTime.of(12, 20), endTime = LocalTime.of(13, 0), configName = configName),
            ClassTime(sectionNumber = 6, startTime = LocalTime.of(13, 10), endTime = LocalTime.of(13, 50), configName = configName),
            // 下午
            ClassTime(sectionNumber = 7, startTime = LocalTime.of(14, 30), endTime = LocalTime.of(15, 10), configName = configName),
            ClassTime(sectionNumber = 8, startTime = LocalTime.of(15, 20), endTime = LocalTime.of(16, 0), configName = configName),
            ClassTime(sectionNumber = 9, startTime = LocalTime.of(16, 20), endTime = LocalTime.of(17, 0), configName = configName),
            ClassTime(sectionNumber = 10, startTime = LocalTime.of(17, 10), endTime = LocalTime.of(17, 50), configName = configName),
            // 晚上
            ClassTime(sectionNumber = 11, startTime = LocalTime.of(19, 0), endTime = LocalTime.of(19, 40), configName = configName),
            ClassTime(sectionNumber = 12, startTime = LocalTime.of(19, 50), endTime = LocalTime.of(20, 30), configName = configName)
        )
        
        // 删除已存在的配置（如果有）
        classTimeRepository.deleteAllByConfig(configName)
        
        // 插入新的默认配置
        classTimeRepository.insertClassTimes(defaultTimes)
        
        // 更新节次数设置
        _morningSections.value = 6  // 上午6节
        _afternoonSections.value = 4  // 下午4节
        _uniformDuration.value = true  // 统一时长40分钟
        _classDuration.value = 40  // 40分钟一节课
        _breakDuration.value = 10  // 10分钟课间休息
        
        // 保存到DataStore
        viewModelScope.launch {
            classTimeDataStore.edit { preferences ->
                preferences[MORNING_SECTIONS_KEY] = 6
                preferences[AFTERNOON_SECTIONS_KEY] = 4
                preferences[CLASS_DURATION_KEY] = 40
                preferences[BREAK_DURATION_KEY] = 10
            }
        }
    }
    
    fun showEditDialog(classTime: ClassTime) {
        _editingClassTime.value = classTime
        _showEditDialog.value = true
    }
    
    fun hideEditDialog() {
        _showEditDialog.value = false
        _editingClassTime.value = null
    }
    
    fun updateUniformDuration(enabled: Boolean) {
        _uniformDuration.value = enabled
    }
    
    fun showBreakDurationDialog() {
        _showBreakDurationDialog.value = true
    }
    
    fun hideBreakDurationDialog() {
        _showBreakDurationDialog.value = false
    }
    
    fun updateBreakDuration(minutes: Int) {
        viewModelScope.launch {
            classTimeDataStore.edit { preferences ->
                preferences[BREAK_DURATION_KEY] = minutes
            }
            _breakDuration.value = minutes
            // 重新生成课程时间表
            regenerateClassTimes()
        }
    }
    
    fun showClassDurationDialog() {
        _showClassDurationDialog.value = true
    }
    
    fun hideClassDurationDialog() {
        _showClassDurationDialog.value = false
    }
    
    fun updateClassDuration(minutes: Int) {
        viewModelScope.launch {
            classTimeDataStore.edit { preferences ->
                preferences[CLASS_DURATION_KEY] = minutes
            }
            _classDuration.value = minutes
            // 重新生成课程时间表
            regenerateClassTimes()
        }
    }
    
    fun showMorningSectionsDialog() {
        _showMorningSectionsDialog.value = true
    }
    
    fun hideMorningSectionsDialog() {
        _showMorningSectionsDialog.value = false
    }
    
    fun showAfternoonSectionsDialog() {
        _showAfternoonSectionsDialog.value = true
    }
    
    fun hideAfternoonSectionsDialog() {
        _showAfternoonSectionsDialog.value = false
    }
    
    fun updateMorningSections(sections: Int) {
        viewModelScope.launch {
            classTimeDataStore.edit { preferences ->
                preferences[MORNING_SECTIONS_KEY] = sections
            }
            _morningSections.value = sections
            // 重新生成课程时间表
            regenerateClassTimes()
        }
    }
    
    fun updateAfternoonSections(sections: Int) {
        viewModelScope.launch {
            classTimeDataStore.edit { preferences ->
                preferences[AFTERNOON_SECTIONS_KEY] = sections
            }
            _afternoonSections.value = sections
            // 重新生成课程时间表
            regenerateClassTimes()
        }
    }
    
    /**
     * 从导入的课程中自动检测并设置上午/下午节次数
     * 供教务系统导入时调用
     */
    fun autoDetectSectionsFromCourses(courses: List<com.wind.ggbond.classtime.data.local.entity.Course>) {
        viewModelScope.launch {
            if (courses.isEmpty()) return@launch
            
            // 找出最大的节次号
            val maxSection = courses.maxOfOrNull { it.startSection + it.sectionCount - 1 } ?: 0
            
            // 简单规则：第1-5节算上午，第6节及以后算下午
            // 可以根据实际情况调整这个分界点
            val morningCutoff = 5
            
            val morningSections = minOf(maxSection, morningCutoff)
            val afternoonSections = maxOf(0, maxSection - morningCutoff)
            
            // 保存设置
            classTimeDataStore.edit { preferences ->
                preferences[MORNING_SECTIONS_KEY] = morningSections
                preferences[AFTERNOON_SECTIONS_KEY] = afternoonSections
            }
            _morningSections.value = morningSections
            _afternoonSections.value = afternoonSections
            
            // 重新生成课程时间表
            regenerateClassTimes()
        }
    }
    
    /**
     * 根据上午和下午节次数重新生成课程时间表
     */
    private suspend fun regenerateClassTimes() {
        val morningSections = _morningSections.value
        val afternoonSections = _afternoonSections.value
        val totalSections = morningSections + afternoonSections
        val configName = _currentConfigName.value
        
        // 如果总节次为0，清空所有课程时间
        if (totalSections == 0) {
            classTimeRepository.deleteAllByConfig(configName)
            return
        }
        
        // 删除所有现有时间
        classTimeRepository.deleteAllByConfig(configName)
        
        // 生成新的时间表
        val newTimes = mutableListOf<ClassTime>()
        val breakMinutes = _breakDuration.value.toLong()
        val defaultDuration = _classDuration.value.toLong() // 使用用户设置的课程时长
        
        // 上午课程 (从8:00开始)
        var currentStartTime = LocalTime.of(8, 0)
        for (i in 1..morningSections) {
            val endTime = currentStartTime.plusMinutes(defaultDuration)
            newTimes.add(
                ClassTime(
                    sectionNumber = i,
                    startTime = currentStartTime,
                    endTime = endTime,
                    configName = configName
                )
            )
            currentStartTime = endTime.plusMinutes(breakMinutes)
        }
        
        // 下午课程 (从14:00开始，保证午休间隔)
        if (afternoonSections > 0) {
            // 无论上午是否有课，下午统一从14:00开始（保证午休时间）
            // 如果上午课程结束时间晚于14:00，则在上午结束后加30分钟午休
            val afternoonDefaultStart = LocalTime.of(14, 0)
            currentStartTime = if (morningSections > 0 && currentStartTime.isAfter(afternoonDefaultStart)) {
                // 上午课已超过14:00，加30分钟午休
                currentStartTime.plusMinutes(30)
            } else {
                afternoonDefaultStart
            }
            
            for (i in 1..afternoonSections) {
                val sectionNumber = morningSections + i
                val endTime = currentStartTime.plusMinutes(defaultDuration)
                newTimes.add(
                    ClassTime(
                        sectionNumber = sectionNumber,
                        startTime = currentStartTime,
                        endTime = endTime,
                        configName = configName
                    )
                )
                currentStartTime = endTime.plusMinutes(breakMinutes)
            }
        }
        
        // 插入新的课程时间
        if (newTimes.isNotEmpty()) {
            classTimeRepository.insertClassTimes(newTimes)
        }
    }
    
    fun updateClassTime(classTime: ClassTime) {
        viewModelScope.launch {
            classTimeRepository.updateClassTime(classTime)
            // 自动调整后续节次的时间（保持各节次原有时长）
            adjustSubsequentClassTimes(classTime)
        }
    }
    
    /**
     * 更新课程时间并应用统一时长
     * 用于"统一时长"模式下的更新
     */
    fun updateClassTimeAndApplyUniform(classTime: ClassTime) {
        viewModelScope.launch {
            classTimeRepository.updateClassTime(classTime)
            // 应用统一时长到所有节次
            applyUniformDuration()
        }
    }
    
    /**
     * 调整后续节次的时间
     * 当修改某节课时间后，自动将后续节次的时间往后推
     */
    private suspend fun adjustSubsequentClassTimes(changedClassTime: ClassTime) {
        val allTimes = _classTimes.value.sortedBy { it.sectionNumber }
        val changedIndex = allTimes.indexOfFirst { it.id == changedClassTime.id }
        
        if (changedIndex == -1 || changedIndex >= allTimes.size - 1) {
            // 如果是最后一节或找不到，不需要调整
            return
        }
        
        // 使用用户设置的课间休息时间
        val breakMinutes = _breakDuration.value.toLong()
        
        // 从修改的节次的下一节开始调整
        var previousEndTime = changedClassTime.endTime
        
        for (i in (changedIndex + 1) until allTimes.size) {
            val currentClassTime = allTimes[i]
            val currentDuration = java.time.Duration.between(currentClassTime.startTime, currentClassTime.endTime)
            
            // 新的开始时间 = 前一节的结束时间 + 课间休息
            val newStartTime = previousEndTime.plusMinutes(breakMinutes)
            val newEndTime = newStartTime.plus(currentDuration)
            
            // 只有当时间真的需要改变时才更新
            if (newStartTime != currentClassTime.startTime || newEndTime != currentClassTime.endTime) {
                val updatedClassTime = currentClassTime.copy(
                    startTime = newStartTime,
                    endTime = newEndTime
                )
                classTimeRepository.updateClassTime(updatedClassTime)
                previousEndTime = newEndTime
            } else {
                // 如果不需要改变，后面的也不需要改变了
                break
            }
        }
    }
    
    /**
     * 应用统一时长到所有节次
     */
    fun applyUniformDuration() {
        viewModelScope.launch {
            // 从数据库获取最新数据，而不是使用缓存的_classTimes.value
            val latestTimes = classTimeRepository.getClassTimesByConfigSync(_currentConfigName.value)
            if (latestTimes.isEmpty()) return@launch
            
            val sortedTimes = latestTimes.sortedBy { it.sectionNumber }
            val firstClassTime = sortedTimes.first()
            val duration = java.time.Duration.between(firstClassTime.startTime, firstClassTime.endTime)
            
            val updatedTimes = sortedTimes.mapIndexed { index, classTime ->
                val newStartTime = if (index == 0) {
                    classTime.startTime
                } else {
                    val prevClassTime = sortedTimes[index - 1]
                    prevClassTime.endTime.plusMinutes(_breakDuration.value.toLong())
                }
                val newEndTime = newStartTime.plus(duration)
                
                classTime.copy(
                    startTime = newStartTime,
                    endTime = newEndTime
                )
            }
            
            // 批量更新所有节次
            updatedTimes.forEach { classTimeRepository.updateClassTime(it) }
        }
    }
    
    /**
     * 重置为默认时间表
     */
    fun resetToDefault() {
        viewModelScope.launch {
            // 删除所有现有时间配置
            classTimeRepository.deleteAllByConfig("default")
            
            // 添加默认时间表（参考图片：上午4节，下午5节，晚上1节）
            val defaultTimes = listOf(
                // 上午
                ClassTime(sectionNumber = 1, startTime = LocalTime.of(8, 30), endTime = LocalTime.of(9, 10)),
                ClassTime(sectionNumber = 2, startTime = LocalTime.of(9, 20), endTime = LocalTime.of(10, 0)),
                ClassTime(sectionNumber = 3, startTime = LocalTime.of(10, 30), endTime = LocalTime.of(11, 10)),
                ClassTime(sectionNumber = 4, startTime = LocalTime.of(11, 20), endTime = LocalTime.of(12, 0)),
                // 下午
                ClassTime(sectionNumber = 5, startTime = LocalTime.of(12, 40), endTime = LocalTime.of(13, 20)),
                ClassTime(sectionNumber = 6, startTime = LocalTime.of(13, 30), endTime = LocalTime.of(14, 10)),
                ClassTime(sectionNumber = 7, startTime = LocalTime.of(14, 30), endTime = LocalTime.of(15, 10)),
                ClassTime(sectionNumber = 8, startTime = LocalTime.of(15, 20), endTime = LocalTime.of(16, 0)),
                ClassTime(sectionNumber = 9, startTime = LocalTime.of(16, 20), endTime = LocalTime.of(17, 0)),
                // 晚上
                ClassTime(sectionNumber = 10, startTime = LocalTime.of(19, 0), endTime = LocalTime.of(19, 40))
            )
            classTimeRepository.insertClassTimes(defaultTimes)
        }
    }
}


