package com.wind.ggbond.classtime.service.contract

interface IDataExporter {

    enum class ExportFormat {
        JSON, ICS, CSV, TXT, HTML
    }

    data class ExportResult(
        val success: Boolean,
        val filePath: String? = null,
        val errorMessage: String? = null,
        val fileUri: android.net.Uri? = null
    )

    suspend fun export(scheduleId: Long, format: ExportFormat): ExportResult

    fun shareFile(filePath: String, mimeType: String = "*/*")
}
