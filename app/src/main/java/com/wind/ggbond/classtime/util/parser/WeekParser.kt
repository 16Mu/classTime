package com.wind.ggbond.classtime.util.parser

/**
 * 周次表达式解析器
 * 支持格式：
 * - "1-16" 或 "1-16周" -> [1,2,3,...,16]
 * - "1-16单周" -> [1,3,5,...,15]
 * - "1-16双周" -> [2,4,6,...,16]
 * - "1,3,5,7" -> [1,3,5,7]
 * - "1-4,6,8-10" -> [1,2,3,4,6,8,9,10]
 */
object WeekParser {
    
    /**
     * 解析周次表达式为周次列表
     * 支持复杂格式：如 "1-3周(单),4-6周(双),7-15周,19周" 或 "(7-8节)1-3周(单),4-6周(双),7-15周,19周"
     */
    fun parseWeekExpression(expression: String): List<Int> {
        if (expression.isBlank()) return emptyList()
        
        val result = mutableSetOf<Int>()
        
        // Debug日志
        android.util.Log.d("WeekParser", "原始表达式: $expression")
        
        // 第一步：先按逗号分割（保留单双周标记）
        val segments = expression
            .split(Regex("[,，]"))  // 只按逗号分割
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        for (segment in segments) {
            // 第二步：处理每个段
            // 去除节次信息（支持全角和半角括号）
            var withoutSection = segment
                .replace(Regex("\\([0-9]+-[0-9]+节\\)"), "")  // 半角括号 (1-2节)
                .replace(Regex("（[0-9]+-[0-9]+节）"), "")  // 全角括号 （1-2节）
                .replace(Regex("\\([0-9]+-[0-9]+\\)"), "")  // 半角括号无"节"字 (1-2)
                .replace(Regex("（[0-9]+-[0-9]+）"), "")   // 全角括号无"节"字 （1-2）
                .trim()
            
            // 检查是否有单双周标记
            val isOddWeek = withoutSection.contains("(单)") || withoutSection.contains("（单）") || 
                           withoutSection.matches(Regex(".*[0-9]+单.*"))
            val isEvenWeek = withoutSection.contains("(双)") || withoutSection.contains("（双）") || 
                            withoutSection.matches(Regex(".*[0-9]+双.*"))
            
            // Debug日志
            android.util.Log.d("WeekParser", "  段落: $segment -> 去节次后: $withoutSection")
            
            // 第三步：提取数字部分（去除所有非数字非连字符的字符）
            val numberPart = withoutSection
                .replace(Regex("[^0-9-]"), " ")  // 将非数字非连字符替换为空格
                .trim()
                .split(Regex("\\s+"))  // 按空格分割，获取所有数字段
                .firstOrNull { it.isNotBlank() }  // 取第一个非空的数字段
            
            android.util.Log.d("WeekParser", "  数字部分: $numberPart, 单周: $isOddWeek, 双周: $isEvenWeek")
            
            if (numberPart.isNullOrBlank()) continue
            
            // 第四步：解析数字段
            if (numberPart.contains("-")) {
                // 范围表达式 例如 "1-3" 或 "7-15"
                val parts = numberPart.split("-")
                if (parts.size == 2) {
                    try {
                        val start = parts[0].trim().toInt()
                        val end = parts[1].trim().toInt()
                        
                        for (week in start..end) {
                            when {
                                isOddWeek && week % 2 == 1 -> result.add(week)
                                isEvenWeek && week % 2 == 0 -> result.add(week)
                                !isOddWeek && !isEvenWeek -> result.add(week)
                            }
                        }
                    } catch (e: NumberFormatException) {
                        android.util.Log.w("WeekParser", "周次范围解析失败: $numberPart", e)
                    }
                }
            } else {
                // 单个数字
                try {
                    val week = numberPart.toInt()
                    result.add(week)
                } catch (e: NumberFormatException) {
                    android.util.Log.w("WeekParser", "周次数字解析失败: $numberPart", e)
                }
            }
        }
        
        val sorted = result.sorted()
        android.util.Log.d("WeekParser", "解析结果: $sorted (共${sorted.size}周)")
        
        return sorted
    }
    
    /**
     * 将周次列表转换为表达式字符串（用于显示）
     */
    fun formatWeekList(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        
        val sortedWeeks = weeks.sorted()
        
        // 检查是否为连续的单周或双周
        val isOddWeek = sortedWeeks.all { it % 2 == 1 } && sortedWeeks.size > 1
        val isEvenWeek = sortedWeeks.all { it % 2 == 0 } && sortedWeeks.size > 1
        
        // 查找连续区间
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = sortedWeeks[0]
        var end = sortedWeeks[0]
        
        for (i in 1 until sortedWeeks.size) {
            val step = if (isOddWeek || isEvenWeek) 2 else 1
            if (sortedWeeks[i] == end + step) {
                end = sortedWeeks[i]
            } else {
                ranges.add(start to end)
                start = sortedWeeks[i]
                end = sortedWeeks[i]
            }
        }
        ranges.add(start to end)
        
        // 格式化输出
        val parts = ranges.map { (s, e) ->
            if (s == e) {
                "$s"
            } else {
                "$s-$e"
            }
        }
        
        val suffix = when {
            isOddWeek -> "单周"
            isEvenWeek -> "双周"
            else -> "周"
        }
        
        return parts.joinToString(",") + suffix
    }
}
