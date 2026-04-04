package com.wind.ggbond.classtime.util

import org.junit.Assert.*
import org.junit.Test

class ValidationResultTest {

    @Test
    fun `ValidationResult - should hold valid state`() {
        val valid = ValidationResult(true, "验证成功")
        assertTrue(valid.isValid)
        assertEquals("验证成功", valid.message)
    }

    @Test
    fun `ValidationResult - should hold invalid state`() {
        val invalid = ValidationResult(false, "错误：字段不能为空")
        assertFalse(invalid.isValid)
        assertEquals("错误：字段不能为空", invalid.message)
    }

    @Test
    fun `ValidationResult - should support empty message`() {
        val result = ValidationResult(true, "")
        assertTrue(result.isValid)
        assertEquals("", result.message)
    }

    @Test
    fun `ValidationResult - equality should work correctly`() {
        val result1 = ValidationResult(true, "OK")
        val result2 = ValidationResult(true, "OK")
        val result3 = ValidationResult(false, "Error")

        assertEquals(result1, result2)
        assertNotEquals(result1, result3)
    }
}
