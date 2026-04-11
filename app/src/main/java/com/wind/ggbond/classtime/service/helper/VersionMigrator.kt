package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.util.AppLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionMigrator @Inject constructor() {

    companion object {
        private const val TAG = "VersionMigrator"
        val VERSION_MIGRATION_MAP = mapOf(
            "1.0" to "2.0",
            "2.0" to "3.0"
        )
    }

    data class MigrationResult(
        val success: Boolean,
        val migratedData: String?,
        val fromVersion: String,
        val toVersion: String,
        val warnings: List<String>,
        val incompatibleFields: List<String>
    )

    fun migrateJson(content: String, detectedVersion: String?): MigrationResult {
        if (detectedVersion == null) {
            return tryMigrateFromUnknownVersion(content)
        }

        if (detectedVersion == ExportMeta.CURRENT_EXPORT_VERSION) {
            return MigrationResult(
                success = true,
                migratedData = content,
                fromVersion = detectedVersion,
                toVersion = detectedVersion,
                warnings = emptyList(),
                incompatibleFields = emptyList()
            )
        }

        if (!ExportMeta.SUPPORTED_IMPORT_VERSIONS.contains(detectedVersion)) {
            AppLogger.w(TAG, "不支持的版本: $detectedVersion，尝试最低兼容版本迁移")
        }

        return performMigration(content, detectedVersion)
    }

    private fun tryMigrateFromUnknownVersion(content: String): MigrationResult {
        val gson = Gson()
        return try {
            val jsonObj = gson.fromJson(content, JsonObject::class.java)

            if (jsonObj.has("meta")) {
                val meta = jsonObj.getAsJsonObject("meta")
                val version = meta.get("version")?.asString ?: meta.get("exportVersion")?.asString ?: "2.0"
                return migrateJson(content, version)
            }

            if (jsonObj.has("courses")) {
                AppLogger.d(TAG, "检测到旧版格式（无meta字段），按 v1.0 迁移")
                return migrateFromV1toV3(content)
            }

            MigrationResult(
                success = false,
                migratedData = null,
                fromVersion = "unknown",
                toVersion = ExportMeta.CURRENT_EXPORT_VERSION,
                warnings = listOf("无法识别的JSON格式"),
                incompatibleFields = emptyList()
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "JSON迁移失败", e)
            MigrationResult(
                success = false,
                migratedData = null,
                fromVersion = "unknown",
                toVersion = ExportMeta.CURRENT_EXPORT_VERSION,
                warnings = listOf("JSON解析失败: ${e.message}"),
                incompatibleFields = emptyList()
            )
        }
    }

    private fun performMigration(content: String, fromVersion: String): MigrationResult {
        var currentVersion = fromVersion
        var currentContent = content
        val allWarnings = mutableListOf<String>()
        val allIncompatible = mutableListOf<String>()

        while (currentVersion != ExportMeta.CURRENT_EXPORT_VERSION) {
            val nextVersion = VERSION_MIGRATION_MAP[currentVersion]
            if (nextVersion == null) {
                allWarnings.add("无法从版本 $currentVersion 迁移，缺少迁移路径")
                break
            }

            val result = when (currentVersion) {
                "1.0" -> migrateFromV1toV2(currentContent)
                "2.0" -> migrateFromV2toV3(currentContent)
                else -> {
                    allWarnings.add("未知的迁移路径: $currentVersion -> $nextVersion")
                    MigrationResult(false, null, currentVersion, nextVersion, emptyList(), emptyList())
                }
            }

            if (!result.success) {
                allWarnings.add("迁移 $currentVersion -> $nextVersion 失败")
                break
            }

            allWarnings.addAll(result.warnings)
            allIncompatible.addAll(result.incompatibleFields)
            currentContent = result.migratedData ?: content
            currentVersion = nextVersion
        }

        return MigrationResult(
            success = currentVersion == ExportMeta.CURRENT_EXPORT_VERSION,
            migratedData = currentContent,
            fromVersion = fromVersion,
            toVersion = currentVersion,
            warnings = allWarnings,
            incompatibleFields = allIncompatible
        )
    }

    private fun migrateFromV1toV3(content: String): MigrationResult {
        val gson = Gson()
        val warnings = mutableListOf<String>()
        val incompatible = mutableListOf<String>()

        return try {
            val jsonObj = gson.fromJson(content, JsonObject::class.java)
            val coursesArray = if (jsonObj.has("courses")) {
                jsonObj.getAsJsonArray("courses")
            } else {
                incompatible.add("缺少courses字段")
                return MigrationResult(false, null, "1.0", "3.0", warnings, incompatible)
            }

            val now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val newJson = JsonObject().apply {
                add("meta", JsonObject().apply {
                    addProperty("exportTime", now)
                    addProperty("appName", "课程表")
                    addProperty("appVersion", ExportMeta.CURRENT_APP_VERSION)
                    addProperty("exportVersion", ExportMeta.CURRENT_EXPORT_VERSION)
                    addProperty("format", "CourseScheduleExport")
                    addProperty("checksum", "")
                })

                if (jsonObj.has("schedule")) {
                    add("schedule", jsonObj.get("schedule"))
                }

                if (jsonObj.has("classTimes")) {
                    add("classTimes", jsonObj.get("classTimes"))
                } else {
                    add("classTimes", gson.toJsonTree(emptyList<Any>()))
                }

                if (jsonObj.has("statistics")) {
                    add("statistics", jsonObj.get("statistics"))
                }

                add("courses", coursesArray)
            }

            MigrationResult(
                success = true,
                migratedData = gson.toJson(newJson),
                fromVersion = "1.0",
                toVersion = "3.0",
                warnings = warnings,
                incompatibleFields = incompatible
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "v1.0 -> v3.0 迁移失败", e)
            MigrationResult(false, null, "1.0", "3.0",
                warnings + "迁移失败: ${e.message}", incompatible)
        }
    }

    private fun migrateFromV1toV2(content: String): MigrationResult {
        val gson = Gson()
        val warnings = mutableListOf<String>()
        val incompatible = mutableListOf<String>()

        return try {
            val jsonObj = gson.fromJson(content, JsonObject::class.java)
            val coursesArray = if (jsonObj.has("courses")) {
                jsonObj.getAsJsonArray("courses")
            } else {
                incompatible.add("缺少courses字段")
                return MigrationResult(false, null, "1.0", "2.0", warnings, incompatible)
            }

            val now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val newJson = JsonObject().apply {
                add("meta", JsonObject().apply {
                    addProperty("exportTime", now)
                    addProperty("appName", "课程表")
                    addProperty("version", "2.0")
                    addProperty("format", "CourseScheduleExport")
                })

                if (jsonObj.has("schedule")) {
                    add("schedule", jsonObj.get("schedule"))
                }

                if (jsonObj.has("classTimes")) {
                    add("classTimes", jsonObj.get("classTimes"))
                } else {
                    add("classTimes", gson.toJsonTree(emptyList<Any>()))
                }

                add("statistics", JsonObject().apply {
                    addProperty("totalCourses", coursesArray.size())
                    addProperty("totalCredits", 0.0)
                })

                add("courses", coursesArray)
            }

            MigrationResult(
                success = true,
                migratedData = gson.toJson(newJson),
                fromVersion = "1.0",
                toVersion = "2.0",
                warnings = warnings,
                incompatibleFields = incompatible
            )
        } catch (e: Exception) {
            MigrationResult(false, null, "1.0", "2.0",
                warnings + "迁移失败: ${e.message}", incompatible)
        }
    }

    private fun migrateFromV2toV3(content: String): MigrationResult {
        val gson = Gson()
        val warnings = mutableListOf<String>()
        val incompatible = mutableListOf<String>()

        return try {
            val jsonObj = gson.fromJson(content, JsonObject::class.java)

            if (!jsonObj.has("meta")) {
                incompatible.add("缺少meta字段")
                return MigrationResult(false, null, "2.0", "3.0", warnings, incompatible)
            }

            val meta = jsonObj.getAsJsonObject("meta")

            meta.remove("version")
            meta.addProperty("appVersion", meta.get("appVersion")?.asString ?: "1.0.0")
            meta.addProperty("exportVersion", ExportMeta.CURRENT_EXPORT_VERSION)
            meta.addProperty("checksum", "")

            if (!jsonObj.has("classTimes")) {
                jsonObj.add("classTimes", gson.toJsonTree(emptyList<Any>()))
            }

            if (jsonObj.has("schedule")) {
                val schedule = jsonObj.getAsJsonObject("schedule")
                if (!schedule.has("classTimeConfigName")) {
                    schedule.addProperty("classTimeConfigName", "default")
                }
            }

            if (jsonObj.has("courses")) {
                val courses = jsonObj.getAsJsonArray("courses")
                courses.forEach { element ->
                    if (element.isJsonObject) {
                        val course = element.asJsonObject
                        if (!course.has("courseCode")) {
                            course.addProperty("courseCode", "")
                        }
                    }
                }
            }

            MigrationResult(
                success = true,
                migratedData = gson.toJson(jsonObj),
                fromVersion = "2.0",
                toVersion = "3.0",
                warnings = warnings,
                incompatibleFields = incompatible
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "v2.0 -> v3.0 迁移失败", e)
            MigrationResult(false, null, "2.0", "3.0",
                warnings + "迁移失败: ${e.message}", incompatible)
        }
    }
}
