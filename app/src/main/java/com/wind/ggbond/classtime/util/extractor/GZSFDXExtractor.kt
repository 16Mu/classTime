package com.wind.ggbond.classtime.util.extractor

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GZSFDXExtractor @Inject constructor() : BaseSchoolExtractor() {

    override val schoolId = "gzsfdx"
    override val schoolName = "贵州师范大学"
    override val systemType = "zfsoft"

    override val tag = "GZSFDXExtractor"

    override val aliases = listOf("贵州师范大学")
    override val supportedUrls = listOf("jwgl.gznu.edu.cn")

    override fun getLoginUrl(): String {
        return "http://jwgl.gznu.edu.cn/"
    }

    override fun getScheduleUrl(): String {
        return "/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html"
    }

    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取贵州师范大学课表...');
                    
                    var htt = null;
                    var xnm = '';
                    var xqm = '';
                    
                    // 尝试直接从当前页面获取
                    try {
                        var forms = document.getElementById('ajaxForm');
                        if (forms) {
                            xnm = forms.xnm.value;
                            xqm = forms.xqm.value;
                            
                            console.log('学年: ' + xnm + ', 学期: ' + xqm);
                            
                            var formData = 'xnm=' + xnm + '&xqm=' + xqm;
                            var response = await fetch('/jwglxt/kbcx/xskbcxMobile_cxXsKb.html', {
                                method: 'POST',
                                body: formData,
                                headers: {
                                    'Content-Type': 'application/x-www-form-urlencoded'
                                }
                            });
                            htt = await response.json();
                        } else {
                            throw new Error('未找到ajaxForm');
                        }
                    } catch (e) {
                        console.log('直接获取失败，尝试通过导航菜单: ' + e.message);
                        
                        // 通过导航菜单查找
                        var cdNav = document.getElementById('cdNav');
                        if (!cdNav) {
                            return JSON.stringify({courses: [], error: '未找到导航菜单，请确保在学生课表查询页面'});
                        }
                        
                        var matches = cdNav.outerHTML.match(/clickMenu\((.*?)\);/g);
                        var id = '';
                        
                        if (matches) {
                            for (var i = 0; i < matches.length; i++) {
                                if (matches[i].indexOf('学生课表查询') != -1) {
                                    var parts = matches[i].match(/clickMenu\('([^']+)'/);
                                    if (parts && parts.length > 1) {
                                        id = parts[1];
                                        break;
                                    }
                                }
                            }
                        }
                        
                        if (!id) {
                            return JSON.stringify({courses: [], error: '未找到学生课表查询菜单'});
                        }
                        
                        console.log('找到菜单ID: ' + id);
                        
                        var response = await fetch('/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html?gnmkdm=' + id, {
                            method: 'GET'
                        });
                        var html = await response.text();
                        var parser = new DOMParser();
                        var doc = parser.parseFromString(html, 'text/html');
                        var form = doc.getElementById('ajaxForm');
                        
                        if (!form) {
                            return JSON.stringify({courses: [], error: '未找到课表表单'});
                        }
                        
                        xnm = form.xnm.value;
                        xqm = form.xqm.value;
                        
                        var formData = 'xnm=' + xnm + '&xqm=' + xqm;
                        var response2 = await fetch('/jwglxt/kbcx/xskbcxMobile_cxXsKb.html', {
                            method: 'POST',
                            body: formData,
                            headers: {
                                'Content-Type': 'application/x-www-form-urlencoded'
                            }
                        });
                        htt = await response2.json();
                    }
                    
                    console.log('获取到课表数据:', htt);
                    
                    if (!htt || !htt.kbList) {
                        return JSON.stringify({courses: [], error: '未获取到课表数据'});
                    }
                    
                    var courses = [];
                    htt.kbList.forEach(function(course) {
                        courses.push({
                            courseName: course.kcmc || '',
                            teacher: course.xm || '',
                            classroom: course.cdmc || '',
                            dayOfWeek: parseInt(course.xqj) || 1,
                            weekExpression: course.zcd || '',
                            sections: course.jc || '',
                            credit: 0
                        });
                    });
                    
                    console.log('✅ 提取完成，共 ' + courses.length + ' 门课程');
                    return JSON.stringify({courses: courses});
                    
                } catch (error) {
                    console.error('❌ 提取失败:', error);
                    return JSON.stringify({courses: [], error: '提取失败: ' + error.message});
                }
            })();
        """.trimIndent()
    }
}
