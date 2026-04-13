package com.wind.ggbond.classtime.service.helper

object ScheduleFieldParser {

    private val DAY_MAP = mapOf(
        "周一" to 1, "星期一" to 1, "Monday" to 1, "一" to 1,
        "周二" to 2, "星期二" to 2, "Tuesday" to 2, "二" to 2,
        "周三" to 3, "星期三" to 3, "Wednesday" to 3, "三" to 3,
        "周四" to 4, "星期四" to 4, "Thursday" to 4, "四" to 4,
        "周五" to 5, "星期五" to 5, "Friday" to 5, "五" to 5,
        "周六" to 6, "星期六" to 6, "Saturday" to 6, "六" to 6,
        "周日" to 7, "星期日" to 7, "Sunday" to 7, "日" to 7,
    )

    fun parseDayOfWeek(value: String): Int {
        val trimmed = value.trim()
        DAY_MAP[trimmed]?.let { return it }
        val numMatch = Regex("\\d+").find(trimmed)
        val num = numMatch?.value?.toIntOrNull() ?: return 0
        return if (num in 1..7) num else 0
    }

    fun parseSectionInfo(value: String): Pair<Int, Int> {
        val trimmed = value.trim()
        val rangeMatch = Regex("(\\d+)\\s*[-–—]\\s*(\\d+)").find(trimmed)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toIntOrNull() ?: 1
            val end = rangeMatch.groupValues[2].toIntOrNull() ?: start
            return Pair(start, end - start + 1)
        }
        val singleMatch = Regex("(\\d+)").find(trimmed)
        if (singleMatch != null) {
            val start = singleMatch.groupValues[1].toIntOrNull() ?: 1
            return Pair(start, 1)
        }
        return Pair(1, 1)
    }

    fun parseWeekInfo(value: String): Pair<List<Int>, String> {
        if (value.isBlank()) return Pair(emptyList(), "")
        val trimmed = value.trim()
        val weeks = mutableSetOf<Int>()

        val rangePattern = Regex("(\\d+)\\s*[-–—]\\s*(\\d+)\\s*周?\\s*(?:\\(([单双])\\))?")
        rangePattern.findAll(trimmed).forEach { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@forEach
            val end = match.groupValues[2].toIntOrNull() ?: return@forEach
            val oddEven = match.groupValues.getOrNull(3)
            (start..end).filter { w ->
                when (oddEven) {
                    "单" -> w % 2 == 1
                    "双" -> w % 2 == 0
                    else -> true
                }
            }.forEach { weeks.add(it) }
        }

        if (weeks.isEmpty()) {
            val rangeNoWeek = Regex("(\\d+)\\s*[-–—]\\s*(\\d+)")
            rangeNoWeek.find(trimmed)?.let { match ->
                val start = match.groupValues[1].toIntOrNull() ?: return@let
                val end = match.groupValues[2].toIntOrNull() ?: return@let
                if (start in 1..30 && end in 1..30 && end >= start) {
                    (start..end).forEach { weeks.add(it) }
                }
            }
        }

        if (weeks.isEmpty()) {
            Regex("(\\d+)").findAll(trimmed).mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in 1..30 }.forEach { weeks.add(it) }
        }

        val sortedWeeks = weeks.sorted()
        val weekExpr = if (sortedWeeks.isNotEmpty()) formatWeekExpression(sortedWeeks) else trimmed
        return Pair(sortedWeeks, weekExpr)
    }

    fun formatWeekExpression(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val ranges = mutableListOf<String>()
        var rs = weeks[0]
        var re = weeks[0]
        for (i in 1 until weeks.size) {
            if (weeks[i] == re + 1) {
                re = weeks[i]
            } else {
                ranges.add(if (rs == re) "$rs" else "$rs-$re")
                rs = weeks[i]
                re = weeks[i]
            }
        }
        ranges.add(if (rs == re) "$rs" else "$rs-$re")
        return ranges.joinToString(",") + "周"
    }
}
