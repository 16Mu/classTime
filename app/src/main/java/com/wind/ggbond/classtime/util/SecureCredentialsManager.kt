package com.wind.ggbond.classtime.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 安全凭据管理器
 * 
 * ✅ 使用EncryptedSharedPreferences加密存储敏感的账号密码信息
 * ✅ 线程安全
 * ✅ 支持多学校账号管理
 * 
 * @author AI Assistant
 * @since 2025-11-04
 */
class SecureCredentialsManager(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "SecureCredentialsManager"
        private const val ENCRYPTED_PREFS_NAME = "secure_credentials"
        private const val KEY_PREFIX_USERNAME = "username_"
        private const val KEY_PREFIX_PASSWORD = "password_"
    }
    
    // 加密存储，初始化失败时为null（不回退到明文，避免安全降级）
    private val encryptedPrefs: SharedPreferences? = EncryptedPrefsFactory.create(context, ENCRYPTED_PREFS_NAME, TAG)
    
    /**
     * 保存账号密码
     * 
     * @param schoolId 学校ID（如：jwc.example.edu.cn）
     * @param username 用户名
     * @param password 密码
     */
    fun saveCredentials(schoolId: String, username: String, password: String) {
        val prefs = encryptedPrefs ?: run {
            AppLogger.e(TAG, "加密存储不可用，无法保存凭据")
            return
        }
        try {
            prefs.edit()
                .putString(KEY_PREFIX_USERNAME + schoolId, username)
                .putString(KEY_PREFIX_PASSWORD + schoolId, password)
                .apply()
            AppLogger.d(TAG, "凭据已保存: schoolId=$schoolId")
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存凭据失败: schoolId=$schoolId", e)
        }
    }
    
    /**
     * 获取账号密码
     * 
     * @param schoolId 学校ID
     * @return Pair<用户名, 密码>，如果不存在则返回null
     */
    fun getCredentials(schoolId: String): Pair<String, String>? {
        val prefs = encryptedPrefs ?: return null
        return try {
            val username = prefs.getString(KEY_PREFIX_USERNAME + schoolId, null)
            val password = prefs.getString(KEY_PREFIX_PASSWORD + schoolId, null)
            
            if (username != null && password != null) {
                Pair(username, password)
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取凭据失败: schoolId=$schoolId", e)
            null
        }
    }
    
    /**
     * 删除指定学校的账号密码
     * 
     * @param schoolId 学校ID
     */
    fun removeCredentials(schoolId: String) {
        val prefs = encryptedPrefs ?: return
        try {
            prefs.edit()
                .remove(KEY_PREFIX_USERNAME + schoolId)
                .remove(KEY_PREFIX_PASSWORD + schoolId)
                .apply()
            AppLogger.d(TAG, "凭据已删除: schoolId=$schoolId")
        } catch (e: Exception) {
            AppLogger.e(TAG, "删除凭据失败: schoolId=$schoolId", e)
        }
    }
    
    /**
     * 清除所有账号密码
     */
    fun clearAllCredentials() {
        val prefs = encryptedPrefs ?: return
        try {
            prefs.edit().clear().apply()
            AppLogger.d(TAG, "所有凭据已清除")
        } catch (e: Exception) {
            AppLogger.e(TAG, "清除所有凭据失败", e)
        }
    }
    
    /**
     * 检查是否存储了指定学校的账号密码
     * 
     * @param schoolId 学校ID
     * @return true如果存在
     */
    fun hasCredentials(schoolId: String): Boolean {
        val prefs = encryptedPrefs ?: return false
        return try {
            val username = prefs.getString(KEY_PREFIX_USERNAME + schoolId, null)
            val password = prefs.getString(KEY_PREFIX_PASSWORD + schoolId, null)
            username != null && password != null
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查凭据失败: schoolId=$schoolId", e)
            false
        }
    }
    
    /**
     * 获取用户名（仅用于显示）
     * 
     * @param schoolId 学校ID
     * @return 用户名，如果不存在则返回null
     */
    fun getUsername(schoolId: String): String? {
        val prefs = encryptedPrefs ?: return null
        return try {
            prefs.getString(KEY_PREFIX_USERNAME + schoolId, null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取用户名失败: schoolId=$schoolId", e)
            null
        }
    }
}

