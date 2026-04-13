package com.wind.ggbond.classtime.service.helper

data class TableImportConfig(
    val sheetIndex: Int = 0,
    val headerRowIndex: Int = 0,
    val dataStartRowIndex: Int = 1,
    val fieldMapping: Map<String, Int> = emptyMap(),
    val skipEmptyRows: Boolean = true,
    val trimWhitespace: Boolean = true,
    val delimiter: Char = ',',
    val encoding: String = "UTF-8",
    val hasBom: Boolean = false
)
