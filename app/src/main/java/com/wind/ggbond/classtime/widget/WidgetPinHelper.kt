package com.wind.ggbond.classtime.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

object WidgetPinHelper {

    enum class WidgetType {
        TODAY_COURSE,
        NEXT_CLASS,
        COMPACT_LIST,
        WEEK_OVERVIEW,
        LARGE_TODAY_COURSE,
        TOMORROW_COURSE;

        val displayName: String
            get() = when (this) {
                TODAY_COURSE -> "今日课程"
                NEXT_CLASS -> "下节课倒计时"
                COMPACT_LIST -> "紧凑列表"
                WEEK_OVERVIEW -> "周概览"
                LARGE_TODAY_COURSE -> "大尺寸课程表"
                TOMORROW_COURSE -> "智能课表"
            }
    }

    enum class CompatibilityLevel {
        HIGH,
        MEDIUM,
        LOW
    }

    data class CompatibilityReport(
        val compatibilityLevel: CompatibilityLevel,
        val message: String
    )

    sealed class PinResult {
        data class Success(val widgetType: WidgetType) : PinResult()
        data class FallbackNeeded(val widgetType: WidgetType, val guideTitle: String, val guideSteps: List<String>) : PinResult()
        data class Failed(val widgetType: WidgetType, val reason: String) : PinResult()
    }

    private val manufacturer: String get() = Build.MANUFACTURER.lowercase()

    private fun isKnownProblematicOEM(): Boolean {
        val m = manufacturer
        return m.contains("vivo") || m.contains("iqoo")
    }

    private fun isXiaomiDevice(): Boolean {
        val m = manufacturer
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
    }

    private fun isHuaweiDevice(): Boolean {
        val m = manufacturer
        return m.contains("huawei") || m.contains("honor")
    }

    fun isRequestPinAppWidgetSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        return appWidgetManager.isRequestPinAppWidgetSupported
    }

    fun requestPinWidget(context: Context, widgetType: WidgetType): PinResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(context, "系统版本过低，不支持自动添加小组件", Toast.LENGTH_LONG).show()
            return PinResult.FallbackNeeded(widgetType, "请手动添加小组件", getManualGuideSteps())
        }

        if (!isRequestPinAppWidgetSupported(context)) {
            Toast.makeText(context, "当前设备不支持自动添加，请手动添加", Toast.LENGTH_LONG).show()
            return PinResult.FallbackNeeded(widgetType, "请手动添加小组件", getManualGuideSteps())
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)

        val providerComponent = when (widgetType) {
            WidgetType.TODAY_COURSE -> ComponentName(context, TodayCourseWidgetReceiver::class.java)
            WidgetType.NEXT_CLASS -> ComponentName(context, NextClassWidgetReceiver::class.java)
            WidgetType.COMPACT_LIST -> ComponentName(context, CompactListViewWidgetReceiver::class.java)
            WidgetType.WEEK_OVERVIEW -> ComponentName(context, WeekOverviewWidgetReceiver::class.java)
            WidgetType.LARGE_TODAY_COURSE -> ComponentName(context, LargeTodayCourseWidgetProvider::class.java)
            WidgetType.TOMORROW_COURSE -> ComponentName(context, TomorrowCourseWidgetReceiver::class.java)
        }

        if (isKnownProblematicOEM()) {
            Toast.makeText(context, "检测到 ${Build.MANUFACTURER} 设备，建议手动添加小组件以获得最佳体验", Toast.LENGTH_LONG).show()
            return PinResult.FallbackNeeded(widgetType, "${Build.MANUFACTURER} 设备手动添加指南", getOEMGuideSteps())
        }

        val callbackIntent = Intent(context, WidgetPinCallbackReceiver::class.java)
        val successCallback = PendingIntent.getBroadcast(
            context,
            widgetType.ordinal,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val result = appWidgetManager.requestPinAppWidget(providerComponent, null, successCallback)
                if (result) {
                    if (isXiaomiDevice()) {
                        Toast.makeText(context, "小组件已请求添加，请在桌面查看", Toast.LENGTH_SHORT).show()
                    } else if (isHuaweiDevice()) {
                        Toast.makeText(context, "请确认弹窗后将小组件拖入桌面", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "请确认添加小组件", Toast.LENGTH_SHORT).show()
                    }
                    PinResult.Success(widgetType)
                } else {
                    Toast.makeText(context, "添加请求未成功，请尝试手动添加", Toast.LENGTH_LONG).show()
                    PinResult.FallbackNeeded(widgetType, "自动添加未成功，请手动添加", getManualGuideSteps())
                }
            } else {
                PinResult.Failed(widgetType, "系统版本不满足要求")
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Toast.makeText(context, "缺少小组件权限，请手动添加", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
            PinResult.FallbackNeeded(widgetType, "权限不足，请手动添加", getManualGuideSteps())
        } catch (e: Exception) {
            Toast.makeText(context, "添加小组件失败: ${e.message}", Toast.LENGTH_LONG).show()
            PinResult.FallbackNeeded(widgetType, "添加失败，请手动添加", getManualGuideSteps())
        }
    }

    fun getManualGuideSteps(): List<String> {
        return when {
            isXiaomiDevice() -> listOf(
                "1. 在桌面任意空白处长按",
                "2. 点击「小组件」或「Widgets」",
                "3. 找到「课表时间」应用",
                "4. 选择喜欢的小组件样式",
                "5. 长按拖动到桌面合适位置"
            )
            isHuaweiDevice() -> listOf(
                "1. 在桌面任意空白处长按",
                "2. 点击「服务卡片」或「窗口小工具」",
                "3. 找到「课表时间」应用",
                "4. 选择喜欢的小组件样式并点击添加",
                "5. 如提示空间不足，请先清理桌面或翻页"
            )
            manufacturer.contains("samsung") -> listOf(
                "1. 在桌面任意空白处长按",
                "2. 点击「小组件」(Widgets)",
                "3. 找到「课表时间」小组件",
                "4. 选择样式并拖到桌面"
            )
            isKnownProblematicOEM() -> getOEMGuideSteps()
            else -> listOf(
                "1. 在桌面任意空白处长按",
                "2. 找到「小组件」/「小部件」/「Widgets」入口",
                "3. 在列表中找到「课表时间」",
                "4. 长按选择的小组件",
                "5. 拖动到桌面合适位置后松手"
            )
        }
    }

    private fun getOEMGuideSteps(): List<String> {
        return when {
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                "1. 在桌面任意空白处长按",
                "2. 点击「组件」或「原子组件」",
                "3. 找到「课表时间」",
                "4. 选择小组件样式并添加到桌面",
                "5. 提示：vivo/iQOO 设备暂不支持一键添加"
            )
            else -> getManualGuideSteps()
        }
    }

    fun getWidgetDisplayName(widgetType: WidgetType): String {
        return widgetType.displayName
    }

    fun getWidgetDescription(widgetType: WidgetType): String {
        return when (widgetType) {
            WidgetType.TODAY_COURSE -> "4x2 尺寸，显示今日课程列表"
            WidgetType.NEXT_CLASS -> "3x2 尺寸，显示下节课倒计时"
            WidgetType.COMPACT_LIST -> "紧凑列表样式的小组件"
            WidgetType.WEEK_OVERVIEW -> "周概览样式的小组件"
            WidgetType.LARGE_TODAY_COURSE -> "4x4 大屏展示更多课程细节"
            WidgetType.TOMORROW_COURSE -> "3x2 智能切换今日/明日课程"
        }
    }

    fun getDeviceCompatibilityReport(): CompatibilityReport {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> CompatibilityReport(
                compatibilityLevel = CompatibilityLevel.HIGH,
                message = "完全支持小组件功能" + if (isKnownProblematicOEM()) "（需手动添加）" else ""
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> CompatibilityReport(
                compatibilityLevel = CompatibilityLevel.MEDIUM,
                message = "部分功能受限"
            )
            else -> CompatibilityReport(
                compatibilityLevel = CompatibilityLevel.LOW,
                message = "系统版本过低"
            )
        }
    }
}
