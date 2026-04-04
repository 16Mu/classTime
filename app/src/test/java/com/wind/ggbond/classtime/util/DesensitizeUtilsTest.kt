package com.wind.ggbond.classtime.util

import org.junit.Assert.*
import org.junit.Test

class DesensitizeUtilsTest {

    @Test
    fun `maskPassword - should completely mask password`() {
        assertEquals("***", DesensitizeUtils.maskPassword("123456"))
        assertEquals("***", DesensitizeUtils.maskPassword("mypassword"))
    }

    @Test
    fun `maskPassword - should handle empty and null input`() {
        assertEquals("", DesensitizeUtils.maskPassword(null))
        assertEquals("", DesensitizeUtils.maskPassword(""))
    }

    @Test
    fun `maskCookie - should show first 4 chars plus mask`() {
        val result = DesensitizeUtils.maskCookie("session_abc123xyz")
        assertTrue("应显示前4位", result.startsWith("sess"))
        assertTrue("应包含掩码", result.contains("****"))
        assertEquals(8, result.length)
    }

    @Test
    fun `maskStudentId - should show format 2+mask+2`() {
        val result = DesensitizeUtils.maskStudentId("20210001")
        assertEquals("20****01", result)
    }

    @Test
    fun `maskPhone - should show format 3+mask+4`() {
        val result = DesensitizeUtils.maskPhone("13812345678")
        assertEquals("138****5678", result)
    }

    @Test
    fun `maskToken - should show first 8 plus mask plus last 4`() {
        val result = DesensitizeUtils.maskToken("abcdef1234567890")
        assertTrue("应显示前8位", result.startsWith("abcdef12"))
        assertTrue("应包含掩码", result.contains("***"))
        assertTrue("应显示后4位", result.endsWith("7890"))
    }

    @Test
    fun `maskByKey - should select correct masking strategy`() {
        assertEquals("***", DesensitizeUtils.maskByKey("password", "secret123"))
        assertTrue(DesensitizeUtils.maskByKey("cookie", "sessionvalue").startsWith("sess"))
        assertEquals("20****01", DesensitizeUtils.maskByKey("studentId", "20210001"))
        assertEquals("138****5678", DesensitizeUtils.maskByKey("phone", "13812345678"))
    }

    @Test
    fun `isSensitiveKeyword - should detect sensitive keys`() {
        assertTrue("password应是敏感词", DesensitizeUtils.isSensitiveKeyword("password"))
        assertTrue("cookie应是敏感词", DesensitizeUtils.isSensitiveKeyword("my_cookie"))
        assertTrue("token应是敏感词", DesensitizeUtils.isSensitiveKeyword("accessToken"))
        assertTrue("学号应是敏感词", DesensitizeUtils.isSensitiveKeyword("studentId"))
        assertFalse("name不应是敏感词", DesensitizeUtils.isSensitiveKeyword("username"))
    }
}
