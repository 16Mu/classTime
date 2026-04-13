package com.wind.ggbond.classtime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import com.wind.ggbond.classtime.service.KeepAliveService
import com.wind.ggbond.classtime.worker.ReminderCheckWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var alarmReminderScheduler: IAlarmScheduler
    
    companion object {
        private const val TAG = "BootReceiver"
        private val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_PACKAGE_REPLACED) {
            
            AppLogger.d(TAG, "收到系统启动/应用更新广播: ${intent.action}")
            
            // 恢复WorkManager兜底检查任务（由系统进程调度，优先注册）
            ReminderCheckWorker.enqueue(context)
            AppLogger.d(TAG, "WorkManager兜底任务已恢复")
            
            // 启动前台保活服务
            try {
                KeepAliveService.start(context)
                AppLogger.d(TAG, "前台保活服务已启动")
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动保活服务失败", e)
            }
            
            // 使用 goAsync() 延长 BroadcastReceiver 生命周期，确保协程完成
            val pendingResult = goAsync()
            
            bootScope.launch {
                try {
                    alarmReminderScheduler.rescheduleAllReminders()
                    AppLogger.d(TAG, "开机后重新调度AlarmManager提醒完成")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "开机后重新调度提醒失败", e)
                } finally {
                    // 通知系统 BroadcastReceiver 处理完毕
                    pendingResult.finish()
                }
            }
        }
    }
}
