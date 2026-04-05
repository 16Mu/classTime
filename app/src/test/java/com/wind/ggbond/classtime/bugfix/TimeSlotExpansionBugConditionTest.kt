package com.wind.ggbond.classtime.bugfix

import com.wind.ggbond.classtime.ui.screen.scheduleimport.BatchCourseItem
import com.wind.ggbond.classtime.ui.screen.scheduleimport.TimeSlot
import org.junit.Test
import org.junit.Assert.*

/**
 * Bug Condition Exploration Test for Bug 0: 时间段展开逻辑错误
 * 
 * **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
 * **DO NOT attempt to fix the test or the code when it fails**
 * 
 * Bug Description:
 * - When user clicks "添加时间段" button in BatchCourseCreateScreen
 * - The newly added time slot should be expanded
 * - But currently the first time slot is always expanded (the oldest one)
 * 
 * Root Cause:
 * - LaunchedEffect uses: expandedSlotIds.value = setOf(course.timeSlots.last().id)
 * - New time slots are inserted at the HEAD of the list (index 0)
 * - last() returns the LAST element in the list, which is the OLDEST time slot
 * - Should use first() or maxByOrNull { it.id } to get the newest time slot
 * 
 * Expected Behavior Properties:
 * - result.expandedSlotId = result.latestAddedSlotId
 * - result.otherSlotsCollapsed = true
 * 
 * **Validates: Requirements 1.1, 1.2**
 */
class TimeSlotExpansionBugConditionTest {

    /**
     * Helper function to simulate the BUGGY expansion logic from BatchCourseCreateScreen
     * This replicates the current (incorrect) behavior: expandedSlotIds.value = setOf(course.timeSlots.last().id)
     */
    private fun getBuggyExpandedSlotId(timeSlots: List<TimeSlot>): Long? {
        return if (timeSlots.isNotEmpty()) {
            timeSlots.last().id  // BUG: This gets the oldest slot, not the newest
        } else {
            null
        }
    }

    /**
     * Helper function to get the latest added slot ID (the one that SHOULD be expanded)
     * Since new slots are inserted at the head (index 0) and have the highest ID (System.nanoTime()),
     * the latest slot is at index 0 or has the maximum ID
     */
    private fun getLatestAddedSlotId(timeSlots: List<TimeSlot>): Long? {
        return timeSlots.maxByOrNull { it.id }?.id
    }

    /**
     * Test Case 1: Single time slot - should pass (baseline)
     * 
     * Scenario: User adds the first time slot
     * Expected: The first time slot is expanded (this works correctly)
     */
    @Test
    fun `test single time slot expansion - baseline case`() {
        // Given: A course with one time slot
        val slot1 = TimeSlot(id = 1000L, dayOfWeek = 1, startSection = 1, sectionCount = 2)
        val timeSlots = listOf(slot1)
        
        // When: Simulating the buggy expansion logic
        val expandedSlotId = getBuggyExpandedSlotId(timeSlots)
        val latestSlotId = getLatestAddedSlotId(timeSlots)
        
        // Then: For a single slot, last() and first() are the same, so it works
        assertEquals(
            "Single time slot: expanded slot should be the latest slot",
            latestSlotId,
            expandedSlotId
        )
    }

    /**
     * Test Case 2: Two time slots - BUG DETECTED
     * 
     * Scenario: User adds a second time slot
     * Expected: The second (newest) time slot should be expanded
     * Actual (BUGGY): The first (oldest) time slot is expanded
     * 
     * **EXPECTED OUTCOME**: This test FAILS on unfixed code
     */
    @Test
    fun `test second time slot expansion - BUG DETECTED`() {
        // Given: A course with two time slots
        // Slot 1 was added first (older, at the end of the list)
        val slot1 = TimeSlot(id = 1000L, dayOfWeek = 1, startSection = 1, sectionCount = 2)
        
        // Slot 2 was added second (newer, at the head of the list)
        val slot2 = TimeSlot(id = 2000L, dayOfWeek = 2, startSection = 3, sectionCount = 2)
        
        // Time slots list: [slot2 (newest), slot1 (oldest)]
        val timeSlots = listOf(slot2, slot1)
        
        // When: Simulating the buggy expansion logic
        val expandedSlotId = getBuggyExpandedSlotId(timeSlots)
        val latestSlotId = getLatestAddedSlotId(timeSlots)
        
        // Then: BUG - expandedSlotId is slot1 (1000L), but should be slot2 (2000L)
        println("=== Bug Condition Test Case 2 ===")
        println("Time slots: [slot2(id=2000, newest), slot1(id=1000, oldest)]")
        println("Latest added slot ID (expected): $latestSlotId")
        println("Expanded slot ID (actual): $expandedSlotId")
        println("BUG: Expanded slot is ${if (expandedSlotId == 1000L) "slot1 (oldest)" else "slot2 (newest)"}")
        
        // This assertion SHOULD FAIL on unfixed code
        assertEquals(
            "BUG: When adding 2nd time slot, the newest slot (slot2) should be expanded, but oldest slot (slot1) is expanded instead",
            latestSlotId,
            expandedSlotId
        )
    }

    /**
     * Test Case 3: Three time slots - BUG DETECTED
     * 
     * Scenario: User adds a third time slot
     * Expected: The third (newest) time slot should be expanded
     * Actual (BUGGY): The first (oldest) time slot is still expanded
     * 
     * **EXPECTED OUTCOME**: This test FAILS on unfixed code
     */
    @Test
    fun `test third time slot expansion - BUG DETECTED`() {
        // Given: A course with three time slots
        val slot1 = TimeSlot(id = 1000L, dayOfWeek = 1, startSection = 1, sectionCount = 2)
        val slot2 = TimeSlot(id = 2000L, dayOfWeek = 2, startSection = 3, sectionCount = 2)
        val slot3 = TimeSlot(id = 3000L, dayOfWeek = 3, startSection = 5, sectionCount = 2)
        
        // Time slots list: [slot3 (newest), slot2, slot1 (oldest)]
        val timeSlots = listOf(slot3, slot2, slot1)
        
        // When: Simulating the buggy expansion logic
        val expandedSlotId = getBuggyExpandedSlotId(timeSlots)
        val latestSlotId = getLatestAddedSlotId(timeSlots)
        
        // Then: BUG - expandedSlotId is slot1 (1000L), but should be slot3 (3000L)
        println("=== Bug Condition Test Case 3 ===")
        println("Time slots: [slot3(id=3000, newest), slot2(id=2000), slot1(id=1000, oldest)]")
        println("Latest added slot ID (expected): $latestSlotId")
        println("Expanded slot ID (actual): $expandedSlotId")
        println("BUG: Expanded slot is ${when (expandedSlotId) {
            1000L -> "slot1 (oldest)"
            2000L -> "slot2 (middle)"
            3000L -> "slot3 (newest)"
            else -> "unknown"
        }}")
        
        // This assertion SHOULD FAIL on unfixed code
        assertEquals(
            "BUG: When adding 3rd time slot, the newest slot (slot3) should be expanded, but oldest slot (slot1) is expanded instead",
            latestSlotId,
            expandedSlotId
        )
    }

    /**
     * Test Case 4: Property-based test - multiple time slots
     * 
     * Scenario: User adds N time slots (N = 2 to 5)
     * Expected: The latest added slot should always be expanded
     * Actual (BUGGY): The oldest slot is always expanded
     * 
     * **EXPECTED OUTCOME**: This test FAILS on unfixed code for N >= 2
     */
    @Test
    fun `test multiple time slots expansion - property based`() {
        // Test with 2 to 5 time slots
        for (slotCount in 2..5) {
            // Given: Create N time slots with increasing IDs
            val timeSlots = (1..slotCount).map { i ->
                TimeSlot(
                    id = i * 1000L,
                    dayOfWeek = i,
                    startSection = i * 2 - 1,
                    sectionCount = 2
                )
            }.reversed() // Reverse to simulate newest at head
            
            // When: Simulating the buggy expansion logic
            val expandedSlotId = getBuggyExpandedSlotId(timeSlots)
            val latestSlotId = getLatestAddedSlotId(timeSlots)
            
            // Then: BUG - expandedSlotId is the oldest, but should be the newest
            println("=== Property-based Test: $slotCount time slots ===")
            println("Latest added slot ID (expected): $latestSlotId")
            println("Expanded slot ID (actual): $expandedSlotId")
            
            // This assertion SHOULD FAIL on unfixed code for slotCount >= 2
            assertEquals(
                "BUG: With $slotCount time slots, the newest slot should be expanded, but oldest slot is expanded instead",
                latestSlotId,
                expandedSlotId
            )
        }
    }

    /**
     * Test Case 5: After delete and re-add scenario
     * 
     * Scenario:
     * 1. User has 3 time slots
     * 2. User deletes the middle slot
     * 3. User adds a new slot
     * 
     * Expected: The newly added slot should be expanded
     * Actual (BUGGY): The oldest remaining slot is expanded
     * 
     * **EXPECTED OUTCOME**: This test FAILS on unfixed code
     */
    @Test
    fun `test expansion after delete and re-add - BUG DETECTED`() {
        // Given: Initial 3 time slots
        val slot1 = TimeSlot(id = 1000L, dayOfWeek = 1, startSection = 1, sectionCount = 2)
        val slot2 = TimeSlot(id = 2000L, dayOfWeek = 2, startSection = 3, sectionCount = 2)
        val slot3 = TimeSlot(id = 3000L, dayOfWeek = 3, startSection = 5, sectionCount = 2)
        
        // After deleting slot2: [slot3, slot1]
        var timeSlots = listOf(slot3, slot1)
        
        // User adds a new slot (slot4)
        val slot4 = TimeSlot(id = 4000L, dayOfWeek = 4, startSection = 7, sectionCount = 2)
        timeSlots = listOf(slot4) + timeSlots  // Insert at head
        
        // Now timeSlots = [slot4 (newest), slot3, slot1 (oldest)]
        
        // When: Simulating the buggy expansion logic
        val expandedSlotId = getBuggyExpandedSlotId(timeSlots)
        val latestSlotId = getLatestAddedSlotId(timeSlots)
        
        // Then: BUG - expandedSlotId is slot1 (1000L), but should be slot4 (4000L)
        println("=== Bug Condition Test Case 5: After delete and re-add ===")
        println("Time slots after delete and re-add: [slot4(id=4000, newest), slot3(id=3000), slot1(id=1000, oldest)]")
        println("Latest added slot ID (expected): $latestSlotId")
        println("Expanded slot ID (actual): $expandedSlotId")
        
        // This assertion SHOULD FAIL on unfixed code
        assertEquals(
            "BUG: After delete and re-add, the newly added slot (slot4) should be expanded, but oldest slot (slot1) is expanded instead",
            latestSlotId,
            expandedSlotId
        )
    }

    /**
     * Test Case 6: Verify the root cause - last() vs maxByOrNull { it.id }
     * 
     * This test explicitly demonstrates the difference between:
     * - last() - gets the last element in the list (oldest slot)
     * - maxByOrNull { it.id } - gets the slot with the highest ID (newest slot)
     */
    @Test
    fun `verify root cause - last vs maxByOrNull`() {
        // Given: 3 time slots with increasing IDs
        val slot1 = TimeSlot(id = 1000L, dayOfWeek = 1, startSection = 1, sectionCount = 2)
        val slot2 = TimeSlot(id = 2000L, dayOfWeek = 2, startSection = 3, sectionCount = 2)
        val slot3 = TimeSlot(id = 3000L, dayOfWeek = 3, startSection = 5, sectionCount = 2)
        
        // Time slots list: [slot3 (newest), slot2, slot1 (oldest)]
        val timeSlots = listOf(slot3, slot2, slot1)
        
        // When: Compare last() vs maxByOrNull { it.id }
        val lastSlotId = timeSlots.last().id  // BUGGY: Gets slot1 (1000L)
        val maxIdSlotId = timeSlots.maxByOrNull { it.id }?.id  // CORRECT: Gets slot3 (3000L)
        
        // Then: Demonstrate the difference
        println("=== Root Cause Analysis ===")
        println("Time slots: [slot3(id=3000, newest), slot2(id=2000), slot1(id=1000, oldest)]")
        println("last().id (BUGGY): $lastSlotId")
        println("maxByOrNull { it.id }?.id (CORRECT): $maxIdSlotId")
        println("Difference: last() returns ${lastSlotId}, but should return ${maxIdSlotId}")
        
        // Verify the bug
        assertNotEquals(
            "Root cause confirmed: last() returns oldest slot, not newest slot",
            maxIdSlotId,
            lastSlotId
        )
        
        assertEquals("last() returns the oldest slot (slot1)", 1000L, lastSlotId)
        assertEquals("maxByOrNull returns the newest slot (slot3)", 3000L, maxIdSlotId)
    }
}
