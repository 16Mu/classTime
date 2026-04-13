package com.wind.ggbond.classtime.util

import android.content.Context
import com.wind.ggbond.classtime.data.repository.AutoLoginLogRepository
import com.wind.ggbond.classtime.util.business.LoginNotificationHelper
import com.wind.ggbond.classtime.util.business.LoginService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoLoginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loginService: LoginService,
    private val notificationHelper: LoginNotificationHelper
) {

    companion object {
        private const val TAG = "AutoLoginManager"
    }

    fun isAutoLoginEnabled(): Boolean {
        return loginService.isAutoLoginEnabled()
    }

    fun setAutoLoginEnabled(enabled: Boolean) {
        loginService.setAutoLoginEnabled(enabled)
    }

    fun getUsername(): String? {
        return loginService.getUsername()
    }

    fun getPassword(): String? {
        return loginService.getPassword()
    }

    fun saveCredentials(username: String, password: String) {
        loginService.saveCredentials(username, password)
    }

    fun clearCredentials() {
        loginService.clearCredentials()
    }

    fun hasCredentials(): Boolean {
        return loginService.hasCredentials()
    }

    fun saveLastUpdateResult(resultCode: String, resultMessage: String) {
        loginService.saveLastUpdateResult(resultCode, resultMessage)

        when (resultCode) {
            AutoLoginResultCode.OK -> {
                notificationHelper.showLoginSuccessNotification(resultMessage)
            }
            else -> {
                notificationHelper.showLoginFailureNotification(resultCode, resultMessage)
            }
        }
    }

    fun getLastUpdateResultCode(): String? {
        return loginService.getLastUpdateResultCode()
    }

    fun getLastUpdateResultMessage(): String? {
        return loginService.getLastUpdateResultMessage()
    }

    fun getLastUpdateTime(): Long {
        return loginService.getLastUpdateTime()
    }

    fun clearAll() {
        loginService.clearAll()
    }

    suspend fun logAutoLogin(
        logRepository: AutoLoginLogRepository,
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
                AppLogger.d(TAG, "自动登录日志已记录: $resultCode")
            } catch (e: Exception) {
                AppLogger.e(TAG, "记录自动登录日志失败", e)
            }
        }
    }

    fun sendLoginSuccessNotification(message: String = "课表已自动更新") {
        notificationHelper.showLoginSuccessNotification(message)
    }

    fun sendLoginFailureNotification(resultCode: String, resultMessage: String) {
        notificationHelper.showLoginFailureNotification(resultCode, resultMessage)
    }

    fun cancelNotification() {
        notificationHelper.cancelNotification()
    }
}
