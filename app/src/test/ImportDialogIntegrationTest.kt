package com.wind.ggbond.classtime.ui.screen.tools

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
import com.wind.ggbond.classtime.ui.screen.settings.ImportDialog
import com.wind.ggbond.classtime.ui.screen.settings.SettingsViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Task 6.1 验证测试：确认 ImportDialog 组件可被 ToolsScreen 复用
 * 
 * 测试目标：
 * 1. ImportDialog 组件存在且可以被导入
 * 2. ToolsScreen 正确导入和使用 ImportDialog
 * 3. 导入对话框的显示和交互功能正常
 * 
 * Requirements: 2.4, 11.1
 */
class ImportDialogIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockNavController: NavController
    private lateinit var mockSettingsViewModel: SettingsViewModel
    private lateinit var showImportDialogFlow: MutableStateFlow<Boolean>
    private lateinit var showExportDialogFlow: MutableStateFlow<Boolean>
    private lateinit var exportResultFlow: MutableStateFlow<com.wind.ggbond.classtime.ui.screen.settings.ExportResult?>

    @Before
    fun setup() {
        mockNavController = mockk(relaxed = true)
        mockSettingsViewModel = mockk(relaxed = true)
        
        // 初始化 StateFlow
        showImportDialogFlow = MutableStateFlow(false)
        showExportDialogFlow = MutableStateFlow(false)
        exportResultFlow = MutableStateFlow(null)
        
        // 配置 ViewModel mock
        every { mockSettingsViewModel.showImportDialog } returns showImportDialogFlow
        every { mockSettingsViewModel.showExportDialog } returns showExportDialogFlow
        every { mockSettingsViewModel.exportResult } returns exportResultFlow
        every { mockSettingsViewModel.showImportDialog() } answers {
            showImportDialogFlow.value = true
        }
        every { mockSettingsViewModel.hideImportDialog() } answers {
            showImportDialogFlow.value = false
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * 测试 1: 验证 ImportDialog 组件可以独立渲染
     */
    @Test
    fun importDialog_canBeRenderedIndependently() {
        // Given
        var dismissCalled = false
        var importCalled = false
        var importedUri: Uri? = null

        // When
        composeTestRule.setContent {
            ImportDialog(
                onDismiss = { dismissCalled = true },
                onImport = { uri ->
                    importCalled = true
                    importedUri = uri
                }
            )
        }

        // Then - 验证对话框标题显示
        composeTestRule
            .onNodeWithText("导入课程表")
            .assertIsDisplayed()

        // Then - 验证对话框说明文本显示
        composeTestRule
            .onNodeWithText("支持导入JSON、ICS、CSV格式的课程表文件")
            .assertIsDisplayed()

        // Then - 验证选择文件按钮存在
        composeTestRule
            .onNodeWithText("选择文件")
            .assertIsDisplayed()

        // Then - 验证取消按钮存在
        composeTestRule
            .onNodeWithText("取消")
            .assertIsDisplayed()

        // Then - 验证导入按钮存在（但应该是禁用状态）
        composeTestRule
            .onNodeWithText("导入")
            .assertIsDisplayed()
    }

    /**
     * 测试 2: 验证 ToolsScreen 正确导入 ImportDialog
     */
    @Test
    fun toolsScreen_importsImportDialogCorrectly() {
        // When
        composeTestRule.setContent {
            ToolsScreen(
                navController = mockNavController,
                settingsViewModel = mockSettingsViewModel
            )
        }

        // Then - 验证 ToolsScreen 正常渲染
        composeTestRule
            .onNodeWithText("工具")
            .assertIsDisplayed()

        // Then - 验证"从文件导入"按钮存在
        composeTestRule
            .onNodeWithText("从文件导入")
            .assertIsDisplayed()
    }

    /**
     * 测试 3: 验证点击"从文件导入"按钮触发 ImportDialog 显示
     */
    @Test
    fun toolsScreen_clickingImportButton_showsImportDialog() {
        // Given
        composeTestRule.setContent {
            ToolsScreen(
                navController = mockNavController,
                settingsViewModel = mockSettingsViewModel
            )
        }

        // When - 点击"从文件导入"按钮
        composeTestRule
            .onNodeWithText("从文件导入")
            .performClick()

        // Then - 验证 ViewModel 的 showImportDialog 方法被调用
        verify(exactly = 1) { mockSettingsViewModel.showImportDialog() }
    }

    /**
     * 测试 4: 验证 ImportDialog 在 ToolsScreen 中正确显示
     */
    @Test
    fun toolsScreen_displaysImportDialog_whenStateIsTrue() {
        // Given
        showImportDialogFlow.value = true

        // When
        composeTestRule.setContent {
            ToolsScreen(
                navController = mockNavController,
                settingsViewModel = mockSettingsViewModel
            )
        }

        // Then - 验证 ImportDialog 显示
        composeTestRule
            .onNodeWithText("导入课程表")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("支持导入JSON、ICS、CSV格式的课程表文件")
            .assertIsDisplayed()
    }

    /**
     * 测试 5: 验证 ImportDialog 的取消功能
     */
    @Test
    fun importDialog_cancelButton_callsOnDismiss() {
        // Given
        var dismissCalled = false

        composeTestRule.setContent {
            ImportDialog(
                onDismiss = { dismissCalled = true },
                onImport = { }
            )
        }

        // When - 点击取消按钮
        composeTestRule
            .onNodeWithText("取消")
            .performClick()

        // Then - 验证 onDismiss 被调用
        assert(dismissCalled) { "onDismiss should be called when cancel button is clicked" }
    }

    /**
     * 测试 6: 验证 ImportDialog 在 ToolsScreen 中的完整交互流程
     */
    @Test
    fun toolsScreen_importDialogInteraction_worksCorrectly() {
        // Given
        composeTestRule.setContent {
            ToolsScreen(
                navController = mockNavController,
                settingsViewModel = mockSettingsViewModel
            )
        }

        // When - 点击"从文件导入"按钮
        composeTestRule
            .onNodeWithText("从文件导入")
            .performClick()

        // Then - 验证 showImportDialog 被调用
        verify(exactly = 1) { mockSettingsViewModel.showImportDialog() }

        // When - 更新状态以显示对话框
        showImportDialogFlow.value = true
        composeTestRule.waitForIdle()

        // Then - 验证对话框显示
        composeTestRule
            .onNodeWithText("导入课程表")
            .assertIsDisplayed()

        // When - 点击取消按钮
        composeTestRule
            .onNodeWithText("取消")
            .performClick()

        // Then - 验证 hideImportDialog 被调用
        verify(exactly = 1) { mockSettingsViewModel.hideImportDialog() }
    }

    /**
     * 测试 7: 验证 ImportDialog 的导入说明信息显示
     */
    @Test
    fun importDialog_displaysImportInstructions() {
        // When
        composeTestRule.setContent {
            ImportDialog(
                onDismiss = { },
                onImport = { }
            )
        }

        // Then - 验证导入说明标题
        composeTestRule
            .onNodeWithText("导入说明")
            .assertIsDisplayed()

        // Then - 验证导入说明内容包含关键信息
        composeTestRule
            .onNode(
                hasText("• 导入的课程将添加到当前课表\n• 重复的课程会自动跳过\n• 建议先备份现有数据", substring = true)
            )
            .assertIsDisplayed()
    }

    /**
     * 测试 8: 验证 ImportDialog 的按钮状态
     */
    @Test
    fun importDialog_importButton_isDisabledInitially() {
        // When
        composeTestRule.setContent {
            ImportDialog(
                onDismiss = { },
                onImport = { }
            )
        }

        // Then - 验证导入按钮存在但禁用（因为没有选择文件）
        composeTestRule
            .onNodeWithText("导入")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    /**
     * 测试 9: 验证 ToolsScreen 中所有导入相关功能入口存在
     */
    @Test
    fun toolsScreen_displaysAllImportOptions() {
        // When
        composeTestRule.setContent {
            ToolsScreen(
                navController = mockNavController,
                settingsViewModel = mockSettingsViewModel
            )
        }

        // Then - 验证"导入导出"分类标题
        composeTestRule
            .onNodeWithText("导入导出")
            .assertIsDisplayed()

        // Then - 验证"从教务系统导入课表"（推荐）
        composeTestRule
            .onNodeWithText("从教务系统导入课表")
            .assertIsDisplayed()

        // Then - 验证"手动添加"
        composeTestRule
            .onNodeWithText("手动添加")
            .assertIsDisplayed()

        // Then - 验证"从文件导入"
        composeTestRule
            .onNodeWithText("从文件导入")
            .assertIsDisplayed()
    }

    /**
     * 测试 10: 验证 ImportDialog 组件的可复用性
     * 确认组件可以在不同上下文中使用相同的接口
     */
    @Test
    fun importDialog_isReusable_withDifferentCallbacks() {
        // Given - 第一个使用场景
        var scenario1DismissCalled = false
        var scenario1ImportCalled = false

        composeTestRule.setContent {
            ImportDialog(
                onDismiss = { scenario1DismissCalled = true },
                onImport = { scenario1ImportCalled = true }
            )
        }

        // Then - 验证第一个场景正常工作
        composeTestRule
            .onNodeWithText("导入课程表")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("取消")
            .performClick()

        assert(scenario1DismissCalled) { "Scenario 1: onDismiss should be called" }

        // Given - 第二个使用场景（模拟在 ToolsScreen 中使用）
        var scenario2DismissCalled = false

        composeTestRule.setContent {
            ImportDialog(
                onDismiss = { scenario2DismissCalled = true },
                onImport = { mockSettingsViewModel.importSchedule(mockk()) }
            )
        }

        // Then - 验证第二个场景也正常工作
        composeTestRule
            .onNodeWithText("导入课程表")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("取消")
            .performClick()

        assert(scenario2DismissCalled) { "Scenario 2: onDismiss should be called" }
    }
}
