package com.wind.ggbond.classtime.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MediaFileManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MediaFileManager"
        private const val BACKGROUNDS_DIR = "backgrounds"
    }
    
    private fun getBackgroundsDir(): File {
        val dir = File(context.filesDir, BACKGROUNDS_DIR)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            AppLogger.d(TAG, "Created directory: ${dir.absolutePath}, success=$created")
        } else {
            AppLogger.d(TAG, "Directory exists: ${dir.absolutePath}")
        }
        return dir
    }
    
    suspend fun copyMediaToPrivateStorage(
        sourceUri: Uri,
        fileExtension: String
    ): Uri? = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "文件复制 START | sourceUri=$sourceUri | extension=$fileExtension")
        
        try {
            val fileName = "${UUID.randomUUID()}.$fileExtension"
            val destFile = File(getBackgroundsDir(), fileName)
            AppLogger.d(TAG, "目标文件路径: ${destFile.absolutePath}")
            
            var bytesCopied = 0L
            
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                AppLogger.d(TAG, "成功打开源文件的输入流")
                
                FileOutputStream(destFile).use { output ->
                    bytesCopied = input.copyTo(output)
                    val sizeInKB = bytesCopied / 1024
                    AppLogger.d(TAG, "文件复制完成 | $bytesCopied bytes ($sizeInKB KB)")
                }
            } ?: run {
                AppLogger.e(TAG, "无法打开源文件的输入流 | URI: $sourceUri")
                return@withContext null
            }
            
            if (destFile.exists()) {
                val fileSize = destFile.length()
                AppLogger.d(TAG, "SUCCESS: 目标文件已创建 | ${fileSize / 1024} KB")
                Uri.fromFile(destFile)
            } else {
                AppLogger.e(TAG, "复制完成后目标文件不存在 | ${destFile.absolutePath}")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "文件复制异常 | sourceUri=$sourceUri", e)
            null
        }
    }
    
    suspend fun deleteBackgroundFile(fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "deleteBackgroundFile START: fileUri=$fileUri")
        
        try {
            val filePath = fileUri.path
            
            val file = File(filePath ?: run {
                AppLogger.e(TAG, "No file path in URI")
                return@withContext false
            })
            
            if (file.exists()) {
                val backgroundsDir = File(context.filesDir, BACKGROUNDS_DIR)
                
                if (file.canonicalPath.startsWith(backgroundsDir.canonicalPath)) {
                    val deleted = file.delete()
                    AppLogger.d(TAG, "deleteBackgroundFile Result: deleted=$deleted")
                    deleted
                } else {
                    AppLogger.w(TAG, "SECURITY: File not in backgrounds directory, refusing delete")
                    false
                }
            } else {
                AppLogger.w(TAG, "WARNING: File does not exist: ${file.absolutePath}")
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteBackgroundFile EXCEPTION", e)
            false
        }
    }
    
    suspend fun clearAllBackgrounds(): Int = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "clearAllBackgrounds START")
        
        try {
            val dir = getBackgroundsDir()
            val files = dir.listFiles()
            
            if (files == null) {
                AppLogger.w(TAG, "Cannot list files in directory")
                return@withContext 0
            }
            
            AppLogger.d(TAG, "Found ${files.size} files to clear")
            
            var deletedCount = 0
            
            files.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                    AppLogger.d(TAG, "Deleted: ${file.name}")
                } else {
                    AppLogger.w(TAG, "Failed to delete: ${file.name}")
                }
            }
            
            AppLogger.d(TAG, "END: Deleted $deletedCount files")
            deletedCount
        } catch (e: Exception) {
            AppLogger.e(TAG, "clearAllBackgrounds EXCEPTION", e)
            0
        }
    }
    
    suspend fun getBackgroundFileSize(fileUri: Uri): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            val filePath = fileUri.path
            val file = File(filePath ?: return@withContext 0L)
            
            if (file.exists()) {
                val size = file.length()
                AppLogger.d(TAG, "getBackgroundFileSize File exists, size=$size bytes")
                size
            } else {
                AppLogger.w(TAG, "getBackgroundFileSize File does not exist")
                0L
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getBackgroundFileSize EXCEPTION", e)
            0L
        }
    }
    
    suspend fun getTotalBackgroundsSize(): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            val dir = getBackgroundsDir()
            val files = dir.listFiles() ?: run {
                AppLogger.d(TAG, "getTotalBackgroundsSize No files found")
                return@withContext 0L
            }
            
            val totalSize = files.sumOf { it.length() }
            AppLogger.d(TAG, "getTotalBackgroundsSize END: ${files.size} files, total=${totalSize / 1024} KB")
            totalSize
        } catch (e: Exception) {
            AppLogger.e(TAG, "getTotalBackgroundsSize EXCEPTION", e)
            0L
        }
    }
    
    suspend fun fileExists(fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val filePath = fileUri.path
        
        return@withContext try {
            val file = File(filePath ?: return@withContext false)
            val exists = file.exists()
            AppLogger.d(TAG, "fileExists uri=$fileUri, exists=$exists")
            exists
        } catch (e: Exception) {
            AppLogger.e(TAG, "fileExists EXCEPTION", e)
            false
        }
    }
}
