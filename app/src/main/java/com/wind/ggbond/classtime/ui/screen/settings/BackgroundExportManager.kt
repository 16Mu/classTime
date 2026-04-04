package com.wind.ggbond.classtime.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.wind.ggbond.classtime.ui.theme.BackgroundScheme
import com.wind.ggbond.classtime.ui.theme.BackgroundType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundExportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    
    companion object {
        private const val TAG = "BgExportManager"
        private const val EXPORT_FILE_NAME = "background_schemes_export.json"
    }

    data class ExportData(
        val schemes: List<BackgroundScheme>,
        val exportTime: Long = System.currentTimeMillis(),
        val version: Int = 1
    )

    suspend fun exportSchemes(schemes: List<BackgroundScheme>): Uri? = withContext(Dispatchers.IO) {
        try {
            val exportData = ExportData(schemes = schemes)
            val json = gson.toJson(exportData)
            
            val file = java.io.File(context.cacheDir, EXPORT_FILE_NAME)
            file.writeText(json)
            
            Log.d(TAG, "成功导出 ${schemes.size} 套背景方案")
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "导出背景方案失败", e)
            null
        }
    }

    suspend fun createShareIntent(uri: Uri): Intent? = withContext(Dispatchers.IO) {
        try {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建分享Intent失败", e)
            null
        }
    }

    suspend fun importSchemes(uri: Uri): List<BackgroundScheme> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.readText()
                reader.close()
                
                val type = object : TypeToken<ExportData>() {}.type
                val exportData = gson.fromJson<ExportData>(json, type)
                
                Log.d(TAG, "成功导入 ${exportData.schemes.size} 套背景方案")
                exportData.schemes
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "导入背景方案失败", e)
            emptyList()
        }
    }

    suspend fun validateImportFile(uri: Uri): ValidationResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.readText()
                reader.close()
                
                val type = object : TypeToken<ExportData>() {}.type
                val exportData = gson.fromJson<ExportData>(json, type)
                
                when {
                    exportData.schemes.isEmpty() -> ValidationResult(false, "文件中不包含任何背景方案")
                    exportData.schemes.size > 10 -> ValidationResult(false, "背景方案数量超过上限（最多10套）")
                    else -> ValidationResult(true, "验证通过，包含 ${exportData.schemes.size} 套背景方案")
                }
            } ?: ValidationResult(false, "无法读取文件")
        } catch (e: Exception) {
            Log.e(TAG, "验证导入文件失败", e)
            ValidationResult(false, "文件格式无效：${e.message}")
        }
    }

    data class ValidationResult(val isValid: Boolean, val message: String = "")
}
