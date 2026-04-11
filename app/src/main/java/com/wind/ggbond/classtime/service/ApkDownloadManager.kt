package com.wind.ggbond.classtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wind.ggbond.classtime.MainActivity
import com.wind.ggbond.classtime.R
import com.wind.ggbond.classtime.util.AppLogger
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object ApkDownloadManager {

    private const val TAG = "ApkDownloadManager"
    private const val CHANNEL_ID = "apk_download_channel"
    private const val NOTIFICATION_ID = 10001

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Int) : DownloadState()
        data class Success(val file: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val _downloadState = MutableLiveData<DownloadState>(DownloadState.Idle)
    val downloadState: LiveData<DownloadState> = _downloadState

    private var downloadJob: Job? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun downloadApk(context: Context, url: String) {
        if (downloadJob?.isActive == true) return

        _downloadState.value = DownloadState.Downloading(0)
        createNotificationChannel(context)
        showNotification(context, 0)

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileName = "classtime_update.apk"
                val outputFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                if (outputFile.exists()) outputFile.delete()

                val request = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) Classtime-App")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    _downloadState.postValue(DownloadState.Error("下载失败: HTTP ${response.code}"))
                    withContext(Dispatchers.Main) { cancelNotification(context) }
                    return@launch
                }

                val body = response.body ?: run {
                    _downloadState.postValue(DownloadState.Error("下载失败: 响应为空"))
                    withContext(Dispatchers.Main) { cancelNotification(context) }
                    return@launch
                }

                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(outputFile)
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var lastProgress = -1

                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                    totalRead += read

                    if (contentLength > 0) {
                        val progress = ((totalRead * 100) / contentLength).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            _downloadState.postValue(DownloadState.Downloading(progress))
                            withContext(Dispatchers.Main) { showNotification(context, progress) }
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                _downloadState.postValue(DownloadState.Success(outputFile))
                withContext(Dispatchers.Main) {
                    showCompleteNotification(context, outputFile)
                }
                AppLogger.d(TAG, "APK 下载完成: ${outputFile.absolutePath}")
            } catch (e: CancellationException) {
                _downloadState.postValue(DownloadState.Error("下载已取消"))
                withContext(Dispatchers.Main) { cancelNotification(context) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "APK 下载异常", e)
                _downloadState.postValue(DownloadState.Error("下载失败: ${e.message}"))
                withContext(Dispatchers.Main) { cancelNotification(context) }
            }
        }
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "应用更新下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "显示APK下载进度" }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, progress: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("正在下载更新")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification(context: Context, file: File) {
        val installIntent = getInstallIntent(context, file)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("下载完成")
            .setContentText("点击安装新版本")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    fun getInstallIntent(context: Context, file: File): Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun getInstallPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else Intent()
    }

    fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canRequestInstall(context)) {
            context.startActivity(getInstallPermissionIntent(context))
            return
        }
        try {
            context.startActivity(getInstallIntent(context, file))
        } catch (e: Exception) {
            AppLogger.e(TAG, "安装APK失败", e)
        }
    }
}
