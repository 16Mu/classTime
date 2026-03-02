package com.wind.ggbond.classtime.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自动登录账号管理器
 * 
 * 用于管理课表自动更新时的自动登录账号和密码
 * 仅用于自动更新流程，与手动导入完全隔离
 * 
 * 存储策略：
 * - 账号密码 → EncryptedSharedPreferences（加密存储）
 * - 开关/更新结果等非敏感配置 → 普通 SharedPreferences
 */
class AutoLoginManager(private val context: Context) {
    
    // 非敏感配置（开关状态、更新结果等）
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "auto_login_prefs",
        Context.MODE_PRIVATE
    )
    
    // 加密存储（仅用于账号密码）
    private val encryptedPrefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "auto_login_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "加密存储初始化失败，凭据功能不可用", e)
        null
    }
    
    init {
        // 数据迁移：将旧明文凭据迁移到加密存储
        migrateFromPlainText()
    }
    
    companion object {
        private const val TAG = "AutoLoginManager"
        
        // 非敏感配置键（普通SP）
        private const val KEY_AUTO_LOGIN_ENABLED = "auto_login_enabled"
        private const val KEY_LAST_UPDATE_RESULT_CODE = "last_update_result_code"
        private const val KEY_LAST_UPDATE_RESULT_MESSAGE = "last_update_result_message"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"
        
        // 敏感凭据键（加密SP）
        private const val KEY_USERNAME = "auto_login_username"
        private const val KEY_PASSWORD = "auto_login_password"
        
        // 迁移标记
        private const val KEY_MIGRATED = "credentials_migrated"
    }
    
    /**
     * 将旧版本明文存储的凭据迁移到加密存储
     * 仅执行一次，迁移后删除旧明文数据
     */
    private fun migrateFromPlainText() {
        // 如果加密存储不可用，跳过迁移
        if (encryptedPrefs == null) return
        // 如果已迁移，跳过
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        
        try {
            val oldUsername = prefs.getString(KEY_USERNAME, null)
            val oldPassword = prefs.getString(KEY_PASSWORD, null)
            
            if (!oldUsername.isNullOrBlank() && !oldPassword.isNullOrBlank()) {
                // 将旧数据写入加密存储
                encryptedPrefs.edit()
                    .putString(KEY_USERNAME, oldUsername)
                    .putString(KEY_PASSWORD, oldPassword)
                    .apply()
                Log.d(TAG, "凭据已从明文迁移到加密存储")
            }
            
            // 删除旧明文凭据并标记已迁移
            prefs.edit()
                .remove(KEY_USERNAME)
                .remove(KEY_PASSWORD)
                .putBoolean(KEY_MIGRATED, true)
                .apply()
                
        } catch (e: Exception) {
            Log.e(TAG, "凭据迁移失败", e)
        }
    }
    
    /**
     * 获取自动登录是否启用
     */
    fun isAutoLoginEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_LOGIN_ENABLED, false)
    }
    
    /**
     * 设置自动登录启用状态
     */
    fun setAutoLoginEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LOGIN_ENABLED, enabled).apply()
        Log.d(TAG, "自动登录已${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 获取保存的用户名（从加密存储读取）
     */
    fun getUsername(): String? {
        return try {
            encryptedPrefs?.getString(KEY_USERNAME, null)
        } catch (e: Exception) {
            Log.e(TAG, "读取用户名失败", e)
            null
        }
    }
    
    /**
     * 获取保存的密码（从加密存储读取）
     */
    fun getPassword(): String? {
        return try {
            encryptedPrefs?.getString(KEY_PASSWORD, null)
        } catch (e: Exception) {
            Log.e(TAG, "读取密码失败", e)
            null
        }
    }
    
    /**
     * 保存账号和密码（写入加密存储）
     */
    fun saveCredentials(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            Log.w(TAG, "账号或密码为空，不保存")
            return
        }
        
        val securePrefs = encryptedPrefs ?: run {
            Log.e(TAG, "加密存储不可用，无法保存凭据")
            return
        }
        
        try {
            securePrefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .apply()
            Log.d(TAG, "账号已加密保存")
        } catch (e: Exception) {
            Log.e(TAG, "保存凭据失败", e)
        }
    }
    
    /**
     * 清除保存的账号和密码
     */
    fun clearCredentials() {
        try {
            encryptedPrefs?.edit()
                ?.remove(KEY_USERNAME)
                ?.remove(KEY_PASSWORD)
                ?.apply()
            Log.d(TAG, "账号已清除")
        } catch (e: Exception) {
            Log.e(TAG, "清除凭据失败", e)
        }
    }
    
    /**
     * 检查是否已配置账号
     */
    fun hasCredentials(): Boolean {
        val username = getUsername()
        val password = getPassword()
        return !username.isNullOrBlank() && !password.isNullOrBlank()
    }
    
    /**
     * 保存最后一次自动更新的结果
     */
    fun saveLastUpdateResult(resultCode: String, resultMessage: String) {
        prefs.edit()
            .putString(KEY_LAST_UPDATE_RESULT_CODE, resultCode)
            .putString(KEY_LAST_UPDATE_RESULT_MESSAGE, resultMessage)
            .putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "更新结果已保存: $resultCode - $resultMessage")
    }
    
    /**
     * 获取最后一次自动更新的结果代码
     */
    fun getLastUpdateResultCode(): String? {
        return prefs.getString(KEY_LAST_UPDATE_RESULT_CODE, null)
    }
    
    /**
     * 获取最后一次自动更新的结果消息
     */
    fun getLastUpdateResultMessage(): String? {
        return prefs.getString(KEY_LAST_UPDATE_RESULT_MESSAGE, null)
    }
    
    /**
     * 获取最后一次自动更新的时间
     */
    fun getLastUpdateTime(): Long {
        return prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)
    }
    
    /**
     * 清除所有自动登录相关数据
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        try {
            encryptedPrefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "清除加密数据失败", e)
        }
        Log.d(TAG, "所有自动登录数据已清除")
    }
    
    /**
     * 记录自动登录日志
     * 
     * @param logRepository 日志仓库（由调用方传入）
     * @param resultCode 结果代码
     * @param resultMessage 结果消息
     * @param durationMs 耗时（毫秒）
     * @param remark 备注
     */
    suspend fun logAutoLogin(
        logRepository: com.wind.ggbond.classtime.data.repository.AutoLoginLogRepository,
        resultCode: String,
        resultMessage: String,
        durationMs: Long = 0,
        remark: String? = null
    ) {
        val username = getUsername()
        val success = resultCode == AutoLoginResultCode.OK
        
        withContext(Dispatchers.IO) {
            try {
                logRepository.logAutoLogin(
                    resultCode = resultCode,
                    resultMessage = resultMessage,
                    username = username,
                    durationMs = durationMs,
                    success = success,
                    remark = remark
                )
                Log.d(TAG, "自动登录日志已记录: $resultCode")
            } catch (e: Exception) {
                Log.e(TAG, "记录自动登录日志失败", e)
            }
        }
    }
}

/**
 * 自动登录结果代码
 */
object AutoLoginResultCode {
    const val OK = "OK"                              // 成功
    const val NO_CREDENTIAL = "NO_CREDENTIAL"        // 未配置账号
    const val NEED_CAPTCHA = "NEED_CAPTCHA"          // 需要验证码
    const val LOGIN_FAIL = "LOGIN_FAIL"              // 登录失败
    const val NETWORK_ERROR = "NETWORK_ERROR"        // 网络错误
    const val UNKNOWN_ERROR = "UNKNOWN_ERROR"        // 未知错误
}

/**
 * 自动登录结果消息映射
 */
object AutoLoginResultMessages {
    fun getMessage(code: String): String {
        return when (code) {
            AutoLoginResultCode.OK -> "自动更新成功"
            AutoLoginResultCode.NO_CREDENTIAL -> "未配置自动登录账号"
            AutoLoginResultCode.NEED_CAPTCHA -> "需要在设置页完成验证码和登录"
            AutoLoginResultCode.LOGIN_FAIL -> "登录失败，请检查账号密码"
            AutoLoginResultCode.NETWORK_ERROR -> "网络错误，请检查网络连接"
            AutoLoginResultCode.UNKNOWN_ERROR -> "未知错误，请重试"
            else -> "更新失败"
        }
    }
}
