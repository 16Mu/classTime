package com.wind.ggbond.classtime.service

import android.content.Context
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UpdateOrchestratorTest {

    private lateinit var orchestrator: UpdateOrchestrator
    private lateinit var mockContext: Context
    private lateinit var mockRepository: ScheduleRepository
    private lateinit var mockDataStoreManager: DataStoreManager

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockDataStoreManager = mockk(relaxed = true)

        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.v(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        coEvery { DataStoreManager.getSettingsDataStore(mockContext) } returns mockk {
            coEvery { data } returns flowOf(
                mapOf(
                    "auto_update_enabled" to true,
                    "interval_update_enabled" to true,
                    "auto_update_interval_hours" to 2,
                    "last_auto_update_time" to 0L
                )
            )
        }

        orchestrator = UpdateOrchestrator(mockContext, mockRepository, mockDataStoreManager)
    }

    @After
    fun tearDown() {
    }

    @Test
    fun `should skip check when within min interval`() = runTest {
        val decision1 = orchestrator.checkAndTriggerAutoUpdate()
        assertFalse("第一次检查应通过", decision1.shouldUpdate || decision1.reason == "检查频率限制")

        val decision2 = orchestrator.checkAndTriggerAutoUpdate()
        assertEquals("短时间内第二次检查应被拒绝", "检查频率限制", decision2.reason)
        assertFalse(decision2.shouldUpdate)
    }

    @Test
    fun `should allow check after min interval`() = runTest {
        mockkStatic(System::class)
        every { System.currentTimeMillis() } returns 0L andThen (UpdateOrchestrator.MIN_CHECK_INTERVAL_MS + 1000)

        val decision1 = orchestrator.checkAndTriggerAutoUpdate()
        val decision2 = orchestrator.checkAndTriggerAutoUpdate()

        assertNotEquals("超过最小间隔后应允许检查", "检查频率限制", decision2.reason)
    }

    @Test
    fun `should not update when auto update disabled`() = runTest {
        mockkStatic(System::class)
        every { System.currentTimeMillis() } returns 0L andThen (UpdateOrchestrator.MIN_CHECK_INTERVAL_MS + 1000)

        coEvery { DataStoreManager.getSettingsDataStore(mockContext) } returns mockk {
            coEvery { data } returns flowOf(
                mapOf(
                    "auto_update_enabled" to false,
                    "interval_update_enabled" to true,
                    "auto_update_interval_hours" to 2,
                    "last_auto_update_time" to 0L
                )
            )
        }

        val freshOrchestrator = UpdateOrchestrator(mockContext, mockRepository, mockDataStoreManager)
        freshOrchestrator.checkAndTriggerAutoUpdate()
        val decision = freshOrchestrator.checkAndTriggerAutoUpdate()

        assertEquals("自动更新关闭时不应更新", "自动更新未启用", decision.reason)
        assertFalse(decision.shouldUpdate)
    }

    @Test
    fun `should not update when no schedule exists`() = runTest {
        mockkStatic(System::class)
        every { System.currentTimeMillis() } returns 0L andThen (UpdateOrchestrator.MIN_CHECK_INTERVAL_MS + 1000) andThen (UpdateOrchestrator.MIN_CHECK_INTERVAL_MS * 2 + 2000)

        coEvery { mockRepository.getCurrentSchedule() } returns null

        val decision1 = orchestrator.checkAndTriggerAutoUpdate()
        val decision2 = orchestrator.checkAndTriggerAutoUpdate()

        assertEquals("无课表时不应更新", "未导入课表", decision2.reason)
        assertFalse(decision2.shouldUpdate)
    }

    @Test
    fun `should not update when school name is empty`() = runTest {
        mockkStatic(System::class)
        every { System.currentTimeMillis() } returns 0L andThen (UpdateOrchestrator.MIN_CHECK_INTERVAL_MS + 1000) andThen (UpdateOrchestrator.MIN_CHECK_INTERVAL_MS * 2 + 2000)

        coEvery { mockRepository.getCurrentSchedule() } returns Schedule(schoolName = "")

        val decision1 = orchestrator.checkAndTriggerAutoUpdate()
        val decision2 = orchestrator.checkAndTriggerAutoUpdate()

        assertEquals("学校配置为空时不应更新", "课表缺少学校配置", decision2.reason)
        assertFalse(decision2.shouldUpdate)
    }
}
