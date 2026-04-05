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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Preservation Property Tests for Bug 4 - 壁纸未应用到课表界面
 * 
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
 * 
 * **IMPORTANT**: Follow observation-first methodology
 * - These tests observe behavior on UNFIXED code for non-buggy inputs
 * - They capture existing wallpaper functionality that must be preserved
 * - Property-based testing generates many test cases for stronger guarantees
 * 
 * Non-Bug Conditions: Operations that should work correctly on unfixed code
 * - Wallpaper display on other tabs (if working) (3.1)
 * - Default background when no wallpaper configured (3.2)
 * - Wallpaper blur and dim parameters application (3.3)
 * - Wallpaper scheme deletion and switching (3.4)
 * 
 * **EXPECTED ON UNFIXED CODE**: Tests PASS (confirms baseline behavior to preserve)
 * **EXPECTED ON FIXED CODE**: Tests still PASS (confirms no regressions)
 * 
 * Note: Since the bug is that wallpaper doesn't display on Schedule screen,
 * these tests focus on:
 * 1. Wallpaper configuration and management (should work)
 * 2. Default background behavior (should work)
 * 3. Wallpaper parameters (should be stored correctly)
 * 4. Other screens (may or may not work - to be observed)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleWallpaperPreservationTest {

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
        val testFile = File.createTempFile("test_wallpaper_preservation", ".preferences_pb")
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
     * Property 2.1: Preservation - Default background when no wallpaper configured
     * 
     * **Validates: Requirement 3.2**
     * 
     * WHEN user has not configured any wallpaper
     * THEN system SHALL CONTINUE TO display default background
     * AND isDynamicThemeEnabled should return false
     * AND activeScheme should be null
     * 
     * This behavior should work on both unfixed and fixed code.
     */
    @Test
    fun `property - default background shown when no wallpaper configured`() = runTest {
        // Initial state: no wallpaper configured
        val schemes = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have no schemes initially", 0, schemes.size)
        
        // Verify dynamic theme is disabled
        val isDynamicEnabled = backgroundThemeManager.isDynamicThemeEnabled().first()
        assertFalse("Dynamic theme should be disabled when no wallpaper", isDynamicEnabled)
        
        // Verify active scheme is null (no wallpaper)
        val activeScheme = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertEquals("Active scheme should be null when no wallpaper", null, activeScheme)
        
        // Verify seed color is default
        val seedColor = backgroundThemeManager.getSeedColor().first()
        assertEquals("Seed color should be default", 
            BackgroundScheme.DEFAULT_SEED_COLOR, seedColor)
        
        println("✅ Default background behavior preserved: no wallpaper = default background")
    }

    /**
     * Property 2.2: Preservation - Wallpaper configuration and storage
     * 
     * **Validates: Requirement 3.3, 3.4**
     * 
     * WHEN user configures a wallpaper with blur and dim parameters
     * THEN system SHALL CONTINUE TO correctly store the configuration
     * AND parameters should be retrievable
     * 
     * This tests that wallpaper configuration works (even if display doesn't).
     */
    @Test
    fun `property - wallpaper configuration with blur and dim parameters stored correctly`() = runTest {
        // Configure an image wallpaper with blur and dim
        val imageWallpaper = BackgroundScheme(
            name = "Test Wallpaper",
            uri = "content://test/wallpaper.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF3498DB.toInt(),
            blurRadius = 10,
            dimAmount = 40
        )
        
        backgroundThemeManager.addBackgroundScheme(imageWallpaper)
        advanceUntilIdle()
        
        // Verify scheme was added
        val schemes = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 1 scheme", 1, schemes.size)
        
        // Verify scheme properties are stored correctly
        val storedScheme = schemes[0]
        assertEquals("URI should match", imageWallpaper.uri, storedScheme.uri)
        assertEquals("Type should be IMAGE", BackgroundType.IMAGE, storedScheme.type)
        assertEquals("Seed color should match", imageWallpaper.seedColor, storedScheme.seedColor)
        assertEquals("Name should match", imageWallpaper.name, storedScheme.name)
        assertEquals("Blur radius should be 10", 10, storedScheme.blurRadius)
        assertEquals("Dim amount should be 40", 40, storedScheme.dimAmount)
        
        // Verify dynamic theme is enabled
        val isDynamicEnabled = backgroundThemeManager.isDynamicThemeEnabled().first()
        assertTrue("Dynamic theme should be enabled after adding wallpaper", isDynamicEnabled)
        
        // Verify active scheme is set
        val activeScheme = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertNotNull("Active scheme should not be null", activeScheme)
        assertEquals("Active scheme should match added wallpaper", imageWallpaper.uri, activeScheme?.uri)
        
        println("✅ Wallpaper configuration preserved: blur=$10, dim=$40 stored correctly")
    }

    /**
     * Property 2.3: Preservation - Wallpaper blur parameter variations
     * 
     * **Validates: Requirement 3.3**
     * 
     * WHEN user sets different blur radius values
     * THEN system SHALL CONTINUE TO correctly store and retrieve blur values
     * 
     * Property-based approach: Test multiple blur values
     */
    @Test
    fun `property - wallpaper blur parameter correctly stored for various values`() = runTest {
        val blurValues = listOf(0, 5, 10, 15, 20, 25, 30)
        
        blurValues.forEachIndexed { index, blurRadius ->
            val scheme = BackgroundScheme(
                name = "Wallpaper Blur $blurRadius",
                uri = "content://test/wallpaper_blur_$blurRadius.jpg",
                type = BackgroundType.IMAGE,
                seedColor = 0xFF000000.toInt() or (index * 0x111111),
                blurRadius = blurRadius,
                dimAmount = 30
            )
            
            backgroundThemeManager.addBackgroundScheme(scheme)
            advanceUntilIdle()
        }
        
        // Verify all schemes were added with correct blur values
        val schemes = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have ${blurValues.size} schemes", blurValues.size, schemes.size)
        
        schemes.forEachIndexed { index, storedScheme ->
            val expectedBlur = blurValues[index]
            assertEquals("Blur radius should be $expectedBlur for scheme $index", 
                expectedBlur, storedScheme.blurRadius)
        }
        
        println("✅ Blur parameter preservation verified for values: $blurValues")
    }

    /**
     * Property 2.4: Preservation - Wallpaper dim parameter variations
     * 
     * **Validates: Requirement 3.3**
     * 
     * WHEN user sets different dim amount values
     * THEN system SHALL CONTINUE TO correctly store and retrieve dim values
     * 
     * Property-based approach: Test multiple dim values
     */
    @Test
    fun `property - wallpaper dim parameter correctly stored for various values`() = runTest {
        val dimValues = listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        
        dimValues.forEachIndexed { index, dimAmount ->
            val scheme = BackgroundScheme(
                name = "Wallpaper Dim $dimAmount",
                uri = "content://test/wallpaper_dim_$dimAmount.jpg",
                type = BackgroundType.IMAGE,
                seedColor = 0xFF000000.toInt() or (index * 0x0F0F0F),
                blurRadius = 5,
                dimAmount = dimAmount
            )
            
            backgroundThemeManager.addBackgroundScheme(scheme)
            advanceUntilIdle()
        }
        
        // Verify all schemes were added with correct dim values
        val schemes = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have ${dimValues.size} schemes", dimValues.size, schemes.size)
        
        schemes.forEachIndexed { index, storedScheme ->
            val expectedDim = dimValues[index]
            assertEquals("Dim amount should be $expectedDim for scheme $index", 
                expectedDim, storedScheme.dimAmount)
        }
        
        println("✅ Dim parameter preservation verified for values: $dimValues")
    }

    /**
     * Property 2.5: Preservation - Wallpaper scheme switching
     * 
     * **Validates: Requirement 3.4**
     * 
     * WHEN user switches between different wallpaper schemes
     * THEN system SHALL CONTINUE TO correctly update active scheme
     * AND active scheme should reflect the selected wallpaper
     * 
     * This tests scheme switching functionality (even if display doesn't work on Schedule screen).
     */
    @Test
    fun `property - wallpaper scheme switching updates active scheme correctly`() = runTest {
        // Add 3 different wallpaper schemes
        val scheme1 = BackgroundScheme(
            name = "Red Wallpaper",
            uri = "content://test/wallpaper1.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFFFF0000.toInt(),
            blurRadius = 5,
            dimAmount = 30
        )
        val scheme2 = BackgroundScheme(
            name = "Green Wallpaper",
            uri = "content://test/wallpaper2.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF00FF00.toInt(),
            blurRadius = 10,
            dimAmount = 40
        )
        val scheme3 = BackgroundScheme(
            name = "Blue Wallpaper",
            uri = "content://test/wallpaper3.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF0000FF.toInt(),
            blurRadius = 15,
            dimAmount = 50
        )
        
        backgroundThemeManager.addBackgroundScheme(scheme1)
        backgroundThemeManager.addBackgroundScheme(scheme2)
        backgroundThemeManager.addBackgroundScheme(scheme3)
        advanceUntilIdle()
        
        // Initially, active should be scheme 1 (index 0)
        val initialActive = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertNotNull("Initial active scheme should not be null", initialActive)
        assertEquals("Initial active should be Red Wallpaper", "Red Wallpaper", initialActive?.name)
        
        // Switch to scheme 2 (index 1)
        backgroundThemeManager.setActiveBackgroundIndex(1)
        advanceUntilIdle()
        
        val activeAfterSwitch1 = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertNotNull("Active scheme should not be null after switch", activeAfterSwitch1)
        assertEquals("Active should be Green Wallpaper", "Green Wallpaper", activeAfterSwitch1?.name)
        assertEquals("Blur should be 10", 10, activeAfterSwitch1?.blurRadius)
        assertEquals("Dim should be 40", 40, activeAfterSwitch1?.dimAmount)
        
        // Switch to scheme 3 (index 2)
        backgroundThemeManager.setActiveBackgroundIndex(2)
        advanceUntilIdle()
        
        val activeAfterSwitch2 = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertNotNull("Active scheme should not be null after second switch", activeAfterSwitch2)
        assertEquals("Active should be Blue Wallpaper", "Blue Wallpaper", activeAfterSwitch2?.name)
        assertEquals("Blur should be 15", 15, activeAfterSwitch2?.blurRadius)
        assertEquals("Dim should be 50", 50, activeAfterSwitch2?.dimAmount)
        
        println("✅ Wallpaper scheme switching preserved: switched between 3 schemes successfully")
    }

    /**
     * Property 2.6: Preservation - Wallpaper scheme deletion
     * 
     * **Validates: Requirement 3.4**
     * 
     * WHEN user deletes a wallpaper scheme
     * THEN system SHALL CONTINUE TO correctly remove the scheme
     * AND adjust active index appropriately
     * 
     * This tests scheme deletion functionality.
     */
    @Test
    fun `property - wallpaper scheme deletion removes scheme and adjusts active index`() = runTest {
        // Add 3 wallpaper schemes
        val schemes = listOf(
            BackgroundScheme(name = "Wallpaper 1", uri = "content://test/w1.jpg", type = BackgroundType.IMAGE, seedColor = 0xFFFF0000.toInt()),
            BackgroundScheme(name = "Wallpaper 2", uri = "content://test/w2.jpg", type = BackgroundType.IMAGE, seedColor = 0xFF00FF00.toInt()),
            BackgroundScheme(name = "Wallpaper 3", uri = "content://test/w3.jpg", type = BackgroundType.IMAGE, seedColor = 0xFF0000FF.toInt())
        )
        
        schemes.forEach { backgroundThemeManager.addBackgroundScheme(it) }
        advanceUntilIdle()
        
        val schemesBeforeDelete = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 3 schemes", 3, schemesBeforeDelete.size)
        
        // Set active to scheme 2 (index 1)
        backgroundThemeManager.setActiveBackgroundIndex(1)
        advanceUntilIdle()
        
        val activeBeforeDelete = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertEquals("Active should be Wallpaper 2", "Wallpaper 2", activeBeforeDelete?.name)
        
        // Delete the active scheme (index 1)
        backgroundThemeManager.removeBackgroundScheme(1)
        advanceUntilIdle()
        
        // Verify scheme was deleted
        val schemesAfterDelete = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 2 schemes after delete", 2, schemesAfterDelete.size)
        assertEquals("First scheme should be Wallpaper 1", "Wallpaper 1", schemesAfterDelete[0].name)
        assertEquals("Second scheme should be Wallpaper 3", "Wallpaper 3", schemesAfterDelete[1].name)
        
        // Verify active index was adjusted
        val activeAfterDelete = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertNotNull("Active scheme should not be null after delete", activeAfterDelete)
        assertTrue("Active should be either Wallpaper 1 or Wallpaper 3",
            activeAfterDelete?.name == "Wallpaper 1" || activeAfterDelete?.name == "Wallpaper 3")
        
        println("✅ Wallpaper scheme deletion preserved: deleted scheme and adjusted active index")
    }

    /**
     * Property 2.7: Preservation - Different wallpaper types (IMAGE, GIF, VIDEO)
     * 
     * **Validates: Requirement 3.1, 3.3**
     * 
     * WHEN user configures different wallpaper types
     * THEN system SHALL CONTINUE TO correctly store type information
     * AND type should be retrievable
     * 
     * Property-based approach: Test all wallpaper types
     */
    @Test
    fun `property - different wallpaper types stored correctly`() = runTest {
        val imageWallpaper = BackgroundScheme(
            name = "Image Wallpaper",
            uri = "content://test/image.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFFFF0000.toInt(),
            blurRadius = 10,
            dimAmount = 30
        )
        
        val gifWallpaper = BackgroundScheme(
            name = "GIF Wallpaper",
            uri = "content://test/animation.gif",
            type = BackgroundType.GIF,
            seedColor = 0xFF00FF00.toInt(),
            blurRadius = 5,
            dimAmount = 40
        )
        
        val videoWallpaper = BackgroundScheme(
            name = "Video Wallpaper",
            uri = "content://test/video.mp4",
            type = BackgroundType.VIDEO,
            seedColor = 0xFF0000FF.toInt(),
            blurRadius = 0,
            dimAmount = 50
        )
        
        backgroundThemeManager.addBackgroundScheme(imageWallpaper)
        backgroundThemeManager.addBackgroundScheme(gifWallpaper)
        backgroundThemeManager.addBackgroundScheme(videoWallpaper)
        advanceUntilIdle()
        
        // Verify all types were stored correctly
        val schemes = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 3 schemes", 3, schemes.size)
        
        assertEquals("First should be IMAGE type", BackgroundType.IMAGE, schemes[0].type)
        assertEquals("Second should be GIF type", BackgroundType.GIF, schemes[1].type)
        assertEquals("Third should be VIDEO type", BackgroundType.VIDEO, schemes[2].type)
        
        // Verify each type can be set as active
        backgroundThemeManager.setActiveBackgroundIndex(0)
        advanceUntilIdle()
        val activeImage = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertEquals("Active IMAGE type should be correct", BackgroundType.IMAGE, activeImage?.type)
        
        backgroundThemeManager.setActiveBackgroundIndex(1)
        advanceUntilIdle()
        val activeGif = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertEquals("Active GIF type should be correct", BackgroundType.GIF, activeGif?.type)
        
        backgroundThemeManager.setActiveBackgroundIndex(2)
        advanceUntilIdle()
        val activeVideo = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertEquals("Active VIDEO type should be correct", BackgroundType.VIDEO, activeVideo?.type)
        
        println("✅ Different wallpaper types preserved: IMAGE, GIF, VIDEO all stored correctly")
    }

    /**
     * Property 2.8: Preservation - Disable dynamic theme restores default
     * 
     * **Validates: Requirement 3.2**
     * 
     * WHEN user disables dynamic theme (by clearing background)
     * THEN system SHALL CONTINUE TO restore default background
     * AND isDynamicThemeEnabled should return false
     * 
     * Note: BackgroundThemeManager doesn't have setDynamicThemeEnabled,
     * so we test clearBackground which disables dynamic theme.
     */
    @Test
    fun `property - clear background restores default and disables dynamic theme`() = runTest {
        // Add a wallpaper
        val wallpaper = BackgroundScheme(
            name = "Test Wallpaper",
            uri = "content://test/wallpaper.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF3498DB.toInt()
        )
        
        backgroundThemeManager.addBackgroundScheme(wallpaper)
        advanceUntilIdle()
        
        // Verify dynamic theme is enabled
        val dynamicEnabled = backgroundThemeManager.isDynamicThemeEnabled().first()
        assertTrue("Dynamic theme should be enabled", dynamicEnabled)
        
        // Verify active scheme is set
        val activeWithDynamic = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertNotNull("Active scheme should not be null when dynamic enabled", activeWithDynamic)
        
        // Clear background (this disables dynamic theme)
        backgroundThemeManager.clearBackground()
        advanceUntilIdle()
        
        // Verify dynamic theme is disabled
        val dynamicDisabled = backgroundThemeManager.isDynamicThemeEnabled().first()
        assertFalse("Dynamic theme should be disabled after clear", dynamicDisabled)
        
        // Verify active scheme is null
        val activeAfterClear = backgroundThemeManager.getActiveBackgroundScheme().first()
        assertEquals("Active scheme should be null after clear", null, activeAfterClear)
        
        // Verify schemes list is empty
        val schemesAfterClear = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 0 schemes after clear", 0, schemesAfterClear.size)
        
        println("✅ Clear background preserved: disables dynamic theme and restores default")
    }

    /**
     * Property 2.9: Preservation - Wallpaper parameters boundary values
     * 
     * **Validates: Requirement 3.3**
     * 
     * WHEN user sets boundary values for blur and dim
     * THEN system SHALL CONTINUE TO correctly store and retrieve boundary values
     * 
     * Property-based approach: Test boundary conditions
     */
    @Test
    fun `property - wallpaper parameters boundary values stored correctly`() = runTest {
        // Test minimum values
        val minScheme = BackgroundScheme(
            name = "Min Parameters",
            uri = "content://test/min.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFF000000.toInt(),
            blurRadius = 0,
            dimAmount = 0
        )
        
        // Test maximum values (assuming max blur = 30, max dim = 100)
        val maxScheme = BackgroundScheme(
            name = "Max Parameters",
            uri = "content://test/max.jpg",
            type = BackgroundType.IMAGE,
            seedColor = 0xFFFFFFFF.toInt(),
            blurRadius = 30,
            dimAmount = 100
        )
        
        backgroundThemeManager.addBackgroundScheme(minScheme)
        backgroundThemeManager.addBackgroundScheme(maxScheme)
        advanceUntilIdle()
        
        val schemes = backgroundThemeManager.getAllBackgroundSchemes().first()
        assertEquals("Should have 2 schemes", 2, schemes.size)
        
        // Verify minimum values
        val storedMin = schemes[0]
        assertEquals("Min blur should be 0", 0, storedMin.blurRadius)
        assertEquals("Min dim should be 0", 0, storedMin.dimAmount)
        
        // Verify maximum values
        val storedMax = schemes[1]
        assertEquals("Max blur should be 30", 30, storedMax.blurRadius)
        assertEquals("Max dim should be 100", 100, storedMax.dimAmount)
        
        println("✅ Boundary values preserved: blur(0-30), dim(0-100) stored correctly")
    }
}
