package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 超级智能课表提取器
 * 
 * 整合了所有教务系统的提取逻辑，能够智能识别并提取课表数据
 * 
 * 支持的教务系统：
 * - 树维教务系统 (SHUWEI)
 * - 强智教务系统 (QIANGZHI)
 * - 青果教务系统 (KINGOSOFT/QINGGUO)
 * - 正方教务系统 (ZHENGFANG)
 * - URP教务系统
 * - 金智教务系统 (JINZHI)
 * - 乘方教务系统 (CHENGFANG)
 * - 联亦科技系统 (LIANYI)
 * - 各类自研教务系统
 * 
 * 特性：
 * 1. 自动识别教务系统类型
 * 2. 多策略智能提取
 * 3. 容错能力强
 * 4. 支持多种数据格式（HTML表格、JavaScript变量、API JSON）
 * 
 * @author AI Assistant
 * @since 2025-11-05
 */
@Singleton
class UniversalSmartExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "universal_smart"
    override val schoolName = "通用智能提取器"
    override val systemType = "universal"
    
    companion object {
        private const val TAG = "UniversalSmartExtractor"
        
        // 教务系统类型枚举
        enum class SystemType {
            SHUWEI,          // 树维教务系统
            QIANGZHI,        // 强智教务系统
            KINGOSOFT,       // 青果教务系统
            ZHENGFANG,       // 正方教务系统
            URP,             // URP教务系统
            JINZHI,          // 金智教务系统
            CHENGFANG,       // 乘方教务系统
            LIANYI,          // 联亦科技
            CUSTOM,          // 自研系统
            UNKNOWN          // 未知系统
        }
        
        // 系统特征模式
        private val SYSTEM_PATTERNS = mapOf(
            SystemType.SHUWEI to listOf(
                "/eams/",
                "courseTableForStd",
                "activity = new TaskActivity",
                "var table = new TaskActivity"
            ),
            SystemType.QIANGZHI to listOf(
                "/jsxsd/xskb/xskb_list.do",
                "kbtable",
                "div.kbcontent",
                "强智"
            ),
            SystemType.KINGOSOFT to listOf(
                "青果",
                "kingosoft",
                "frmDesk",
                "wsxk.xskcb",
                "selGS"
            ),
            SystemType.ZHENGFANG to listOf(
                "zfsoft",
                "正方",
                "var kbList",
                "div.kcmc"
            ),
            SystemType.URP to listOf(
                "cas.urp.edu.cn",
                "urp",
                "datagridsearch"
            ),
            SystemType.JINZHI to listOf(
                "campusphere",
                "jinzhiapp",
                "金智"
            ),
            SystemType.CHENGFANG to listOf(
                "乘方",
                "chengfang"
            ),
            SystemType.LIANYI to listOf(
                "studentCourseSchedule",
                "ant-spin-container",
                "联亦"
            )
        )
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        // 通用检测：只要包含课表相关关键词就认为是课表页面
        return html.contains("课程表", ignoreCase = true) ||
               html.contains("课表", ignoreCase = true) ||
               html.contains("timetable", ignoreCase = true) ||
               html.contains("schedule", ignoreCase = true) ||
               html.contains("kbtable", ignoreCase = true) ||
               detectSystemType(html, url) != SystemType.UNKNOWN
    }
    
    override fun getLoginUrl(): String? = null
    override fun getScheduleUrl(): String? = null
    
    /**
     * 智能识别教务系统类型
     */
    fun detectSystemType(html: String, url: String): SystemType {
        Log.d(TAG, "🔍 开始智能识别教务系统类型...")
        
        // 记录每个系统的匹配分数
        val scores = mutableMapOf<SystemType, Int>()
        
        // 遍历所有系统特征，计算匹配分数
        SYSTEM_PATTERNS.forEach { (type, patterns) ->
            var score = 0
            patterns.forEach { pattern ->
                if (html.contains(pattern, ignoreCase = true) || 
                    url.contains(pattern, ignoreCase = true)) {
                    score += 1
                }
            }
            if (score > 0) {
                scores[type] = score
            }
        }
        
        // 找出得分最高的系统类型
        val detectedType = scores.maxByOrNull { it.value }?.key ?: SystemType.UNKNOWN
        
        if (detectedType != SystemType.UNKNOWN) {
            Log.d(TAG, "✅ 识别为: ${detectedType.name} 系统 (得分: ${scores[detectedType]})")
        } else {
            Log.d(TAG, "⚠️ 未能识别系统类型，将使用通用解析")
        }
        
        return detectedType
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🚀 超级智能提取器启动...');
                    
                    // ============================================
                    // 第一步：智能识别教务系统类型
                    // ============================================
                    function detectSystemType() {
                        var html = document.documentElement.outerHTML;
                        var url = window.location.href;
                        
                        // 树维系统特征
                        if (html.indexOf('activity = new TaskActivity') > -1 || 
                            url.indexOf('/eams/') > -1) {
                            return 'SHUWEI';
                        }
                        
                        // 强智系统特征
                        if (html.indexOf('kbtable') > -1 && 
                            (html.indexOf('kbcontent') > -1 || url.indexOf('/jsxsd/xskb/') > -1)) {
                            return 'QIANGZHI';
                        }
                        
                        // 青果系统特征
                        if (html.indexOf('frmDesk') > -1 || 
                            html.indexOf('selGS') > -1 || 
                            url.indexOf('wsxk.xskcb') > -1) {
                            return 'KINGOSOFT';
                        }
                        
                        // 正方系统特征
                        if (html.indexOf('var kbList') > -1 || 
                            html.indexOf('zfsoft') > -1 ||
                            html.indexOf('div.kcmc') > -1) {
                            return 'ZHENGFANG';
                        }
                        
                        // 联亦科技特征
                        if (html.indexOf('studentCourseSchedule') > -1 || 
                            html.indexOf('ant-spin-container') > -1) {
                            return 'LIANYI';
                        }
                        
                        return 'UNKNOWN';
                    }
                    
                    var systemType = detectSystemType();
                    console.log('📌 识别到系统类型: ' + systemType);
                    
                    // ============================================
                    // 第二步：根据系统类型选择提取策略
                    // ============================================
                    
                    // 通用辅助函数
                    ${generateHelperFunctions()}
                    
                    // 根据系统类型提取
                    var courses = [];
                    
                    if (systemType === 'SHUWEI') {
                        courses = ${generateShuweiExtraction()};
                    } else if (systemType === 'QIANGZHI') {
                        courses = ${generateQiangzhiExtraction()};
                    } else if (systemType === 'KINGOSOFT') {
                        courses = ${generateKingosoftExtraction()};
                    } else if (systemType === 'ZHENGFANG') {
                        courses = ${generateZhengfangExtraction()};
                    } else if (systemType === 'LIANYI') {
                        courses = ${generateLianyiExtraction()};
                    } else {
                        // 未知系统，使用通用提取
                        courses = ${generateGenericExtraction()};
                    }
                    
                    console.log('✅ 提取完成，共 ' + courses.length + ' 门课程');
                    return JSON.stringify({
                        courses: courses,
                        systemType: systemType
                    });
                    
                } catch (error) {
                    console.error('❌ 提取失败:', error);
                    return JSON.stringify({
                        courses: [],
                        error: '提取失败: ' + error.message
                    });
                }
            })();
        """.trimIndent()
    }
    
    /**
     * 生成通用辅助函数（JavaScript）
     */
    private fun generateHelperFunctions(): String {
        return """
            // ========== 通用辅助函数 ==========
            
            // 解析周次字符串
            function parseWeeks(str) {
                if (!str) return [];
                
                function range(start, end, step) {
                    var result = [];
                    if (step === 1 || step === 2) {
                        for (var i = start; i <= end; i++) {
                            if (i % step === 0 || step === 1) result.push(i);
                        }
                    } else { // 单周
                        for (var i = start; i <= end; i++) {
                            if (i % 2 !== 0) result.push(i);
                        }
                    }
                    return result;
                }
                
                str = str.replace(/[(){}|第\[\]]/g, '').replace(/到/g, '-');
                var weeks = [];
                var segments = [];
                
                while (str.search(/周|\s/) !== -1) {
                    var index = str.search(/周|\s/);
                    var segment = '';
                    if (str[index + 1] === '单' || str[index + 1] === '双') {
                        segment = str.slice(0, index + 2).replace(/周|\s/g, '');
                        index += 2;
                    } else {
                        segment = str.slice(0, index + 1).replace(/周|\s/g, '');
                        index += 1;
                    }
                    segments.push(segment);
                    str = str.slice(index);
                    index = str.search(/\d/);
                    if (index !== -1) str = str.slice(index);
                    else str = '';
                }
                if (str.length !== 0) segments.push(str);
                
                segments.forEach(function(seg) {
                    var parts = seg.replace(/单|双/g, '').split(',');
                    parts.forEach(function(part) {
                        var nums = part.split('-');
                        if (nums.length === 1) {
                            weeks.push(parseInt(nums[0]));
                        } else {
                            var start = parseInt(nums[0]);
                            var end = parseInt(nums[nums.length - 1]);
                            if (seg.indexOf('双') > -1) {
                                weeks.push.apply(weeks, range(start, end, 2));
                            } else if (seg.indexOf('单') > -1) {
                                weeks.push.apply(weeks, range(start, end, 3));
                            } else {
                                weeks.push.apply(weeks, range(start, end, 1));
                            }
                        }
                    });
                });
                
                return weeks.filter(function(v, i, a) { return a.indexOf(v) === i; }).sort(function(a, b) { return a - b; });
            }
            
            // 解析节次字符串
            function parseSections(str) {
                if (!str) return [];
                var sections = [];
                var nums = str.replace(/节|\[|\]/g, '').split('-');
                if (nums.length === 1) {
                    sections.push(parseInt(nums[0]));
                } else {
                    var start = parseInt(nums[0]);
                    var end = parseInt(nums[nums.length - 1]);
                    for (var i = start; i <= end; i++) {
                        sections.push(i);
                    }
                }
                return sections;
            }
            
            // 清理文本
            function cleanText(text) {
                return text ? text.replace(/\s+/g, ' ').replace(/\\n/g, '').trim() : '';
            }
            
            // 中文星期转数字
            function dayToNumber(dayStr) {
                var map = {'一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '七': 7, '日': 7};
                for (var key in map) {
                    if (dayStr.indexOf(key) > -1) return map[key];
                }
                return parseInt(dayStr) || 1;
            }
        """.trimIndent()
    }
    
    /**
     * 生成树维系统提取逻辑
     */
    private fun generateShuweiExtraction(): String {
        return """
            (function() {
                console.log('📦 使用树维系统提取器...');
                var courses = [];
                var html = document.documentElement.outerHTML;
                var blocks = html.split(/activity = new /);
                
                for (var i = 1; i < blocks.length; i++) {
                    try {
                        var block = blocks[i];
                        var match = block.match(/TaskActivity\((.*?)\);/);
                        if (!match) continue;
                        
                        var params = match[1].split('","').map(function(p) { 
                            return p.replace(/^"|"$/g, ''); 
                        });
                        
                        if (params.length < 7) continue;
                        
                        var dayMatch = block.match(/index\s*=\s*(\d+)\s*\*\s*unitCount/);
                        var day = dayMatch ? parseInt(dayMatch[1]) + 1 : 1;
                        
                        var sectionMatches = block.match(/unitCount\+(\d+);/g);
                        var sections = [];
                        if (sectionMatches) {
                            var sectionSet = {};
                            sectionMatches.forEach(function(m) {
                                var num = parseInt(m.match(/\d+/)[0]) + 1;
                                sectionSet[num] = true;
                            });
                            sections = Object.keys(sectionSet).map(Number).sort(function(a, b) { return a - b; });
                        }
                        
                        var weekStr = params[6] || '';
                        var weeks = [];
                        for (var w = 0; w < weekStr.length; w++) {
                            if (weekStr[w] == '1') weeks.push(w);
                        }
                        
                        courses.push({
                            courseName: cleanText(params[3]),
                            teacher: cleanText(params[1]),
                            classroom: cleanText(params[5]),
                            day: day,
                            startSection: sections[0] || 1,
                            sectionCount: sections.length || 2,
                            weeks: weeks
                        });
                    } catch (e) {
                        console.log('解析树维课程出错:', e);
                    }
                }
                return courses;
            })()
        """.trimIndent()
    }
    
    /**
     * 生成强智系统提取逻辑
     */
    private fun generateQiangzhiExtraction(): String {
        return """
            (function() {
                console.log('📦 使用强智系统提取器...');
                var courses = [];
                
                // 尝试从iframe获取
                var kbtableHtml = '';
                var iframes = document.getElementsByTagName('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var iframe = iframes[i];
                        if (iframe.src && iframe.src.indexOf('/jsxsd/xskb/') > -1) {
                            var doc = iframe.contentDocument || iframe.contentWindow.document;
                            var table = doc.getElementById('kbtable');
                            if (table) {
                                kbtableHtml = table.outerHTML;
                                break;
                            }
                        }
                    } catch (e) {}
                }
                
                if (!kbtableHtml) {
                    var table = document.getElementById('kbtable');
                    if (table) kbtableHtml = table.outerHTML;
                }
                
                if (!kbtableHtml) return courses;
                
                var parser = new DOMParser();
                var doc = parser.parseFromString(kbtableHtml, 'text/html');
                var rows = doc.querySelectorAll('tbody tr');
                
                rows.forEach(function(row, jcIndex) {
                    var cells = row.querySelectorAll('td');
                    cells.forEach(function(cell, dayIndex) {
                        var kbcontents = cell.querySelectorAll('div.kbcontent');
                        if (kbcontents.length === 0) return;
                        
                        kbcontents.forEach(function(content) {
                            try {
                                var html = content.innerHTML;
                                var parts = html.split(/<br>/i);
                                
                                if (parts.length >= 3) {
                                    var courseName = cleanText(parts[0].replace(/<.*?>/g, ''));
                                    var weeks = parseWeeks(parts[1]);
                                    var sections = parseSections(parts[2]);
                                    var teacher = parts.length > 3 ? cleanText(parts[3].replace(/<.*?>/g, '')) : '';
                                    var classroom = parts.length > 4 ? cleanText(parts[4].replace(/<.*?>/g, '')) : '';
                                    
                                    courses.push({
                                        courseName: courseName,
                                        teacher: teacher,
                                        classroom: classroom,
                                        day: dayIndex,
                                        startSection: sections[0] || (jcIndex * 2 + 1),
                                        sectionCount: sections.length || 2,
                                        weeks: weeks
                                    });
                                }
                            } catch (e) {
                                console.log('解析强智课程出错:', e);
                            }
                        });
                    });
                });
                
                return courses;
            })()
        """.trimIndent()
    }
    
    /**
     * 生成青果系统提取逻辑
     */
    private fun generateKingosoftExtraction(): String {
        return """
            (function() {
                console.log('📦 使用青果系统提取器...');
                var courses = [];
                var html = '';
                
                // 从frmDesk获取
                try {
                    if (window.frames && window.frames['frmDesk']) {
                        var frmDesk = window.frames['frmDesk'];
                        if (frmDesk.frames['frmReport']) {
                            var tables = frmDesk.frames['frmReport'].document.getElementsByTagName('table');
                            for (var i = 0; i < tables.length; i++) {
                                html += tables[i].outerHTML;
                            }
                        }
                    }
                } catch (e) {
                    console.log('从frmDesk获取失败:', e);
                }
                
                if (!html) {
                    var tables = document.getElementsByTagName('table');
                    for (var i = 0; i < tables.length; i++) {
                        html += tables[i].outerHTML;
                    }
                }
                
                if (!html) return courses;
                
                var parser = new DOMParser();
                var doc = parser.parseFromString(html, 'text/html');
                var rows = doc.querySelectorAll('tr');
                
                rows.forEach(function(row) {
                    var cells = row.querySelectorAll('td');
                    cells.forEach(function(cell, index) {
                        var text = cell.innerText || cell.textContent;
                        if (!text || text.length < 10) return;
                        
                        try {
                            var lines = text.split('\\n').filter(function(l) { return l.trim(); });
                            if (lines.length >= 3) {
                                var courseName = cleanText(lines[0]);
                                var weeks = parseWeeks(lines[1]);
                                var sections = parseSections(lines[2]);
                                var teacher = lines.length > 3 ? cleanText(lines[3]) : '';
                                var classroom = lines.length > 4 ? cleanText(lines[4]) : '';
                                
                                courses.push({
                                    courseName: courseName,
                                    teacher: teacher,
                                    classroom: classroom,
                                    day: (index % 7) || 1,
                                    startSection: sections[0] || 1,
                                    sectionCount: sections.length || 2,
                                    weeks: weeks
                                });
                            }
                        } catch (e) {
                            console.log('解析青果课程出错:', e);
                        }
                    });
                });
                
                return courses;
            })()
        """.trimIndent()
    }
    
    /**
     * 生成正方系统提取逻辑
     */
    private fun generateZhengfangExtraction(): String {
        return """
            (function() {
                console.log('📦 使用正方系统提取器...');
                var courses = [];
                
                // 尝试从JavaScript变量提取
                var html = document.documentElement.outerHTML;
                var match = html.match(/var kbList\s*=\s*(\[[\s\S]*?\]);/);
                if (match) {
                    try {
                        var kbList = eval(match[1]);
                        kbList.forEach(function(item) {
                            courses.push({
                                courseName: cleanText(item.kcmc || ''),
                                teacher: cleanText(item.xm || ''),
                                classroom: cleanText(item.cdmc || ''),
                                day: parseInt(item.xqj) || 1,
                                startSection: parseInt(item.jcs) || 1,
                                sectionCount: parseInt(item.jcs) || 2,
                                weeks: parseWeeks(item.zcd || '')
                            });
                        });
                        return courses;
                    } catch (e) {
                        console.log('从kbList解析失败:', e);
                    }
                }
                
                // 从HTML表格提取
                var table = document.getElementById('kbtable');
                if (!table) {
                    var tables = document.querySelectorAll('table');
                    for (var i = 0; i < tables.length; i++) {
                        if (tables[i].innerHTML.indexOf('kbcontent') > -1) {
                            table = tables[i];
                            break;
                        }
                    }
                }
                
                if (table) {
                    var rows = table.querySelectorAll('tr');
                    rows.forEach(function(row, rowIndex) {
                        var cells = row.querySelectorAll('td');
                        cells.forEach(function(cell, dayIndex) {
                            var kbcontents = cell.querySelectorAll('div.kbcontent');
                            kbcontents.forEach(function(content) {
                                try {
                                    var text = content.textContent || content.innerText;
                                    var lines = text.split('\\n').filter(function(l) { return l.trim(); });
                                    if (lines.length >= 3) {
                                        courses.push({
                                            courseName: cleanText(lines[0]),
                                            teacher: cleanText(lines.length > 3 ? lines[3] : ''),
                                            classroom: cleanText(lines.length > 2 ? lines[2] : ''),
                                            day: dayIndex || 1,
                                            startSection: (rowIndex - 1) * 2 + 1,
                                            sectionCount: 2,
                                            weeks: parseWeeks(lines.length > 1 ? lines[1] : '')
                                        });
                                    }
                                } catch (e) {}
                            });
                        });
                    });
                }
                
                return courses;
            })()
        """.trimIndent()
    }
    
    /**
     * 生成联亦科技系统提取逻辑
     */
    private fun generateLianyiExtraction(): String {
        return """
            (function() {
                console.log('📦 使用联亦科技提取器...');
                var courses = [];
                
                // 尝试从API获取
                var bodyText = document.getElementsByTagName("body")[0].outerText.replace(/\\n|\\s/g, "");
                var idMatch = bodyText.match(/(?<=学号:).*?(?=姓名)/);
                
                if (idMatch) {
                    // 这里需要异步获取，返回空数组，让后台处理
                    console.log('检测到API模式，需要后台处理');
                    return courses;
                }
                
                // 从HTML表格提取
                var container = document.getElementsByClassName("ant-spin-container")[0];
                if (container) {
                    var table = container.getElementsByTagName("table")[0];
                    if (table) {
                        var rows = table.querySelectorAll('tbody tr');
                        rows.forEach(function(row, rowIndex) {
                            var cells = row.querySelectorAll('td');
                            cells.forEach(function(cell, dayIndex) {
                                var text = cell.textContent || cell.innerText;
                                if (text && text.length > 10) {
                                    try {
                                        var lines = text.split('\\n').filter(function(l) { return l.trim(); });
                                        if (lines.length >= 3) {
                                            courses.push({
                                                courseName: cleanText(lines[0]),
                                                teacher: cleanText(lines.length > 1 ? lines[1] : ''),
                                                classroom: cleanText(lines.length > 2 ? lines[2] : ''),
                                                day: dayIndex || 1,
                                                startSection: rowIndex * 2 + 1,
                                                sectionCount: 2,
                                                weeks: parseWeeks(lines.length > 3 ? lines[3] : '')
                                            });
                                        }
                                    } catch (e) {}
                                }
                            });
                        });
                    }
                }
                
                return courses;
            })()
        """.trimIndent()
    }
    
    /**
     * 生成通用提取逻辑（兜底方案）
     */
    private fun generateGenericExtraction(): String {
        return """
            (function() {
                console.log('📦 使用通用提取器（兜底方案）...');
                var courses = [];
                
                // 策略1：查找所有表格，遍历单元格
                var tables = document.getElementsByTagName('table');
                for (var t = 0; t < tables.length; t++) {
                    var rows = tables[t].querySelectorAll('tr');
                    rows.forEach(function(row, rowIndex) {
                        if (rowIndex === 0) return; // 跳过表头
                        
                        var cells = row.querySelectorAll('td');
                        cells.forEach(function(cell, dayIndex) {
                            var text = (cell.textContent || cell.innerText || '').trim();
                            
                            // 判断是否像课程信息
                            if (text.length > 10 && text.length < 200) {
                                var lines = text.split('\\n').filter(function(l) { return l.trim(); });
                                
                                // 至少要有课程名
                                if (lines.length >= 1) {
                                    var courseName = cleanText(lines[0]);
                                    
                                    // 过滤掉明显不是课程的文本
                                    if (courseName.length < 2 || 
                                        courseName.indexOf('节次') > -1 ||
                                        courseName.indexOf('星期') > -1 ||
                                        courseName.indexOf('周次') > -1) {
                                        return;
                                    }
                                    
                                    var teacher = '';
                                    var classroom = '';
                                    var weekText = '';
                                    
                                    // 智能提取教师、教室、周次
                                    lines.forEach(function(line, i) {
                                        if (i === 0) return;
                                        line = cleanText(line);
                                        
                                        if (line.indexOf('周') > -1 || line.match(/\\d+-\\d+/)) {
                                            weekText = line;
                                        } else if (line.length < 10 && !classroom) {
                                            teacher = line;
                                        } else if (!classroom) {
                                            classroom = line;
                                        }
                                    });
                                    
                                    courses.push({
                                        courseName: courseName,
                                        teacher: teacher,
                                        classroom: classroom,
                                        day: (dayIndex % 7) || 1,
                                        startSection: Math.max(1, rowIndex * 2 - 1),
                                        sectionCount: 2,
                                        weeks: parseWeeks(weekText)
                                    });
                                }
                            }
                        });
                    });
                }
                
                return courses;
            })()
        """.trimIndent()
    }
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        try {
            Log.d(TAG, "开始解析课程数据...")
            
            // 清理 JSON 字符串
            val cleanJson = jsonData.trim()
                .removePrefix("\"").removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("\\r", "")
            
            val jsonObject = JSONObject(cleanJson)
            
            // 检查是否有错误
            if (jsonObject.has("error")) {
                val error = jsonObject.getString("error")
                Log.e(TAG, "提取失败: $error")
                throw Exception(error)
            }
            
            // 获取系统类型
            val systemType = jsonObject.optString("systemType", "UNKNOWN")
            Log.d(TAG, "系统类型: $systemType")
            
            // 获取课程数组
            val coursesArray = jsonObject.getJSONArray("courses")
            Log.d(TAG, "课程数量: ${coursesArray.length()}")
            
            for (i in 0 until coursesArray.length()) {
                val courseObj = coursesArray.getJSONObject(i)
                
                try {
                    // 提取课程名称
                    val courseName = courseObj.optString("courseName", "").trim()
                    if (courseName.isEmpty()) {
                        Log.w(TAG, "课程名称为空，跳过")
                        continue
                    }
                    
                    // 提取教师
                    val teacher = courseObj.optString("teacher", "").trim()
                    
                    // 提取教室
                    val classroom = courseObj.optString("classroom", "").trim()
                    
                    // 提取星期（day 或 dayOfWeek）
                    val dayOfWeek = courseObj.optInt("day", courseObj.optInt("dayOfWeek", 1))
                    if (dayOfWeek < 1 || dayOfWeek > 7) {
                        Log.w(TAG, "星期数无效: $dayOfWeek，跳过课程: $courseName")
                        continue
                    }
                    
                    // 提取开始节次
                    val startSection = courseObj.optInt("startSection", 1)
                    if (startSection < 1) {
                        Log.w(TAG, "开始节次无效: $startSection，跳过课程: $courseName")
                        continue
                    }
                    
                    // 提取节数
                    val sectionCount = courseObj.optInt("sectionCount", 2)
                    if (sectionCount < 1) {
                        Log.w(TAG, "节数无效: $sectionCount，跳过课程: $courseName")
                        continue
                    }
                    
                    // 提取周次
                    val weeksArray = courseObj.optJSONArray("weeks")
                    val weeks = mutableListOf<Int>()
                    if (weeksArray != null && weeksArray.length() > 0) {
                        for (j in 0 until weeksArray.length()) {
                            val week = weeksArray.optInt(j, 0)
                            if (week > 0) {
                                weeks.add(week)
                            }
                        }
                    }
                    
                    // 如果周次为空，使用默认值 1-16周
                    if (weeks.isEmpty()) {
                        Log.w(TAG, "周次为空，使用默认值 1-16周")
                        weeks.addAll(1..16)
                    }
                    
                    // 提取学分（可选）
                    val credit = courseObj.optDouble("credit", 0.0).toFloat()
                    
                    // 创建 ParsedCourse 对象
                    val parsedCourse = ParsedCourse(
                        courseName = courseName,
                        teacher = teacher,
                        classroom = classroom,
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        sectionCount = sectionCount,
                        weeks = weeks.sorted(),
                        credit = credit,
                        weekExpression = if (weeks.isNotEmpty()) {
                            "${weeks.min()}-${weeks.max()}周"
                        } else {
                            ""
                        }
                    )
                    
                    courses.add(parsedCourse)
                    Log.d(TAG, "✓ 解析课程: $courseName (星期$dayOfWeek, 第${startSection}节, ${weeks.size}周)")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "解析单个课程失败", e)
                    // 继续处理下一个课程
                }
            }
            
            Log.d(TAG, "✅ 解析完成，成功解析 ${courses.size} 门课程")
            
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e
        }
        
        return courses
    }
}

