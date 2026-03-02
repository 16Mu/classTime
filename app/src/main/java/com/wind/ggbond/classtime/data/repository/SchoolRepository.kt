package com.wind.ggbond.classtime.data.repository

import android.content.Context
import com.wind.ggbond.classtime.data.local.dao.SchoolDao
import com.wind.ggbond.classtime.data.local.entity.SchoolEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学校数据仓库
 */
@Singleton
class SchoolRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val schoolDao: SchoolDao,
    private val gson: Gson
) {
    
    /**
     * 初始化学校数据（从 assets 加载）
     * 先清空所有学校，再重新加载，确保已移除的学校被删除
     */
    suspend fun initializeSchoolsFromAssets() {
        try {
            val jsonString = context.assets.open("schools.json")
                .bufferedReader()
                .use { it.readText() }
                .trim()  // 去除首尾空白字符
                .replace("\uFEFF", "")  // 移除 UTF-8 BOM
            
            // 使用 JsonReader 进行宽松解析
            val reader = JsonReader(StringReader(jsonString))
            reader.isLenient = true  // 允许宽松的 JSON 格式
            
            val type = object : TypeToken<List<SchoolData>>() {}.type
            val schoolDataList: List<SchoolData> = gson.fromJson(reader, type)
            
            val schools = schoolDataList.map { it.toEntity() }
            
            // 先删除所有旧数据，确保已移除的学校被清理
            schoolDao.deleteAllSchools()
            // 再插入新的学校列表
            schoolDao.insertSchools(schools)
            
            // 验证数据是否插入成功
            val insertedCount = schoolDao.getSchoolCount()
            android.util.Log.d("SchoolRepository", "学校数据已更新，共 ${schools.size} 所学校，数据库中有 $insertedCount 条记录")
            
            // 验证isEnabled字段
            val enabledCount = schoolDao.getAllSchools().first().size
            android.util.Log.d("SchoolRepository", "已启用的学校数量：$enabledCount")
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("SchoolRepository", "加载学校数据失败", e)
        }
    }
    
    /**
     * 强制重新加载学校数据（用于调试或更新）
     */
    suspend fun reloadSchoolsFromAssets() {
        initializeSchoolsFromAssets()
    }
    
    fun getAllSchools(): Flow<List<SchoolEntity>> {
        return flow {
            // 先检查数据是否存在，如果不存在则自动加载
            val count = schoolDao.getSchoolCount()
            if (count == 0) {
                android.util.Log.w("SchoolRepository", "⚠️ 学校数据为空，自动加载...")
                initializeSchoolsFromAssets()
            }
            // 发出数据
            schoolDao.getAllSchools().collect { emit(it) }
        }
    }
    
    suspend fun getSchoolById(schoolId: String): SchoolEntity? {
        return schoolDao.getSchoolById(schoolId)
    }
    
    fun searchSchools(keyword: String): Flow<List<SchoolEntity>> {
        return schoolDao.searchSchools(keyword)
    }
    
    suspend fun insertSchool(school: SchoolEntity) {
        schoolDao.insertSchool(school)
    }
    
    fun getAllProvinces(): Flow<List<String>> {
        return flow {
            // 先检查数据是否存在，如果不存在则自动加载
            val count = schoolDao.getSchoolCount()
            if (count == 0) {
                android.util.Log.w("SchoolRepository", "⚠️ 学校数据为空，自动加载...")
                initializeSchoolsFromAssets()
            }
            // 发出数据
            schoolDao.getAllProvinces().collect { emit(it) }
        }
    }
    
    fun getSchoolsByProvince(province: String): Flow<List<SchoolEntity>> {
        return schoolDao.getSchoolsByProvince(province)
    }
    
    /**
     * 用于从 JSON 解析的数据类
     */
    private data class SchoolData(
        val id: String,
        val name: String,
        val shortName: String,
        val province: String,
        val systemType: String,
        val loginUrl: String,
        val scheduleUrl: String,
        val scheduleMethod: String,
        val scheduleParams: Map<String, String>,
        val dataFormat: String,
        val needCsrfToken: Boolean,
        val csrfTokenName: String,
        val jsonMapping: Map<String, String>,
        val description: String,
        val tips: String
    ) {
        fun toEntity() = SchoolEntity(
            id = id,
            name = name,
            shortName = shortName,
            province = province,
            systemType = systemType,
            loginUrl = upgradeToHttpsIfNeeded(loginUrl),
            scheduleUrl = upgradeToHttpsIfNeeded(scheduleUrl),
            scheduleMethod = scheduleMethod,
            scheduleParams = scheduleParams,
            dataFormat = dataFormat,
            needCsrfToken = needCsrfToken,
            csrfTokenName = csrfTokenName,
            jsonMapping = jsonMapping,
            description = description,
            tips = tips,
            isEnabled = true  // 显式设置为true，确保数据可以查询
            // createdAt 使用默认值 System.currentTimeMillis()
        )
        
        /**
         * 智能URL升级：自动将HTTP升级为HTTPS
         * 策略：优先保持原配置，让WebView的自动重试机制处理协议问题
         */
        private fun upgradeToHttpsIfNeeded(url: String): String {
            // 如果URL已经是HTTPS或HTTP，保持不变
            // WebView会自动处理协议升级和降级
            if (url.startsWith("https://", ignoreCase = true) || 
                url.startsWith("http://", ignoreCase = true)) {
                return url
            }
            
            // 其他情况（相对路径等），保持不变
            return url
        }
    }
}



