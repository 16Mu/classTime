package com.wind.ggbond.classtime.util

import android.util.Log
import com.wind.ggbond.classtime.data.repository.SettingsRepository
import com.wind.ggbond.classtime.ui.theme.BackgroundThemeManager

object CourseColorProvider {

    private const val TAG = "CourseColorProvider"

    @Volatile
    var settingsRepository: SettingsRepository? = null

    @Volatile
    var backgroundThemeManager: BackgroundThemeManager? = null

    suspend fun getColorForCourse(
        courseName: String,
        existingColors: List<String> = emptyList()
    ): String {
        return try {
            val repo = settingsRepository
            val themeMgr = backgroundThemeManager

            if (repo != null && themeMgr != null && repo.isMonetCourseColorsEnabled()) {
                val seedColor = themeMgr.getCurrentSeedColor()
                val saturationInt = repo.getCourseColorSaturation()
                val saturationLevel = MonetColorPalette.SaturationLevel.entries.getOrElse(saturationInt) {
                    MonetColorPalette.SaturationLevel.STANDARD
                }

                MonetColorPalette.getColorForCourse(
                    courseName = courseName,
                    seedColor = seedColor,
                    saturationLevel = saturationLevel,
                    existingColors = existingColors
                )
            } else {
                CourseColorPalette.getColorForCourse(courseName, existingColors)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getColorForCourse failed, fallback to fixed palette", e)
            CourseColorPalette.getColorForCourse(courseName, existingColors)
        }
    }

    suspend fun assignColorsForCourses(
        courseNames: List<String>
    ): Map<String, String> {
        return try {
            val repo = settingsRepository
            val themeMgr = backgroundThemeManager

            if (repo != null && themeMgr != null && repo.isMonetCourseColorsEnabled()) {
                val seedColor = themeMgr.getCurrentSeedColor()
                val saturationInt = repo.getCourseColorSaturation()
                val saturationLevel = MonetColorPalette.SaturationLevel.entries.getOrElse(saturationInt) {
                    MonetColorPalette.SaturationLevel.STANDARD
                }

                MonetColorPalette.assignColorsForCourses(courseNames, seedColor, saturationLevel)
            } else {
                CourseColorPalette.assignColorsForCourses(courseNames)
            }
        } catch (e: Exception) {
            Log.w(TAG, "assignColorsForCourses failed, fallback to fixed palette", e)
            CourseColorPalette.assignColorsForCourses(courseNames)
        }
    }
}
