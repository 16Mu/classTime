package com.wind.ggbond.classtime.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 安全Cookie管理器
 * 
 * ✅ 使用EncryptedSharedPreferences加密存储敏感的Cookie信息
 * ✅ 支持Cookie过期管理
 * ✅ 线程安全
 * ✅ 支持Cookie健康检查
 */
class SecureCookieManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient? = null  // 可选注入，用于健康检查
) {
    
    companion object {
        private const val TAG = "SecureCookieManager"
        private const val ENCRYPTED_PREFS_NAME = "secure_cookies"
        private const val KEY_PREFIX_COOKIE = "cookie_"
        private const val KEY_PREFIX_EXPIRY = "expiry_"
        
        // Cookie默认有效期：7天（教务系统会话通常较短）
        // 注意：实际过期时间还取决于教务系统的会话设置
        private val DEFAULT_COOKIE_LIFETIME_MS = TimeUnit.DAYS.toMillis(7)
    }
    
    // 加密存储，初始化失败时为null（不回退到明文，避免安全降级）
    private val encryptedPrefs: SharedPreferences?
    
    init {
        // 创建或获取主密钥
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        // 创建加密的SharedPreferences
        encryptedPrefs = try {
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "加密存储初始化失败，Cookie功能不可用", e)
            null  // 不回退到明文存储
        }
    }
    
    /**
     * 保存Cookie（带默认过期时间）
     * 
     * @param domain 域名（如：jwc.example.edu.cn）
     * @param cookies Cookie字符串
     */
    fun saveCookies(domain: String, cookies: String) {
        saveCookies(domain, cookies, DEFAULT_COOKIE_LIFETIME_MS)
    }
    
    /**
     * 保存Cookie（带自定义过期时间）
     * 
     * @param domain 域名
     * @param cookies Cookie字符串
     * @param lifetimeMs 生命周期（毫秒）
     */
    fun saveCookies(domain: String, cookies: String, lifetimeMs: Long) {
        val prefs = encryptedPrefs ?: run {
            AppLogger.e(TAG, "加密存储不可用，无法保存Cookie")
            return
        }
        try {
            val expiryTime = System.currentTimeMillis() + lifetimeMs
            prefs.edit()
                .putString(KEY_PREFIX_COOKIE + domain, cookies)
                .putLong(KEY_PREFIX_EXPIRY + domain, expiryTime)
                .apply()
            AppLogger.sensitive(TAG, "Cookie", cookies)
            AppLogger.d(TAG, "Cookie已保存: domain=$domain")
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存Cookie失败: domain=$domain", e)
        }
    }
    
    /**
     * 获取Cookie
     * 
     * @param domain 域名
     * @return Cookie字符串，如果不存在或已过期则返回null
     */
    fun getCookies(domain: String): String? {
        val prefs = encryptedPrefs ?: return null
        return try {
            val cookies = prefs.getString(KEY_PREFIX_COOKIE + domain, null)
            val expiryTime = prefs.getLong(KEY_PREFIX_EXPIRY + domain, 0L)
            
            if (cookies != null) {
                // 检查是否过期
                if (expiryTime == 0L || System.currentTimeMillis() < expiryTime) {
                    cookies
                } else {
                    // Cookie已过期，删除它
                    AppLogger.d(TAG, "Cookie已过期: domain=$domain")
                    removeCookies(domain)
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取Cookie失败: domain=$domain", e)
            null
        }
    }
    
    /**
     * 删除指定域名的Cookie
     * 
     * @param domain 域名
     */
    fun removeCookies(domain: String) {
        val prefs = encryptedPrefs ?: return
        try {
            prefs.edit()
                .remove(KEY_PREFIX_COOKIE + domain)
                .remove(KEY_PREFIX_EXPIRY + domain)
                .apply()
            AppLogger.d(TAG, "Cookie已删除: domain=$domain")
        } catch (e: Exception) {
            AppLogger.e(TAG, "删除Cookie失败: domain=$domain", e)
        }
    }
    
    /**
     * 清除所有Cookie
     */
    fun clearAllCookies() {
        val prefs = encryptedPrefs ?: return
        try {
            prefs.edit().clear().apply()
            AppLogger.d(TAG, "所有Cookie已清除")
        } catch (e: Exception) {
            AppLogger.e(TAG, "清除所有Cookie失败", e)
        }
    }
    
    /**
     * 检查Cookie是否存在且有效
     * 
     * @param domain 域名
     * @return true如果存在且未过期
     */
    fun hasCookies(domain: String): Boolean {
        return getCookies(domain) != null
    }
    
    /**
     * 清理所有过期的Cookie
     * 
     * 建议定期调用此方法进行清理
     */
    fun cleanExpiredCookies() {
        val prefs = encryptedPrefs ?: return
        try {
            val allKeys = prefs.all.keys
            val currentTime = System.currentTimeMillis()
            val editor = prefs.edit()
            
            var removedCount = 0
            for (key in allKeys) {
                if (key.startsWith(KEY_PREFIX_EXPIRY)) {
                    val expiryTime = prefs.getLong(key, 0L)
                    if (expiryTime > 0 && currentTime >= expiryTime) {
                        // 过期了，删除对应的cookie和expiry
                        val domain = key.removePrefix(KEY_PREFIX_EXPIRY)
                        editor.remove(KEY_PREFIX_COOKIE + domain)
                        editor.remove(key)
                        removedCount++
                    }
                }
            }
            
            editor.apply()
            AppLogger.d(TAG, "已清理 $removedCount 个过期Cookie")
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理过期Cookie失败", e)
        }
    }
    
    /**
     * 获取Cookie的剩余生命周期
     * 
     * @param domain 域名
     * @return 剩余毫秒数，-1表示不存在或已过期
     */
    fun getRemainingLifetime(domain: String): Long {
        val prefs = encryptedPrefs ?: return -1
        return try {
            val expiryTime = prefs.getLong(KEY_PREFIX_EXPIRY + domain, 0L)
            if (expiryTime > 0) {
                val remaining = expiryTime - System.currentTimeMillis()
                if (remaining > 0) remaining else -1
            } else {
                -1
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取Cookie剩余有效期失败: domain=$domain", e)
            -1
        }
    }
    
    /**
     * ✅ 新增：验证Cookie是否有效
     * 
     * 通过发送轻量级请求验证Cookie是否真实有效
     * 
     * @param domain 域名
     * @param validateUrl 验证URL（建议使用轻量级API）
     * @return true表示Cookie有效
     */
    suspend fun validateCookies(domain: String, validateUrl: String): Boolean = withContext(Dispatchers.IO) {
        // 如果没有注入OkHttpClient，跳过网络验证
        if (okHttpClient == null) {
            AppLogger.w(TAG, "OkHttpClient未注入，跳过Cookie网络验证")
            return@withContext getCookies(domain) != null
        }
        
        val cookies = getCookies(domain)
        if (cookies == null) {
            AppLogger.d(TAG, "Cookie不存在: $domain")
            return@withContext false
        }
        
        return@withContext try {
            AppLogger.d(TAG, "开始验证Cookie: $domain")
            
            val request = Request.Builder()
                .url(validateUrl)
                .addHeader("Cookie", cookies)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val isValid = response.isSuccessful
            
            if (!isValid) {
                AppLogger.w(TAG, "Cookie验证失败: HTTP ${response.code}")
                // ✅ 验证失败时自动删除Cookie
                removeCookies(domain)
            } else {
                AppLogger.d(TAG, "✅ Cookie验证成功: $domain")
            }
            
            response.close()
            isValid
        } catch (e: Exception) {
            AppLogger.e(TAG, "Cookie验证异常: ${e.message}", e)
            // 网络异常不删除Cookie，可能只是临时网络问题
            false
        }
    }
    
    /**
     * ✅ 新增：获取Cookie并验证有效性
     * 
     * 先检查本地过期时间，再选择性进行网络验证
     * 
     * @param domain 域名
     * @param validateUrl 可选的验证URL
     * @param forceValidate 是否强制网络验证（默认false）
     * @return Cookie字符串，如果无效或过期则返回null
     */
    suspend fun getCookiesWithValidation(
        domain: String,
        validateUrl: String? = null,
        forceValidate: Boolean = false
    ): String? {
        // 先检查本地过期时间
        val cookies = getCookies(domain)
        if (cookies == null) {
            return null
        }
        
        // 如果强制验证且提供了验证URL，进行网络验证
        if (forceValidate && validateUrl != null) {
            val isValid = validateCookies(domain, validateUrl)
            return if (isValid) cookies else null
        }
        
        return cookies
    }
}
