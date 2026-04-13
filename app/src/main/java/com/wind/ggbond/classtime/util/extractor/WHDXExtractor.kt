package com.wind.ggbond.classtime.util.extractor

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WHDXExtractor @Inject constructor() : BaseSchoolExtractor() {

    override val schoolId = "whdx"
    override val schoolName = "武汉大学"
    override val systemType = "zfsoft"

    override val tag = "WHDXExtractor"

    override val aliases = listOf("武汉大学")
    override val supportedUrls = listOf("210.42.121.241")

    override fun getLoginUrl(): String {
        return "http://210.42.121.241/"
    }

    override fun getScheduleUrl(): String {
        return "/kbcx/xskbcxZccx_cxXskbcxIndex.html"
    }

    override fun generateExtractionScript(): String {
        return """
            (function() {
                try {
                    console.log('🔍 开始提取武汉大学课表...');
                    
                    var htt = null;
                    var xnm = '';
                    var xqm = '';
                    var url = location.href;
                    
                    // 检查是否在移动端页面
                    if (url.search("xskbcxMobile_cxXskbcxIndex") != -1 || url.search("xskbcxMobile_cxTimeTableIndex") != -1) {
                        xnm = document.getElementById("xnm_hide").value;
                        xqm = document.getElementById("xqm_hide").value;
                        
                        console.log('移动端页面，学年: ' + xnm + ', 学期: ' + xqm);
                        
                        var formData = "xnm=" + xnm + "&zs=1&doType=app&xqm=" + xqm + "&kblx=2";
                        var response = await fetch("/jwglxt/kbcx/xskbcxMobile_cxXsgrkb.html?sf_request_type=ajax", {
                            method: 'POST',
                            body: formData,
                            headers: {
                                'Content-Type': 'application/x-www-form-urlencoded'
                            }
                        });
                        htt = await response.json();
                    } else {
                        // PC端页面
                        var forms = document.getElementById('ajaxForm');
                        if (forms) {
                            xnm = forms.xnm.value;
                            xqm = forms.xqm.value;
                            
                            console.log('PC端页面，学年: ' + xnm + ', 学期: ' + xqm);
                            
                            // 武汉大学特殊参数 kzlx=ck
                            var formData = 'xnm=' + xnm + '&xqm=' + xqm + '&kzlx=ck';
                            var response = await fetch('/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151', {
                                method: 'POST',
                                body: formData,
                                headers: {
                                    'Content-Type': 'application/x-www-form-urlencoded'
                                }
                            });
                            htt = await response.json();
                        } else {
                            // 通过导航菜单查找
                            var cdNav = document.getElementById('cdNav');
                            if (!cdNav) {
                                return JSON.stringify({courses: [], error: '未找到导航菜单，请确保在个人课表查询页面'});
                            }
                            
                            var matches = cdNav.outerHTML.match(/clickMenu\((.*?)\);/g);
                            var id = '';
                            
                            if (matches) {
                                for (var i = 0; i < matches.length; i++) {
                                    if (matches[i].indexOf('个人课表查询') != -1) {
                                        var parts = matches[i].match(/clickMenu\('([^']+)'/);
                                        if (parts && parts.length > 1) {
                                            id = parts[1];
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (!id) {
                                return JSON.stringify({courses: [], error: '未找到个人课表查询菜单'});
                            }
                            
                            console.log('找到菜单ID: ' + id);
                            
                            var response = await fetch('/kbcx/xskbcxZccx_cxXskbcxIndex.html?gnmkdm=' + id, {
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
                            
                            var formData = 'xnm=' + xnm + '&xqm=' + xqm + '&kzlx=ck';
                            var response2 = await fetch('/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151', {
                                method: 'POST',
                                body: formData,
                                headers: {
                                    'Content-Type': 'application/x-www-form-urlencoded'
                                }
                            });
                            htt = await response2.json();
                        }
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
