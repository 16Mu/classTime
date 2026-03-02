package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ImportedSemesterInfo
import com.wind.ggbond.classtime.data.model.ParsedCourse

/**
 * 学校课表提取器接口
 * 
 * 每个学校都有自己独特的课表页面结构，因此需要为每个学校创建专门的提取器
 */
interface SchoolScheduleExtractor {
    
    /**
     * 学校标识符（唯一）
     */
    val schoolId: String
    
    /**
     * 学校名称
     */
    val schoolName: String
    
    /**
     * 教务系统类型（如：强智、正方等）
     */
    val systemType: String
    
    /**
     * 检测当前页面是否是课表页面
     * 
     * @param html 页面HTML
     * @param url 页面URL
     * @return 是否是课表页面
     */
    fun isSchedulePage(html: String, url: String): Boolean
    
    /**
     * 生成用于提取课表的JavaScript代码
     * 
     * 这段JavaScript会在WebView中执行，提取页面中的课程信息并返回JSON格式数据
     * 
     * @return JavaScript代码字符串
     */
    fun generateExtractionScript(): String
    
    /**
     * 从JSON数据中解析课程列表
     * 
     * @param jsonData JavaScript返回的JSON数据
     * @return 解析后的课程列表
     */
    fun parseCourses(jsonData: String): List<ParsedCourse>
    
    /**
     * 获取登录页面URL（可选）
     */
    fun getLoginUrl(): String? = null
    
    /**
     * 获取课表页面URL（可选）
     */
    fun getScheduleUrl(): String? = null
    
    /**
     * 从JSON数据中解析学期信息（可选）
     * 如果页面包含学期开始日期、结束日期等信息，可以在此方法中提取
     * 
     * @param jsonData JavaScript返回的JSON数据
     * @return 解析后的学期信息，如果无法解析则返回null
     */
    fun parseSemesterInfo(jsonData: String): ImportedSemesterInfo? = null
}







