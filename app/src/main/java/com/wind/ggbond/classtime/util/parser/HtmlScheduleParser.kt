package com.wind.ggbond.classtime.util.parser

import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.common.Constants
import com.wind.ggbond.classtime.util.parser.WeekParser
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTML课表解析器
 * 支持正方教务系统和强智系统的HTML课表解析
 */
@Singleton
class HtmlScheduleParser @Inject constructor() {
    
    /**
     * 解析强智系统的课表HTML
     */
    fun parseQiangzhiHtml(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        try {
            val doc = Jsoup.parse(html)
            
            // 强智系统的课表在 id="timetable" 的表格中
            val table = doc.select("#timetable").firstOrNull()
            if (table == null) {
                println("未找到课表表格（#timetable）")
                return emptyList()
            }
            
            println("找到强智系统课表表格，开始解析...")
            
            // 获取所有课程行（跳过表头和备注行）
            val rows = table.select("tbody tr").filter { row ->
                // 过滤掉备注行
                !row.text().contains("备注")
            }
            
            rows.forEachIndexed { rowIndex, row ->
                val cells = row.select("td")
                
                // 第一列是节次信息，跳过
                cells.drop(1).forEachIndexed { dayIndex, cell ->
                    val dayOfWeek = dayIndex + 1 // 1=周一, 2=周二...
                    
                    // 每个单元格可能包含多个课程
                    // 强智系统有两种结构：div.item-box 或 直接在td中
                    val itemBoxes = cell.select("div.item-box")
                    
                    if (itemBoxes.isNotEmpty()) {
                        // 使用 div.item-box 结构
                        itemBoxes.forEach { itemBox ->
                            parseQiangzhiCourse(itemBox, dayOfWeek)?.let { course ->
                                println("解析到课程: ${course.courseName}, 周${course.dayOfWeek}, 第${course.startSection}节")
                                courses.add(course)
                            }
                        }
                    }
                }
            }
            
            println("强智系统解析完成，共 ${courses.size} 门课程")
        } catch (e: Exception) {
            e.printStackTrace()
            println("强智系统解析异常: ${e.message}")
        }
        
        return courses
    }
    
    /**
     * 清理课程名称，移除学校名称前缀
     * 例如："重庆电力高等专科学校 高等数学" -> "高等数学"
     *      "重庆电力高等专科学校高等数学" -> "高等数学"
     */
    private fun cleanCourseName(rawName: String): String {
        var cleaned = rawName.trim()
        
        // 常见的学校名称后缀模式（按长度从长到短排序，优先匹配更长的模式）
        val schoolNamePatterns = listOf(
            Regex("""^[^\s]*高等专科学校\s*"""),
            Regex("""^[^\s]*职业技术大学\s*"""),
            Regex("""^[^\s]*职业技术学院\s*"""),
            Regex("""^[^\s]*专科学校\s*"""),
            Regex("""^[^\s]*职业大学\s*"""),
            Regex("""^[^\s]*大学\s*"""),
            Regex("""^[^\s]*学院\s*""")
        )
        
        // 尝试移除学校名称前缀
        for (pattern in schoolNamePatterns) {
            val beforeReplace = cleaned
            cleaned = cleaned.replace(pattern, "").trim()
            // 如果匹配成功并移除了内容，就不再尝试其他模式
            if (cleaned != beforeReplace && cleaned.isNotEmpty()) {
                break
            }
        }
        
        // 如果清理后为空，返回原始名称
        return if (cleaned.isEmpty()) rawName else cleaned
    }
    
    /**
     * 解析强智系统的单个课程
     */
    private fun parseQiangzhiCourse(itemBox: org.jsoup.nodes.Element, dayOfWeek: Int): ParsedCourse? {
        try {
            // 课程名称：第一个 <p> 标签
            var courseName = itemBox.select("p").firstOrNull()?.text()?.trim()
            if (courseName.isNullOrEmpty()) return null
            
            // 🔧 强化清理：移除课程名称中的多余信息（如节次、周次等）
            courseName = courseName
                .replace(Regex("\\(\\d+-\\d+节\\).*"), "")  // 移除(1-2节)及之后
                .replace(Regex("\\d+-\\d+周.*"), "")  // 移除周次信息
                .replace(Regex("[,;，；].*"), "")  // 移除逗号及之后
                .trim()
            
            // 清理课程名称，移除学校名称前缀
            courseName = cleanCourseName(courseName)
            
            // 教师、学分、节次在 div.tch-name 中的 span 标签里
            val tchNameDiv = itemBox.select("div.tch-name").firstOrNull()
            var teacher = ""
            var credit = 0f
            var startSection = 1
            var sectionCount = 2
            
            if (tchNameDiv != null) {
                val spans = tchNameDiv.select("span")
                spans.forEach { span ->
                    val text = span.text().trim()
                    when {
                        // 支持多种教师标识格式
                        text.startsWith("教师：") || text.startsWith("教师:") || 
                        text.startsWith("教师 ：") || text.startsWith("教师 :") ||
                        text.startsWith("任课教师：") || text.startsWith("任课教师:") ||
                        text.startsWith("授课教师：") || text.startsWith("授课教师:") -> {
                            teacher = text
                                .replace(Regex("^(教师|任课教师|授课教师)\\s*[：:]"), "")
                                .split("教学班")[0]  // 如果包含教学班信息，只取前面部分
                                .trim()
                        }
                        text.startsWith("学分：") || text.startsWith("学分:") -> {
                            val creditStr = text.replace(Regex("^学分[：:]"), "").trim()
                            credit = creditStr.toFloatOrNull() ?: 0f
                        }
                        text.contains("~") && text.contains("节") -> {
                            // 🔧 解析节次，如 "01~02节" 或 "03~04节"
                            val numbers = Regex("""\d+""").findAll(text)
                                .map { it.value.toIntOrNull() ?: 0 }
                                .filter { it > 0 }
                                .toList()
                            
                            if (numbers.isNotEmpty()) {
                                startSection = numbers.first()
                                sectionCount = numbers.last() - numbers.first() + 1
                            }
                        }
                        // 🔧 也尝试从 "1-2节" 或 "(1-2节)" 格式解析
                        text.matches(Regex(".*\\(?\\d+-\\d+节\\)?.*")) -> {
                            val match = Regex("""(\d+)-(\d+)节""").find(text)
                            if (match != null) {
                                startSection = match.groupValues[1].toIntOrNull() ?: 1
                                val endSection = match.groupValues[2].toIntOrNull() ?: startSection
                                sectionCount = endSection - startSection + 1
                            }
                        }
                    }
                }
                
                // 兜底逻辑：如果没有找到标准格式的教师信息，尝试智能识别
                if (teacher.isEmpty()) {
                    val fullText = tchNameDiv.text()
                    // 尝试匹配2-4个连续的中文字符（可能是教师姓名）
                    val teacherPattern = Regex("""([\u4e00-\u9fa5]{2,4})""")
                    val matches = teacherPattern.findAll(fullText)
                    val excludedWords = listOf("教师", "学分", "节次", "上课", "地点", "考查", "考试", "理论", "实践")
                    
                    for (match in matches) {
                        val candidate = match.value
                        // 如果不在排除列表中，可能是教师名
                        if (!excludedWords.any { candidate.contains(it) }) {
                            teacher = candidate
                            break
                        }
                    }
                }
            }
            
            // 教室和周次在第二个 div 中
            var classroom = ""
            var weekExpression = ""
            var weeks = emptyList<Int>()
            
            val detailDivs = itemBox.select("div").filter { div ->
                div.select("img").isNotEmpty() && !div.hasClass("tch-name")
            }
            
            detailDivs.forEach { div ->
                val spans = div.select("span")
                spans.forEach { span ->
                    val img = span.select("img").firstOrNull()
                    val imgSrc = img?.attr("src") ?: ""
                    val text = span.text().trim()
                    
                    when {
                        imgSrc.contains("item1.png") -> {
                            // 教室信息
                            classroom = text
                        }
                        imgSrc.contains("item3.png") -> {
                            // 周次信息，如 "第9周(全部) 星期一" 或 "第2-4,6-19周(全部) 星期二"
                            weekExpression = text
                            weeks = parseQiangzhiWeekExpression(text)
                        }
                    }
                }
            }
            
            return ParsedCourse(
                courseName = courseName,
                teacher = teacher,
                classroom = classroom,
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                sectionCount = sectionCount,
                weekExpression = weekExpression,
                weeks = weeks,
                credit = credit
            )
        } catch (e: Exception) {
            e.printStackTrace()
            println("解析强智课程失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 解析强智系统的周次表达式
     * 例如：
     * - "第9周(全部) 星期一" -> [9]
     * - "第2-4,6-19周(全部) 星期二" -> [2,3,4,6,7,8...19]
     * - "第1-16周(单周) 星期三" -> [1,3,5,7...15]
     * - "第1-16周(双周) 星期四" -> [2,4,6,8...16]
     */
    private fun parseQiangzhiWeekExpression(expression: String): List<Int> {
        val weeks = mutableListOf<Int>()
        
        try {
            // 提取周次部分，如 "第9周" 或 "第2-4,6-19周"
            val weekPattern = Regex("""第([0-9\-,]+)周""")
            val weekMatch = weekPattern.find(expression)
            
            if (weekMatch != null) {
                val weekStr = weekMatch.groupValues[1]
                
                // 判断是全部、单周还是双周
                val isOddWeek = expression.contains("(单周)")
                val isEvenWeek = expression.contains("(双周)")
                
                // 分割多个范围，如 "2-4,6-19"
                val ranges = weekStr.split(",")
                
                ranges.forEach { range ->
                    if (range.contains("-")) {
                        // 范围，如 "2-4" 或 "6-19"
                        val parts = range.split("-")
                        if (parts.size == 2) {
                            val start = parts[0].toIntOrNull() ?: return@forEach
                            val end = parts[1].toIntOrNull() ?: return@forEach
                            
                            for (week in start..end) {
                                when {
                                    isOddWeek && week % 2 == 1 -> weeks.add(week)
                                    isEvenWeek && week % 2 == 0 -> weeks.add(week)
                                    !isOddWeek && !isEvenWeek -> weeks.add(week)
                                }
                            }
                        }
                    } else {
                        // 单个周次，如 "9"
                        val week = range.toIntOrNull()
                        if (week != null) {
                            weeks.add(week)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("解析周次表达式失败: $expression, ${e.message}")
        }
        
        return weeks.distinct().sorted()
    }
    
    /**
     * 解析正方系统的课表HTML
     */
    fun parseZhengfangHtml(html: String): List<ParsedCourse> {
        var courses = mutableListOf<ParsedCourse>()
        
        try {
            // 方法1：尝试从JavaScript变量中提取课表数据（正方系统常用）⭐
            courses.addAll(parseFromJavaScriptVariable(html))
            if (courses.isNotEmpty()) {
                println("从JavaScript变量解析到 ${courses.size} 门课程")
                return courses
            }
            
            // 方法2：从HTML表格解析
            val doc = Jsoup.parse(html)
            
            // 尝试多种选择器策略
            var table = doc.select("#kbtable table").firstOrNull()
            
            if (table == null) {
                // 尝试直接查找 id="kbtable"
                table = doc.select("#kbtable").firstOrNull()
            }
            
            if (table == null) {
                // 尝试查找包含 kbcontent 的表格
                table = doc.select("table").firstOrNull { 
                    it.select("div.kbcontent").isNotEmpty()
                }
            }
            
            if (table == null) {
                // 最后尝试：查找任何包含课程信息的表格
                table = doc.select("table").firstOrNull()
            }
            
            if (table == null) {
                println("未找到课表表格")
                return emptyList()
            }
            
            println("找到课表表格，开始解析...")
            
            // 遍历所有行
            table.select("tr").forEachIndexed { rowIndex, row ->
                // 跳过表头行
                if (rowIndex == 0) return@forEachIndexed
                
                // 遍历所有列（每列代表一天）
                row.select("td").forEachIndexed { colIndex, cell ->
                    // 跳过第一列（节次列）
                    if (colIndex == 0) return@forEachIndexed
                    
                    // 提取课程内容
                    parseCourseCell(cell.html(), colIndex, rowIndex)?.let { course ->
                        println("解析到课程: ${course.courseName}")
                        courses.add(course)
                    }
                }
            }
            
            println("总共解析到 ${courses.size} 门课程")
        } catch (e: Exception) {
            e.printStackTrace()
            println("解析异常: ${e.message}")
        }
        
        return courses
    }
    
    /**
     * 从JavaScript变量中提取课表数据
     * 正方系统通常将课表数据存储在 kbList 或类似的JavaScript变量中
     */
    private fun parseFromJavaScriptVariable(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        try {
            // 查找 var kbList = [...] 或类似的JavaScript数组定义
            val kbListPattern = Regex("""var\s+kbList\s*=\s*(\[[^;]+\])""", RegexOption.DOT_MATCHES_ALL)
            val match = kbListPattern.find(html)
            
            if (match != null) {
                val jsonArray = match.groupValues[1]
                println("找到kbList数据: ${jsonArray.take(200)}...")
                
                // 这里需要解析JSON数组
                // 由于结构可能复杂，我们先打印出来查看
                android.util.Log.d("HTMLParser", "kbList内容: $jsonArray")
            } else {
                println("未找到kbList变量")
            }
            
            // 尝试查找其他可能的变量名
            val patterns = listOf(
                Regex("""var\s+courseList\s*=\s*(\[[^;]+\])""", RegexOption.DOT_MATCHES_ALL),
                Regex("""var\s+scheduleData\s*=\s*(\[[^;]+\])""", RegexOption.DOT_MATCHES_ALL),
                Regex("""var\s+kbData\s*=\s*(\[[^;]+\])""", RegexOption.DOT_MATCHES_ALL)
            )
            
            patterns.forEach { pattern ->
                val m = pattern.find(html)
                if (m != null) {
                    println("找到课表数据变量")
                    android.util.Log.d("HTMLParser", "课表数据: ${m.groupValues[1].take(500)}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return courses
    }
    
    /**
     * 解析单个课程单元格
     */
    private fun parseCourseCell(cellHtml: String, dayOfWeek: Int, rowIndex: Int): ParsedCourse? {
        try {
            val cell = Jsoup.parse(cellHtml)
            
            // 方式1：尝试标准的 div.kbcontent 结构
            var courseDiv = cell.select("div.kbcontent").firstOrNull()
            
            if (courseDiv != null) {
                // 提取课程名称
                var courseName = courseDiv.select("div.kcmc").text().trim()
                if (courseName.isEmpty()) {
                    // 尝试其他可能的类名
                    courseName = courseDiv.select(".kcmc").text().trim()
                }
                if (courseName.isEmpty()) return null
                
                // 清理课程名称，移除学校名称前缀
                courseName = cleanCourseName(courseName)
                
                // 提取教师
                var teacher = courseDiv.select("div.jshi").text().trim()
                if (teacher.isEmpty()) {
                    teacher = courseDiv.select(".jshi").text().trim()
                }
                
                // 提取教室
                var classroom = courseDiv.select("div.jxdd").text().trim()
                if (classroom.isEmpty()) {
                    classroom = courseDiv.select(".jxdd").text().trim()
                }
                
                // 提取周次信息
                var weekText = courseDiv.select("div.zcd").text().trim()
                if (weekText.isEmpty()) {
                    weekText = courseDiv.select(".zcd").text().trim()
                }
                
                // 计算开始节次（限制在有效范围内）
                val startSection = (rowIndex - 1) * 2 + 1
                
                // 检查节次是否超出范围
                if (startSection > Constants.Course.MAX_SECTION_NUMBER) {
                    println("⚠️ 节次超出范围: rowIndex=$rowIndex, startSection=$startSection，跳过该课程: $courseName")
                    return null
                }
                
                // 解析周次
                val weeks = WeekParser.parseWeekExpression(weekText)
                
                return ParsedCourse(
                    courseName = courseName,
                    teacher = teacher,
                    classroom = classroom,
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    sectionCount = 2,
                    weeks = weeks
                )
            }
            
            // 方式2：尝试直接从文本解析（兼容模式）
            val text = cell.text().trim()
            if (text.isNotEmpty() && text.length > 2) {
                // 简单的文本模式，按行分割
                val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isNotEmpty()) {
                    var courseName = lines.firstOrNull() ?: return null
                    // 清理课程名称，移除学校名称前缀
                    courseName = cleanCourseName(courseName)
                    val teacher = lines.getOrNull(1) ?: ""
                    val classroom = lines.getOrNull(2) ?: ""
                    val weekText = lines.getOrNull(3) ?: ""
                    
                    // 计算开始节次并检查范围
                    val startSection = (rowIndex - 1) * 2 + 1
                    if (startSection > Constants.Course.MAX_SECTION_NUMBER) {
                        println("⚠️ 节次超出范围: rowIndex=$rowIndex, startSection=$startSection，跳过该课程: $courseName")
                        return null
                    }
                    
                    return ParsedCourse(
                        courseName = courseName,
                        teacher = teacher,
                        classroom = classroom,
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        sectionCount = 2,
                        weeks = WeekParser.parseWeekExpression(weekText)
                    )
                }
            }
            
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 检测HTML是否包含课表数据
     */
    fun isScheduleHtml(html: String): Boolean {
        try {
            val doc = Jsoup.parse(html)
            
            // 检查常见的课表标识
            return doc.select("#kbtable").isNotEmpty() ||
                   doc.select("div.kbcontent").isNotEmpty() ||
                   doc.select("div.kcmc").isNotEmpty() ||
                   html.contains("课程表", ignoreCase = true)
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * 自动检测HTML类型并解析
     * 会依次尝试强智系统和正方系统的解析方法
     */
    fun parseHtmlAuto(html: String): List<ParsedCourse> {
        println("开始自动检测HTML类型...")
        
        try {
            val doc = Jsoup.parse(html)
            
            // 1. 检测强智系统特征
            val hasQiangzhiFeatures = 
                doc.select("#timetable").isNotEmpty() ||
                doc.select("div.item-box").isNotEmpty() ||
                doc.select("div.tch-name").isNotEmpty() ||
                html.contains("qiangzhi", ignoreCase = true) ||
                html.contains("强智", ignoreCase = true)
            
            if (hasQiangzhiFeatures) {
                println("检测到强智系统特征，尝试强智解析...")
                val courses = parseQiangzhiHtml(html)
                if (courses.isNotEmpty()) {
                    println("强智系统解析成功，共 ${courses.size} 门课程")
                    return courses
                }
            }
            
            // 2. 检测正方系统特征
            val hasZhengfangFeatures =
                doc.select("#kbtable").isNotEmpty() ||
                doc.select("div.kbcontent").isNotEmpty() ||
                doc.select("div.kcmc").isNotEmpty() ||
                html.contains("zfsoft", ignoreCase = true) ||
                html.contains("正方", ignoreCase = true) ||
                html.contains("var kbList", ignoreCase = false)
            
            if (hasZhengfangFeatures) {
                println("检测到正方系统特征，尝试正方解析...")
                val courses = parseZhengfangHtml(html)
                if (courses.isNotEmpty()) {
                    println("正方系统解析成功，共 ${courses.size} 门课程")
                    return courses
                }
            }
            
            // 3. 如果没有明确特征，先尝试强智再尝试正方
            println("未检测到明确系统特征，依次尝试解析...")
            
            var courses = parseQiangzhiHtml(html)
            if (courses.isNotEmpty()) {
                println("强智系统解析成功（通用尝试），共 ${courses.size} 门课程")
                return courses
            }
            
            courses = parseZhengfangHtml(html)
            if (courses.isNotEmpty()) {
                println("正方系统解析成功（通用尝试），共 ${courses.size} 门课程")
                return courses
            }
            
            // 4. 尝试通用表格解析
            println("尝试通用表格解析...")
            courses = parseGenericTableHtml(html)
            if (courses.isNotEmpty()) {
                println("通用表格解析成功，共 ${courses.size} 门课程")
                return courses
            }
            
            println("所有解析方法均未成功")
            return emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            println("自动解析异常: ${e.message}")
            return emptyList()
        }
    }
    
    /**
     * 通用表格解析
     * 适用于简单的课表格式
     */
    private fun parseGenericTableHtml(html: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        try {
            val doc = Jsoup.parse(html)
            
            // 查找所有表格
            val tables = doc.select("table")
            
            for (table in tables) {
                val rows = table.select("tr")
                
                // 跳过表头，从第二行开始
                rows.drop(1).forEachIndexed { rowIndex, row ->
                    val cells = row.select("td, th")
                    
                    // 跳过第一列（通常是节次）
                    cells.drop(1).forEachIndexed { colIndex, cell ->
                        val text = cell.text().trim()
                        
                        // 如果单元格有内容且不是空白，尝试解析
                        if (text.isNotEmpty() && text.length > 2) {
                            // 简单的课程名提取
                            val lines = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
                            if (lines.isNotEmpty()) {
                                var courseName = lines[0]
                                // 清理课程名称，移除学校名称前缀
                                courseName = cleanCourseName(courseName)
                                
                                // 尝试提取其他信息
                                var teacher = ""
                                var classroom = ""
                                
                                for (line in lines.drop(1)) {
                                    when {
                                        line.contains("老师") || line.contains("教师") -> teacher = line
                                        line.contains("教室") || line.matches(Regex(".*[A-Z]\\d+.*")) -> classroom = line
                                    }
                                }
                                
                                // 计算开始节次并检查范围
                                val startSection = rowIndex * 2 + 1
                                if (startSection <= Constants.Course.MAX_SECTION_NUMBER) {
                                courses.add(
                                    ParsedCourse(
                                        courseName = courseName,
                                        teacher = teacher,
                                        classroom = classroom,
                                        dayOfWeek = colIndex + 1,
                                            startSection = startSection,
                                        sectionCount = 2,
                                        weeks = (1..16).toList() // 默认1-16周
                                    )
                                )
                                } else {
                                    println("⚠️ 节次超出范围: rowIndex=$rowIndex, startSection=$startSection，跳过该课程: $courseName")
                                }
                            }
                        }
                    }
                }
                
                // 如果从这个表格解析到了课程，就返回
                if (courses.isNotEmpty()) {
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return courses
    }
}
