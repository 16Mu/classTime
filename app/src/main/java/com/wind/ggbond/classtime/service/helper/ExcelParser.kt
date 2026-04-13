package com.wind.ggbond.classtime.service.helper

import android.content.Context
import android.net.Uri
import com.wind.ggbond.classtime.util.AppLogger
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelParser @Inject constructor(
    private val xlsxParser: LightweightXlsxParser,
    private val xlsParser: LightweightXlsParser
) : TableParser {

    companion object {
        private const val TAG = "ExcelParser"
        private const val XLS_MAGIC_0 = 0xD0
        private const val XLS_MAGIC_1 = 0xCF
        private const val XLS_MAGIC_2 = 0x11
        private const val XLS_MAGIC_3 = 0xE0
        private const val ZIP_MAGIC_0 = 0x50
        private const val ZIP_MAGIC_1 = 0x4B
        private const val ZIP_MAGIC_2 = 0x03
        private const val ZIP_MAGIC_3 = 0x04
    }

    private inline fun <T> executeByFormat(
        format: ExcelFormat,
        onXls: () -> T,
        onXlsx: () -> T,
        isEmpty: (T) -> Boolean = { false },
        noinline fallback: (() -> T)? = null
    ): T {
        return when (format) {
            ExcelFormat.XLS -> onXls()
            ExcelFormat.XLSX -> onXlsx()
            ExcelFormat.UNKNOWN -> {
                val result = onXlsx()
                if (isEmpty(result) && fallback != null) fallback() else result
            }
        }
    }

    fun parseExcelFromUri(
        context: Context,
        uri: Uri,
        config: ExcelImportConfig = ExcelImportConfig()
    ): ExcelParseResult {
        return try {
            val format = detectFormat(context, uri)
            executeByFormat(
                format,
                onXls = { xlsParser.parseXlsFromUri(context, uri, config) },
                onXlsx = { xlsxParser.parseXlsxFromUri(context, uri, config) },
                isEmpty = { it.courses.isEmpty() && it.warnings.any { w -> w.contains("解析失败") } },
                fallback = { xlsParser.parseXlsFromUri(context, uri, config) }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Excel解析失败", e)
            ExcelParseResult(courses = emptyList(), warnings = listOf("解析失败: ${e.message}"))
        }
    }

    fun parseExcel(
        inputStream: InputStream,
        fileName: String?,
        config: ExcelImportConfig = ExcelImportConfig()
    ): ExcelParseResult {
        return try {
            val format = detectFormatByExtension(fileName)
            executeByFormat(
                format,
                onXls = { xlsParser.parseXls(inputStream, config) },
                onXlsx = { xlsxParser.parseXlsx(inputStream, config) },
                isEmpty = { it.courses.isEmpty() && it.warnings.any { w -> w.contains("解析失败") } },
                fallback = { xlsParser.parseXls(inputStream, config) }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Excel解析失败", e)
            ExcelParseResult(courses = emptyList(), warnings = listOf("解析失败: ${e.message}"))
        }
    }

    fun getSheetNames(context: Context, uri: Uri): List<String> {
        return try {
            val format = detectFormat(context, uri)
            executeByFormat(
                format,
                onXls = { xlsParser.getSheetNames(context, uri) },
                onXlsx = { xlsxParser.getSheetNames(context, uri) },
                isEmpty = { it.isEmpty() },
                fallback = { xlsParser.getSheetNames(context, uri) }
            )
        } catch (e: Exception) { emptyList() }
    }

    fun getSheetNames(inputStream: InputStream, fileName: String?): List<String> {
        return try {
            val format = detectFormatByExtension(fileName)
            executeByFormat(
                format,
                onXls = { xlsParser.getSheetNames(inputStream) },
                onXlsx = { xlsxParser.getSheetNames(inputStream) },
                isEmpty = { it.isEmpty() },
                fallback = { xlsParser.getSheetNames(inputStream) }
            )
        } catch (e: Exception) { emptyList() }
    }

    fun getSheetPreview(
        context: Context,
        uri: Uri,
        sheetIndex: Int = 0,
        maxRows: Int = 10
    ): List<List<String>> {
        return try {
            val format = detectFormat(context, uri)
            executeByFormat(
                format,
                onXls = { xlsParser.getSheetPreview(context, uri, sheetIndex, maxRows) },
                onXlsx = { xlsxParser.getSheetPreview(context, uri, sheetIndex, maxRows) },
                isEmpty = { it.isEmpty() },
                fallback = { xlsParser.getSheetPreview(context, uri, sheetIndex, maxRows) }
            )
        } catch (e: Exception) { emptyList() }
    }

    fun getSheetPreview(
        inputStream: InputStream,
        fileName: String?,
        sheetIndex: Int = 0,
        maxRows: Int = 10
    ): List<List<String>> {
        return try {
            val format = detectFormatByExtension(fileName)
            executeByFormat(
                format,
                onXls = { xlsParser.getSheetPreview(inputStream, sheetIndex, maxRows) },
                onXlsx = { xlsxParser.getSheetPreview(inputStream, fileName, sheetIndex, maxRows) },
                isEmpty = { it.isEmpty() },
                fallback = { xlsParser.getSheetPreview(inputStream, sheetIndex, maxRows) }
            )
        } catch (e: Exception) { emptyList() }
    }

    private enum class ExcelFormat { XLS, XLSX, UNKNOWN }

    private fun detectFormat(context: Context, uri: Uri): ExcelFormat {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(8)
                val read = stream.read(header)
                if (read >= 4) {
                    if (header[0].toInt() and 0xFF == XLS_MAGIC_0 &&
                        header[1].toInt() and 0xFF == XLS_MAGIC_1 &&
                        header[2].toInt() and 0xFF == XLS_MAGIC_2 &&
                        header[3].toInt() and 0xFF == XLS_MAGIC_3
                    ) return ExcelFormat.XLS

                    if (header[0].toInt() and 0xFF == ZIP_MAGIC_0 &&
                        header[1].toInt() and 0xFF == ZIP_MAGIC_1 &&
                        header[2].toInt() and 0xFF == ZIP_MAGIC_2 &&
                        header[3].toInt() and 0xFF == ZIP_MAGIC_3
                    ) return ExcelFormat.XLSX
                }
            }
        } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }

        val fileName = getFileName(context, uri)
        return detectFormatByExtension(fileName)
    }

    private fun detectFormatByExtension(fileName: String?): ExcelFormat {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
        return when (ext) {
            "xls" -> ExcelFormat.XLS
            "xlsx", "xlsm" -> ExcelFormat.XLSX
            else -> ExcelFormat.UNKNOWN
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
        return fileName ?: uri.lastPathSegment
    }

    override fun parseFromUri(context: Context, uri: Uri, config: TableImportConfig): TableParser.TableParseResult {
        val excelConfig = ExcelImportConfig(
            sheetIndex = config.sheetIndex, headerRowIndex = config.headerRowIndex,
            dataStartRowIndex = config.dataStartRowIndex, fieldMapping = config.fieldMapping,
            skipEmptyRows = config.skipEmptyRows, trimWhitespace = config.trimWhitespace
        )
        val result = parseExcelFromUri(context, uri, excelConfig)
        return TableParser.TableParseResult(
            courses = result.courses, warnings = result.warnings,
            detectedTemplate = result.detectedTemplate, confidence = result.confidence,
            headerRowIndex = result.headerRowIndex, fieldMapping = result.fieldMapping
        )
    }

    override fun parseFromStream(inputStream: InputStream, fileName: String?, config: TableImportConfig): TableParser.TableParseResult {
        val excelConfig = ExcelImportConfig(
            sheetIndex = config.sheetIndex, headerRowIndex = config.headerRowIndex,
            dataStartRowIndex = config.dataStartRowIndex, fieldMapping = config.fieldMapping,
            skipEmptyRows = config.skipEmptyRows, trimWhitespace = config.trimWhitespace
        )
        val result = parseExcel(inputStream, fileName, excelConfig)
        return TableParser.TableParseResult(
            courses = result.courses, warnings = result.warnings,
            detectedTemplate = result.detectedTemplate, confidence = result.confidence,
            headerRowIndex = result.headerRowIndex, fieldMapping = result.fieldMapping
        )
    }

    override fun getPreviewFromUri(context: Context, uri: Uri, maxRows: Int): List<List<String>> {
        return getSheetPreview(context, uri, 0, maxRows)
    }

    override fun getPreviewFromStream(inputStream: InputStream, fileName: String?, maxRows: Int): List<List<String>> {
        return getSheetPreview(inputStream, fileName, 0, maxRows)
    }
}
