package com.wind.ggbond.classtime.service.helper

import android.content.Context
import android.net.Uri
import com.wind.ggbond.classtime.data.model.ParsedCourse
import java.io.InputStream

interface TableParser {

    data class TableParseResult(
        val courses: List<ParsedCourse>,
        val warnings: List<String> = emptyList(),
        val detectedTemplate: String? = null,
        val confidence: Float = 0f,
        val headerRowIndex: Int = 0,
        val fieldMapping: Map<String, Int> = emptyMap(),
        val detectedEncoding: String? = null
    )

    fun parseFromUri(
        context: Context,
        uri: Uri,
        config: TableImportConfig = TableImportConfig()
    ): TableParseResult

    fun parseFromStream(
        inputStream: InputStream,
        fileName: String?,
        config: TableImportConfig = TableImportConfig()
    ): TableParseResult

    fun getPreviewFromUri(
        context: Context,
        uri: Uri,
        maxRows: Int = 10
    ): List<List<String>>

    fun getPreviewFromStream(
        inputStream: InputStream,
        fileName: String?,
        maxRows: Int = 10
    ): List<List<String>>
}
