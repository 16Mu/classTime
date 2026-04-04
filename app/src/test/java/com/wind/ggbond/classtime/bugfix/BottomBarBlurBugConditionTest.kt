package com.wind.ggbond.classtime.bugfix

import android.app.Application
import com.wind.ggbond.classtime.data.repository.InitializationRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.repository.SettingsRepository
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.service.contract.IUpdateManager
import com.wind.ggbond.classtime.ui.viewmodel.MainViewModel
import com.wind.ggbond.classtime.util.ScheduledUpdateManager
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Bug Condition Exploration Test for Bottom Bar Transparency/Blur Fix
 * 
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
 * 
 * **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bugs exist
 * 
 * This test explores four distinct bug conditions:
 * 1. MainViewModel initialization with hardcoded true instead of reading from DataStore (Bug 1)
 * 2. MainViewModel doesn't observe DataStore changes (Bug 2)
 * 3. Two independent StateFlows remain unsynchronized after toggle (Bug 3)
 * 4. glassModifier only draws semi-transparent rectangle without Gaussian blur (Bug 4)
 * 
 * **Expected Outcome on UNFIXED code**: Test FAILS
 * **Expected Outcome on FIXED code**: Test PASSES
 * 
 * The test uses property-based testing to generate various scenarios and verify
 * that the expected behavior holds across all inputs where bug conditions apply.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BottomBarBlurBugConditionTest {
    
    private val testDispatcher = UnconfinedTestDispatcher()
    
    private lateinit var initializationRepository: InitializationRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var scheduledUpdateManager: ScheduledUpdateManager
    private lateinit var alarmScheduler: IAlarmScheduler
    private lateinit var updateManager: IUpdateManager
    private lateinit var application: Application
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock dependencies
        initializationRepository = mockk(relaxed = true)
        scheduleRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        scheduledUpdateManager = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        updateManager = mockk(relaxed = true)
        application = mockk(relaxed = true)
        
        // Setup default mock behaviors
        coEvery { initializationRepository.initializeDefaultData() } returns Unit
        coEvery { initializationRepository.preloadCoreData() } returns Unit
        coEvery { scheduledUpdateManager.isScheduledUpdateEnabled() } returns false
        coEvery { alarmScheduler.rescheduleAllReminders() } returns Unit
        coEvery { updateManager.checkAndTriggerAutoUpdate() } returns mockk(relaxed = true)
        
        // Setup settings repository defaults
        coEvery { settingsRepository.isDisclaimerAccepted() } returns true
        coEvery { settingsRepository.isOnboardingCompleted() } returns true
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    /**
     * Property 1: Bug Condition - MainViewModel Initialization Reads from DataStore
     * 
     * **Validates: Requirement 2.1**
     * 
     * For any app initialization event where DataStore contains a specific blur setting value,
     * the MainViewModel SHALL read the initial value from SettingsRepository and initialize
     * bottomBarBlurEnabled StateFlow with that value (NOT hardcoded true).
     * 
     * **Bug Condition**: MainViewModel initializes with hardcoded `true` regardless of DataStore value
     * 
     * **Expected on UNFIXED code**: FAILS - MainViewModel.bottomBarBlurEnabled is always `true`
     * **Expected on FIXED code**: PASSES - MainViewModel.bottomBarBlurEnabled matches DataStore
     */
    @Test
    fun `Property 1 - MainViewModel SHALL read initial blur setting from DataStore on initialization`() = runTest {
        checkAll(Arb.boolean()) { dataStoreValue ->
            // Given: DataStore contains a specific blur setting value
            coEvery { settingsRepository.isBottomBarBlurEnabled() } returns dataStoreValue
            
            // When: MainViewModel is initialized (app starts)
            val viewModel = MainViewModel(
                initializationRepository = initializationRepository,
                scheduleRepository = scheduleRepository,
                settingsRepository = settingsRepository,
                scheduledUpdateManager = scheduledUpdateManager,
                alarmReminderScheduler = alarmScheduler,
                updateOrchestrator = updateManager,
                application = application
            )
            
            // Allow initialization to complete
            advanceUntilIdle()
            
            // Then: MainViewModel.bottomBarBlurEnabled SHALL match the DataStore value
            val actualValue = viewModel.bottomBarBlurEnabled.value
            
            assertEquals(
                """
                    Bug 1 Detected: MainViewModel initialization does not read from DataStore
                    
                    Expected: MainViewModel.bottomBarBlurEnabled = $dataStoreValue (from DataStore)
                    Actual: MainViewModel.bottomBarBlurEnabled = $actualValue (hardcoded)
                    
                    Root Cause: Line 66 in MainViewModel.kt initializes with hardcoded `true`:
                    val bottomBarBlurEnabled = MutableStateFlow(true)
                    
                    This test confirms Bug 1 exists: MainViewModel does not read initial value from DataStore.
                """.trimIndent(),
                dataStoreValue,
                actualValue
            )
            
            // Verify that the repository method was called during initialization
            coVerify(atLeast = 1) { settingsRepository.isBottomBarBlurEnabled() }
        }
    }
    
    /**
     * Property 2: Bug Condition - MainViewModel Observes DataStore Changes
     * 
     * **Validates: Requirement 2.3**
     * 
     * For any DataStore change event, the MainViewModel SHALL observe the DataStore flow
     * via settingsRepository.observeBottomBarBlurEnabled() and update its StateFlow to
     * match the new persisted value.
     * 
     * **Bug Condition**: MainViewModel doesn't observe DataStore changes
     * 
     * **Expected on UNFIXED code**: FAILS - MainViewModel doesn't react to DataStore changes
     * **Expected on FIXED code**: PASSES - MainViewModel updates when DataStore changes
     */
    @Test
    fun `Property 2 - MainViewModel SHALL observe DataStore changes and update StateFlow`() = runTest {
        checkAll(Arb.boolean(), Arb.boolean()) { initialValue, changedValue ->
            // Given: DataStore initially contains a value
            val dataStoreFlow = MutableStateFlow(initialValue)
            coEvery { settingsRepository.isBottomBarBlurEnabled() } returns initialValue
            every { settingsRepository.observeBottomBarBlurEnabled() } returns dataStoreFlow
            
            // When: MainViewModel is initialized
            val viewModel = MainViewModel(
                initializationRepository = initializationRepository,
                scheduleRepository = scheduleRepository,
                settingsRepository = settingsRepository,
                scheduledUpdateManager = scheduledUpdateManager,
                alarmReminderScheduler = alarmScheduler,
                updateOrchestrator = updateManager,
                application = application
            )
            
            advanceUntilIdle()
            
            // And: DataStore value changes (simulating SettingsViewModel update)
            dataStoreFlow.value = changedValue
            advanceUntilIdle()
            
            // Then: MainViewModel.bottomBarBlurEnabled SHALL reflect the new DataStore value
            val actualValue = viewModel.bottomBarBlurEnabled.value
            
            assertEquals(
                """
                    Bug 2 Detected: MainViewModel does not observe DataStore changes
                    
                    Initial DataStore value: $initialValue
                    Changed DataStore value: $changedValue
                    Expected MainViewModel value: $changedValue
                    Actual MainViewModel value: $actualValue
                    
                    Root Cause: MainViewModel lacks DataStore observation coroutine in init block.
                    SettingsViewModel has this pattern (lines 200-207) but MainViewModel doesn't.
                    
                    This test confirms Bug 2 exists: MainViewModel does not observe DataStore changes.
                """.trimIndent(),
                changedValue,
                actualValue
            )
            
            // Verify that observeBottomBarBlurEnabled was called
            coVerify(atLeast = 1) { settingsRepository.observeBottomBarBlurEnabled() }
        }
    }
    
    /**
     * Property 3: Bug Condition - Single Source of Truth for Blur Setting
     * 
     * **Validates: Requirement 2.2**
     * 
     * For any switch toggle event in ProfileScreen, the system SHALL use a single source
     * of truth (DataStore) with observation pattern. MainViewModel SHALL NOT have a separate
     * update method that creates dual StateFlows.
     * 
     * **Bug Condition**: ProfileScreen calls both ViewModels, creating two independent StateFlows
     * 
     * **Expected on UNFIXED code**: FAILS - Two StateFlows can drift out of sync
     * **Expected on FIXED code**: PASSES - Single source of truth via DataStore observation
     * 
     * Note: This test verifies the architecture by checking if MainViewModel has an
     * updateBottomBarBlurEnabled method. In the fixed code, this method should be removed
     * and MainViewModel should only observe DataStore.
     */
    @Test
    fun `Property 3 - MainViewModel SHALL use single source of truth via DataStore observation`() = runTest {
        // Given: DataStore contains initial value
        val dataStoreFlow = MutableStateFlow(true)
        coEvery { settingsRepository.isBottomBarBlurEnabled() } returns true
        every { settingsRepository.observeBottomBarBlurEnabled() } returns dataStoreFlow
        
        // When: MainViewModel is initialized
        val viewModel = MainViewModel(
            initializationRepository = initializationRepository,
            scheduleRepository = scheduleRepository,
            settingsRepository = settingsRepository,
            scheduledUpdateManager = scheduledUpdateManager,
            alarmReminderScheduler = alarmScheduler,
            updateOrchestrator = updateManager,
            application = application
        )
        
        advanceUntilIdle()
        
        // Then: Check if MainViewModel has updateBottomBarBlurEnabled method
        val hasUpdateMethod = try {
            viewModel.javaClass.getDeclaredMethod("updateBottomBarBlurEnabled", Boolean::class.java)
            true
        } catch (e: NoSuchMethodException) {
            false
        }
        
        // Bug 3: If updateBottomBarBlurEnabled exists, it creates dual StateFlows
        if (hasUpdateMethod) {
            // Simulate ProfileScreen calling both ViewModels (current buggy behavior)
            val settingsViewModelValue = false
            dataStoreFlow.value = settingsViewModelValue
            
            // ProfileScreen also calls mainViewModel.updateBottomBarBlurEnabled(false)
            // Note: This code is commented out because the method has been removed in the fix
            // viewModel.updateBottomBarBlurEnabled(false)
            advanceUntilIdle()
            
            // The problem: Two independent StateFlows can drift out of sync
            // This test documents the bug but doesn't fail here because we're calling both
            // The real issue is the architecture allows this dual-call pattern
            
            throw AssertionError(
                """
                Bug 3 Detected: MainViewModel has updateBottomBarBlurEnabled method
                
                Current Architecture (BUGGY):
                - ProfileScreen calls settingsViewModel.updateBottomBarBlurEnabled(it)
                - ProfileScreen also calls mainViewModel.updateBottomBarBlurEnabled(it)
                - Two independent StateFlows can drift out of sync
                
                Expected Architecture (FIXED):
                - ProfileScreen only calls settingsViewModel.updateBottomBarBlurEnabled(it)
                - MainViewModel observes DataStore via settingsRepository.observeBottomBarBlurEnabled()
                - Single source of truth: DataStore
                - MainViewModel.updateBottomBarBlurEnabled method should be removed
                
                Root Cause: Lines 69-78 in MainViewModel.kt define updateBottomBarBlurEnabled
                This creates a dual-update pattern that violates single source of truth principle.
                
                This test confirms Bug 3 exists: Dual ViewModel architecture without synchronization.
                """.trimIndent()
            )
        }
        
        // If we reach here, the fix is implemented (no updateBottomBarBlurEnabled method)
        // Verify that MainViewModel correctly observes DataStore
        dataStoreFlow.value = false
        advanceUntilIdle()
        
        assertEquals(
            "MainViewModel should observe DataStore changes",
            false,
            viewModel.bottomBarBlurEnabled.value
        )
    }
    
    /**
     * Property 4: Bug Condition - glassModifier Applies Gaussian Blur
     * 
     * **Validates: Requirement 2.4**
     * 
     * For any bottom bar render event where blur is enabled, the glassModifier SHALL apply
     * actual Gaussian blur using RenderEffect.createBlurEffect() or equivalent blur implementation,
     * making background content visible through the bottom bar with blur effect.
     * 
     * **Bug Condition**: glassModifier only draws semi-transparent rectangle without Gaussian blur
     * 
     * **Expected on UNFIXED code**: FAILS - glassModifier uses drawRect without blur
     * **Expected on FIXED code**: PASSES - glassModifier applies RenderEffect.createBlurEffect()
     * 
     * Note: This is a code inspection test since we can't easily test Compose rendering in unit tests.
     * We verify the implementation by checking if the glassModifier function uses blur APIs.
     */
    @Test
    fun `Property 4 - glassModifier SHALL apply actual Gaussian blur when enabled`() {
        // This test documents Bug 4 by inspecting the implementation
        // In a real scenario, this would be tested with UI tests or screenshot tests
        
        // Bug 4: Current implementation in GlassEffect.kt (lines 18-28)
        // Uses drawBehind { drawRect(tintColor.copy(alpha = alpha)) }
        // Does NOT use RenderEffect.createBlurEffect() or Modifier.blur()
        
        val bugDescription = """
            Bug 4 Detected: glassModifier does not apply Gaussian blur
            
            Current Implementation (BUGGY):
            File: app/src/main/java/com/wind/ggbond/classtime/ui/components/GlassEffect.kt
            Lines 18-28: Uses drawBehind { drawRect(tintColor.copy(alpha = alpha)) }
            
            Problem:
            - Only draws a semi-transparent rectangle
            - Does NOT apply actual Gaussian blur effect
            - Background content is not blurred, just tinted
            
            Expected Implementation (FIXED):
            - Use Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(...) } for API 31+
            - Apply blur radius of 25-50 pixels for visible blur effect
            - Fall back to current approach for API < 31
            - Add blurEnabled parameter to control blur application
            
            Root Cause: GlassEffect.kt lacks RenderEffect.createBlurEffect() implementation
            
            This test confirms Bug 4 exists: glassModifier draws rectangle without Gaussian blur.
        """.trimIndent()
        
        // Read the actual GlassEffect.kt file content to verify
        val glassEffectFile = java.io.File("app/src/main/java/com/wind/ggbond/classtime/ui/components/GlassEffect.kt")
        
        if (glassEffectFile.exists()) {
            val content = glassEffectFile.readText()
            
            // Check if the file uses blur APIs
            val usesRenderEffect = content.contains("RenderEffect") || content.contains("createBlurEffect")
            val usesModifierBlur = content.contains("Modifier.blur")
            val usesGraphicsLayer = content.contains("graphicsLayer")
            
            val hasBlurImplementation = usesRenderEffect || usesModifierBlur
            
            if (!hasBlurImplementation) {
                throw AssertionError(bugDescription)
            }
            
            // If we reach here, blur is implemented
            // Verify it's used correctly with graphicsLayer
            if (!usesGraphicsLayer && usesRenderEffect) {
                throw AssertionError(
                    """
                    Partial Fix Detected: RenderEffect found but not used with graphicsLayer
                    
                    RenderEffect.createBlurEffect() must be used with Modifier.graphicsLayer:
                    Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(...) }
                    """.trimIndent()
                )
            }
        } else {
            // File doesn't exist in test environment, document the bug
            throw AssertionError(bugDescription)
        }
    }
    
    /**
     * Integration Test: Full Bug Scenario
     * 
     * This test combines all four bugs into a realistic user scenario:
     * 1. User disables blur in settings
     * 2. User closes app
     * 3. User reopens app
     * 4. Expected: Blur is disabled
     * 5. Actual (UNFIXED): Blur is enabled (hardcoded true)
     */
    @Test
    fun `Integration - User disables blur, closes app, reopens app - blur should remain disabled`() = runTest {
        // Scenario: User has previously disabled blur
        val userPreference = false
        
        // Given: DataStore contains user's preference (blur disabled)
        val dataStoreFlow = MutableStateFlow(userPreference)
        coEvery { settingsRepository.isBottomBarBlurEnabled() } returns userPreference
        every { settingsRepository.observeBottomBarBlurEnabled() } returns dataStoreFlow
        
        // When: App restarts and MainViewModel is initialized
        val viewModel = MainViewModel(
            initializationRepository = initializationRepository,
            scheduleRepository = scheduleRepository,
            settingsRepository = settingsRepository,
            scheduledUpdateManager = scheduledUpdateManager,
            alarmReminderScheduler = alarmScheduler,
            updateOrchestrator = updateManager,
            application = application
        )
        
        advanceUntilIdle()
        
        // Then: MainViewModel should respect user's preference
        val actualValue = viewModel.bottomBarBlurEnabled.value
        
        assertEquals(
            """
                Integration Bug: User preference not persisted across app restarts
                
                User Action: Disabled blur in settings, closed app, reopened app
                Expected: Blur remains disabled (bottomBarBlurEnabled = false)
                Actual: Blur is enabled (bottomBarBlurEnabled = $actualValue)
                
                This integration test confirms the combined effect of Bugs 1, 2, and 3:
                - Bug 1: MainViewModel initializes with hardcoded true
                - Bug 2: MainViewModel doesn't observe DataStore changes
                - Bug 3: Dual ViewModel architecture creates synchronization issues
                
                User Impact: Settings don't persist, poor user experience
            """.trimIndent(),
            userPreference,
            actualValue
        )
    }
}
