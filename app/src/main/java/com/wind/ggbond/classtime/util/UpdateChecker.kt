package com.wind.ggbond.classtime.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.wind.ggbond.classtime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class VersionInfo(
    val latestVersion: String,
    val latestVersionCode: Int,
    val downloadUrl: String,
    val updateLog: String,
    val forceUpdate: Boolean,
    val minSupportVersion: String,
    val publishTime: String
) {
    fun needUpdate(currentVersion: String): Boolean {
        return compareVersion(currentVersion, latestVersion) < 0
    }
    
    fun needForceUpdate(currentVersion: String): Boolean {
        return forceUpdate || compareVersion(currentVersion, minSupportVersion) < 0
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
class UpdateChecker @Inject constructor() {
    
    companion object {
        private const val TAG = "UpdateChecker"
        
        private const val VERSION_URL = "https://gitee.com/ggbondpy/classTime/raw/main/version.json"
        private const val RELEASES_API = "https://gitee.com/api/v5/repos/ggbondpy/classTime/releases/latest"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    suspend fun checkUpdate(context: Context): Result<VersionInfo?> {
        return withContext(Dispatchers.IO) {
            try {
                val currentVersion = getCurrentVersion(context)
                AppLogger.d(TAG, "当前版本: $currentVersion, 开始检查更新...")
                
                var result = fetchVersionInfo(VERSION_URL)
                
                if (result == null) {
                    AppLogger.d(TAG, "version.json 获取失败，尝试 Releases API...")
                    result = fetchFromReleasesApi()
                }
                
                if (result != null) {
                    val needUpdate = result.needUpdate(currentVersion)
                    AppLogger.d(TAG, "检查完成: 最新版本=${result.latestVersion}, 需要更新=$needUpdate")
                    Result.success(result)
                } else {
                    AppLogger.w(TAG, "获取版本信息失败")
                    Result.success(null)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "检查更新异常: ${e.message}")
                Result.failure(e)
            }
        }
    }
    
    private fun fetchVersionInfo(url: String): VersionInfo? {
        var response: Response? = null
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) Classtime-App/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .header("Referer", "https://gitee.com/ggbondpy/classTime")
                .build()
            
            response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                
                VersionInfo(
                    latestVersion = json.getString("latestVersion"),
                    latestVersionCode = json.getInt("latestVersionCode"),
                    downloadUrl = json.getString("downloadUrl"),
                    updateLog = json.getString("updateLog"),
                    forceUpdate = json.optBoolean("forceUpdate", false),
                    minSupportVersion = json.optString("minSupportVersion", "1.0.0"),
                    publishTime = json.optString("publishTime", "")
                )
            } else {
                AppLogger.w(TAG, "HTTP ${response.code}: $url")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "fetchVersionInfo 异常: ${e.message}")
            null
        } finally {
            response?.close()
        }
    }
    
    private fun fetchFromReleasesApi(): VersionInfo? {
        var response: Response? = null
        return try {
            val request = Request.Builder()
                .url(RELEASES_API)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) Classtime-App/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .build()
            
            response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                
                val tagName = json.getString("tag_name")
                val assets = json.optJSONArray("assets")
                val downloadUrl = if (assets != null && assets.length() > 0) {
                    assets.getJSONObject(0).getString("browser_download_url")
                } else {
                    ""
                }
                
                VersionInfo(
                    latestVersion = tagName.removePrefix("v"),
                    latestVersionCode = parseVersionCode(tagName),
                    downloadUrl = downloadUrl,
                    updateLog = json.optString("body", "暂无更新说明"),
                    forceUpdate = false,
                    minSupportVersion = "1.0.0",
                    publishTime = json.optString("created_at", "")
                )
            } else {
                AppLogger.w(TAG, "Releases API HTTP ${response.code}")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "fetchFromReleasesApi 异常: ${e.message}")
            null
        } finally {
            response?.close()
        }
    }
    
    private fun parseVersionCode(version: String): Int {
        val parts = version.removePrefix("v").split("-").first().split(".")
        return if (parts.size >= 3) {
            (parts[0].toIntOrNull() ?: 0) * 10000 +
            (parts[1].toIntOrNull() ?: 0) * 100 +
            (parts[2].toIntOrNull() ?: 0)
        } else 0
    }
    
    fun getCurrentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }
    
    fun getCurrentVersionCode(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(packageInfo).toInt()
        } catch (e: PackageManager.NameNotFoundException) {
            1
        }
    }
}
