package com.wind.ggbond.classtime.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 媒体文件管理器 - 负责将用户选择的媒体文件复制到应用私有存储
 * 
 * 优势：
 * 1. 不依赖外部文件的生命周期
 * 2. 用户删除原文件不影响应用
 * 3. 不需要持久化 URI 权限
 * 4. 应用卸载时自动清理
 */
class MediaFileManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MediaFileManager"
        private const val BACKGROUNDS_DIR = "backgrounds"
    }
    
    /**
     * 获取背景文件存储目录
     */
    private fun getBackgroundsDir(): File {
        val dir = File(context.filesDir, BACKGROUNDS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * 将 URI 指向的文件复制到应用私有存储
     * 
     * @param sourceUri 源文件 URI
     * @param fileExtension 文件扩展名（如 "jpg", "png", "gif", "mp4"）
     * @return 复制后的本地文件 URI，失败返回 null
     */
    suspend fun copyMediaToPrivateStorage(
        sourceUri: Uri,
        fileExtension: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Copying media from URI: $sourceUri")
            
            // 生成唯一文件名
            val fileName = "${UUID.randomUUID()}.$fileExtension"
            val destFile = File(getBackgroundsDir(), fileName)
            
            // 复制文件
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                Log.e(TAG, "Failed to open input stream for URI: $sourceUri")
                return@withContext null
            }
            
            Log.d(TAG, "Media copied successfully to: ${destFile.absolutePath}")
            
            // 返回本地文件的 URI
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy media to private storage", e)
            null
        }
    }
    
    /**
     * 删除指定的背景文件
     * 
     * @param fileUri 文件 URI
     * @return 是否删除成功
     */
    suspend fun deleteBackgroundFile(fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(fileUri.path ?: return@withContext false)
            if (file.exists() && file.parentFile?.name == BACKGROUNDS_DIR) {
                val deleted = file.delete()
                Log.d(TAG, "Deleted background file: ${file.name}, success: $deleted")
                deleted
            } else {
                Log.w(TAG, "File does not exist or not in backgrounds directory: ${file.absolutePath}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete background file", e)
            false
        }
    }
    
    /**
     * 清理所有背景文件
     * 
     * @return 删除的文件数量
     */
    suspend fun clearAllBackgrounds(): Int = withContext(Dispatchers.IO) {
        try {
            val dir = getBackgroundsDir()
            val files = dir.listFiles() ?: return@withContext 0
            
            var deletedCount = 0
            files.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
            
            Log.d(TAG, "Cleared $deletedCount background files")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear backgrounds", e)
            0
        }
    }
    
    /**
     * 获取背景文件的大小（字节）
     */
    fun getBackgroundFileSize(fileUri: Uri): Long {
        return try {
            val file = File(fileUri.path ?: return 0L)
            if (file.exists()) file.length() else 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file size", e)
            0L
        }
    }
    
    /**
     * 获取所有背景文件的总大小（字节）
     */
    fun getTotalBackgroundsSize(): Long {
        return try {
            val dir = getBackgroundsDir()
            val files = dir.listFiles() ?: return 0L
            files.sumOf { it.length() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get total size", e)
            0L
        }
    }
    
    /**
     * 检查文件是否存在
     */
    fun fileExists(fileUri: Uri): Boolean {
        return try {
            val file = File(fileUri.path ?: return false)
            file.exists()
        } catch (e: Exception) {
            false
        }
    }
}
