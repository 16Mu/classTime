package com.wind.ggbond.classtime.util.business

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wind.ggbond.classtime.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "LoginService"

        private const val PREFS_NAME = "auto_login_prefs"
        private const val SECURE_PREFS_NAME = "auto_login_secure"

        private const val KEY_AUTO_LOGIN_ENABLED = "auto_login_enabled"
        private const val KEY_USERNAME = "auto_login_username"
        private const val KEY_PASSWORD = "auto_login_password"
        private const val KEY_LAST_UPDATE_RESULT_CODE = "last_update_result_code"
        private const val KEY_LAST_UPDATE_RESULT_MESSAGE = "last_update_result_message"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"
        private const val KEY_MIGRATED = "credentials_migrated"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val encryptedPrefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        AppLogger.e(TAG, "加密存储初始化失败", e)
        null
    }

    init {
        migrateFromPlainText()
    }

    fun isAutoLoginEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_LOGIN_ENABLED, false)
    }

    fun setAutoLoginEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LOGIN_ENABLED, enabled).apply()
        AppLogger.d(TAG, "自动登录已${if (enabled) "启用" else "禁用"}")
    }

    fun getUsername(): String? {
        return try {
            encryptedPrefs?.getString(KEY_USERNAME, null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取用户名失败", e)
            null
        }
    }

    fun getPassword(): String? {
        return try {
            encryptedPrefs?.getString(KEY_PASSWORD, null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取密码失败", e)
            null
        }
    }

    fun saveCredentials(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            AppLogger.w(TAG, "账号或密码为空，不保存")
            return
        }

        val securePrefs = encryptedPrefs ?: run {
            AppLogger.e(TAG, "加密存储不可用，无法保存凭据")
            return
        }

        try {
            securePrefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .apply()
            AppLogger.sensitive(TAG, "Username", username)
            AppLogger.d(TAG, "账号已加密保存")
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存凭据失败", e)
        }
    }

    fun clearCredentials() {
        try {
            encryptedPrefs?.edit()
                ?.remove(KEY_USERNAME)
                ?.remove(KEY_PASSWORD)
                ?.apply()
            AppLogger.d(TAG, "账号已清除")
        } catch (e: Exception) {
            AppLogger.e(TAG, "清除凭据失败", e)
        }
    }

    fun hasCredentials(): Boolean {
        val username = getUsername()
        val password = getPassword()
        return !username.isNullOrBlank() && !password.isNullOrBlank()
    }

    fun saveLastUpdateResult(resultCode: String, resultMessage: String) {
        prefs.edit()
            .putString(KEY_LAST_UPDATE_RESULT_CODE, resultCode)
            .putString(KEY_LAST_UPDATE_RESULT_MESSAGE, resultMessage)
            .putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
            .apply()

        AppLogger.d(TAG, "更新结果已保存: $resultCode - $resultMessage")
    }

    fun getLastUpdateResultCode(): String? {
        return prefs.getString(KEY_LAST_UPDATE_RESULT_CODE, null)
    }

    fun getLastUpdateResultMessage(): String? {
        return prefs.getString(KEY_LAST_UPDATE_RESULT_MESSAGE, null)
    }

    fun getLastUpdateTime(): Long {
        return prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        try {
            encryptedPrefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            AppLogger.e(TAG, "清除加密数据失败", e)
        }
        AppLogger.d(TAG, "所有自动登录数据已清除")
    }

    private fun migrateFromPlainText() {
        if (encryptedPrefs == null) return
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        try {
            val oldUsername = prefs.getString(KEY_USERNAME, null)
            val oldPassword = prefs.getString(KEY_PASSWORD, null)

            if (!oldUsername.isNullOrBlank() && !oldPassword.isNullOrBlank()) {
                encryptedPrefs.edit()
                    .putString(KEY_USERNAME, oldUsername)
                    .putString(KEY_PASSWORD, oldPassword)
                    .apply()
                AppLogger.d(TAG, "凭据已从明文迁移到加密存储")
            }

            prefs.edit()
                .remove(KEY_USERNAME)
                .remove(KEY_PASSWORD)
                .putBoolean(KEY_MIGRATED, true)
                .apply()

        } catch (e: Exception) {
            AppLogger.e(TAG, "凭据迁移失败", e)
        }
    }
}
