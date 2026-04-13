package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.repository.SchoolRepository
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class SchoolExtractorFactory @Inject constructor(
    private val extractors: Map<String, @JvmSuppressWildcards SchoolScheduleExtractor>,
    private val schoolRepository: SchoolRepository
) {

    private val allExtractors by lazy { extractors.values.toList() }

    private val reflectionCache = mutableMapOf<String, SchoolScheduleExtractor?>()

    private val aliasMap by lazy {
        val map = mutableMapOf<String, SchoolScheduleExtractor>()
        for (extractor in allExtractors) {
            map[extractor.schoolId.lowercase()] = extractor
            map[extractor.schoolName.lowercase()] = extractor
            for (alias in extractor.aliases) {
                map[alias.lowercase()] = extractor
            }
        }
        map
    }

    private val urlMap by lazy {
        val map = mutableMapOf<String, SchoolScheduleExtractor>()
        for (extractor in allExtractors) {
            for (url in extractor.supportedUrls) {
                map[url.lowercase()] = extractor
            }
        }
        map
    }

    fun getExtractor(schoolId: String): SchoolScheduleExtractor? {
        val key = schoolId.lowercase()
        return aliasMap[key]
            ?: extractors[key]
            ?: createExtractorFromConfig(schoolId)
            ?: run {
                AppLogger.w("SchoolExtractorFactory", "未找到学校 '$schoolId' 的提取器")
                null
            }
    }

    private fun createExtractorFromConfig(schoolId: String): SchoolScheduleExtractor? {
        reflectionCache[schoolId]?.let { return it }
        if (schoolId in reflectionCache) return null

        val className = runCatching {
            runBlocking { schoolRepository.getSchoolById(schoolId) }
        }.getOrNull()?.extractorClass

        if (className.isNullOrBlank()) {
            reflectionCache[schoolId] = null
            return null
        }

        val extractor = createExtractorByReflection(className)
        reflectionCache[schoolId] = extractor
        if (extractor != null) {
            AppLogger.i("SchoolExtractorFactory", "通过反射创建提取器: $className -> $schoolId")
        }
        return extractor
    }

    private fun createExtractorByReflection(className: String): SchoolScheduleExtractor? {
        return try {
            val clazz = Class.forName(className)
            val constructor = clazz.getDeclaredConstructor()
            constructor.isAccessible = true
            val instance = constructor.newInstance()
            instance as SchoolScheduleExtractor
        } catch (e: Exception) {
            AppLogger.e("SchoolExtractorFactory", "反射创建提取器失败: $className", e)
            null
        }
    }

    fun detectExtractorByUrl(url: String): SchoolScheduleExtractor? {
        return urlMap.entries.find { (urlKey, _) ->
            url.contains(urlKey, ignoreCase = true)
        }?.value
            ?: if (url.contains("/eams/", ignoreCase = true) &&
                    url.contains("courseTableForStd", ignoreCase = true)) {
                extractors["cqust"]
            } else null
    }

    fun detectExtractorByContent(html: String, url: String): SchoolScheduleExtractor? =
        detectExtractorByUrl(url) ?: allExtractors.find { it.isSchedulePage(html, url) }

    fun getSupportedSchools(): List<SchoolScheduleExtractor> = allExtractors
}
