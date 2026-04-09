package com.wind.ggbond.classtime.ui.theme

import com.wind.ggbond.classtime.data.datastore.DataStoreManager
import com.wind.ggbond.classtime.util.AppLogger

enum class BackgroundType(val value: String) {
    IMAGE("image"),
    VIDEO("video"),
    GIF("gif");
    
    companion object {
        fun fromValue(value: String): BackgroundType = 
            values().find { it.value == value } ?: IMAGE
    }
}

data class BackgroundScheme(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "自定义背景",
    val uri: String,
    val type: BackgroundType = BackgroundType.IMAGE,
    val seedColor: Int = DEFAULT_SEED_COLOR,
    val blurRadius: Int = DEFAULT_BLUR_RADIUS,
    val dimAmount: Int = DEFAULT_DIM_AMOUNT,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_SEED_COLOR = 0xFFD4A574.toInt()
        const val DEFAULT_BLUR_RADIUS = 0
        const val DEFAULT_DIM_AMOUNT = 40
        
        fun fromJson(json: String): BackgroundScheme? {
            return try {
                val obj = org.json.JSONObject(json)
                BackgroundScheme(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    name = obj.optString("name", "自定义背景"),
                    uri = obj.getString("uri"),
                    type = BackgroundType.fromValue(obj.optString("type", "image")),
                    seedColor = obj.optInt("seedColor", DEFAULT_SEED_COLOR),
                    blurRadius = obj.optInt("blurRadius", DEFAULT_BLUR_RADIUS),
                    dimAmount = obj.optInt("dimAmount", DEFAULT_DIM_AMOUNT),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                AppLogger.e("BackgroundScheme", "fromJson failed", e)
                null
            }
        }
        
        fun fromJsonArray(json: String?): List<BackgroundScheme> {
            if (json.isNullOrBlank()) return emptyList()
            
            return try {
                val array = org.json.JSONArray(json)
                (0 until array.length()).mapNotNull { index ->
                    fromJson(array.optString(index, ""))
                }
            } catch (e: Exception) {
                AppLogger.e("BackgroundScheme", "fromJsonArray failed", e)
                emptyList()
            }
        }
        
        fun toJsonArray(schemes: List<BackgroundScheme>): String {
            val array = org.json.JSONArray()
            schemes.forEach { scheme -> array.put(scheme.toJson()) }
            return array.toString()
        }
    }
    
    fun toJson(): String {
        return org.json.JSONObject().apply {
            put("id", id)
            put("name", name)
            put("uri", uri)
            put("type", type.value)
            put("seedColor", seedColor)
            put("blurRadius", blurRadius)
            put("dimAmount", dimAmount)
            put("createdAt", createdAt)
        }.toString()
    }
}
