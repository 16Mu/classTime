package com.wind.ggbond.classtime.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wind.ggbond.classtime.R

/**
 * 自动登录通知助手
 * 
 * 负责在自动登录失败或需要验证码时发送通知
 */
object AutoLoginNotificationHelper {
    
    private const val TAG = "AutoLoginNotification"
    private const val CHANNEL_ID = "auto_login_channel"
    private const val NOTIFICATION_ID = 4001
    
    /**
     * 发送自动登录失败通知
     */
    fun sendAutoLoginFailureNotification(
        context: Context,
        resultCode: String,
        resultMessage: String
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 创建通知渠道（Android 8.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "自动登录",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "课表自动更新登录相关通知"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // 构建通知
            val title = when (resultCode) {
                AutoLoginResultCode.NEED_CAPTCHA -> "时课 自动更新需要验证"
                AutoLoginResultCode.LOGIN_FAIL -> "时课 自动登录失败"
                AutoLoginResultCode.NO_CREDENTIAL -> "时课 未配置自动登录"
                AutoLoginResultCode.NETWORK_ERROR -> "时课 网络错误"
                else -> "时课 自动更新失败"
            }
            
            val content = when (resultCode) {
                AutoLoginResultCode.NEED_CAPTCHA -> "需要在设置页完成验证码和登录"
                AutoLoginResultCode.LOGIN_FAIL -> "请检查账号密码是否正确"
                AutoLoginResultCode.NO_CREDENTIAL -> "请在设置页配置自动登录账号"
                AutoLoginResultCode.NETWORK_ERROR -> "请检查网络连接"
                else -> resultMessage
            }
            
            // 创建跳转到设置页的Intent
            val intent = Intent().apply {
                action = "com.wind.ggbond.classtime.ACTION_OPEN_AUTO_UPDATE_SETTINGS"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build()
            
            notificationManager.notify(NOTIFICATION_ID, notification)
            
            Log.d(TAG, "通知已发送: $title - $content")
        } catch (e: Exception) {
            Log.e(TAG, "发送通知失败", e)
        }
    }
    
    /**
     * 发送自动登录成功通知
     */
    fun sendAutoLoginSuccessNotification(
        context: Context,
        message: String = "课表已自动更新"
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 创建通知渠道（Android 8.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "自动登录",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "课表自动更新登录相关通知"
                    enableVibration(false)
                    enableLights(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("时课 自动更新成功")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText("课表已自动更新\n\n$message\n\n更新时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            notificationManager.notify(NOTIFICATION_ID, notification)
            
            Log.d(TAG, "成功通知已发送: $message")
        } catch (e: Exception) {
            Log.e(TAG, "发送成功通知失败", e)
        }
    }
    
    /**
     * 取消通知
     */
    fun cancelNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d(TAG, "通知已取消")
        } catch (e: Exception) {
            Log.e(TAG, "取消通知失败", e)
        }
    }
}
