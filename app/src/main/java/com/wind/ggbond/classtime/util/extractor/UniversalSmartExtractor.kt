package com.wind.ggbond.classtime.util.extractor

import com.wind.ggbond.classtime.data.model.ParsedCourse
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Singleton

@Singleton
class UniversalSmartExtractor @Inject constructor() : SchoolScheduleExtractor {
    override val schoolId = "universal_smart"
    override val schoolName = "通用智能提取器"
    override val systemType = "universal"

    companion object {
        private const val TAG = "UniversalExtractor"
        enum class SystemType { SHUWEI, QIANGZHI, KINGOSOFT, ZHENGFANG, URP, JINZHI, CHENGFANG, LIANYI, UNKNOWN }
        private val SYSTEM_PATTERNS: Map<SystemType, List<String>> = mapOf(
            SystemType.SHUWEI to listOf("/eams/", "courseTableForStd", "activity = new TaskActivity"),
            SystemType.QIANGZHI to listOf("/jsxsd/xskb/xskb_list.do", "kbtable", "div.kbcontent", "强智"),
            SystemType.KINGOSOFT to listOf("青果", "kingosoft", "frmDesk", "wsxk.xskcb", "selGS"),
            SystemType.ZHENGFANG to listOf("zfsoft", "正方", "var kbList", "div.kcmc"),
            SystemType.URP to listOf("cas.urp.edu.cn", "urp", "datagridsearch"),
            SystemType.JINZHI to listOf("campusphere", "jinzhiapp", "金智"),
            SystemType.CHENGFANG to listOf("乘方", "chengfang"),
            SystemType.LIANYI to listOf("studentCourseSchedule", "ant-spin-container", "联亦")
        )
    }

    override fun isSchedulePage(html: String, url: String): Boolean =
        html.contains("课程表", ignoreCase = true) || html.contains("课表", ignoreCase = true) ||
            html.contains("timetable", ignoreCase = true) || html.contains("schedule", ignoreCase = true) ||
            html.contains("kbtable", ignoreCase = true) || detectSystemType(html, url) != SystemType.UNKNOWN

    override fun getLoginUrl(): String? = null
    override fun getScheduleUrl(): String? = null

    fun detectSystemType(html: String, url: String): SystemType =
        SYSTEM_PATTERNS.mapValues { (_, patterns) -> patterns.count { p -> html.contains(p, ignoreCase = true) || url.contains(p, ignoreCase = true) } }
            .filter { it.value > 0 }.maxByOrNull { it.value }?.key ?: SystemType.UNKNOWN

    override fun generateExtractionScript(): String = """
(function() {
    try {
        var type = detectSystem();
        ${generateHelperFunctions()}
        var courses = [];
        switch(type) {
            case 'SHUWEI': courses = extractShuwei(); break;
            case 'QIANGZHI': courses = extractQiangzhi(); break;
            case 'KINGOSOFT': courses = extractKingosoft(); break;
            case 'ZHENGFANG': courses = extractZhengfang(); break;
            case 'LIANYI': courses = extractLianyi(); break;
            default: courses = extractGeneric();
        }
        return JSON.stringify({courses: courses, systemType: type});
    } catch(e) { return JSON.stringify({courses:[],error:e.message}); }

    function detectSystem() {
        var h = document.documentElement.outerHTML; var u = location.href;
        if (h.indexOf('activity = new TaskActivity') > -1 || u.indexOf('/eams/') > -1) return 'SHUWEI';
        if ((h.indexOf('kbtable') > -1 && h.indexOf('kbcontent') > -1) || u.indexOf('/jsxsd/xskb/') > -1) return 'QIANGZHI';
        if (h.indexOf('frmDesk') > -1 || u.indexOf('wsxk.xskcb') > -1) return 'KINGOSOFT';
        if (h.indexOf('var kbList') > -1 || h.indexOf('zfsoft') > -1 || h.indexOf('div.kcmc') > -1) return 'ZHENGFANG';
        if (h.indexOf('studentCourseSchedule') > -1 || h.indexOf('ant-spin-container') > -1) return 'LIANYI';
        return 'UNKNOWN';
    }

    function parseWeeks(str) { if (!str) return []; str = str.replace(/[(){}|第\[\]]/g,'').replace(/到/g,'-'); var weeks=[],segs=[]; while(str.search(/周|\s/) !== -1){var i=str.search(/周|\s/);var s=(str[i+1]==='单'||str[i+1]==='双')?str.slice(0,i+2).replace(/周|\s/g,''):str.slice(0,i+1).replace(/周|\s/g,'');segs.push(s);str=str.slice(i+(str[i+1]==='单'||str[i+1]==='双'?2:1));i=str.search(/\d/);str=i!==-1?str.slice(i):'';} if(str.length) segs.push(str);
        segs.forEach(function(seg){var parts=seg.replace(/单|双/g,'').split(',');parts.forEach(function(p){var nums=p.split('-');if(nums.length===1)weeks.push(parseInt(nums[0]));else{var s=parseInt(nums[0]),e=parseInt(nums[nums.length-1]);if(seg.indexOf('双')>-1){for(var i=s;i<=e;i+=2)weeks.push(i);}else if(seg.indexOf('单')>-1){for(var i=s;i<=e;i++)if(i%2!==0)weeks.push(i);}else{for(var i=s;i<=e;i++)weeks.push(i);}}}}); return weeks.filter(function(v,i,a){return a.indexOf(v)===i}).sort(function(a,b){return a-b});}
    function parseSections(str) { if(!str) return []; var n=str.replace(/节|\[|\]/g,'').split('-'); if(n.length===1)return[parseInt(n[0))];var s=parseInt(n[0]),e=parseInt(n[n.length-1]),r=[];for(var i=s;i<=e;i++)r.push(i);return r;}
    function cleanText(t) { return t ? t.replace(/\s+/g,' ').replace(/\\n/g,'').trim() : ''; }
    function dayToNum(d) { var m={'一':1,'二':2,'三':3,'四':4,'五':5,'六':6,'七':7,'日':7}; for(var k in m) if(d.indexOf(k)>-1) return m[k]; return parseInt(d)||1; }

    function extractShuwei() { var c=[],b=document.documentElement.outerHTML.split(/activity = new /); for(var i=1;i<b.length;i++){try{var m=b[i].match(/TaskActivity\((.*?)\);/);if(!m)continue;var p=m[1].split('","').map(function(x){return x.replace(/^"|"$/g,'');});if(p.length<7)continue;var dm=b[i].match(/index\s*=\s*(\d+)\s*\*\s*unitCount/),day=dm?parseInt(dm[1])+1:1;var sm=b[i].match(/unitCount\+(\d+);/g),secs={};if(sm)sm.forEach(function(x){secs[parseInt(x.match(/\d+/)[0])+1]=true;});var sk=Object.keys(secs).map(Number).sort(function(a,b){return a-b;}),ws=[];(p[6]||'').split('').forEach(function(ch,i){if(ch==='1')ws.push(i)});c.push({courseName:cleanText(p[3]),teacher:cleanText(p[1]),classroom:cleanText(p[5]),day:day,startSection:sk[0]||1,sectionCount:sk.length||2,weeks:ws})}catch(e){}} return c; }

    function extractQiangzhi() { var c=[],html=''; var ifs=document.getElementsByTagName('iframe'); for(var i=0;i<ifs.length;i++){try{var f=ifs[i];if(f.src&&f.src.indexOf('/jsxsd/xskb/')>-1){var d=f.contentDocument||f.contentWindow.document,t=d.getElementById('kbtable');if(t){html=t.outerHTML;break}}}catch(e){}} if(!html){var tb=document.getElementById('kbtable');if(tb)html=tb.outerHTML;} if(!html) return c; var doc=new DOMParser().parseFromString(html,'text/html'),rows=doc.querySelectorAll('tbody tr');
        rows.forEach(function(ri,row){row.querySelectorAll('td').forEach(function(ci,cell){cell.querySelectorAll('div.kbcontent').forEach(function(content){try{var ps=content.innerHTML.split(/<br>/i);if(ps.length>=3)c.push({courseName:cleanText(ps[0].replace(/<.*?>/g,'')),teacher:ps.length>3?cleanText(ps[3].replace(/<.*?>/g,'')):'',classroom:ps.length>4?cleanText(ps[4].replace(/<.*?>/g,'')):'',day:ci,startSection:ps[2]?parseSections(ps[2])[0]||(ri*2+1):1,sectionCount:ps[2]?parseSections(ps[2]).length||2:2,weeks:parseWeeks(ps[1])})}catch(e){}})})}); return c; }

    function extractKingosoft() { var c=[],html=''; try{if(window.frames&&window.frames['frmDesk']&&window.frames['frmDesk'].frames['frmReport']){var ts=window.frames['frmDesk'].frames['frmReport'].document.getElementsByTagName('table');for(var i=0;i<ts.length;i++)html+=ts[i].outerHTML;}}catch(e){} if(!html){var ts=document.getElementsByTagName('table');for(var i=0;i<ts.length;i++)html+=ts[i].outerHTML;} if(!html) return c; var doc=new DOMParser().parseFromString(html,'text/html');doc.querySelectorAll('tr').forEach(function(row){row.querySelectorAll('td').forEach(function(cell,idx){var t=cell.innerText||cell.textContent;if(!t||t.length<10)return;var ls=t.split('\\n').filter(function(l){return l.trim()});if(ls.length>=3)c.push({courseName:cleanText(ls[0]),teacher:ls.length>3?cleanText(ls[3]):'',classroom:ls.length>4?cleanText(ls[4]):'',day:(idx%7)||1,startSection:1,sectionCount:2,weeks:parseWeeks(ls[1])})})}); return c; }

    function extractZhengfang() { var c=[],html=document.documentElement.outerHTML,m=html.match(/var kbList\s*=\s*(\[[\s\S]*?\]);/); if(m){try{JSON.parse(m[1]).forEach(function(it){c.push({courseName:cleanText(it.kcmc||''),teacher:cleanText(it.xm||''),classroom:cleanText(it.cdmc||''),day:parseInt(it.xqj)||1,startSection:parseInt(it.jcs)||1,sectionCount:parseInt(it.jcs)||2,weeks:parseWeeks(it.zcd||'')})});return c}catch(e){}}
        var tb=document.getElementById('kbtable');if(!tb){var ts=document.querySelectorAll('table');for(var i=0;i<ts.length;i++){if(ts[i].innerHTML.indexOf('kbcontent')>-1){tb=ts[i];break}}} if(tb)tb.querySelectorAll('tr').forEach(function(ri,row){row.querySelectorAll('td').forEach(function(ci,cell){cell.querySelectorAll('div.kbcontent').forEach(function(content){try{var ls=(content.textContent||content.innerText).split('\\n').filter(function(l){return l.trim()});if(ls.length>=3)c.push({courseName:cleanText(ls[0]),teacher:ls.length>3?cleanText(ls[3]):'',classroom:ls.length>2?cleanText(ls[2]):'',day:ci||1,startSection:(ri-1)*2+1,sectionCount:2,weeks:parseWeeks(ls[1]||'')})}catch(e){}})})}); return c; }

    function extractLianyi() { var c[],ct=document.getElementsByClassName('ant-spin-container')[0]; if(ct){var tb=ct.getElementsByTagName('table')[0];if(tb)tb.querySelectorAll('tbody tr').forEach(function(ri,row){row.querySelectorAll('td').forEach(function(ci,cell){var t=cell.textContent||cell.innerText;if(t&&t.length>10){try{var ls=t.split('\\n').filter(function(l){return l.trim()});if(ls.length>=3)c.push({courseName:cleanText(ls[0]),teacher:ls.length>1?cleanText(ls[1]):'',classroom:ls.length>2?cleanText(ls[2]):'',day:ci||1,startSection:ri*2+1,sectionCount:2,weeks:parseWeeks(ls.length>3?ls[3]:'')})}catch(e){}})})} return c; }

    function extractGeneric() { var c=[];document.getElementsByTagName('table').forEach(function(tb){tb.querySelectorAll('tr').forEach(function(ri,row){if(ri===0)return;row.querySelectorAll('td').forEach(function(ci,cell){var t=(cell.textContent||cell.innerText||'').trim();if(t.length>10&&t.length<200){var ls=t.split('\\n').filter(function(l){return l.trim()}),cn=cleanText(ls[0]);if(cn.length<2||cn.indexOf('节次')>-1||cn.indexOf('星期')>-1||cn.indexOf('周次')>-1)return;var tr='',cr='',wt='';ls.forEach(function(l,i){if(i===0)return;l=cleanText(l);if(l.indexOf('周')>-1||l.match(/\\d+-\\d+/))wt=l;else if(l.length<10&&!cr)tr=l;else if(!cr)cr=l});c.push({courseName:cn,teacher:tr,classroom:cr,day:(ci%7)||1,startSection:Math.max(1,ri*2-1),sectionCount:2,weeks:parseWeeks(wt)})}})})}); return c; }
})()
""".trimIndent()

    private fun generateHelperFunctions(): String = ""

    override fun parseCourses(jsonData: String): List<ParsedCourse> {
        val courses = mutableListOf<ParsedCourse>()
        try {
            val cleanJson = jsonData.trim().removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"").replace("\\n", "").replace("\\r", "")
            val json = JSONObject(cleanJson)
            if (json.has("error")) throw Exception(json.getString("error"))
            val arr = json.getJSONArray("courses")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("courseName", "").trim()
                if (name.isEmpty()) continue
                val dayOfWeek = obj.optInt("day", obj.optInt("dayOfWeek", 1)).coerceIn(1, 7)
                val startSection = obj.optInt("startSection", 1).coerceAtLeast(1)
                val sectionCount = obj.optInt("sectionCount", 2).coerceAtLeast(1)
                val weekArr = obj.optJSONArray("weeks")
                val weeks = (if (weekArr != null && weekArr.length() > 0) (0 until weekArr.length()).mapNotNull { weekArr.optInt(it)?.takeIf { it > 0 } }.toMutableList() else null) ?: (1..16).toMutableList()
                val credit = obj.optDouble("credit", 0.0f.toDouble()).toFloat()
                courses.add(ParsedCourse(name, obj.optString("teacher", "").trim(), obj.optString("classroom", "").trim(),
                    dayOfWeek, startSection, sectionCount,
                    "${weeks.min()}-${weeks.max()}周",
                    weeks.sorted(), credit))
            }
            AppLogger.d(TAG, "解析完成: ${courses.size} 门课程 (${json.optString("systemType","UNKNOWN")})")
        } catch (e: Exception) { AppLogger.e(TAG, "解析失败", e); throw e }
        return courses
    }
}
