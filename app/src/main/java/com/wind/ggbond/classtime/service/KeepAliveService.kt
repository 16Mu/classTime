package com.wind.ggbond.classtime.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.R

/**
 * 保活前台服务
 * 通过在通知栏显示常驻通知来保持应用在后台运行
 */
class KeepAliveService : Service() {
    
    companion object {
        private const val TAG = "KeepAliveService"
        const val CHANNEL_ID = "keep_alive_channel"
        const val NOTIFICATION_ID = 10001
        
        /**
         * 启动服务
         */
        fun start(context: android.content.Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 停止服务
         */
        fun stop(context: android.content.Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
        }
        
        /**
         * 检查服务是否运行
         */
        fun isRunning(context: android.content.Context): Boolean {
            val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
                if (KeepAliveService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "保活服务已创建")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "保活服务已启动")
        // 重新创建通知，确保显示
        startForeground(NOTIFICATION_ID, createNotification())
        // 返回 START_STICKY，确保服务被杀死后自动重启
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AppLogger.d(TAG, "保活服务被销毁，尝试重启...")
        // 服务被杀死时尝试重新启动
        try {
            val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restartIntent)
            } else {
                applicationContext.startService(restartIntent)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "重启保活服务失败", e)
        }
    }
    
    /**
     * 用户从最近任务列表滑掉App时触发
     * 此时重新注册WorkManager兜底任务（WorkManager由系统调度，不会随App被杀而消失）
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        AppLogger.d(TAG, "App被从最近任务移除，重新注册兜底任务")
        // 重新注册WorkManager兜底任务（这是最后的保障）
        com.wind.ggbond.classtime.worker.ReminderCheckWorker.enqueue(applicationContext)
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "保活服务"
            val channelDescription = "保持应用在后台运行，确保课程提醒正常工作"
            val importance = NotificationManager.IMPORTANCE_LOW  // 低重要性，不打扰用户
            
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance).apply {
                description = channelDescription
                setShowBadge(false)  // 不显示角标
                setSound(null, null)  // 无声音
                enableVibration(false)  // 无震动
                enableLights(false)  // 无指示灯
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("课程表正在运行")
            .setContentText("请勿划掉此通知，否则可能导致课程提醒收不到或延迟")
            .setSmallIcon(R.drawable.ic_notification)  // 使用通知图标
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // 设置为持续通知，用户无法滑动删除
            .setPriority(NotificationCompat.PRIORITY_LOW)  // 低优先级，不打扰用户
            .setCategory(NotificationCompat.CATEGORY_SERVICE)  // 服务类别
            .setShowWhen(false)  // 不显示时间
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)  // 锁屏不显示
            .build()
    }
}







