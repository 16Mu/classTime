package com.wind.ggbond.classtime.service.helper

import android.content.Context
import android.os.Environment
import com.wind.ggbond.classtime.service.contract.IDataExporter
import com.wind.ggbond.classtime.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportDirectoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ExportDirectoryManager"
        const val BASE_DIR_NAME = "classTimeOutput"
        const val CSV_SUBDIR = "csv"
        const val HTML_SUBDIR = "html"
        const val JSON_SUBDIR = "json"
        const val ICS_SUBDIR = "ics"
        const val TXT_SUBDIR = "txt"
    }

    private val _baseDir: File by lazy {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BASE_DIR_NAME)
    }

    fun getExportDir(format: IDataExporter.ExportFormat): File {
        val subDir = when (format) {
            IDataExporter.ExportFormat.CSV -> CSV_SUBDIR
            IDataExporter.ExportFormat.HTML -> HTML_SUBDIR
            IDataExporter.ExportFormat.JSON -> JSON_SUBDIR
            IDataExporter.ExportFormat.ICS -> ICS_SUBDIR
            IDataExporter.ExportFormat.TXT -> TXT_SUBDIR
        }
        val dir = File(_baseDir, subDir)
        return ensureDirectory(dir)
    }

    fun getBaseDir(): File = ensureDirectory(_baseDir)

    fun ensureAllDirectories(): Boolean {
        return try {
            val dirs = listOf(CSV_SUBDIR, HTML_SUBDIR, JSON_SUBDIR, ICS_SUBDIR, TXT_SUBDIR)
            var allSuccess = true
            dirs.forEach { subDir ->
                val dir = File(_baseDir, subDir)
                if (!dir.exists() && !dir.mkdirs()) {
                    AppLogger.e(TAG, "无法创建目录: ${dir.absolutePath}")
                    allSuccess = false
                }
            }
            allSuccess
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建目录结构失败: ${e.message}")
            false
        }
    }

    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    fun getFileForExport(fileName: String, format: IDataExporter.ExportFormat): File {
        val dir = getExportDir(format)
        return File(dir, fileName)
    }

    private fun ensureDirectory(dir: File): File {
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (!created) {
                AppLogger.w(TAG, "无法创建目录: ${dir.absolutePath}，尝试使用应用私有目录")
            }
        }
        return dir
    }

    fun getFallbackDir(format: IDataExporter.ExportFormat): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports")
        val subDir = when (format) {
            IDataExporter.ExportFormat.CSV -> CSV_SUBDIR
            IDataExporter.ExportFormat.HTML -> HTML_SUBDIR
            IDataExporter.ExportFormat.JSON -> JSON_SUBDIR
            IDataExporter.ExportFormat.ICS -> ICS_SUBDIR
            IDataExporter.ExportFormat.TXT -> TXT_SUBDIR
        }
        return File(exportDir, subDir).apply { if (!exists()) mkdirs() }
    }
}
