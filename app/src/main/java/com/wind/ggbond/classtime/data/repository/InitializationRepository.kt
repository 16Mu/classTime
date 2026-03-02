package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Schedule
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 初始化数据仓库
 * 
 * ✅ 优化：支持数据预加载
 */
@Singleton
class InitializationRepository @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val courseRepository: CourseRepository
) {
    
    companion object {
        private const val TAG = "InitializationRepo"
    }
    
    /**
     * 初始化默认数据
     */
    suspend fun initializeDefaultData() {
        android.util.Log.d(TAG, "开始初始化默认数据...")
        
        // 检查是否已有课表数据
        val currentSchedule = scheduleRepository.getCurrentSchedule()
        if (currentSchedule == null) {
            android.util.Log.d(TAG, "未找到课表数据，创建默认配置")
            
            // 创建默认课表（包含学期时间信息）
            createDefaultSchedule()
            
            // 创建默认上下课时间
            createDefaultClassTimes()
        }
        
        android.util.Log.d(TAG, "默认数据初始化完成")
    }
    
    /**
     * ✅ 预加载核心数据到内存
     * 在应用启动时调用，确保主界面能立即显示数据
     */
    suspend fun preloadCoreData() {
        android.util.Log.d(TAG, "开始预加载核心数据...")
        val startTime = System.currentTimeMillis()
        
        try {
            // 预加载当前课表（包含学期时间信息）
            val schedule = scheduleRepository.getCurrentSchedule()
            android.util.Log.d(TAG, "预加载课表: ${schedule?.name}")
            
            // 预加载上课时间
            val classTimes = classTimeRepository.getClassTimesByConfig("default").first()
            android.util.Log.d(TAG, "预加载上课时间: ${classTimes.size}个时间段")
            
            // ✅ 关键：预加载所有课程数据
            if (schedule != null) {
                val courses = courseRepository.getAllCoursesBySchedule(schedule.id).first()
                android.util.Log.d(TAG, "预加载课程: ${courses.size}门课程")
            }
            
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.d(TAG, "核心数据预加载完成，耗时: ${duration}ms")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "预加载数据失败", e)
            // 不抛出异常，允许应用继续运行
        }
    }
    
    /**
     * 创建默认课表（包含学期时间信息）
     */
    private suspend fun createDefaultSchedule() {
        // 根据当前月份推算学期开始日期
        val currentYear = LocalDate.now().year
        val startMonth = LocalDate.now().monthValue
        
        val (scheduleName, startDate) = if (startMonth >= 9) {
            // 第一学期（秋季）
            "$currentYear-${currentYear + 1}学年第一学期" to LocalDate.of(currentYear, 9, 1)
        } else {
            // 第二学期（春季）
            "${currentYear - 1}-${currentYear}学年第二学期" to LocalDate.of(currentYear, 2, 20)
        }
        
        val schedule = Schedule(
            name = scheduleName,
            schoolName = "",
            startDate = startDate,
            endDate = startDate.plusWeeks(20),
            totalWeeks = 20,
            isCurrent = true
        )
        
        scheduleRepository.insertSchedule(schedule)
    }
    
    /**
     * 创建默认上下课时间配置
     * 使用 DEFAULT_CLASS_DURATION(40分钟) + DEFAULT_BREAK_DURATION(10分钟) 动态生成
     * 上午4节(8:00起) + 下午8节(14:00起)，与DataStoreManager默认值保持一致
     */
    private suspend fun createDefaultClassTimes() {
        val classDuration = 40L  // 与 DataStoreManager.ClassTimeKeys.DEFAULT_CLASS_DURATION 一致
        val breakDuration = 10L  // 与 DataStoreManager.ClassTimeKeys.DEFAULT_BREAK_DURATION 一致
        val morningSections = 4  // 与 DataStoreManager.ClassTimeKeys.DEFAULT_MORNING_SECTIONS 一致
        val afternoonSections = 8 // 与 DataStoreManager.ClassTimeKeys.DEFAULT_AFTERNOON_SECTIONS 一致

        val defaultTimes = mutableListOf<ClassTime>()

        // 上午课程（从8:00开始）
        var currentStart = LocalTime.of(8, 0)
        for (i in 1..morningSections) {
            val endTime = currentStart.plusMinutes(classDuration)
            defaultTimes.add(
                ClassTime(sectionNumber = i, startTime = currentStart, endTime = endTime)
            )
            currentStart = endTime.plusMinutes(breakDuration)
        }

        // 下午课程（从14:00开始）
        currentStart = LocalTime.of(14, 0)
        for (i in 1..afternoonSections) {
            val sectionNumber = morningSections + i
            val endTime = currentStart.plusMinutes(classDuration)
            defaultTimes.add(
                ClassTime(sectionNumber = sectionNumber, startTime = currentStart, endTime = endTime)
            )
            currentStart = endTime.plusMinutes(breakDuration)
        }

        classTimeRepository.insertClassTimes(defaultTimes)
    }
}



