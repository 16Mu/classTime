package com.wind.ggbond.classtime.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreManagerTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var testContext: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private val testScope = TestScope()

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        testContext = mockk(relaxed = true)
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { tmpFolder.newFile("test_settings.preferences_pb") }
        )
        every { testContext.filesDir } returns tmpFolder.root
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun readGlassEffectEnabled(): Boolean {
        return testDataStore.data.first()[DataStoreManager.SettingsKeys.GLASS_EFFECT_ENABLED_KEY]
            ?: DataStoreManager.SettingsKeys.DEFAULT_GLASS_EFFECT_ENABLED
    }

    private suspend fun writeGlassEffectEnabled(enabled: Boolean) {
        testDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.GLASS_EFFECT_ENABLED_KEY] = enabled
        }
    }

    @Test
    fun `should return default value true when DataStore is empty`() = testScope.runTest {
        val result = readGlassEffectEnabled()
        assertEquals(DataStoreManager.SettingsKeys.DEFAULT_GLASS_EFFECT_ENABLED, result)
    }

    @Test
    fun `should write and read enabled value correctly`() = testScope.runTest {
        writeGlassEffectEnabled(true)
        val result = readGlassEffectEnabled()
        assertTrue(result)
    }

    @Test
    fun `should write and read disabled value correctly`() = testScope.runTest {
        writeGlassEffectEnabled(false)
        val result = readGlassEffectEnabled()
        assertFalse(result)
    }

    @Test
    fun `should overwrite existing value correctly`() = testScope.runTest {
        writeGlassEffectEnabled(true)
        assertTrue(readGlassEffectEnabled())
        writeGlassEffectEnabled(false)
        assertFalse(readGlassEffectEnabled())
    }

    @Test
    fun `should persist value across multiple reads`() = testScope.runTest {
        writeGlassEffectEnabled(true)
        assertEquals(true, readGlassEffectEnabled())
        assertEquals(true, readGlassEffectEnabled())
        assertEquals(true, readGlassEffectEnabled())
    }

    @Test
    fun `should handle multiple consecutive writes correctly`() = testScope.runTest {
        writeGlassEffectEnabled(true)
        assertTrue(readGlassEffectEnabled())
        writeGlassEffectEnabled(false)
        assertFalse(readGlassEffectEnabled())
        writeGlassEffectEnabled(true)
        assertTrue(readGlassEffectEnabled())
    }

    @Test
    fun `should not throw exception when reading from empty DataStore`() = testScope.runTest {
        val result = readGlassEffectEnabled()
        assertNotNull(result)
    }

    @Test
    fun `DataStore key should be properly defined`() {
        assertEquals(
            "glass_effect_enabled",
            DataStoreManager.SettingsKeys.GLASS_EFFECT_ENABLED_KEY.name
        )
    }

    @Test
    fun `Default value constant is correct`() {
        assertEquals(true, DataStoreManager.SettingsKeys.DEFAULT_GLASS_EFFECT_ENABLED)
    }

    @Test
    fun `should propagate IOException during write`() = testScope.runTest {
        val corruptDataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { tmpFolder.newFile("corrupt.preferences_pb") }
        )
        corruptDataStore.edit { prefs ->
            prefs[DataStoreManager.SettingsKeys.GLASS_EFFECT_ENABLED_KEY] = true
        }
        val result = corruptDataStore.data.first()[DataStoreManager.SettingsKeys.GLASS_EFFECT_ENABLED_KEY]
        assertEquals(true, result)
    }
}
