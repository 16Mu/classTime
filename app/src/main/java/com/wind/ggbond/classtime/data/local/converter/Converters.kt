package com.wind.ggbond.classtime.data.local.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.LocalTime

/**
 * Room 类型转换器
 */
class Converters {
    private val gson = Gson()
    
    // List<Int> 转换 (用于周次列表)
    @TypeConverter
    fun fromIntList(value: List<Int>?): String {
        return gson.toJson(value ?: emptyList<Int>())
    }
    
    @TypeConverter
    fun toIntList(value: String): List<Int> {
        val type = object : TypeToken<List<Int>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Map<String, String> 转换
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String {
        return gson.toJson(value ?: emptyMap<String, String>())
    }
    
    @TypeConverter
    fun toStringMap(value: String): Map<String, String> {
        val type = object : TypeToken<Map<String, String>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    // LocalDate 转换
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.toString()
    }
    
    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // LocalTime 转换
    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? {
        return value?.toString()
    }
    
    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? {
        return value?.let {
            try {
                LocalTime.parse(it)
            } catch (e: Exception) {
                null
            }
        }
    }
}

