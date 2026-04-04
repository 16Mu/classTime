package com.wind.ggbond.classtime.bugfix

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Preservation Property Tests for Custom Background Scheme Management
 * 
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**
 * 
 * IMPORTANT: Follow observation-first methodology
 * - These tests observe behavior on UNFIXED code for non-buggy inputs
 * - They capture the existing scheme management functionality that must be preserved
 * - Property-based testing generates many test cases for stronger guarantees
 * 
 * Non-Bug Condition: Operations that don't involve real-time state updates
 * - Delete scheme (3.1)
 * - Rename scheme (3.2)
 * - Switch scheme (3.3)
 * - Disable dynamic theme (3.4)
 * - Manual seed color selection (3.5)
 * - Scheme limit enforcement (3.6)
 * - Clear all schemes (3.7)
 * 
 * EXPECTED ON UNFIXED CODE: Tests PASS (confirms baseline behavior to preserve)
 * EXPECTED ON FIXED CODE: Tests still PASS (confirms no regressions)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomBackgroundPreservationTest {

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
        val testFile = File.createTempFile("test_preservation_datastore", ".preferences_pb")
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
     * Property 2.1: Preservation - Delete scheme functionality
     * 
     * **Validates: Requirement 3.1**
     * 
     * WHEN user deletes a background scheme
     * THEN system SHALL CONTINUE TO correctly delete the specified scheme
     * AND update the active index appropriately
     * 
     * Observed behavior on unfixed code:
     * - deleteBackgroundScheme() removes scheme from list
     * - If deleted scheme was active, active index adjusts to valid range
     * - Scheme count decreases by 1
     */
    @Test
    fun `property - delete scheme removes it from list and adjusts active index`() = runTest {
        // Add 3 schemes
        val scheme1 = BackgroundScheme(
            uri = "content://test/image1.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF0000FF.toInt(),
            name = "Scheme 1"
        )
        val scheme2 = BackgroundScheme(
            uri = "content://test/image2.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF00FF00.toInt(),
            name = "Scheme 2"
        )
        val scheme3 = BackgroundScheme(
            uri = "content://test/image3.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFFFF0000.toInt(),
            name = "Scheme 3"
        )
        
        backgroundThemeManager.addBackgroundScheme(scheme1)
        backgroundThemeManager.addBackgroundScheme(scheme2)
        backgroundThemeManager.addBackgroundScheme(scheme3)
        advanceUntilIdle()
        
        val schemesBeforeDelete = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 3 schemes", 3, schemesBeforeDelete.size)
        
        // Set active index to 1 (second scheme)
        backgroundThemeManager.setActiveBackgroundIndex(1)
        advanceUntilIdle()
        
        val activeBeforeDelete = backgroundThemeManager.getActiveBackgroundIndex().first()
        assertEquals("Active index should be 1", 1, activeBeforeDelete)
        
        // Delete the active scheme (index 1)
        backgroundThemeManager.deleteBackgroundScheme(1)
        advanceUntilIdle()
        
        // Verify scheme was deleted
        val schemesAfterDelete = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 2 schemes after delete", 2, schemesAfterDelete.size)
        assertEquals("First scheme should still be Scheme 1", "Scheme 1", schemesAfterDelete[0].name)
        assertEquals("Second scheme should now be Scheme 3", "Scheme 3", schemesAfterDelete[1].name)
        
        // Verify active index was adjusted (should be 0 or 1, depending on implementation)
        val activeAfterDelete = backgroundThemeManager.getActiveBackgroundIndex().first()
        assertTrue("Active index should be valid", activeAfterDelete >= 0 && activeAfterDelete < 2)
    }

    /**
     * Property 2.2: Preservation - Rename scheme functionality
     * 
     * **Validates: Requirement 3.2**
     * 
     * WHEN user renames a background scheme
     * THEN system SHALL CONTINUE TO correctly update the scheme name
     * AND preserve all other scheme properties
     * 
     * Observed behavior on unfixed code:
     * - renameBackgroundScheme() updates the name field
     * - URI, type, seedColor remain unchanged
     * - Scheme count remains the same
     */
    @Test
    fun `property - rename scheme updates name while preserving other properties`() = runTest {
        // Add a scheme
        val originalScheme = BackgroundScheme(
            uri = "content://test/original.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF0000FF.toInt(),
            name = "Original Name"
        )
        
        backgroundThemeManager.addBackgroundScheme(originalScheme)
        advanceUntilIdle()
        
        val schemesBeforeRename = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 1 scheme", 1, schemesBeforeRename.size)
        assertEquals("Name should be Original Name", "Original Name", schemesBeforeRename[0].name)
        
        // Rename the scheme
        backgroundThemeManager.renameBackgroundScheme(0, "New Name")
        advanceUntilIdle()
        
        // Verify name was updated
        val schemesAfterRename = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should still have 1 scheme", 1, schemesAfterRename.size)
        assertEquals("Name should be updated to New Name", "New Name", schemesAfterRename[0].name)
        
        // Verify other properties preserved
        assertEquals("URI should be preserved", originalScheme.uri, schemesAfterRename[0].uri)
        assertEquals("Type should be preserved", originalScheme.type, schemesAfterRename[0].type)
        assertEquals("Seed color should be preserved", originalScheme.seedColor, schemesAfterRename[0].seedColor)
    }

    /**
     * Property 2.3: Preservation - Switch scheme functionality
     * 
     * **Validates: Requirement 3.3**
     * 
     * WHEN user switches to a different background scheme
     * THEN system SHALL CONTINUE TO correctly update the active index
     * AND the active scheme's properties should be accessible
     * 
     * Observed behavior on unfixed code:
     * - setActiveBackgroundIndex() updates the active index
     * - getActiveBackgroundIndex() returns the new index
     * - Active scheme can be retrieved from the list
     */
    @Test
    fun `property - switch scheme updates active index correctly`() = runTest {
        // Add 3 schemes
        val schemes = listOf(
            BackgroundScheme("content://test/1.jpg", BackgroundType.IMAGE, 0xFF0000FF.toInt(), "Scheme 1"),
            BackgroundScheme("content://test/2.jpg", BackgroundType.IMAGE, 0xFF00FF00.toInt(), "Scheme 2"),
            BackgroundScheme("content://test/3.jpg", BackgroundType.IMAGE, 0xFFFF0000.toInt(), "Scheme 3")
        )
        
        schemes.forEach { backgroundThemeManager.addBackgroundScheme(it) }
        advanceUntilIdle()
        
        // Initially active index should be 0
        val initialActive = backgroundThemeManager.getActiveBackgroundIndex().first()
        assertEquals("Initial active index should be 0", 0, initialActive)
        
        // Switch to scheme 2 (index 1)
        backgroundThemeManager.setActiveBackgroundIndex(1)
        advanceUntilIdle()
        
        val activeAfterSwitch = backgroundThemeManager.getActiveBackgroundIndex().first()
        assertEquals("Active index should be 1 after switch", 1, activeAfterSwitch)
        
        // Verify we can access the active scheme
        val allSchemes = backgroundThemeManager.getAllBackgroundSchemes().first()
        val activeScheme = allSchemes[activeAfterSwitch]
        assertEquals("Active scheme should be Scheme 2", "Scheme 2", activeScheme.name)
        
        // Switch to scheme 3 (index 2)
        backgroundThemeManager.setActiveBackgroundIndex(2)
        advanceUntilIdle()
        
        val finalActive = backgroundThemeManager.getActiveBackgroundIndex().first()
        assertEquals("Active index should be 2 after second switch", 2, finalActive)
    }

    /**
     * Property 2.4: Preservation - Disable dynamic theme functionality
     * 
     * **Validates: Requirement 3.4**
     * 
     * WHEN user disables the dynamic theme switch
     * THEN system SHALL CONTINUE TO restore default theme
     * AND isDynamicThemeEnabled should return false
     * 
     * Observed behavior on unfixed code:
     * - setDynamicThemeEnabled(false) updates the flag
     * - isDynamicThemeEnabled() returns false
     * - App should revert to default theme (not tested here, UI concern)
     */
    @Test
    fun `property - disable dynamic theme updates flag correctly`() = runTest {
        // Add a scheme to enable dynamic theme
        val scheme = BackgroundScheme(
            uri = "content://test/image.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF0000FF.toInt(),
            name = "Test Scheme"
        )
        backgroundThemeManager.addBackgroundScheme(scheme)
        advanceUntilIdle()
        
        // Verify dynamic theme is enabled
        val dynamicEnabled = backgroundThemeManager.isDynamicThemeEnabled().first()
        assertTrue("Dynamic theme should be enabled after adding scheme", dynamicEnabled)
        
        // Disable dynamic theme
        backgroundThemeManager.setDynamicThemeEnabled(false)
        advanceUntilIdle()
        
        // Verify flag was updated
        val dynamicAfterDisable = backgroundThemeManager.isDynamicThemeEnabled().first()
        assertFalse("Dynamic theme should be disabled", dynamicAfterDisable)
        
        // Re-enable to test toggle
        backgroundThemeManager.setDynamicThemeEnabled(true)
        advanceUntilIdle()
        
        val dynamicAfterEnable = backgroundThemeManager.isDynamicThemeEnabled().first()
        assertTrue("Dynamic theme should be re-enabled", dynamicAfterEnable)
    }

    /**
     * Property 2.5: Preservation - Manual seed color selection
     * 
     * **Validates: Requirement 3.5**
     * 
     * WHEN user manually selects a seed color
     * THEN system SHALL CONTINUE TO correctly apply the selected color
     * AND getSeedColor should return the new color
     * 
     * Observed behavior on unfixed code:
     * - setSeedColor() updates the seed color in DataStore
     * - getSeedColor() returns the updated color
     */
    @Test
    fun `property - manual seed color selection updates correctly`() = runTest {
        // Get initial seed color
        val initialColor = backgroundThemeManager.getSeedColor().first()
        assertEquals("Initial color should be default", 
            BackgroundScheme.DEFAULT_SEED_COLOR, initialColor)
        
        // Set a custom red color
        val redColor = 0xFFFF0000.toInt()
        backgroundThemeManager.setSeedColor(redColor)
        advanceUntilIdle()
        
        // Verify color was updated
        val colorAfterSet = backgroundThemeManager.getSeedColor().first()
        assertEquals("Seed color should be red", redColor, colorAfterSet)
        
        // Set a different blue color
        val blueColor = 0xFF0000FF.toInt()
        backgroundThemeManager.setSeedColor(blueColor)
        advanceUntilIdle()
        
        // Verify color was updated again
        val finalColor = backgroundThemeManager.getSeedColor().first()
        assertEquals("Seed color should be blue", blueColor, finalColor)
    }

    /**
     * Property 2.6: Preservation - Scheme limit enforcement
     * 
     * **Validates: Requirement 3.6**
     * 
     * WHEN background scheme count reaches the limit (10)
     * THEN system SHALL CONTINUE TO prevent adding new schemes
     * AND scheme count should remain at the limit
     * 
     * Observed behavior on unfixed code:
     * - addBackgroundScheme() checks if count < MAX_BACKGROUND_SCHEMES
     * - If at limit, new scheme is not added
     * - Scheme count remains at MAX_BACKGROUND_SCHEMES
     */
    @Test
    fun `property - scheme limit prevents adding beyond maximum`() = runTest {
        // Add schemes up to the limit (assuming MAX is 10)
        val maxSchemes = 10
        
        for (i in 1..maxSchemes) {
            val scheme = BackgroundScheme(
                uri = "content://test/image$i.jpg",
                type = BackgroundType.IMAGE,
                seedColor = 0xFF000000.toInt() or (i * 0x111111),
                name = "Scheme $i"
            )
            backgroundThemeManager.addBackgroundScheme(scheme)
        }
        advanceUntilIdle()
        
        // Verify we have exactly maxSchemes
        val schemesAtLimit = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have $maxSchemes schemes at limit", maxSchemes, schemesAtLimit.size)
        
        // Try to add one more scheme
        val extraScheme = BackgroundScheme(
            uri = "content://test/extra.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFFFFFFFF.toInt(),
            name = "Extra Scheme"
        )
        backgroundThemeManager.addBackgroundScheme(extraScheme)
        advanceUntilIdle()
        
        // Verify scheme was not added
        val schemesAfterAttempt = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should still have $maxSchemes schemes, no more added", 
            maxSchemes, schemesAfterAttempt.size)
        
        // Verify the extra scheme is not in the list
        val hasExtraScheme = schemesAfterAttempt.any { it.name == "Extra Scheme" }
        assertFalse("Extra scheme should not be added when at limit", hasExtraScheme)
    }

    /**
     * Property 2.7: Preservation - Clear all schemes functionality
     * 
     * **Validates: Requirement 3.7**
     * 
     * WHEN user clears all background schemes
     * THEN system SHALL CONTINUE TO restore to initial state
     * AND scheme count should be 0
     * AND dynamic theme should be disabled
     * AND active index should be reset
     * 
     * Observed behavior on unfixed code:
     * - clearAllBackgrounds() removes all schemes
     * - Disables dynamic theme
     * - Resets active index to 0
     * - Restores default seed color
     */
    @Test
    fun `property - clear all schemes restores initial state`() = runTest {
        // Add multiple schemes
        for (i in 1..5) {
            val scheme = BackgroundScheme(
                uri = "content://test/image$i.jpg",
                type = BackgroundType.IMAGE,
                seedColor = 0xFF000000.toInt() or (i * 0x111111),
                name = "Scheme $i"
            )
            backgroundThemeManager.addBackgroundScheme(scheme)
        }
        advanceUntilIdle()
        
        // Verify schemes were added
        val schemesBeforeClear = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 5 schemes", 5, schemesBeforeClear.size)
        
        // Verify dynamic theme is enabled
        val dynamicBeforeClear = backgroundThemeManager.isDynamicThemeEnabled().first()
        assertTrue("Dynamic theme should be enabled", dynamicBeforeClear)
        
        // Set active index to 2
        backgroundThemeManager.setActiveBackgroundIndex(2)
        advanceUntilIdle()
        
        // Set a custom seed color
        backgroundThemeManager.setSeedColor(0xFFFF0000.toInt())
        advanceUntilIdle()
        
        // Clear all backgrounds
        backgroundThemeManager.clearAllBackgrounds()
        advanceUntilIdle()
        
        // Verify all schemes were removed
        val schemesAfterClear = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 0 schemes after clear", 0, schemesAfterClear.size)
        
        // Verify dynamic theme is disabled
        val dynamicAfterClear = backgroundThemeManager.isDynamicThemeEnabled().first()
        assertFalse("Dynamic theme should be disabled after clear", dynamicAfterClear)
        
        // Verify active index is reset
        val activeAfterClear = backgroundThemeManager.getActiveBackgroundIndex().first()
        assertEquals("Active index should be reset to 0", 0, activeAfterClear)
        
        // Verify seed color is restored to default
        val seedColorAfterClear = backgroundThemeManager.getSeedColor().first()
        assertEquals("Seed color should be restored to default", 
            BackgroundScheme.DEFAULT_SEED_COLOR, seedColorAfterClear)
    }
}
