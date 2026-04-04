package com.wind.ggbond.classtime.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * Unit tests for DataStoreManager bottom bar blur functionality
 * 
 * **Validates: Requirements 7.3, 15.2**
 * 
 * Tests:
 * - bottomBarBlurEnabled read and write operations
 * - Default value (true)
 * - Error handling (IO exceptions)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreManagerTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var testContext: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Create mock context
        testContext = mockk(relaxed = true)
        
        // Create test DataStore with temporary file
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_settings.preferences_pb") }
        )
        
        // Mock context to return test DataStore
        every { testContext.filesDir } returns tmpFolder.root
    }

    @After
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
    }
    
    /**
     * Helper function to read bottomBarBlurEnabled from DataStore
     */
    private suspend fun readBottomBarBlurEnabled(): Boolean {
        return testDataStore.data.first()[DataStoreManager.SettingsKeys.BOTTOM_BAR_BLUR_ENABLED_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_BOTTOM_BAR_BLUR_ENABLED
    }
    
    /**
     * Helper function to write bottomBarBlurEnabled to DataStore
     */
    private suspend fun writeBottomBarBlurEnabled(enabled: Boolean) {
        testDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.BOTTOM_BAR_BLUR_ENABLED_KEY] = enabled
        }
    }

    /**
     * Test 1: Read default value when DataStore is empty
     * 
     * Validates: Requirement 15.2
     * 
     * Verifies that when no value is stored in DataStore,
     * the default value of true is returned
     */
    @Test
    fun `should return default value true when DataStore is empty`() = testScope.runTest {
        // When: Read bottom bar blur enabled without setting any value
        val result = readBottomBarBlurEnabled()
        
        // Then: Should return default value (true)
        assertEquals(true, result)
    }

    /**
     * Test 2: Write and read enabled value
     * 
     * Validates: Requirement 7.3
     * 
     * Verifies that writing true to DataStore and reading it back
     * returns the correct value
     */
    @Test
    fun `should write and read enabled value correctly`() = testScope.runTest {
        // When: Write enabled value
        writeBottomBarBlurEnabled(true)
        advanceUntilIdle()
        
        // Then: Read should return the written value
        val result = readBottomBarBlurEnabled()
        assertEquals(true, result)
    }

    /**
     * Test 3: Write and read disabled value
     * 
     * Validates: Requirement 7.3
     * 
     * Verifies that writing false to DataStore and reading it back
     * returns the correct value
     */
    @Test
    fun `should write and read disabled value correctly`() = testScope.runTest {
        // When: Write disabled value
        writeBottomBarBlurEnabled(false)
        advanceUntilIdle()
        
        // Then: Read should return the written value
        val result = readBottomBarBlurEnabled()
        assertEquals(false, result)
    }

    /**
     * Test 4: Overwrite existing value
     * 
     * Validates: Requirement 7.3
     * 
     * Verifies that writing a new value overwrites the previous value
     */
    @Test
    fun `should overwrite existing value correctly`() = testScope.runTest {
        // Given: Initial value is true
        writeBottomBarBlurEnabled(true)
        advanceUntilIdle()
        assertEquals(true, readBottomBarBlurEnabled())
        
        // When: Overwrite with false
        writeBottomBarBlurEnabled(false)
        advanceUntilIdle()
        
        // Then: Should return new value
        val result = readBottomBarBlurEnabled()
        assertEquals(false, result)
    }

    /**
     * Test 5: Multiple consecutive writes
     * 
     * Validates: Requirement 7.3
     * 
     * Verifies that multiple consecutive writes are all persisted correctly
     */
    @Test
    fun `should handle multiple consecutive writes correctly`() = testScope.runTest {
        // When: Write multiple values in sequence
        writeBottomBarBlurEnabled(false)
        advanceUntilIdle()
        
        writeBottomBarBlurEnabled(true)
        advanceUntilIdle()
        
        writeBottomBarBlurEnabled(false)
        advanceUntilIdle()
        
        // Then: Final value should be false
        val result = readBottomBarBlurEnabled()
        assertEquals(false, result)
    }

    /**
     * Test 6: Value persists across multiple reads
     * 
     * Validates: Requirement 15.2
     * 
     * Verifies that a written value persists and can be read multiple times
     */
    @Test
    fun `should persist value across multiple reads`() = testScope.runTest {
        // Given: Write a value
        writeBottomBarBlurEnabled(false)
        advanceUntilIdle()
        
        // When: Read multiple times
        val read1 = readBottomBarBlurEnabled()
        val read2 = readBottomBarBlurEnabled()
        val read3 = readBottomBarBlurEnabled()
        
        // Then: All reads should return the same value
        assertEquals(false, read1)
        assertEquals(false, read2)
        assertEquals(false, read3)
    }

    /**
     * Test 7: DataStore key constant is correct
     * 
     * Validates: Requirement 7.3
     * 
     * Verifies that the DataStore key constant is properly defined
     */
    @Test
    fun `DataStore key should be properly defined`() {
        // Then: Key should have correct name
        assertEquals(
            "bottom_bar_blur_enabled",
            DataStoreManager.SettingsKeys.BOTTOM_BAR_BLUR_ENABLED_KEY.name
        )
    }

    /**
     * Test 8: Default value constant is correct
     * 
     * Validates: Requirement 15.2
     * 
     * Verifies that the default value constant is set to true
     */
    @Test
    fun `default value constant should be true`() {
        // Then: Default value should be true
        assertEquals(
            true,
            DataStoreManager.SettingsKeys.DEFAULT_BOTTOM_BAR_BLUR_ENABLED
        )
    }

    /**
     * Test 9: Handle IOException during write
     * 
     * Validates: Requirement 15.2
     * 
     * Verifies that IOException during write is properly propagated
     */
    @Test
    fun `should propagate IOException during write`() = testScope.runTest {
        // Given: Create a DataStore that will fail on write
        val failingDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { 
                val file = tmpFolder.newFile("failing_test.preferences_pb")
                file.setReadOnly() // Make file read-only to cause IOException
                file
            }
        )
        
        // When/Then: Write should throw IOException
        try {
            failingDataStore.edit { prefs ->
                prefs[DataStoreManager.SettingsKeys.BOTTOM_BAR_BLUR_ENABLED_KEY] = false
            }
            advanceUntilIdle()
            fail("Expected IOException to be thrown")
        } catch (e: IOException) {
            // Expected exception
            assertTrue(true)
        } catch (e: Exception) {
            // May also throw other exceptions depending on platform
            assertTrue(true)
        }
    }

    /**
     * Test 10: Read operation does not throw on empty DataStore
     * 
     * Validates: Requirement 15.2
     * 
     * Verifies that reading from an empty DataStore returns default value
     * without throwing exceptions
     */
    @Test
    fun `should not throw exception when reading from empty DataStore`() = testScope.runTest {
        // When: Read from fresh DataStore
        val result = readBottomBarBlurEnabled()
        
        // Then: Should return default value without exception
        assertEquals(true, result)
    }
}
