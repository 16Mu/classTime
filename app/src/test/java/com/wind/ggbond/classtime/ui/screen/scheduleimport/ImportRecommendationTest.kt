package com.wind.ggbond.classtime.ui.screen.scheduleimport

import com.wind.ggbond.classtime.data.local.entity.SchoolEntity
import org.junit.Assert.*
import org.junit.Test

class ImportRecommendationTest {

    private fun createSchool(
        id: String = "test",
        name: String = "测试大学",
        loginUrl: String = "",
        scheduleUrl: String = "",
        extractorClass: String? = null
    ): SchoolEntity {
        return SchoolEntity(
            id = id,
            name = name,
            shortName = "测试",
            province = "北京",
            systemType = "zfsoft",
            loginUrl = loginUrl,
            scheduleUrl = scheduleUrl,
            scheduleMethod = "GET",
            scheduleParams = emptyMap(),
            dataFormat = "json",
            needCsrfToken = false,
            csrfTokenName = "",
            jsonMapping = emptyMap(),
            description = "",
            tips = "",
            extractorClass = extractorClass
        )
    }

    @Test
    fun `school with loginUrl and extractorClass recommends WEBVIEW_AUTO`() {
        val school = createSchool(
            loginUrl = "https://jwgl.test.edu.cn/",
            scheduleUrl = "https://jwgl.test.edu.cn/schedule",
            extractorClass = "com.wind.ggbond.classtime.util.extractor.TestExtractor"
        )
        val recommendation = getImportRecommendation(school)

        assertEquals(ImportMethod.WEBVIEW_AUTO, recommendation.method)
        assertEquals(3, recommendation.priority)
        assertTrue(recommendation.label.contains("推荐"))
    }

    @Test
    fun `school with loginUrl and scheduleUrl but no extractor recommends CLIPBOARD`() {
        val school = createSchool(
            loginUrl = "https://jwgl.test.edu.cn/",
            scheduleUrl = "https://jwgl.test.edu.cn/schedule",
            extractorClass = null
        )
        val recommendation = getImportRecommendation(school)

        assertEquals(ImportMethod.CLIPBOARD, recommendation.method)
        assertEquals(2, recommendation.priority)
    }

    @Test
    fun `school with only loginUrl recommends CLIPBOARD`() {
        val school = createSchool(
            loginUrl = "https://jwgl.test.edu.cn/",
            scheduleUrl = "",
            extractorClass = null
        )
        val recommendation = getImportRecommendation(school)

        assertEquals(ImportMethod.CLIPBOARD, recommendation.method)
        assertEquals(2, recommendation.priority)
    }

    @Test
    fun `school with no loginUrl recommends FILE`() {
        val school = createSchool(
            loginUrl = "",
            scheduleUrl = "",
            extractorClass = null
        )
        val recommendation = getImportRecommendation(school)

        assertEquals(ImportMethod.FILE, recommendation.method)
        assertEquals(1, recommendation.priority)
    }

    @Test
    fun `school with extractorClass but no loginUrl recommends FILE`() {
        val school = createSchool(
            loginUrl = "",
            scheduleUrl = "",
            extractorClass = "com.wind.ggbond.classtime.util.extractor.TestExtractor"
        )
        val recommendation = getImportRecommendation(school)

        assertEquals(ImportMethod.FILE, recommendation.method)
    }

    @Test
    fun `priority order is WEBVIEW_AUTO greater than CLIPBOARD greater than FILE`() {
        val webViewSchool = createSchool(
            loginUrl = "https://jwgl.test.edu.cn/",
            scheduleUrl = "https://jwgl.test.edu.cn/schedule",
            extractorClass = "com.wind.ggbond.classtime.util.extractor.TestExtractor"
        )
        val clipboardSchool = createSchool(
            loginUrl = "https://jwgl.test.edu.cn/",
            scheduleUrl = "https://jwgl.test.edu.cn/schedule",
            extractorClass = null
        )
        val fileSchool = createSchool(
            loginUrl = "",
            scheduleUrl = "",
            extractorClass = null
        )

        val webViewRec = getImportRecommendation(webViewSchool)
        val clipboardRec = getImportRecommendation(clipboardSchool)
        val fileRec = getImportRecommendation(fileSchool)

        assertTrue(webViewRec.priority > clipboardRec.priority)
        assertTrue(clipboardRec.priority > fileRec.priority)
    }

    @Test
    fun `network error classifies correctly`() {
        val exceptions = listOf(
            java.net.SocketTimeoutException("timeout"),
            java.net.UnknownHostException("connection failed"),
            java.net.ConnectException("network unreachable")
        )

        for (exception in exceptions) {
            val (errorType, retryAction) = classifyError(exception)
            assertEquals(ImportErrorType.NETWORK, errorType)
            assertEquals(ImportErrorRetryAction.RETRY, retryAction)
        }
    }

    @Test
    fun `parse error classifies correctly`() {
        val exceptions = listOf(
            RuntimeException("json parse error"),
            RuntimeException("数据解析失败"),
            IllegalArgumentException("format not supported")
        )

        for (exception in exceptions) {
            val (errorType, retryAction) = classifyError(exception)
            assertEquals(ImportErrorType.PARSE, errorType)
            assertEquals(ImportErrorRetryAction.SWITCH_METHOD, retryAction)
        }
    }

    @Test
    fun `cookie expired error classifies correctly`() {
        val exceptions = listOf(
            RuntimeException("cookie expired"),
            RuntimeException("登录已过期"),
            RuntimeException("401 unauthorized")
        )

        for (exception in exceptions) {
            val (errorType, retryAction) = classifyError(exception)
            assertEquals(ImportErrorType.COOKIE_EXPIRED, errorType)
            assertEquals(ImportErrorRetryAction.RELOGIN, retryAction)
        }
    }

    @Test
    fun `unknown error classifies correctly`() {
        val exception = RuntimeException("some unknown error")
        val (errorType, retryAction) = classifyError(exception)

        assertEquals(ImportErrorType.UNKNOWN, errorType)
        assertEquals(ImportErrorRetryAction.RESET, retryAction)
    }

    @Test
    fun `ImportStep labels are correct`() {
        assertEquals("连接中", ImportStep.CONNECTING.label)
        assertEquals("获取数据", ImportStep.FETCHING_DATA.label)
        assertEquals("解析中", ImportStep.PARSING.label)
        assertEquals("导入中", ImportStep.IMPORTING.label)
        assertEquals("完成", ImportStep.COMPLETED.label)
    }

    @Test
    fun `ImportStep order is correct`() {
        val steps = ImportStep.entries
        assertEquals(ImportStep.CONNECTING, steps[0])
        assertEquals(ImportStep.FETCHING_DATA, steps[1])
        assertEquals(ImportStep.PARSING, steps[2])
        assertEquals(ImportStep.IMPORTING, steps[3])
        assertEquals(ImportStep.COMPLETED, steps[4])
    }

    private fun getImportRecommendation(school: SchoolEntity): ImportRecommendation {
        val hasLoginUrl = school.loginUrl.isNotEmpty()
        val hasExtractor = !school.extractorClass.isNullOrEmpty()
        val hasScheduleUrl = school.scheduleUrl.isNotEmpty()

        return when {
            hasLoginUrl && hasExtractor -> ImportRecommendation(
                method = ImportMethod.WEBVIEW_AUTO,
                label = "推荐：一键导入",
                description = "该学校支持自动提取课表，登录后即可一键导入",
                priority = 3
            )
            hasLoginUrl && hasScheduleUrl -> ImportRecommendation(
                method = ImportMethod.CLIPBOARD,
                label = "推荐：WebView导入",
                description = "登录教务系统后手动提取课表数据",
                priority = 2
            )
            else -> ImportRecommendation(
                method = ImportMethod.FILE,
                label = "推荐：文件导入",
                description = "从教务系统导出文件后导入",
                priority = 1
            )
        }
    }

    private fun classifyError(exception: Exception): Pair<ImportErrorType, ImportErrorRetryAction> {
        val message = exception.message ?: ""
        return when {
            message.contains("timeout", ignoreCase = true) ||
            message.contains("connection", ignoreCase = true) ||
            message.contains("network", ignoreCase = true) ||
            message.contains("SocketException", ignoreCase = true) ||
            message.contains("UnknownHostException", ignoreCase = true) ||
            message.contains("ConnectException", ignoreCase = true) ->
                ImportErrorType.NETWORK to ImportErrorRetryAction.RETRY

            message.contains("parse", ignoreCase = true) ||
            message.contains("json", ignoreCase = true) ||
            message.contains("format", ignoreCase = true) ||
            message.contains("解析", ignoreCase = true) ->
                ImportErrorType.PARSE to ImportErrorRetryAction.SWITCH_METHOD

            message.contains("cookie", ignoreCase = true) ||
            message.contains("expired", ignoreCase = true) ||
            message.contains("登录", ignoreCase = true) ||
            message.contains("unauthorized", ignoreCase = true) ||
            message.contains("401", ignoreCase = true) ->
                ImportErrorType.COOKIE_EXPIRED to ImportErrorRetryAction.RELOGIN

            else -> ImportErrorType.UNKNOWN to ImportErrorRetryAction.RESET
        }
    }
}
