package com.wind.ggbond.classtime.bugfix

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import app.cash.turbine.test
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.ui.theme.BackgroundScheme
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager
import com.wind.ggbond.classtime.ui.theme.BackgroundType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Bug Condition Exploration Test for Custom Background State Update Failure
 * 
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7**
 * 
 * This test documents the bug condition where UI state fails to update in real-time
 * when users upload media, adjust parameters, or switch schemes. The root cause is
 * nested collect() calls in loadCurrentSettings() blocking state updates.
 * 
 * CRITICAL: This test is EXPECTED TO FAIL on unfixed code.
 * The failure confirms that the bug exists.
 * 
 * Bug Condition:
 * - loadCurrentSettings() uses 6 layers of nested collect()
 * - When user uploads media/adjusts parameters, DataStore is updated
 * - But UI state (_uiState) doesn't update because collect chain is blocked
 * - Expected Result: UI state remains stale, preview doesn't show, effects don't apply
 * 
 * Counterexample Documentation:
 * This test will document specific counterexamples where:
 * 1. Upload image -> preview not updated (Req 2.1)
 * 2. Adjust blur -> effect not applied (Req 2.2)
 * 3. Adjust dim -> effect not applied (Req 2.3)
 * 4. Upload image -> seed color not extracted (Req 2.4)
 * 5. Upload video -> seed color not extracted (Req 2.5)
 * 6. Set background -> not applied to main interface (Req 2.6)
 * 7. Adjust parameters -> active scheme not updated (Req 2.7)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomBackgroundBugConditionTest {

    private lateinit var testScope: TestScope
    private lateinit var testDispatcher: StandardTestDispatcher
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var context: Context
    private lateinit var backgroundThemeManager: BackgroundThemeManager

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)

        // Create test DataStore
        context = mockk<Context>(relaxed = true)
        val testFile = File.createTempFile("test_datastore", ".preferences_pb")
        testFile.deleteOnExit()
        
        every { context.preferencesDataStoreFile(any()) } returns testFile
        
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )

        // Mock DataStoreManager to use test DataStore
        mockkStatic(DataStoreManager::class)
        every { DataStoreManager.getSettingsDataStore(any()) } returns testDataStore

        // Create real BackgroundThemeManager with test context
        backgroundThemeManager = BackgroundThemeManager(context)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
    }

    /**
     * Property 1: Bug Condition - Nested collect() blocks state updates
     * 
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.6, 2.7**
     * 
     * This test demonstrates the core bug: when loadCurrentSettings() uses nested
     * collect() calls, the UI state cannot update in real-time because the collect
     * chain blocks on the first flow.
     * 
     * WHEN DataStore values change (schemes added, parameters adjusted)
     * THEN a listener using nested collect() will NOT receive updates
     * BUT a listener using combine() WILL receive updates
     * 
     * This proves the root cause: nested collect() blocks state propagation.
     * 
     * EXPECTED ON UNFIXED CODE: Test FAILS - nested collect never receives second update
     * EXPECTED ON FIXED CODE: Test PASSES - combine receives all updates
     */
    @Test(timeout = 5000)
    fun `property - nested collect blocks state updates while combine allows real-time updates`() = runTest {
        // Track updates received by nested collect approach (simulating current buggy code)
        val nestedCollectUpdates = mutableListOf<Int>()
        var nestedCollectCompleted = false
        
        // Track updates received by combine approach (simulating fixed code)
        val combineUpdates = mutableListOf<Int>()
        
        // Start a coroutine that simulates the BUGGY nested collect pattern
        val nestedJob = launch {
            try {
                backgroundThemeManager.getAllBackgroundSchemes().collect { schemes ->
                    backgroundThemeManager.getBlurRadius().collect { blur ->
                        // This inner collect blocks - it won't see new values until outer collect emits
                        nestedCollectUpdates.add(schemes.size)
                    }
                }
            } catch (e: Exception) {
                // Collect cancelled
            } finally {
                nestedCollectCompleted = true
            }
        }
        
        // Start a coroutine that simulates the FIXED combine pattern
        val combineJob = launch {
            try {
                kotlinx.coroutines.flow.combine(
                    backgroundThemeManager.getAllBackgroundSchemes(),
                    backgroundThemeManager.getBlurRadius()
                ) { schemes, blur ->
                    Pair(schemes.size, blur)
                }.collect { (schemeCount, blur) ->
                    combineUpdates.add(schemeCount)
                }
            } catch (e: Exception) {
                // Collect cancelled
            }
        }
        
        advanceUntilIdle()
        
        // Initial state: both should receive first emission (0 schemes)
        assertEquals("Nested collect should receive initial state", 1, nestedCollectUpdates.size)
        assertEquals("Combine should receive initial state", 1, combineUpdates.size)
        assertEquals("Initial scheme count should be 0", 0, nestedCollectUpdates[0])
        assertEquals("Initial scheme count should be 0", 0, combineUpdates[0])
        
        // Now add a background scheme
        val scheme1 = BackgroundScheme(
            uri = "content://test/image1.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF0000FF.toInt(),
            name = "Test Scheme 1"
        )
        backgroundThemeManager.addBackgroundScheme(scheme1)
        advanceUntilIdle()
        
        // BUG CONDITION: Nested collect is BLOCKED in the inner collect
        // It won't receive the new scheme count until the outer flow emits again
        // But the outer flow (getAllBackgroundSchemes) already emitted, so it's stuck
        
        // Combine approach receives the update immediately
        assertTrue(
            "BUG: Combine receives update (size=${combineUpdates.size}) but nested collect is blocked (size=${nestedCollectUpdates.size})",
            combineUpdates.size > nestedCollectUpdates.size
        )
        
        assertEquals(
            "Combine should have received 2 updates (initial + after add)",
            2,
            combineUpdates.size
        )
        
        assertEquals(
            "BUG: Nested collect should have received 2 updates but is stuck at 1 due to blocking",
            2,
            nestedCollectUpdates.size
        )
        
        assertEquals("Combine should see 1 scheme after add", 1, combineUpdates[1])
        
        // Add another scheme to further demonstrate the blocking
        val scheme2 = BackgroundScheme(
            uri = "content://test/image2.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF00FF00.toInt(),
            name = "Test Scheme 2"
        )
        backgroundThemeManager.addBackgroundScheme(scheme2)
        advanceUntilIdle()
        
        // Combine continues to receive updates
        assertEquals("Combine should have 3 updates", 3, combineUpdates.size)
        assertEquals("Combine should see 2 schemes", 2, combineUpdates[2])
        
        // Nested collect is still blocked
        assertEquals(
            "BUG: Nested collect should have 3 updates but remains blocked at 1",
            3,
            nestedCollectUpdates.size
        )
        
        nestedJob.cancel()
        combineJob.cancel()
        advanceUntilIdle()
    }

    /**
     * Property 2: Bug Condition - Parameter updates don't trigger UI refresh
     * 
     * **Validates: Requirements 2.2, 2.3**
     * 
     * WHEN user adjusts blur or dim parameters
     * THEN the UI state should update immediately
     * 
     * Bug: setBlurRadius() and setDimAmount() update DataStore but don't trigger
     * the nested collect chain to re-emit, so UI state remains stale.
     * 
     * EXPECTED ON UNFIXED CODE: Test FAILS - parameter changes not reflected
     * EXPECTED ON FIXED CODE: Test PASSES - parameters update immediately
     */
    @Test
    fun `property - parameter adjustments should trigger immediate state updates`() = runTest {
        // First add a scheme so we have something to work with
        val scheme = BackgroundScheme(
            uri = "content://test/image.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF0000FF.toInt(),
            name = "Test Scheme"
        )
        backgroundThemeManager.addBackgroundScheme(scheme)
        advanceUntilIdle()
        
        // Get initial blur value
        val initialBlur = backgroundThemeManager.getBlurRadius().first()
        assertEquals("Initial blur should be 0", 0, initialBlur)
        
        // Adjust blur to 50
        backgroundThemeManager.setBlurRadius(50)
        advanceUntilIdle()
        
        // Verify blur was updated in DataStore
        val updatedBlur = backgroundThemeManager.getBlurRadius().first()
        assertEquals("Blur should be updated to 50 in DataStore", 50, updatedBlur)
        
        // Now test if a nested collect pattern would receive this update
        var receivedUpdate = false
        var receivedBlurValue = 0
        
        val job = launch {
            // Simulate the buggy nested collect pattern
            backgroundThemeManager.getAllBackgroundSchemes().collect { schemes ->
                backgroundThemeManager.getBlurRadius().collect { blur ->
                    receivedBlurValue = blur
                    receivedUpdate = true
                }
            }
        }
        
        advanceUntilIdle()
        
        // The nested collect should receive the current value (50)
        assertTrue("Nested collect should eventually receive a value", receivedUpdate)
        assertEquals(
            "BUG: Nested collect should see updated blur value (50) but may see stale value due to blocking",
            50,
            receivedBlurValue
        )
        
        // Now adjust blur again while the collect is running
        backgroundThemeManager.setBlurRadius(75)
        advanceUntilIdle()
        
        // BUG: The nested collect won't receive this update because it's blocked
        // in the inner collect waiting for the outer flow to emit again
        assertEquals(
            "BUG: After adjusting blur to 75, nested collect should update but remains at 50 due to blocking",
            75,
            receivedBlurValue
        )
        
        job.cancel()
    }

    /**
     * Property 3: Bug Condition - Seed color extraction not synced to DataStore
     * 
     * **Validates: Requirements 2.4, 2.5**
     * 
     * WHEN a scheme is added with an extracted seed color
     * THEN the global seed color in DataStore should also be updated
     * 
     * Bug: Schemes store their own seedColor, but the global SEED_COLOR_KEY
     * in DataStore is not updated, so theme generation uses the wrong color.
     * 
     * EXPECTED ON UNFIXED CODE: Test FAILS - global seed color remains default
     * EXPECTED ON FIXED CODE: Test PASSES - global seed color matches scheme
     */
    @Test
    fun `property - seed color should be synced to DataStore when scheme is added`() = runTest {
        // Get initial seed color (should be default)
        val initialSeedColor = backgroundThemeManager.getSeedColor().first()
        assertEquals("Initial seed color should be default", 
            BackgroundScheme.DEFAULT_SEED_COLOR, initialSeedColor)
        
        // Add a scheme with a blue seed color
        val blueColor = 0xFF0000FF.toInt()
        val scheme = BackgroundScheme(
            uri = "content://test/blue_image.jpg",
            type = BackgroundType.IMAGE,
            seedColor = blueColor,
            name = "Blue Scheme"
        )
        backgroundThemeManager.addBackgroundScheme(scheme)
        advanceUntilIdle()
        
        // Verify the scheme was added with the correct seed color
        val schemes = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 1 scheme", 1, schemes.size)
        assertEquals("Scheme should have blue seed color", blueColor, schemes[0].seedColor)
        
        // BUG: The global seed color in DataStore should be updated but isn't
        val globalSeedColor = backgroundThemeManager.getSeedColor().first()
        
        assertNotEquals(
            "BUG: Global seed color should be updated from default but remains unchanged",
            BackgroundScheme.DEFAULT_SEED_COLOR,
            globalSeedColor
        )
        
        assertEquals(
            "BUG: Global seed color should match the scheme's seed color (blue) but doesn't sync",
            blueColor,
            globalSeedColor
        )
    }

    /**
     * Property 4: Bug Condition - Dynamic theme not auto-enabled after adding scheme
     * 
     * **Validates: Requirement 2.6**
     * 
     * WHEN user adds the first background scheme
     * THEN dynamic theme should be automatically enabled
     * AND the scheme should be set as active
     * 
     * Bug: addBackgroundScheme() sets these values, but the UI state doesn't
     * reflect them due to nested collect blocking.
     * 
     * EXPECTED ON UNFIXED CODE: Test FAILS - dynamic theme enabled but UI doesn't show it
     * EXPECTED ON FIXED CODE: Test PASSES - UI state reflects dynamic theme enabled
     */
    @Test
    fun `property - dynamic theme should be auto-enabled when first scheme is added`() = runTest {
        // Verify initial state: no schemes, dynamic theme disabled
        val initialSchemes = backgroundThemeManager.getAllBackgroundSchemes().first()
        val initialDynamic = backgroundThemeManager.isDynamicThemeEnabled().first()
        val initialIndex = backgroundThemeManager.getActiveBackgroundIndex().first()
        
        assertTrue("Initially should have no schemes", initialSchemes.isEmpty())
        assertFalse("Initially dynamic theme should be disabled", initialDynamic)
        assertEquals("Initially active index should be 0", 0, initialIndex)
        
        // Add first scheme
        val scheme = BackgroundScheme(
            uri = "content://test/image.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF0000FF.toInt(),
            name = "First Scheme"
        )
        backgroundThemeManager.addBackgroundScheme(scheme)
        advanceUntilIdle()
        
        // Verify DataStore was updated correctly
        val schemesAfterAdd = backgroundThemeManager.getAllBackgroundSchemes().first()
        val dynamicAfterAdd = backgroundThemeManager.isDynamicThemeEnabled().first()
        val indexAfterAdd = backgroundThemeManager.getActiveBackgroundIndex().first()
        
        assertEquals("Should have 1 scheme after add", 1, schemesAfterAdd.size)
        assertTrue("Dynamic theme should be enabled after adding first scheme", dynamicAfterAdd)
        assertEquals("Active index should be 0 (first scheme)", 0, indexAfterAdd)
        
        // BUG: If using nested collect in ViewModel, the UI state won't reflect these changes
        // This test verifies the DataStore is correct, but the ViewModel's nested collect
        // pattern prevents the UI from seeing these updates in real-time
        
        // Simulate checking if a nested collect pattern would see the updates
        var seenSchemeCount = 0
        var seenDynamicEnabled = false
        var updateCount = 0
        
        val job = launch {
            backgroundThemeManager.getAllBackgroundSchemes().collect { schemes ->
                backgroundThemeManager.isDynamicThemeEnabled().collect { dynamic ->
                    seenSchemeCount = schemes.size
                    seenDynamicEnabled = dynamic
                    updateCount++
                }
            }
        }
        
        advanceUntilIdle()
        
        // The nested collect should eventually see the current state
        assertEquals("Nested collect should see 1 scheme", 1, seenSchemeCount)
        assertTrue("Nested collect should see dynamic theme enabled", seenDynamicEnabled)
        
        // But if we add another scheme, the nested collect won't update
        val scheme2 = BackgroundScheme(
            uri = "content://test/image2.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF00FF00.toInt(),
            name = "Second Scheme"
        )
        backgroundThemeManager.addBackgroundScheme(scheme2)
        advanceUntilIdle()
        
        // BUG: Nested collect is blocked, won't see the second scheme
        assertEquals(
            "BUG: After adding second scheme, nested collect should see 2 schemes but remains at 1 due to blocking",
            2,
            seenSchemeCount
        )
        
        job.cancel()
    }
}
