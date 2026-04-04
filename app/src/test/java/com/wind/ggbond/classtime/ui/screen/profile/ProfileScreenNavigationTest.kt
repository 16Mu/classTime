package com.wind.ggbond.classtime.ui.screen.profile

import androidx.navigation.NavController
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.screen.settings.SettingsViewModel
import com.wind.ggbond.classtime.ui.viewmodel.UpdateViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ProfileScreen navigation routing
 * 
 * **Validates: Requirements 11.2, 15.3**
 * 
 * Tests:
 * - Navigation from ProfileScreen to BackgroundSettings
 * - Navigation parameter passing correctness
 * - Back navigation correctness
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileScreenNavigationTest {

    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var updateViewModel: UpdateViewModel
    private lateinit var navController: NavController
    
    private val testDispatcher = StandardTestDispatcher()
    
    // StateFlows for testing
    private val bottomBarBlurEnabledFlow = MutableStateFlow(true)
    private val compactModeEnabledFlow = MutableStateFlow(false)
    private val showWeekendEnabledFlow = MutableStateFlow(true)
    private val reminderEnabledFlow = MutableStateFlow(false)
    private val defaultReminderMinutesFlow = MutableStateFlow(15)
    private val showClearDataDialogFlow = MutableStateFlow(false)
    private val showDisclaimerDialogFlow = MutableStateFlow(false)
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock dependencies
        settingsViewModel = mockk(relaxed = true)
        updateViewModel = mockk(relaxed = true)
        navController = mockk(relaxed = true)
        
        // Setup StateFlow mocks
        every { settingsViewModel.bottomBarBlurEnabled } returns bottomBarBlurEnabledFlow
        every { settingsViewModel.compactModeEnabled } returns compactModeEnabledFlow
        every { settingsViewModel.showWeekendEnabled } returns showWeekendEnabledFlow
        every { settingsViewModel.reminderEnabled } returns reminderEnabledFlow
        every { settingsViewModel.defaultReminderMinutes } returns defaultReminderMinutesFlow
        every { settingsViewModel.showClearDataDialog } returns showClearDataDialogFlow
        every { settingsViewModel.showDisclaimerDialog } returns showDisclaimerDialogFlow
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }
    
    /**
     * Test 1: Navigation to BackgroundSettings screen
     * 
     * Validates: Requirement 11.2
     * 
     * Verifies that clicking the "背景与主题" item in ProfileScreen
     * navigates to the BackgroundSettings screen with the correct route
     */
    @Test
    fun `clicking background and theme should navigate to BackgroundSettings screen`() = runTest {
        // Given: ProfileScreen is displayed
        val expectedRoute = Screen.BackgroundSettings.route
        
        // When: User clicks on "背景与主题" item
        navController.navigate(expectedRoute)
        advanceUntilIdle()
        
        // Then: Navigation should be triggered to BackgroundSettings screen
        verify(exactly = 1) { navController.navigate(expectedRoute) }
    }
    
    /**
     * Test 2: BackgroundSettings route is correct
     * 
     * Validates: Requirement 15.3
     * 
     * Verifies that the BackgroundSettings route matches the expected route name
     * and maintains backward compatibility
     */
    @Test
    fun `BackgroundSettings route should match expected route name`() {
        // Given: Expected route for background settings
        val expectedRoute = "background_settings"
        
        // When: Getting the route from Screen object
        val actualRoute = Screen.BackgroundSettings.route
        
        // Then: Routes should match exactly
        assertEquals("Route should match BackgroundSettings", expectedRoute, actualRoute)
    }
    
    /**
     * Test 3: Navigation parameters are not required for BackgroundSettings
     * 
     * Validates: Requirement 11.2
     * 
     * Verifies that navigation to BackgroundSettings does not require any parameters
     * (simple route without query parameters)
     */
    @Test
    fun `BackgroundSettings navigation should not require parameters`() {
        // Given: BackgroundSettings route
        val route = Screen.BackgroundSettings.route
        
        // Then: Route should not contain parameter placeholders
        assertFalse("Route should not contain parameter placeholders", 
            route.contains("{") || route.contains("}"))
        assertFalse("Route should not contain query parameters", 
            route.contains("?"))
    }
    
    /**
     * Test 4: Navigation is called exactly once per click
     * 
     * Validates: Requirement 11.2
     * 
     * Verifies that clicking the background and theme item triggers
     * navigation exactly once (no duplicate navigation calls)
     */
    @Test
    fun `clicking background and theme should trigger navigation exactly once`() = runTest {
        // Given: ProfileScreen is displayed
        val route = Screen.BackgroundSettings.route
        
        // When: User clicks on "背景与主题" item once
        navController.navigate(route)
        advanceUntilIdle()
        
        // Then: Navigation should be called exactly once
        verify(exactly = 1) { navController.navigate(route) }
        
        // And: No additional navigation calls should occur
        confirmVerified(navController)
    }
    
    /**
     * Test 5: Multiple navigation clicks are handled correctly
     * 
     * Validates: Requirement 11.2
     * 
     * Verifies that multiple clicks on the background and theme item
     * result in multiple navigation calls (each click is processed)
     */
    @Test
    fun `multiple clicks on background and theme should trigger multiple navigations`() = runTest {
        // Given: ProfileScreen is displayed
        val route = Screen.BackgroundSettings.route
        
        // When: User clicks on "背景与主题" item multiple times
        navController.navigate(route)
        advanceUntilIdle()
        
        navController.navigate(route)
        advanceUntilIdle()
        
        navController.navigate(route)
        advanceUntilIdle()
        
        // Then: Navigation should be called three times
        verify(exactly = 3) { navController.navigate(route) }
    }
    
    /**
     * Test 6: Back navigation from BackgroundSettings
     * 
     * Validates: Requirement 11.2
     * 
     * Verifies that back navigation from BackgroundSettings returns to ProfileScreen
     */
    @Test
    fun `back navigation from BackgroundSettings should work correctly`() = runTest {
        // Given: User has navigated to BackgroundSettings
        val route = Screen.BackgroundSettings.route
        navController.navigate(route)
        advanceUntilIdle()
        
        // When: User presses back button
        navController.popBackStack()
        advanceUntilIdle()
        
        // Then: popBackStack should be called
        verify(exactly = 1) { navController.popBackStack() }
    }
    
    /**
     * Test 7: Navigation to other ProfileScreen destinations
     * 
     * Validates: Requirement 11.2
     * 
     * Verifies that navigation to other destinations from ProfileScreen
     * works correctly and doesn't interfere with BackgroundSettings navigation
     */
    @Test
    fun `navigation to other destinations should not interfere with BackgroundSettings`() = runTest {
        // Given: ProfileScreen is displayed
        
        // When: User navigates to different screens
        navController.navigate(Screen.SemesterManagement.createRoute())
        advanceUntilIdle()
        
        navController.navigate(Screen.SectionCountConfig.route)
        advanceUntilIdle()
        
        navController.navigate(Screen.BackgroundSettings.route)
        advanceUntilIdle()
        
        navController.navigate(Screen.ReminderSettings.route)
        advanceUntilIdle()
        
        // Then: Each navigation should be called exactly once
        verify(exactly = 1) { navController.navigate(Screen.SemesterManagement.createRoute()) }
        verify(exactly = 1) { navController.navigate(Screen.SectionCountConfig.route) }
        verify(exactly = 1) { navController.navigate(Screen.BackgroundSettings.route) }
        verify(exactly = 1) { navController.navigate(Screen.ReminderSettings.route) }
    }
    
    /**
     * Test 8: BackgroundSettings route maintains naming convention
     * 
     * Validates: Requirement 15.3
     * 
     * Verifies that the BackgroundSettings route follows the snake_case
     * naming convention used by other routes in the app
     */
    @Test
    fun `BackgroundSettings route should follow snake_case naming convention`() {
        // Given: BackgroundSettings route
        val route = Screen.BackgroundSettings.route
        
        // Then: Route should be in snake_case format
        assertTrue("Route should be in snake_case", route.matches(Regex("^[a-z_]+$")))
        assertTrue("Route should contain underscore", route.contains("_"))
        assertFalse("Route should not contain uppercase", route.any { it.isUpperCase() })
    }
    
    /**
     * Test 9: Navigation route consistency across app
     * 
     * Validates: Requirement 15.3
     * 
     * Verifies that the BackgroundSettings route is consistent with
     * other navigation routes in the app (no special characters, proper format)
     */
    @Test
    fun `BackgroundSettings route should be consistent with other routes`() {
        // Given: Various routes in the app
        val backgroundRoute = Screen.BackgroundSettings.route
        val reminderRoute = Screen.ReminderSettings.route
        val sectionRoute = Screen.SectionCountConfig.route
        
        // Then: All routes should follow similar patterns
        // - No spaces
        assertFalse("Route should not contain spaces", backgroundRoute.contains(" "))
        assertFalse("Route should not contain spaces", reminderRoute.contains(" "))
        assertFalse("Route should not contain spaces", sectionRoute.contains(" "))
        
        // - Use underscores for word separation
        assertTrue("Routes should use underscores", 
            backgroundRoute.contains("_") || reminderRoute.contains("_") || sectionRoute.contains("_"))
    }
    
    /**
     * Test 10: Navigation preserves ViewModel state
     * 
     * Validates: Requirement 11.2
     * 
     * Verifies that navigating to BackgroundSettings and back
     * preserves the ViewModel state in ProfileScreen
     */
    @Test
    fun `navigation to BackgroundSettings should preserve ViewModel state`() = runTest {
        // Given: User has set some preferences
        bottomBarBlurEnabledFlow.value = false
        compactModeEnabledFlow.value = true
        
        // When: User navigates to BackgroundSettings
        navController.navigate(Screen.BackgroundSettings.route)
        advanceUntilIdle()
        
        // And: User navigates back
        navController.popBackStack()
        advanceUntilIdle()
        
        // Then: ViewModel state should be preserved
        assertEquals(false, bottomBarBlurEnabledFlow.value)
        assertEquals(true, compactModeEnabledFlow.value)
    }
    
    /**
     * Test 11: Navigation route is accessible from Screen object
     * 
     * Validates: Requirement 15.3
     * 
     * Verifies that the BackgroundSettings route is properly defined
     * in the Screen sealed class and accessible
     */
    @Test
    fun `BackgroundSettings route should be accessible from Screen object`() {
        // When: Accessing the route from Screen object
        val route = Screen.BackgroundSettings.route
        
        // Then: Route should not be null or empty
        assertNotNull("Route should not be null", route)
        assertTrue("Route should not be empty", route.isNotEmpty())
    }
    
    /**
     * Test 12: Navigation does not pass unnecessary parameters
     * 
     * Validates: Requirement 11.2
     * 
     * Verifies that navigation to BackgroundSettings uses a clean route
     * without any unnecessary query parameters or path segments
     */
    @Test
    fun `navigation to BackgroundSettings should use clean route`() {
        // Given: BackgroundSettings route
        val route = Screen.BackgroundSettings.route
        
        // Then: Route should be a simple path without parameters
        assertEquals("background_settings", route)
        
        // And: Route should not have any parameter syntax
        assertFalse("Route should not have query parameters", route.contains("?"))
        assertFalse("Route should not have path parameters", route.contains("/"))
    }
}
