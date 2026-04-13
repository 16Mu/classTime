package com.wind.ggbond.classtime.ui.screen.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ReminderRepository
import com.wind.ggbond.classtime.domain.usecase.ReminderUseCase
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class GroupedReminders(
    val dateGroups: Map<java.time.LocalDate, List<Reminder>>
)

enum class ReminderFilterType {
    ALL,
    TODAY,
    THIS_WEEK,
    EXPIRED
}

@HiltViewModel
class ReminderManagementViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val courseRepository: CourseRepository,
    private val reminderScheduler: IAlarmScheduler,
    private val reminderUseCase: ReminderUseCase
) : ViewModel() {

    private val _allReminders = MutableStateFlow<List<Reminder>>(emptyList())

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private val _courseMap = MutableStateFlow<Map<Long, Course>>(emptyMap())
    val courseMap: StateFlow<Map<Long, Course>> = _courseMap.asStateFlow()

    private val _stats = MutableStateFlow<ReminderStats?>(null)
    val stats: StateFlow<ReminderStats?> = _stats.asStateFlow()

    private val _filterType = MutableStateFlow(ReminderFilterType.ALL)
    val filterType: StateFlow<ReminderFilterType> = _filterType.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    private val _showClearAllDialog = MutableStateFlow(false)
    val showClearAllDialog: StateFlow<Boolean> = _showClearAllDialog.asStateFlow()

    private val _selectedReminder = MutableStateFlow<Reminder?>(null)
    val selectedReminder: StateFlow<Reminder?> = _selectedReminder.asStateFlow()

    private val _groupedReminders = MutableStateFlow(GroupedReminders(emptyMap()))
    val groupedReminders: StateFlow<GroupedReminders> = _groupedReminders.asStateFlow()

    init {
        loadReminders()
        loadStats()
    }

    private fun loadReminders() {
        viewModelScope.launch {
            reminderRepository.getAllFlow().collect { reminders ->
                _allReminders.value = reminders.sortedBy { it.triggerTime }
                loadCourseInfo(reminders)
                applyFilter()
            }
        }
    }

    private suspend fun loadCourseInfo(reminders: List<Reminder>) {
        val courseIds = reminders.map { it.courseId }.distinct()
        val map = mutableMapOf<Long, Course>()
        courseIds.forEach { courseId ->
            courseRepository.getCourseById(courseId)?.let { course ->
                map[courseId] = course
            }
        }
        _courseMap.value = map
    }

    private fun loadStats() {
        viewModelScope.launch {
            val stats = reminderUseCase.getReminderStats()
            _stats.value = ReminderStats(
                totalReminders = stats.totalReminders,
                activeReminders = stats.activeReminders,
                todayReminders = stats.todayReminders,
                upcomingReminders = stats.upcomingReminders
            )
        }
    }

    fun setFilter(type: ReminderFilterType) {
        _filterType.value = type
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = reminderUseCase.filterReminders(_allReminders.value, _filterType.value)
        _reminders.value = filtered

        val grouped = reminderUseCase.groupRemindersByDate(filtered)
        _groupedReminders.value = GroupedReminders(grouped)
    }

    fun showDeleteDialog(reminder: Reminder) {
        _selectedReminder.value = reminder
        _showDeleteDialog.value = true
    }

    fun hideDeleteDialog() {
        _showDeleteDialog.value = false
        _selectedReminder.value = null
    }

    fun showClearAllDialog() {
        _showClearAllDialog.value = true
    }

    fun hideClearAllDialog() {
        _showClearAllDialog.value = false
    }

    fun confirmClearAll() {
        viewModelScope.launch {
            reminderScheduler.cancelAllReminders()
            _showClearAllDialog.value = false
            loadStats()
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderUseCase.deleteReminder(reminder)
            loadStats()
        }
    }

    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            val updatedReminder = reminderUseCase.toggleReminderEnabled(reminder)
            reminderRepository.update(updatedReminder)

            reminderScheduler.toggleSingleReminder(updatedReminder, updatedReminder.isEnabled)

            loadReminders()
            loadStats()
        }
    }

    fun cleanExpiredReminders() {
        viewModelScope.launch {
            reminderUseCase.cleanExpiredReminders()
            loadStats()
        }
    }
}
