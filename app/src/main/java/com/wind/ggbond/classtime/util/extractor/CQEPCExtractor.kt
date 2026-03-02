package com.wind.ggbond.classtime.util.extractor

import android.util.Log
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.WeekParser
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 重庆电力高等专科学校课表提取器
 * 
 * 学校：重庆电力高等专科学校
 * 系统：正方教务系统
 * 
 * API说明：
 * - 端点：xskbcx_cxXsgrkb.html（个人课表API）
 * - 数据源：kbList（完整课表数据，包含节次和周次信息）
 * - 周次格式支持：
 *   1. 单个周：7周、16周
 *   2. 周次范围：1-4周、6-15周
 *   3. 单双周：1-3周(单)、6-14周(双)
 *   4. 复杂组合：1-4周,6-8周(双),9-15周,19周
 */
@Singleton
class CQEPCExtractor @Inject constructor() : SchoolScheduleExtractor {
    
    override val schoolId = "cqepc"
    override val schoolName = "重庆电力高等专科学校"
    override val systemType = "zfsoft"
    
    companion object {
        private const val TAG = "CQEPCExtractor"
    }
    
    override fun isSchedulePage(html: String, url: String): Boolean {
        // 检测正方系统课表页面特征
        return html.contains("kbgrid_table_0") || 
               html.contains("kblist_table") ||
               html.contains("xskbcx") ||
               url.contains("xskbcx")
    }
    
    override fun getLoginUrl(): String {
        // CAS单点登录，登录成功后直接跳转到课表（CAS会自动添加ticket参数）
        return "http://cas.cqepc.com.cn/cas/login?service=https%3A%2F%2Fjwxt.cqepc.edu.cn%2Fjwglxt%2Fkbcx%2Fxskbcx_cxXskbcxIndex.html%3Fgnmkdm%3DN2151%26layout%3Ddefault"
    }
    
    override fun getScheduleUrl(): String {
        return "https://jwxt.cqepc.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"
    }
    
    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取重庆电力高等专科学校课表（个人课表API方式）...');
                    
                    // 1. 获取当前学年学期
                    var xnm = '2025';  // 学年名（默认2025）
                    var xqm = '3';     // 学期名（3=第一学期）
                    
                    // 尝试从页面提取学年学期
                    var xnxqInput = document.querySelector('#xnxqid');
                    if (xnxqInput && xnxqInput.value) {
                        var parts = xnxqInput.value.split('-');
                        if (parts.length === 2) {
                            xnm = parts[0];
                            xqm = parts[1];
                            console.log('✓ 从页面提取学年学期: ' + xnm + '-' + xqm);
                        }
                    } else {
                        console.log('⚠️ 未找到学年学期选择器，使用默认值: ' + xnm + '-' + xqm);
                    }
                    
                    // 2. 发送POST请求到个人课表API（正确的API端点）
                    var xhr = new XMLHttpRequest();
                    xhr.open('POST', 'https://jwxt.cqepc.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151', false);
                    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
                    xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
                    
                    // 完整的请求参数（包含kzlx=ck和xsdm=）
                    var params = 'xnm=' + xnm + '&xqm=' + xqm + '&kzlx=ck&xsdm=';
                    console.log('发送请求参数: ' + params);
                    xhr.send(params);
                    
                    if (xhr.status !== 200) {
                        throw new Error('API请求失败: HTTP ' + xhr.status);
                    }
                    
                    console.log('✓ API响应成功');
                    
                    // 3. 解析返回的JSON
                    var apiData = JSON.parse(xhr.responseText);
                    console.log('API返回数据结构: ', Object.keys(apiData));
                    
                    // 4. 提取课程列表（使用kbList，数据更完整）
                    var kbList = apiData.kbList || [];
                    console.log('找到 ' + kbList.length + ' 条课程记录');
                    
                    var courses = [];
                    
                    // 打印完整的第一条数据用于调试（仅控制台）
                    if (kbList.length > 0) {
                        console.log('第一条完整数据: ' + JSON.stringify(kbList[0], null, 2));
                    }
                    
                    for (var i = 0; i < kbList.length; i++) {
                        var item = kbList[i];
                        
                        var courseName = item.kcmc || '';      // 课程名称
                        var teacher = item.xm || '';           // 教师姓名
                        var classroom = item.cdmc || '';       // 教室名称
                        var weekExpression = item.zcd || '';   // 周次（如"1-4周,6-15周,19周"）
                        var dayOfWeek = parseInt(item.xqj) || 1;  // 星期几
                        
                        // 解析节次（jcs格式："1-2"、"3-4"等）
                        var jcs = item.jcs || '';
                        var startSection = 1;
                        var sectionCount = 2;
                        
                        if (jcs) {
                            var jcsParts = jcs.split('-');
                            if (jcsParts.length === 2) {
                                startSection = parseInt(jcsParts[0]) || 1;
                                var endSection = parseInt(jcsParts[1]) || startSection + 1;
                                sectionCount = endSection - startSection + 1;
                            }
                        }
                        
                        // 学分
                        var credit = parseFloat(item.xf) || 0;
                        
                        // 过滤掉教室为"未排地点"的情况
                        if (classroom === '未排地点') {
                            classroom = '';
                        }
                        
                        console.log('[' + (i+1) + '] ' + courseName + ' | 教师:' + teacher + 
                                    ' | 教室:' + classroom + ' | 星期' + dayOfWeek + 
                                    ' | 节次:' + jcs + ' (第' + startSection + '节,共' + sectionCount + '节)' +
                                    ' | 周次:' + weekExpression);
                        
                        if (courseName && weekExpression) {
                            courses.push({
                                courseName: courseName,
                                teacher: teacher,
                                classroom: classroom,
                                dayOfWeek: dayOfWeek,
                                startSection: startSection,
                                sectionCount: sectionCount,
                                weekExpression: weekExpression,
                                credit: credit
                            });
                        }
                    }
                    
                    console.log('✅ 提取完成，共 ' + courses.length + ' 门课程');
                    return JSON.stringify({
                        courses: courses
                    });
                    
                } catch (error) {
                    console.error('❌ 提取过程中发生错误:', error);
                    return JSON.stringify({
                        courses: [],
                        error: '提取失败: ' + error.message
                    });
                }
            })();
        """.trimIndent()
    }
    
    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        
        try {
            Log.d(TAG, "开始解析课程数据...")
            Log.d(TAG, "原始数据: ${jsonData.take(200)}...")
            
            // 清理JSON数据
            val cleanJson = jsonData
                .trim()
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("\\r", "")
            
            Log.d(TAG, "清理后数据: ${cleanJson.take(200)}...")
            
            val jsonObject = JSONObject(cleanJson)
            
            // 检查是否有错误信息
            if (jsonObject.has("error")) {
                val errorMsg = jsonObject.getString("error")
                Log.e(TAG, "JavaScript提取时发生错误: $errorMsg")
                throw Exception("提取失败: $errorMsg")
            }
            
            val coursesArray = jsonObject.getJSONArray("courses")
            Log.d(TAG, "找到 ${coursesArray.length()} 门课程")
            
            for (i in 0 until coursesArray.length()) {
                val courseObj = coursesArray.getJSONObject(i)
                
                // 提取基本信息
                val rawCourseName = courseObj.optString("courseName", "")
                val teacher = courseObj.optString("teacher", "")
                val classroom = courseObj.optString("classroom", "")
                val dayOfWeek = courseObj.optInt("dayOfWeek", 1)
                val startSection = courseObj.optInt("startSection", 1)
                val sectionCount = courseObj.optInt("sectionCount", 2)
                val weekExpression = courseObj.optString("weekExpression", "")
                val credit = courseObj.optDouble("credit", 0.0).toFloat()
                
                // 清理课程名称：去除emoji、【】符号、★等特殊符号
                val courseName = cleanCourseName(rawCourseName)
                
                // 解析周次
                val weeks = if (weekExpression.isNotEmpty()) {
                    WeekParser.parseWeekExpression(weekExpression)
                } else {
                    emptyList()
                }
                
                courses.add(
                    ParsedCourse(
                        courseName = courseName.trim(),
                        teacher = teacher.trim(),
                        classroom = classroom.trim(),
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        sectionCount = sectionCount,
                        weeks = weeks,
                        credit = credit,
                        weekExpression = weekExpression
                    )
                )
            }
            
            Log.d(TAG, "解析完成，共 ${courses.size} 门课程")
        } catch (e: Exception) {
            Log.e(TAG, "解析课程数据失败", e)
            throw e // 重新抛出异常，让上层处理
        }
        
        return courses
    }
    
    /**
     * 清理课程名称：去除emoji、特殊符号等
     */
    private fun cleanCourseName(courseName: String): String {
        return courseName
            // 去除【】和其中的内容（如【调】）
            .replace(Regex("【[^】]*】"), "")
            .replace(Regex("\\[[^\\]]*\\]"), "")
            // 去除★☆〇■◆等特殊符号
            .replace("★", "")
            .replace("☆", "")
            .replace("〇", "")
            .replace("■", "")
            .replace("◆", "")
            // 去除emoji表情（Unicode范围）
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            // 去除多余空格
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
