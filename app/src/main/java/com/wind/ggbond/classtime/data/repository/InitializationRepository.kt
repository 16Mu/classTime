package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.entity.ClassTime
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
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
     * 
     * 注意：不再自动创建默认课表，用户需要在导入/创建课程时手动创建课表
     * 这符合小爱课程表等应用的设计：先创建课表（设置昵称+开始日期）再导入课程
     */
    suspend fun initializeDefaultData() {
        AppLogger.d(TAG, "开始初始化默认数据...")
        
        // 检查是否已有上课时间配置
        val existingClassTimes = classTimeRepository.getClassTimesByConfig("default").first()
        if (existingClassTimes.isEmpty()) {
            AppLogger.d(TAG, "未找到上课时间配置，创建默认配置")
            // 只创建默认上下课时间，不创建默认课表
            createDefaultClassTimes()
        }
        
        AppLogger.d(TAG, "默认数据初始化完成")
    }
    
    /**
     * ✅ 预加载核心数据到内存
     * 在应用启动时调用，确保主界面能立即显示数据
     */
    suspend fun preloadCoreData() {
        AppLogger.d(TAG, "开始预加载核心数据...")
        val startTime = System.currentTimeMillis()
        
        try {
            // 预加载当前课表（包含学期时间信息）
            val schedule = scheduleRepository.getCurrentSchedule()
            AppLogger.d(TAG, "预加载课表: ${schedule?.name}")
            
            // 预加载上课时间
            val classTimes = classTimeRepository.getClassTimesByConfig("default").first()
            AppLogger.d(TAG, "预加载上课时间: ${classTimes.size}个时间段")
            
            // ✅ 关键：预加载所有课程数据
            if (schedule != null) {
                val courses = courseRepository.getAllCoursesBySchedule(schedule.id).first()
                AppLogger.d(TAG, "预加载课程: ${courses.size}门课程")
            }
            
            val duration = System.currentTimeMillis() - startTime
            AppLogger.d(TAG, "核心数据预加载完成，耗时: ${duration}ms")
        } catch (e: Exception) {
            AppLogger.e(TAG, "预加载数据失败", e)
            // 不抛出异常，允许应用继续运行
        }
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

        classTimeRepository.insertAll(defaultTimes)
    }
}



