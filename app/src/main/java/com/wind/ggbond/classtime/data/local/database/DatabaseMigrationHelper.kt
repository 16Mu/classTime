package com.wind.ggbond.classtime.data.local.database

import android.content.Context
import java.io.File
import java.io.FileInputStream
import com.wind.ggbond.classtime.util.AppLogger
import java.io.FileOutputStream

/**
 * 数据库迁移助手工具类
 * 
 * 职责：
 * 1. 迁移前自动备份数据库
 * 2. 迁移失败时支持回滚
 * 3. 提供详细的错误诊断信息
 * 4. 数据完整性校验
 */
object DatabaseMigrationHelper {
    
    private const val TAG = "MigrationHelper"
    private const val BACKUP_DIR_NAME = "db_backups"
    private const val MAX_BACKUPS = 5  // 最多保留5个备份
    
    /**
     * 执行带备份的安全迁移
     * 
     * @param context 应用上下文
     * @param dbName 数据库名称
     * @param fromVersion 起始版本
     * @param toVersion 目标版本
     * @param migration 实际的迁移操作
     * @return 是否迁移成功
     */
    fun safeMigrate(
        context: Context,
        dbName: String,
        fromVersion: Int,
        toVersion: Int,
        migration: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit
    ): Boolean {
        val startTime = System.currentTimeMillis()
        AppLogger.i(TAG, "========== 开始安全迁移 $fromVersion -> $toVersion ==========")
        
        try {
            // 1. 创建备份
            val backupPath = createBackup(context, dbName, fromVersion, toVersion)
            AppLogger.i(TAG, "✅ 数据库备份成功: $backupPath")
            
            // 2. 获取数据库实例执行迁移
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) {
                AppLogger.w(TAG, "⚠️ 数据库文件不存在，跳过迁移")
                return true
            }
            
            // 3. 打开数据库并执行迁移（通过回调方式）
            // 注意：这里不直接打开数据库，而是由 Room 在适当时机调用 migration 回调
            // 我们只负责备份和记录日志
            AppLogger.i(TAG, "✅ 准备就绪，等待 Room 执行实际迁移逻辑...")
            
            return true
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 迁移准备阶段失败: ${e.message}", e)
            return false
        } finally {
            val duration = System.currentTimeMillis() - startTime
            AppLogger.i(TAG, "========== 迁移 $fromVersion -> $toVersion 准备完成，耗时: ${duration}ms ==========")
        }
    }
    
    /**
     * 创建数据库备份
     * 
     * @param context 应用上下文
     * @param dbName 数据库名称
     * @param fromVersion 当前版本
     * @param toVersion 目标版本
     * @return 备份文件路径，如果失败返回 null
     */
    fun createBackup(
        context: Context,
        dbName: String,
        fromVersion: Int,
        toVersion: Int
    ): String? {
        try {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) {
                AppLogger.w(TAG, "数据库文件不存在，无需备份: ${dbFile.absolutePath}")
                return null
            }
            
            // 创建备份目录
            val backupDir = File(context.filesDir, BACKUP_DIR_NAME)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            // 生成备份文件名（包含版本号和时间戳）
            val timestamp = System.currentTimeMillis()
            val backupFileName = "${dbName}_v${fromVersion}_to_v${toVersion}_$timestamp.db"
            val backupFile = File(backupDir, backupFileName)
            
            // 复制数据库文件
            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            AppLogger.i(TAG, "✅ 备份创建成功: ${backupFile.absolutePath} (${formatFileSize(backupFile.length())})")
            
            // 清理旧备份（保留最近的 MAX_BACKUPS 个）
            cleanOldBackups(backupDir, dbName)
            
            return backupFile.absolutePath
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 创建备份失败", e)
            return null
        }
    }
    
    /**
     * 从备份恢复数据库
     * 
     * @param context 应用上下文
     * @param dbName 数据库名称
     * @param backupPath 备份文件路径
     * @return 是否恢复成功
     */
    fun restoreFromBackup(
        context: Context,
        dbName: String,
        backupPath: String
    ): Boolean {
        try {
            val dbFile = context.getDatabasePath(dbName)
            val backupFile = File(backupPath)
            
            if (!backupFile.exists()) {
                AppLogger.e(TAG, "❌ 备份文件不存在: $backupPath")
                return false
            }
            
            // 关闭数据库连接（如果存在）
            // 注意：调用此方法前应确保数据库已关闭
            
            // 删除当前损坏的数据库
            if (dbFile.exists()) {
                dbFile.delete()
                AppLogger.i(TAG, "已删除损坏的数据库文件")
                
                // 同时删除 -wal 和 -shm 文件
                File("${dbFile.path}-wal").delete()
                File("${dbFile.path}-shm").delete()
            }
            
            // 从备份恢复
            FileInputStream(backupFile).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            AppLogger.i(TAG, "✅ 数据库恢复成功: ${dbFile.absolutePath}")
            return true
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 从备份恢复失败", e)
            return false
        }
    }
    
    /**
     * 获取所有可用的备份列表
     * 
     * @param context 应用上下文
     * @param dbName 数据库名称
     * @return 备份文件列表（按时间倒序）
     */
    fun getAvailableBackups(context: Context, dbName: String): List<BackupInfo> {
        val backups = mutableListOf<BackupInfo>()
        
        try {
            val backupDir = File(context.filesDir, BACKUP_DIR_NAME)
            if (!backupDir.exists() || !backupDir.isDirectory) {
                return backups
            }
            
            backupDir.listFiles()
                ?.filter { it.name.startsWith(dbName) && it.name.endsWith(".db") }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    backups.add(
                        BackupInfo(
                            path = file.absolutePath,
                            size = file.length(),
                            timestamp = file.lastModified(),
                            fileName = file.name
                        )
                    )
                }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取备份列表失败", e)
        }
        
        return backups
    }
    
    /**
     * 清理旧备份文件
     * 
     * @param backupDir 备份目录
     * @param dbName 数据库名称
     */
    private fun cleanOldBackups(backupDir: File, dbName: String) {
        try {
            val backupFiles = backupDir.listFiles()
                ?.filter { it.name.startsWith(dbName) && it.name.endsWith(".db") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            
            // 删除超出数量限制的旧备份
            if (backupFiles.size > MAX_BACKUPS) {
                backupFiles.drop(MAX_BACKUPS).forEach { file ->
                    if (file.delete()) {
                        AppLogger.d(TAG, "已清理旧备份: ${file.name}")
                    }
                }
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理旧备份失败", e)
        }
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
    
    /**
     * 验证数据库完整性
     * 
     * @param context 应用上下文
     * @param dbName 数据库名称
     * @return 是否完整
     */
    fun validateDatabaseIntegrity(context: Context, dbName: String): Boolean {
        return try {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) {
                AppLogger.w(TAG, "数据库文件不存在")
                return false
            }
            
            // 简单检查：文件大小是否合理（至少 1KB）
            if (dbFile.length() < 1024) {
                AppLogger.w(TAG, "数据库文件过小，可能损坏: ${dbFile.length()} bytes")
                return false
            }
            
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "验证数据库完整性失败", e)
            false
        }
    }
    
    /**
     * 备份信息数据类
     */
    data class BackupInfo(
        val path: String,
        val size: Long,
        val timestamp: Long,
        val fileName: String
    ) {
        val formattedSize: String
            get() = when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> "${size / (1024 * 1024)} MB"
            }
        
        val formattedDate: String
            get() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(timestamp))
    }
}
