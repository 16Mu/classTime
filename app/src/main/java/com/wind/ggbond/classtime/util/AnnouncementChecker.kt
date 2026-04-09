package com.wind.ggbond.classtime.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AnnouncementInfo(
    val id: String,
    val title: String,
    val content: String,
    val type: AnnouncementType,
    val priority: Int,
    val publishTime: String,
    val expireTime: String,
    val targetVersionMin: String,
    val targetVersionMax: String,
    val url: String
) {
    enum class AnnouncementType {
        NORMAL,
        IMPORTANT,
        FORCE
    }

    fun shouldShow(currentVersion: String): Boolean {
        if (targetVersionMin.isNotEmpty() && compareVersion(currentVersion, targetVersionMin) < 0) return false
        if (targetVersionMax.isNotEmpty() && compareVersion(currentVersion, targetVersionMax) > 0) return false
        return true
    }

    private fun compareVersion(v1: String, v2: String): Int {
        val parts1 = v1.removePrefix("v").split("-").first().split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.removePrefix("v").split("-").first().split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 < p2) return -1
            if (p1 > p2) return 1
        }
        return 0
    }
}

@Singleton
class AnnouncementChecker @Inject constructor() {

    companion object {
        private const val TAG = "AnnouncementChecker"
        private const val ANNOUNCEMENT_URL = "https://gitee.com/ggbondpy/classTime/raw/main/announcement.json"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAnnouncements(context: Context): Result<List<AnnouncementInfo>> {
        return withContext(Dispatchers.IO) {
            var response: Response? = null
            try {
                val currentVersion = getCurrentVersion(context)
                AppLogger.d(TAG, "当前版本: $currentVersion, 开始拉取公告...")

                val request = Request.Builder()
                    .url(ANNOUNCEMENT_URL)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) Classtime-App/${com.wind.ggbond.classtime.BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/json")
                    .header("Referer", "https://gitee.com/ggbondpy/classTime")
                    .build()

                response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext Result.success(emptyList())
                    val json = JSONObject(body)
                    val announcements = json.optJSONArray("announcements")

                    if (announcements != null && announcements.length() > 0) {
                        val list = mutableListOf<AnnouncementInfo>()
                        for (i in 0 until announcements.length()) {
                            val item = announcements.getJSONObject(i)
                            val info = AnnouncementInfo(
                                id = item.optString("id", i.toString()),
                                title = item.getString("title"),
                                content = item.getString("content"),
                                type = when (item.optString("type", "normal")) {
                                    "important" -> AnnouncementInfo.AnnouncementType.IMPORTANT
                                    "force" -> AnnouncementInfo.AnnouncementType.FORCE
                                    else -> AnnouncementInfo.AnnouncementType.NORMAL
                                },
                                priority = item.optInt("priority", 0),
                                publishTime = item.optString("publishTime", ""),
                                expireTime = item.optString("expireTime", ""),
                                targetVersionMin = item.optString("targetVersionMin", ""),
                                targetVersionMax = item.optString("targetVersionMax", ""),
                                url = item.optString("url", "")
                            )
                            if (info.shouldShow(currentVersion)) {
                                list.add(info)
                            }
                        }
                        AppLogger.d(TAG, "拉取到 ${list.size} 条有效公告")
                        Result.success(list.sortedByDescending { it.priority })
                    } else {
                        AppLogger.d(TAG, "无公告数据")
                        Result.success(emptyList())
                    }
                } else {
                    AppLogger.w(TAG, "HTTP ${response.code}: 获取公告失败")
                    Result.success(emptyList())
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取公告异常: ${e.message}")
                Result.failure(e)
            } finally {
                response?.close()
            }
        }
    }

    fun getCurrentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
