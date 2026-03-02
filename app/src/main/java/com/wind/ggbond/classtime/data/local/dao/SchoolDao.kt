package com.wind.ggbond.classtime.data.local.dao

import androidx.room.*
import com.wind.ggbond.classtime.data.local.entity.SchoolEntity
import kotlinx.coroutines.flow.Flow

/**
 * 学校数据访问对象
 */
@Dao
interface SchoolDao {
    
    @Query("SELECT * FROM schools WHERE isEnabled = 1 ORDER BY name")
    fun getAllSchools(): Flow<List<SchoolEntity>>
    
    @Query("SELECT * FROM schools WHERE id = :schoolId")
    suspend fun getSchoolById(schoolId: String): SchoolEntity?
    
    @Query("""
        SELECT * FROM schools 
        WHERE isEnabled = 1 
        AND (name LIKE '%' || :keyword || '%' OR shortName LIKE '%' || :keyword || '%')
        ORDER BY name
    """)
    fun searchSchools(keyword: String): Flow<List<SchoolEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchool(school: SchoolEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchools(schools: List<SchoolEntity>)
    
    @Update
    suspend fun updateSchool(school: SchoolEntity)
    
    @Delete
    suspend fun deleteSchool(school: SchoolEntity)
    
    @Query("SELECT COUNT(*) FROM schools")
    suspend fun getSchoolCount(): Int
    
    @Query("DELETE FROM schools")
    suspend fun deleteAllSchools()
    
    @Query("SELECT DISTINCT province FROM schools WHERE isEnabled = 1 ORDER BY province")
    fun getAllProvinces(): Flow<List<String>>
    
    @Query("SELECT * FROM schools WHERE isEnabled = 1 AND province = :province ORDER BY name")
    fun getSchoolsByProvince(province: String): Flow<List<SchoolEntity>>
}



