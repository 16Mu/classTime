package com.wind.ggbond.classtime.data.local.dao

import androidx.room.*
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import kotlinx.coroutines.flow.Flow

/**
 * 上下课时间配置数据访问对象
 */
@Dao
interface ClassTimeDao {
    
    @Query("SELECT * FROM class_times WHERE configName = :configName ORDER BY sectionNumber")
    fun getClassTimesByConfig(configName: String = "default"): Flow<List<ClassTime>>
    
    @Query("SELECT * FROM class_times WHERE configName = :configName ORDER BY sectionNumber")
    suspend fun getClassTimesByConfigSync(configName: String = "default"): List<ClassTime>
    
    @Query("SELECT * FROM class_times WHERE configName = :configName AND sectionNumber = :sectionNumber LIMIT 1")
    suspend fun getClassTime(configName: String = "default", sectionNumber: Int): ClassTime?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassTime(classTime: ClassTime): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassTimes(classTimes: List<ClassTime>)
    
    @Update
    suspend fun updateClassTime(classTime: ClassTime)
    
    @Delete
    suspend fun deleteClassTime(classTime: ClassTime)
    
    @Query("DELETE FROM class_times WHERE configName = :configName")
    suspend fun deleteAllByConfig(configName: String = "default")
}



