package com.wind.ggbond.classtime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.wind.ggbond.classtime.data.local.converter.Converters

/**
 * 学校实体（用于本地缓存和搜索）
 */
@Entity(tableName = "schools")
@TypeConverters(Converters::class)
data class SchoolEntity(
    @PrimaryKey
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
    val tips: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val defaultSemesterStartDate: String? = null,  // 学校特定的默认学期开始日期，格式：yyyy-MM-dd（已废弃，使用fallSemesterStartDate）
    val fallSemesterStartDate: String? = null,
    val springSemesterStartDate: String? = null,
    val extractorClass: String? = null
)



