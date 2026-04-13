package com.wind.ggbond.classtime.service

import android.content.Context
import com.wind.ggbond.classtime.data.local.database.CourseDatabase
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.local.entity.SchoolEntity
import com.wind.ggbond.classtime.data.model.DataFormat
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.data.model.SchoolConfig
import com.wind.ggbond.classtime.data.repository.AutoUpdateLogRepository
import com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.data.repository.SchoolRepository
import com.wind.ggbond.classtime.service.contract.IScheduleFetcher
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.util.AutoLoginManager
import com.wind.ggbond.classtime.util.AutoLoginResultCode
import com.wind.ggbond.classtime.util.CourseChangeDetector
import com.wind.ggbond.classtime.util.CourseChangeNotificationHelper
import com.wind.ggbond.classtime.util.CourseColorPalette
import com.wind.ggbond.classtime.util.SecureCookieManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UnifiedScheduleUpdateServiceTest {

    private val scheduleFetcher: IScheduleFetcher = mockk(relaxed = true)
    private val courseRepository: CourseRepository = mockk(relaxed = true)
    private val autoUpdateLogRepository: AutoUpdateLogRepository = mockk(relaxed = true)
    private val secureCookieManager: SecureCookieManager = mockk(relaxed = true)
    private val schoolRepository: SchoolRepository = mockk(relaxed = true)
    private val scheduleRepository: ScheduleRepository = mockk(relaxed = true)
    private val courseAdjustmentRepository: CourseAdjustmentRepository = mockk(relaxed = true)
    private val courseDatabase: CourseDatabase = mockk(relaxed = true)
    private val autoLoginService: AutoLoginService = mockk(relaxed = true)
    private val autoLoginManager: AutoLoginManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private lateinit var service: UnifiedScheduleUpdateService

    private val testSchoolConfig = SchoolConfig(
        id = "school_1",
        name = "测试大学",
        loginUrl = "https://login.test.edu.cn",
        scheduleUrl = "https://schedule.test.edu.cn",
        scheduleMethod = "GET",
        dataFormat = DataFormat.JSON
    )

    private val testParsedCourses = listOf(
        ParsedCourse(
            courseName = "高等数学",
            teacher = "张老师",
            classroom = "A101",
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            weekExpression = "1-16周",
            weeks = (1..16).toList()
        )
    )

    private val testLocalCourses = listOf(
        Course(
            id = 1,
            scheduleId = 100,
            courseName = "高等数学",
            teacher = "张老师",
            classroom = "A101",
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            weeks = (1..16).toList(),
            color = "#5B9BD5"
        )
    )

    @Before
    fun setUp() {
        service = UnifiedScheduleUpdateService(
            context = context,
            scheduleFetcher = scheduleFetcher,
            courseRepository = courseRepository,
            autoUpdateLogRepository = autoUpdateLogRepository,
            secureCookieManager = secureCookieManager,
            schoolRepository = schoolRepository,
            scheduleRepository = scheduleRepository,
            courseAdjustmentRepository = courseAdjustmentRepository,
            courseDatabase = courseDatabase,
            autoLoginService = autoLoginService,
            autoLoginManager = autoLoginManager
        )
        mockkObject(AppLogger)
        mockkObject(CourseChangeNotificationHelper)
        mockkObject(CourseColorPalette)
        every { CourseColorPalette.getColorForCourse(any()) } returns "#5B9BD5"
    }

    @After
    fun tearDown() {
        unmockkObject(AppLogger)
        unmockkObject(CourseChangeNotificationHelper)
        unmockkObject(CourseColorPalette)
    }

    @Test
    fun `performUpdate - Manual策略成功获取课表`() = runTest {
        val request = ScheduleUpdateRequest.Manual(
            schoolConfig = testSchoolConfig,
            scheduleId = 100,
            showWebView = true
        )

        coEvery { scheduleFetcher.fetchSchedule(testSchoolConfig, true) } returns Result.success(
            Pair(testParsedCourses, "cookie_value")
        )
        every { courseRepository.getAllCoursesBySchedule(100) } returns flowOf(testLocalCourses)

        val result = service.performUpdate(request)

        assertTrue(result.isSuccess)
        val updateResult = result.getOrThrow()
        assertTrue(updateResult.success)
        coVerify { scheduleFetcher.fetchSchedule(testSchoolConfig, true) }
    }

    @Test
    fun `performUpdate - Auto策略成功获取课表`() = runTest {
        val request = ScheduleUpdateRequest.Auto(
            schoolConfig = testSchoolConfig,
            scheduleId = 100
        )

        coEvery { scheduleFetcher.fetchSchedule(testSchoolConfig, false) } returns Result.success(
            Pair(testParsedCourses, "cookie_value")
        )
        every { courseRepository.getAllCoursesBySchedule(100) } returns flowOf(testLocalCourses)

        val result = service.performUpdate(request)

        assertTrue(result.isSuccess)
        val updateResult = result.getOrThrow()
        assertTrue(updateResult.success)
        coVerify { scheduleFetcher.fetchSchedule(testSchoolConfig, false) }
    }

    @Test
    fun `performUpdate - CookieRefresh策略成功获取课表`() = runTest {
        val request = ScheduleUpdateRequest.CookieRefresh(
            schoolConfig = testSchoolConfig,
            scheduleId = 100
        )

        coEvery { scheduleFetcher.fetchSchedule(testSchoolConfig, false) } returns Result.success(
            Pair(testParsedCourses, "cookie_value")
        )
        every { courseRepository.getAllCoursesBySchedule(100) } returns flowOf(testLocalCourses)

        val result = service.performUpdate(request)

        assertTrue(result.isSuccess)
        coVerify { scheduleFetcher.fetchSchedule(testSchoolConfig, false) }
    }

    @Test
    fun `performUpdate - 获取课表失败时返回Failure`() = runTest {
        val request = ScheduleUpdateRequest.Auto(
            schoolConfig = testSchoolConfig,
            scheduleId = 100
        )

        coEvery { scheduleFetcher.fetchSchedule(testSchoolConfig, false) } returns Result.failure(
            Exception("网络错误")
        )

        val result = service.performUpdate(request)

        assertTrue(result.isFailure)
        assertEquals("网络错误", result.exceptionOrNull()?.message)
    }

    @Test
    fun `performUpdate - Cookie过期且Auto策略时尝试自动重新登录`() = runTest {
        val request = ScheduleUpdateRequest.Auto(
            schoolConfig = testSchoolConfig,
            scheduleId = 100
        )

        coEvery { scheduleFetcher.fetchSchedule(testSchoolConfig, false) } returns Result.failure(
            Exception("Cookie已失效")
        )
        every { autoLoginManager.isAutoLoginEnabled() } returns true
        every { autoLoginManager.hasCredentials() } returns true
        every { autoLoginManager.getUsername() } returns "testuser"
        every { autoLoginManager.getPassword() } returns "testpass"
        coEvery { autoLoginService.performAutoLogin("school_1", "testuser", "testpass") } returns AutoLoginResult(
            true, AutoLoginResultCode.OK, "登录成功"
        )
        coEvery { scheduleFetcher.fetchSchedule(testSchoolConfig, false) } returns Result.success(
            Pair(testParsedCourses, "new_cookie")
        )
        every { courseRepository.getAllCoursesBySchedule(100) } returns flowOf(testLocalCourses)

        val result = service.performUpdate(request)

        assertTrue(result.isSuccess)
        coVerify { autoLoginService.performAutoLogin("school_1", "testuser", "testpass") }
    }

    @Test
    fun `performUpdate - Cookie过期但Manual策略不尝试重新登录`() = runTest {
        val request = ScheduleUpdateRequest.Manual(
            schoolConfig = testSchoolConfig,
            scheduleId = 100
        )

        coEvery { scheduleFetcher.fetchSchedule(testSchoolConfig, false) } returns Result.failure(
            Exception("Cookie已失效")
        )

        val result = service.performUpdate(request)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { autoLoginService.performAutoLogin(any(), any(), any()) }
    }

    @Test
    fun `performUpdate - 自动重新登录失败时返回Failure`() = runTest {
        val request = ScheduleUpdateRequest.Auto(
            schoolConfig = testSchoolConfig,
            scheduleId = 100
        )

        coEvery { scheduleFetcher.fetchSchedule(testSchoolConfig, false) } returns Result.failure(
            Exception("登录已过期")
        )
        every { autoLoginManager.isAutoLoginEnabled() } returns true
        every { autoLoginManager.hasCredentials() } returns true
        every { autoLoginManager.getUsername() } returns "testuser"
        every { autoLoginManager.getPassword() } returns "testpass"
        coEvery { autoLoginService.performAutoLogin("school_1", "testuser", "testpass") } returns AutoLoginResult(
            false, AutoLoginResultCode.LOGIN_FAIL, "登录失败"
        )

        val result = service.performUpdate(request)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("自动重新登录失败") == true)
    }

    @Test
    fun `performUpdate - 无课程变更时返回成功但hasChanges为false`() = runTest {
        val request = ScheduleUpdateRequest.Auto(
            schoolConfig = testSchoolConfig,
            scheduleId = 100
        )

        coEvery { scheduleFetcher.fetchSchedule(testSchoolConfig, false) } returns Result.success(
            Pair(testParsedCourses, "cookie_value")
        )
        every { courseRepository.getAllCoursesBySchedule(100) } returns flowOf(testLocalCourses)

        val result = service.performUpdate(request)

        assertTrue(result.isSuccess)
        val updateResult = result.getOrThrow()
        assertTrue(updateResult.success)
        assertFalse(updateResult.hasChanges)
        assertEquals("无课程更新", updateResult.message)
    }

    @Test
    fun `performUpdate - 有新增课程时hasChanges为true`() = runTest {
        val request = ScheduleUpdateRequest.Auto(
            schoolConfig = testSchoolConfig,
            scheduleId = 100
        )

        val newParsedCourses = testParsedCourses + ParsedCourse(
            courseName = "大学英语",
            teacher = "李老师",
            classroom = "B202",
            dayOfWeek = 2,
            startSection = 3,
            sectionCount = 2,
            weekExpression = "1-16周",
            weeks = (1..16).toList()
        )

        coEvery { scheduleFetcher.fetchSchedule(testSchoolConfig, false) } returns Result.success(
            Pair(newParsedCourses, "cookie_value")
        )
        every { courseRepository.getAllCoursesBySchedule(100) } returns flowOf(testLocalCourses)

        val result = service.performUpdate(request)

        assertTrue(result.isSuccess)
        val updateResult = result.getOrThrow()
        assertTrue(updateResult.hasChanges)
    }

    @Test
    fun `hasSavedCookie - 有Cookie时返回true`() = runTest {
        every { secureCookieManager.hasCookies("test.edu.cn") } returns true

        val result = service.hasSavedCookie("test.edu.cn")

        assertTrue(result)
    }

    @Test
    fun `hasSavedCookie - 无Cookie时返回false`() = runTest {
        every { secureCookieManager.hasCookies("test.edu.cn") } returns false

        val result = service.hasSavedCookie("test.edu.cn")

        assertFalse(result)
    }

    @Test
    fun `hasSavedCookie - 异常时返回false`() = runTest {
        every { secureCookieManager.hasCookies("test.edu.cn") } throws RuntimeException("error")

        val result = service.hasSavedCookie("test.edu.cn")

        assertFalse(result)
    }

    @Test
    fun `shouldUpdate - 有当前课表和Cookie时返回true`() = runTest {
        val schedule = Schedule(id = 1, name = "我的课表", schoolName = "school_1")
        val schoolEntity = SchoolEntity(
            id = "school_1",
            name = "测试大学",
            shortName = "测试",
            province = "北京",
            systemType = "zhengfang",
            loginUrl = "https://login.test.edu.cn",
            scheduleUrl = "https://schedule.test.edu.cn",
            scheduleMethod = "GET",
            scheduleParams = emptyMap(),
            dataFormat = "json",
            needCsrfToken = false,
            csrfTokenName = "",
            jsonMapping = emptyMap(),
            description = "测试学校",
            tips = ""
        )

        every { scheduleRepository.getCurrentSchedule() } returns schedule
        every { schoolRepository.getSchoolById("school_1") } returns schoolEntity
        every { secureCookieManager.hasCookies("login.test.edu.cn") } returns true

        val result = service.shouldUpdate()

        assertTrue(result)
    }

    @Test
    fun `shouldUpdate - 无当前课表时返回false`() = runTest {
        every { scheduleRepository.getCurrentSchedule() } returns null

        val result = service.shouldUpdate()

        assertFalse(result)
    }

    @Test
    fun `shouldUpdate - 课表缺少学校配置时返回false`() = runTest {
        val schedule = Schedule(id = 1, name = "我的课表", schoolName = "")
        every { scheduleRepository.getCurrentSchedule() } returns schedule

        val result = service.shouldUpdate()

        assertFalse(result)
    }

    @Test
    fun `performSimpleUpdate - 成功更新`() = runTest {
        val schedule = Schedule(id = 100, name = "我的课表", schoolName = "school_1")
        val schoolEntity = SchoolEntity(
            id = "school_1",
            name = "测试大学",
            shortName = "测试",
            province = "北京",
            systemType = "zhengfang",
            loginUrl = "https://login.test.edu.cn",
            scheduleUrl = "https://schedule.test.edu.cn",
            scheduleMethod = "GET",
            scheduleParams = emptyMap(),
            dataFormat = "json",
            needCsrfToken = false,
            csrfTokenName = "",
            jsonMapping = emptyMap(),
            description = "测试学校",
            tips = ""
        )

        every { scheduleRepository.getCurrentSchedule() } returns schedule
        every { schoolRepository.getSchoolById("school_1") } returns schoolEntity
        coEvery { scheduleFetcher.fetchSchedule(any(), any()) } returns Result.success(
            Pair(testParsedCourses, "cookie_value")
        )
        every { courseRepository.getAllCoursesBySchedule(100) } returns flowOf(testLocalCourses)

        val (success, message) = service.performSimpleUpdate()

        assertTrue(success)
    }

    @Test
    fun `performSimpleUpdate - 无当前课表时返回失败`() = runTest {
        every { scheduleRepository.getCurrentSchedule() } returns null

        val (success, message) = service.performSimpleUpdate()

        assertFalse(success)
        assertEquals("未找到当前课表", message)
    }

    @Test
    fun `performSimpleUpdate - 课表缺少学校配置时返回失败`() = runTest {
        val schedule = Schedule(id = 100, name = "我的课表", schoolName = "")
        every { scheduleRepository.getCurrentSchedule() } returns schedule

        val (success, message) = service.performSimpleUpdate()

        assertFalse(success)
        assertEquals("课表缺少学校配置", message)
    }

    @Test
    fun `performSimpleUpdate - 未找到学校配置时返回失败`() = runTest {
        val schedule = Schedule(id = 100, name = "我的课表", schoolName = "school_1")
        every { scheduleRepository.getCurrentSchedule() } returns schedule
        every { schoolRepository.getSchoolById("school_1") } returns null

        val (success, message) = service.performSimpleUpdate()

        assertFalse(success)
        assertTrue(message.contains("未找到学校配置"))
    }
}
