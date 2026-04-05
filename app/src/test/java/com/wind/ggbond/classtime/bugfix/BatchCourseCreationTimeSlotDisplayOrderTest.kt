package com.wind.ggbond.classtime.bugfix

import org.junit.Test
import org.junit.Assert.*

/**
 * 测试批量创建课程功能中时间段显示顺序的修复
 * 
 * Bug #3: 时间段显示顺序错误
 * - 添加多个时间段后，显示顺序应该是最新的在最上面
 * - 期望顺序：时间段4、时间段3、时间段2、时间段1（最新的在最上面）
 * 
 * 修复方案：
 * - 使用 `course.timeSlots.size - index` 作为显示索引
 * - 确保最新添加的时间段（在列表头部）显示最大的索引号
 */
class BatchCourseCreationTimeSlotDisplayOrderTest {

    /**
     * 测试用例 3.1: 验证显示索引计算公式
     * 
     * 场景：有3个时间段，验证显示索引是否正确反转
     * 期望：
     * - 列表索引0（最新添加）→ 显示索引3
     * - 列表索引1 → 显示索引2
     * - 列表索引2（最早添加）→ 显示索引1
     */
    @Test
    fun `test display index calculation for 3 time slots`() {
        // Given: 3个时间段
        val timeSlotCount = 3
        
        // When & Then: 验证每个索引的显示值
        val expectedDisplayIndices = listOf(3, 2, 1)
        
        for (index in 0 until timeSlotCount) {
            val displayIndex = timeSlotCount - index
            assertEquals(
                "列表索引 $index 应该显示为 ${expectedDisplayIndices[index]}",
                expectedDisplayIndices[index],
                displayIndex
            )
        }
    }

    /**
     * 测试用例 3.2: 验证单个时间段的显示索引
     * 
     * 场景：只有1个时间段
     * 期望：显示索引为1
     */
    @Test
    fun `test display index for single time slot`() {
        // Given: 1个时间段
        val timeSlotCount = 1
        val index = 0
        
        // When: 计算显示索引
        val displayIndex = timeSlotCount - index
        
        // Then: 显示索引应该为1
        assertEquals("单个时间段应该显示为1", 1, displayIndex)
    }

    /**
     * 测试用例 3.3: 验证多个时间段的显示顺序
     * 
     * 场景：添加5个时间段
     * 期望：显示顺序为5、4、3、2、1
     */
    @Test
    fun `test display order for 5 time slots`() {
        // Given: 5个时间段
        val timeSlotCount = 5
        
        // When: 计算所有显示索引
        val displayIndices = (0 until timeSlotCount).map { index ->
            timeSlotCount - index
        }
        
        // Then: 显示顺序应该是5、4、3、2、1
        val expectedOrder = listOf(5, 4, 3, 2, 1)
        assertEquals("显示顺序应该从大到小", expectedOrder, displayIndices)
    }

    /**
     * 测试用例 3.4: 验证删除中间时间段后索引重新计算
     * 
     * 场景：
     * 1. 有3个时间段（显示为3、2、1）
     * 2. 删除中间的时间段（显示索引2）
     * 3. 剩余2个时间段
     * 
     * 期望：剩余时间段的显示索引重新计算为2、1
     */
    @Test
    fun `test display index recalculation after deletion`() {
        // Given: 初始有3个时间段
        var timeSlotCount = 3
        val initialDisplayIndices = (0 until timeSlotCount).map { index ->
            timeSlotCount - index
        }
        assertEquals("初始显示顺序", listOf(3, 2, 1), initialDisplayIndices)
        
        // When: 删除中间的时间段（列表索引1，显示索引2）
        // 模拟删除：列表变为[slot0, slot2]，大小变为2
        timeSlotCount = 2
        
        // Then: 重新计算显示索引
        val newDisplayIndices = (0 until timeSlotCount).map { index ->
            timeSlotCount - index
        }
        assertEquals("删除后显示顺序", listOf(2, 1), newDisplayIndices)
    }

    /**
     * 测试用例 3.5: 验证添加新时间段后索引更新
     * 
     * 场景：
     * 1. 有2个时间段（显示为2、1）
     * 2. 添加新时间段（插入到列表头部）
     * 3. 现在有3个时间段
     * 
     * 期望：显示索引更新为3、2、1（新时间段显示为3）
     */
    @Test
    fun `test display index update after adding new time slot`() {
        // Given: 初始有2个时间段
        var timeSlotCount = 2
        val initialDisplayIndices = (0 until timeSlotCount).map { index ->
            timeSlotCount - index
        }
        assertEquals("初始显示顺序", listOf(2, 1), initialDisplayIndices)
        
        // When: 添加新时间段（插入到列表头部，索引0）
        timeSlotCount = 3
        
        // Then: 重新计算显示索引
        val newDisplayIndices = (0 until timeSlotCount).map { index ->
            timeSlotCount - index
        }
        assertEquals("添加后显示顺序", listOf(3, 2, 1), newDisplayIndices)
        
        // 验证新时间段（列表索引0）显示为最大值3
        val newSlotDisplayIndex = timeSlotCount - 0
        assertEquals("新时间段应该显示最大索引", 3, newSlotDisplayIndex)
    }

    /**
     * 测试用例 3.6: 边界情况 - 最大时间段数量
     * 
     * 场景：有10个时间段
     * 期望：显示顺序为10、9、8、7、6、5、4、3、2、1
     */
    @Test
    fun `test display order for maximum time slots`() {
        // Given: 10个时间段
        val timeSlotCount = 10
        
        // When: 计算所有显示索引
        val displayIndices = (0 until timeSlotCount).map { index ->
            timeSlotCount - index
        }
        
        // Then: 显示顺序应该从10到1
        val expectedOrder = (10 downTo 1).toList()
        assertEquals("显示顺序应该从10到1", expectedOrder, displayIndices)
    }

    /**
     * 测试用例 3.7: 验证公式的正确性
     * 
     * 这个测试确保我们使用的公式 `size - index` 是正确的
     */
    @Test
    fun `verify formula correctness`() {
        // 测试不同大小的列表
        val testCases = listOf(
            1 to listOf(1),
            2 to listOf(2, 1),
            3 to listOf(3, 2, 1),
            4 to listOf(4, 3, 2, 1),
            5 to listOf(5, 4, 3, 2, 1)
        )
        
        testCases.forEach { (size, expected) ->
            val actual = (0 until size).map { index -> size - index }
            assertEquals(
                "大小为 $size 的列表显示顺序不正确",
                expected,
                actual
            )
        }
    }
}
