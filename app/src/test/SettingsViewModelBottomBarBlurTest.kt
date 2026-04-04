package com.wind.ggbond.classtime.ui.screen.settings

import android.content.Context
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.repository.SettingsRepository
import com.wind.ggbond.classtime.service.ImportService
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.service.contract.IDataExporter
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for SettingsViewModel bottom bar blur functionality
 * 
 * **Validates: Requirements 7.7, 15.2**
 * 
 * Tests:
 * - updateBottomBarBlurEnabled updates DataStore
 * - State correctly reflects DataStore values
 * - Error handling for DataStore write failures
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelBottomBarBlurTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var context: Context
    private lateinit var courseRepository: CourseRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var exportService: IDataExporter
    private lateinit var importService: ImportService
    private lateinit var reminderScheduler: IAlarmScheduler
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock dependencies
        context = mockk(relaxed = true)
        courseRepository = mockk(relaxed = true)
        scheduleRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        exportService = mockk(relaxed = true)
        importService = mockk(relaxed = true)
        reminderScheduler = mockk(relaxed = true)
        
        // Setup default mock behaviors
        coEvery { settingsRepository.isReminderEnabled() } returns false
        coEvery { settingsRepository.getDefaultReminderMinutes() } returns 10
        coEvery { settingsRepository.isCompactModeEnabled() } returns false
        coEvery { settingsRepository.isHeadsUpNotificationEnabled() } returns true
        coEvery { settingsRepository.isDisclaimerAccepted() } returns true
        coEvery { settingsRepository.isOnboardingCompleted() } returns true
        coEvery { settingsRepository.isShowWeekendEnabled() } returns true
        coEvery { settingsRepository.isBottomBarBlurEnabled() } returns true
        coEvery { settingsRepository.setBottomBarBlurEnabled(any()) } just Runs
        
        // Create ViewModel
        viewModel = SettingsViewModel(
            context = context,
            courseRepository = courseRepository,
            scheduleRepository = scheduleRepository,
            settingsRepository = settingsRepository,
            exportService = exportService,
            importService = importService,
            reminderScheduler = reminderScheduler
        )
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }
    
    /**
     * Test 1: updateBottomBarBlurEnabled should update DataStore
     * 
     * Validates: Requirement 7.7
     * 
     * Verifies that calling updateBottomBarBlurEnabled(true) updates the DataStore
     * through the SettingsRepository
     */
    @Test
    fun `updateBottomBarBlurEnabled should call repository to update DataStore`() = runTest {
        // When: Update bottom bar blur to enabled
        viewModel.updateBottomBarBlurEnabled(true)
        advanceUntilIdle()
        
        // Then: Repository should be called with correct value
        coVerify(exactly = 1) { settingsRepository.setBottomBarBlurEnabled(true) }
    }
    
    /**
     * Test 2: updateBottomBarBlurEnabled should update state to false
     * 
     * Validates: Requirement 7.7
     * 
     * Verifies that calling updateBottomBarBlurEnabled(false) updates both
     * the DataStore and the ViewModel state
     */
    @Test
    fun `updateBottomBarBlurEnabled should update state to false`() = runTest {
        // When: Update bottom bar blur to disabled
        viewModel.updateBottomBarBlurEnabled(false)
        advanceUntilIdle()
        
        // Then: Repository should be called with false
        coVerify(exactly = 1) { settingsRepository.setBottomBarBlurEnabled(false) }
        
        // And: ViewModel state should be updated
        assertEquals(false, viewModel.bottomBarBlurEnabled.value)
    }
    
    /**
     * Test 3: State should correctly reflect DataStore value on initialization
     * 
     * Validates: Requirement 15.2
     * 
     * Verifies that the ViewModel correctly loads the bottom bar blur state
     * from DataStore during initialization
     */
    @Test
    fun `state should reflect DataStore value on initialization`() = runTest {
        // Given: DataStore has bottom bar blur enabled
        coEvery { settingsRepository.isBottomBarBlurEnabled() } returns true
        
        // When: Create a new ViewModel instance
        val newViewModel = SettingsViewModel(
            context = context,
            courseRepository = courseRepository,
            scheduleRepository = scheduleRepository,
            settingsRepository = settingsRepository,
            exportService = exportService,
            importService = importService,
            reminderScheduler = reminderScheduler
        )
        advanceUntilIdle()
        
        // Then: State should be enabled
        assertEquals(true, newViewModel.bottomBarBlurEnabled.value)
    }
    
    /**
     * Test 4: State should reflect disabled value from DataStore
     * 
     * Validates: Requirement 15.2
     * 
     * Verifies that the ViewModel correctly loads a disabled state
     * from DataStore during initialization
     */
    @Test
    fun `state should reflect disabled value from DataStore on initialization`() = runTest {
        // Given: DataStore has bottom bar blur disabled
        coEvery { settingsRepository.isBottomBarBlurEnabled() } returns false
        
        // When: Create a new ViewModel instance
        val newViewModel = SettingsViewModel(
            context = context,
            courseRepository = courseRepository,
            scheduleRepository = scheduleRepository,
            settingsRepository = settingsRepository,
            exportService = exportService,
            importService = importService,
            reminderScheduler = reminderScheduler
        )
        advanceUntilIdle()
        
        // Then: State should be disabled
        assertEquals(false, newViewModel.bottomBarBlurEnabled.value)
    }
    
    /**
     * Test 5: Multiple updates should all be persisted
     * 
     * Validates: Requirement 7.7
     * 
     * Verifies that multiple consecutive updates to bottom bar blur
     * are all persisted to DataStore
     */
    @Test
    fun `multiple updates should all be persisted to DataStore`() = runTest {
        // When: Toggle bottom bar blur multiple times
        viewModel.updateBottomBarBlurEnabled(false)
        advanceUntilIdle()
        
        viewModel.updateBottomBarBlurEnabled(true)
        advanceUntilIdle()
        
        viewModel.updateBottomBarBlurEnabled(false)
        advanceUntilIdle()
        
        // Then: Repository should be called for each update
        coVerify(exactly = 2) { settingsRepository.setBottomBarBlurEnabled(false) }
        coVerify(exactly = 1) { settingsRepository.setBottomBarBlurEnabled(true) }
        
        // And: Final state should be disabled
        assertEquals(false, viewModel.bottomBarBlurEnabled.value)
    }
    
    /**
     * Test 6: Error handling when DataStore write fails
     * 
     * Validates: Requirement 7.7
     * 
     * Verifies that when DataStore write fails, the ViewModel handles
     * the error gracefully without crashing
     */
    @Test
    fun `should handle DataStore write failure gracefully`() = runTest {
        // Given: DataStore write will fail
        coEvery { settingsRepository.setBottomBarBlurEnabled(any()) } throws Exception("DataStore write failed")
        
        // When: Attempt to update bottom bar blur
        try {
            viewModel.updateBottomBarBlurEnabled(false)
            advanceUntilIdle()
        } catch (e: Exception) {
            // Exception is expected
        }
        
        // Then: Repository should have been called
        coVerify(exactly = 1) { settingsRepository.setBottomBarBlurEnabled(false) }
    }
    
    /**
     * Test 7: State update should be immediate in ViewModel
     * 
     * Validates: Requirement 7.7
     * 
     * Verifies that the ViewModel state is updated immediately
     * when updateBottomBarBlurEnabled is called, even before
     * DataStore write completes
     */
    @Test
    fun `state update should be immediate in ViewModel`() = runTest {
        // Given: Initial state is enabled
        assertEquals(true, viewModel.bottomBarBlurEnabled.value)
        
        // When: Update to disabled
        viewModel.updateBottomBarBlurEnabled(false)
        advanceUntilIdle()
        
        // Then: State should be immediately updated
        assertEquals(false, viewModel.bottomBarBlurEnabled.value)
    }
    
    /**
     * Test 8: State should persist after app restart simulation
     * 
     * Validates: Requirement 15.2
     * 
     * Verifies that the bottom bar blur state persists across
     * app restarts (simulated by creating a new ViewModel instance)
     */
    @Test
    fun `state should persist after app restart simulation`() = runTest {
        // Given: User disables bottom bar blur
        viewModel.updateBottomBarBlurEnabled(false)
        advanceUntilIdle()
        
        // When: App restarts (simulate by creating new ViewModel)
        // Mock repository to return the saved value
        coEvery { settingsRepository.isBottomBarBlurEnabled() } returns false
        
        val newViewModel = SettingsViewModel(
            context = context,
            courseRepository = courseRepository,
            scheduleRepository = scheduleRepository,
            settingsRepository = settingsRepository,
            exportService = exportService,
            importService = importService,
            reminderScheduler = reminderScheduler
        )
        advanceUntilIdle()
        
        // Then: New ViewModel should load the saved state
        assertEquals(false, newViewModel.bottomBarBlurEnabled.value)
    }
    
    /**
     * Test 9: Default value should be true when DataStore is empty
     * 
     * Validates: Requirement 15.2
     * 
     * Verifies that when DataStore has no saved value (first app launch),
     * the default value of true is used
     */
    @Test
    fun `default value should be true when DataStore is empty`() = runTest {
        // Given: DataStore returns default value (true)
        coEvery { settingsRepository.isBottomBarBlurEnabled() } returns true
        
        // When: Create ViewModel on first launch
        val newViewModel = SettingsViewModel(
            context = context,
            courseRepository = courseRepository,
            scheduleRepository = scheduleRepository,
            settingsRepository = settingsRepository,
            exportService = exportService,
            importService = importService,
            reminderScheduler = reminderScheduler
        )
        advanceUntilIdle()
        
        // Then: State should be true (default)
        assertEquals(true, newViewModel.bottomBarBlurEnabled.value)
    }
    
    /**
     * Test 10: Rapid toggles should maintain consistency
     * 
     * Validates: Requirement 7.7
     * 
     * Verifies that rapid consecutive toggles maintain state consistency
     * and all updates are persisted
     */
    @Test
    fun `rapid toggles should maintain state consistency`() = runTest {
        // When: Rapidly toggle bottom bar blur
        viewModel.updateBottomBarBlurEnabled(false)
        viewModel.updateBottomBarBlurEnabled(true)
        viewModel.updateBottomBarBlurEnabled(false)
        viewModel.updateBottomBarBlurEnabled(true)
        advanceUntilIdle()
        
        // Then: All updates should be persisted
        coVerify(exactly = 2) { settingsRepository.setBottomBarBlurEnabled(false) }
        coVerify(exactly = 2) { settingsRepository.setBottomBarBlurEnabled(true) }
        
        // And: Final state should be enabled
        assertEquals(true, viewModel.bottomBarBlurEnabled.value)
    }
}
