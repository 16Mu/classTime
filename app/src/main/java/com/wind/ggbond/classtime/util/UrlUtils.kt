package com.wind.ggbond.classtime.util

object UrlUtils {
    fun extractDomain(url: String): String = try {
        java.net.URL(url).host ?: url
    } catch (_: Exception) {
        url
    }
}
