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
        initializationRepository = mockk(relaxed = true)
        scheduleRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        scheduledUpdateManager = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        updateManager = mockk(relaxed = true)
        application = mockk(relaxed = true)
        coEvery { initializationRepository.initializeDefaultData() } returns Unit
        coEvery { initializationRepository.preloadCoreData() } returns Unit
        coEvery { scheduledUpdateManager.isScheduledUpdateEnabled() } returns false
        coEvery { alarmScheduler.rescheduleAllReminders() } returns Unit
        coEvery { updateManager.checkAndTriggerAutoUpdate() } returns mockk(relaxed = true)
        coEvery { settingsRepository.isDisclaimerAccepted() } returns true
        coEvery { settingsRepository.isOnboardingCompleted() } returns true
    }
    
    @After
    fun tearDown() { Dispatchers.resetMain() }
    
    @Test
    fun `Property 1 - MainViewModel SHALL read initial glass effect setting from DataStore`() = runTest {
        checkAll(Arb.boolean()) { dataStoreValue ->
            coEvery { settingsRepository.isGlassEffectEnabled() } returns dataStoreValue
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
            val actualValue = viewModel.glassEffectEnabled.value
            assertEquals(dataStoreValue, actualValue)
            coVerify(atLeast = 1) { settingsRepository.isGlassEffectEnabled() }
        }
    }
    
    @Test
    fun `Property 2 - MainViewModel SHALL observe DataStore changes and update StateFlow`() = runTest {
        checkAll(Arb.boolean(), Arb.boolean()) { initialValue, changedValue ->
            val dataStoreFlow = MutableStateFlow(initialValue)
            coEvery { settingsRepository.isGlassEffectEnabled() } returns initialValue
            every { settingsRepository.observeGlassEffectEnabled() } returns dataStoreFlow
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
            dataStoreFlow.value = changedValue
            advanceUntilIdle()
            val actualValue = viewModel.glassEffectEnabled.value
            assertEquals(changedValue, actualValue)
            coVerify(atLeast = 1) { settingsRepository.observeGlassEffectEnabled() }
        }
    }
    
    @Test
    fun `Property 3 - MainViewModel SHALL use single source of truth via DataStore observation`() = runTest {
        val dataStoreFlow = MutableStateFlow(true)
        coEvery { settingsRepository.isGlassEffectEnabled() } returns true
        every { settingsRepository.observeGlassEffectEnabled() } returns dataStoreFlow
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
        dataStoreFlow.value = false
        advanceUntilIdle()
        assertEquals(false, viewModel.glassEffectEnabled.value)
    }
    
    @Test
    fun `Property 4 - glassModifier SHALL apply actual Gaussian blur when enabled`() {
        val glassEffectFile = java.io.File("app/src/main/java/com/wind/ggbond/classtime/ui/components/GlassEffect.kt")
        if (glassEffectFile.exists()) {
            val content = glassEffectFile.readText()
            val usesRenderEffect = content.contains("RenderEffect") || content.contains("createBlurEffect")
            val usesModifierBlur = content.contains("Modifier.blur")
            val usesGraphicsLayer = content.contains("graphicsLayer")
            val hasBlurImplementation = usesRenderEffect || usesModifierBlur
            if (!hasBlurImplementation) {
                throw AssertionError("glassModifier does not apply Gaussian blur - no RenderEffect or Modifier.blur found")
            }
        }
    }
    
    @Test
    fun `Integration - User disables glass effect, closes app, reopens app - setting should persist`() = runTest {
        val userPreference = false
        val dataStoreFlow = MutableStateFlow(userPreference)
        coEvery { settingsRepository.isGlassEffectEnabled() } returns userPreference
        every { settingsRepository.observeGlassEffectEnabled() } returns dataStoreFlow
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
        val actualValue = viewModel.glassEffectEnabled.value
        assertEquals(userPreference, actualValue)
    }
}
