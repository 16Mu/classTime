package com.wind.ggbond.classtime.util.extractor

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SDSFXYExtractor @Inject constructor() : BaseSchoolExtractor() {

    override val schoolId = "sdsfxy"
    override val schoolName = "山东师范大学"
    override val systemType = "zfsoft"

    override val tag = "SDSFXYExtractor"

    override val aliases = listOf("山东师范大学")
    override val supportedUrls = listOf("bkjx.sdnu.edu.cn")

    override fun getLoginUrl(): String {
        return "http://www.bkjx.sdnu.edu.cn/"
    }

    override fun getScheduleUrl(): String {
        return "/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html"
    }

    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取山东师范大学课表...');
                    
                    var htt = null;
                    var xnm = '';
                    var xqm = '';
                    var url = location.href;
                    
                    if (url.search("xskbcxMobile_cxXskbcxIndex") != -1 || url.search("xskbcxMobile_cxTimeTableIndex") != -1) {
                        xnm = document.getElementById("xnm_hide").value;
                        xqm = document.getElementById("xqm_hide").value;
                        
                        var formData = "xnm=" + xnm + "&zs=1&doType=app&xqm=" + xqm + "&kblx=2";
                        var response = await fetch("/jwglxt/kbcx/xskbcxMobile_cxXsgrkb.html?sf_request_type=ajax", {
                            method: 'POST',
                            body: formData,
                            headers: {'Content-Type': 'application/x-www-form-urlencoded'}
                        });
                        htt = await response.json();
                    } else {
                        var forms = document.getElementById('ajaxForm');
                        if (forms) {
                            xnm = forms.xnm.value;
                            xqm = forms.xqm.value;
                            
                            var formData = 'xnm=' + xnm + '&xqm=' + xqm;
                            var response = await fetch('/jwglxt/kbcx/xskbcxMobile_cxXsKb.html', {
                                method: 'POST',
                                body: formData,
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'}
                            });
                            htt = await response.json();
                        } else {
                            var cdNav = document.getElementById('cdNav');
                            if (!cdNav) {
                                return JSON.stringify({courses: [], error: '未找到导航菜单'});
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
                            
                            var response = await fetch('/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html?gnmkdm=' + id);
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
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'}
                            });
                            htt = await response2.json();
                        }
                    }
                    
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
