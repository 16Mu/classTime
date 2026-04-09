package com.wind.ggbond.classtime.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.wind.ggbond.classtime.ui.theme.BackgroundScheme
import com.wind.ggbond.classtime.ui.theme.BackgroundType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wind.ggbond.classtime.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundExportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    companion object {
        private const val TAG = "BgExportManager"
        private const val EXPORT_ZIP_NAME = "background_schemes_export.zip"
        private const val JSON_ENTRY_NAME = "schemes.json"
        private const val IMAGES_DIR = "images/"
        private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    }

    data class ExportData(
        val schemes: List<ExportableScheme>,
        val exportTime: Long = System.currentTimeMillis(),
        val version: Int = 2
    )

    data class ExportableScheme(
        val name: String,
        val type: BackgroundType,
        val seedColor: Int,
        val blurRadius: Int,
        val dimAmount: Int,
        val imageFileName: String?
    )

    suspend fun exportSchemes(schemes: List<BackgroundScheme>): Uri? = withContext(Dispatchers.IO) {
        try {
            val zipFile = File(context.cacheDir, EXPORT_ZIP_NAME)
            if (zipFile.exists()) zipFile.delete()

            val exportableSchemes = mutableListOf<ExportableScheme>()
            val imageFiles = mutableMapOf<String, File>()

            for (scheme in schemes) {
                val imageFileName = scheme.uri?.let { uriStr ->
                    val srcFile = resolveLocalFile(uriStr)
                    if (srcFile != null && srcFile.exists()) {
                        val fileName = "bg_${scheme.id}.${srcFile.extension}"
                        imageFiles[fileName] = srcFile
                        fileName
                    } else null
                }

                exportableSchemes.add(ExportableScheme(
                    name = scheme.name,
                    type = scheme.type,
                    seedColor = scheme.seedColor,
                    blurRadius = scheme.blurRadius,
                    dimAmount = scheme.dimAmount,
                    imageFileName = imageFileName
                ))
            }

            val exportData = ExportData(schemes = exportableSchemes)
            val json = gson.toJson(exportData)

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val jsonEntry = ZipEntry(JSON_ENTRY_NAME)
                zos.putNextEntry(jsonEntry)
                zos.write(json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                for ((fileName, srcFile) in imageFiles) {
                    val imgEntry = ZipEntry("$IMAGES_DIR$fileName")
                    zos.putNextEntry(imgEntry)
                    FileInputStream(srcFile).use { fis ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (fis.read(buffer).also { len = it } > 0) {
                            zos.write(buffer, 0, len)
                        }
                    }
                    zos.closeEntry()
                }
            }

            AppLogger.d(TAG, "成功导出 ${schemes.size} 套背景方案 (ZIP含${imageFiles.size}个媒体文件)")
            FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX", zipFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "导出背景方案失败", e)
            null
        }
    }

    suspend fun createShareIntent(uri: Uri): Intent? = withContext(Dispatchers.IO) {
        try {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建分享Intent失败", e)
            null
        }
    }

    suspend fun importSchemes(uri: Uri): List<BackgroundScheme> = withContext(Dispatchers.IO) {
        try {
            val backgroundsDir = File(context.filesDir, "backgrounds")
            if (!backgroundsDir.exists()) backgroundsDir.mkdirs()

            var exportData: ExportData? = null
            val importedFileNames = mutableMapOf<String, String>()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = entry.name
                            if (entryName == JSON_ENTRY_NAME) {
                                val json = BufferedReader(InputStreamReader(zis, Charsets.UTF_8)).readText()
                                val type = object : TypeToken<ExportData>() {}.type
                                exportData = gson.fromJson<ExportData>(json, type)
                            } else if (entryName.startsWith(IMAGES_DIR)) {
                                val fileName = entryName.substringAfter(IMAGES_DIR)
                                if (fileName.isEmpty() || fileName.contains("..")) continue
                                val destFile = File(backgroundsDir, fileName)
                                if (!destFile.canonicalPath.startsWith(backgroundsDir.canonicalPath)) continue
                                FileOutputStream(destFile).use { fos ->
                                    val buffer = ByteArray(8192)
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        fos.write(buffer, 0, len)
                                    }
                                }
                                importedFileNames[fileName] = destFile.toUri().toString()
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (exportData == null) {
                AppLogger.e(TAG, "ZIP中未找到schemes.json")
                return@withContext emptyList()
            }

            val schemes = exportData!!.schemes.mapNotNull { es ->
                val resolvedUri = es.imageFileName?.let { importedFileNames[it] }
                when {
                    resolvedUri != null -> BackgroundScheme(
                        name = es.name,
                        type = es.type,
                        seedColor = es.seedColor,
                        blurRadius = es.blurRadius,
                        dimAmount = es.dimAmount,
                        uri = resolvedUri
                    )
                    es.type == BackgroundType.IMAGE || es.type == BackgroundType.GIF || es.type == BackgroundType.VIDEO -> {
                        AppLogger.w(TAG, "跳过媒体方案(文件缺失): ${es.name}")
                        null
                    }
                    else -> BackgroundScheme(
                        name = es.name,
                        type = es.type,
                        seedColor = es.seedColor,
                        blurRadius = es.blurRadius,
                        dimAmount = es.dimAmount,
                        uri = resolvedUri ?: ""
                    )
                }
            }

            AppLogger.d(TAG, "成功导入 ${schemes.size} 套背景方案")
            schemes
        } catch (e: Exception) {
            AppLogger.e(TAG, "导入背景方案失败", e)
            emptyList()
        }
    }

    suspend fun validateImportFile(uri: Uri): ValidationResult = withContext(Dispatchers.IO) {
        try {
            var exportData: ExportData? = null

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name == JSON_ENTRY_NAME) {
                            val json = BufferedReader(InputStreamReader(zis, Charsets.UTF_8)).readText()
                            val type = object : TypeToken<ExportData>() {}.type
                            exportData = gson.fromJson<ExportData>(json, type)
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (exportData == null) {
                return@withContext ValidationResult(false, "无效的导出文件：未找到方案数据")
            }

            when {
                exportData!!.schemes.isEmpty() -> ValidationResult(false, "文件中不包含任何背景方案")
                exportData!!.schemes.size > 10 -> ValidationResult(false, "背景方案数量超过上限（最多10套）")
                else -> ValidationResult(true, "验证通过，包含 ${exportData!!.schemes.size} 套背景方案")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "验证导入文件失败", e)
            ValidationResult(false, "文件格式无效：${e.message}")
        }
    }

    private fun resolveLocalFile(uriStr: String): File? {
        return try {
            if (uriStr.startsWith("file://")) {
                val file = File(Uri.parse(uriStr).path ?: return null)
                if (file.exists()) file else null
            } else if (uriStr.startsWith("/")) {
                val file = File(uriStr)
                if (file.exists()) file else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun File.toUri(): Uri = Uri.fromFile(this)

    data class ValidationResult(val isValid: Boolean, val message: String = "")
}
