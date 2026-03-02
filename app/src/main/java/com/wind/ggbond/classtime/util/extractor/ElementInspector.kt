package com.wind.ggbond.classtime.util.extractor

/**
 * HTML元素检查器
 * 
 * 用于在开发阶段检查页面结构，帮助编写提取逻辑
 */
object ElementInspector {
    
    /**
     * 生成页面结构检查脚本
     * 
     * 这个脚本会在WebView中执行，输出页面的关键元素信息，
     * 帮助开发者了解页面结构，编写正确的提取代码
     */
    fun generateInspectionScript(): String {
        return """
            (function() {
                console.log('🔍 ========== 页面结构检查 ==========');
                
                var report = {
                    url: window.location.href,
                    title: document.title,
                    tables: [],
                    possibleScheduleElements: []
                };
                
                // ===== 1. 检查所有表格 =====
                console.log('\n📊 检查表格元素:');
                var tables = document.querySelectorAll('table');
                console.log('找到 ' + tables.length + ' 个表格');
                
                tables.forEach(function(table, index) {
                    var tableInfo = {
                        index: index,
                        id: table.id || '无',
                        className: table.className || '无',
                        rows: table.rows.length,
                        cols: table.rows.length > 0 ? table.rows[0].cells.length : 0,
                        preview: ''
                    };
                    
                    // 获取前3行的文本预览
                    var previewRows = [];
                    for (var i = 0; i < Math.min(3, table.rows.length); i++) {
                        var rowText = [];
                        for (var j = 0; j < Math.min(5, table.rows[i].cells.length); j++) {
                            rowText.push(table.rows[i].cells[j].textContent.trim().substring(0, 20));
                        }
                        previewRows.push(rowText.join(' | '));
                    }
                    tableInfo.preview = previewRows.join('\n');
                    
                    report.tables.push(tableInfo);
                    
                    console.log('表格 ' + index + ':');
                    console.log('  ID: ' + tableInfo.id);
                    console.log('  Class: ' + tableInfo.className);
                    console.log('  大小: ' + tableInfo.rows + '行 x ' + tableInfo.cols + '列');
                    console.log('  预览:\n' + tableInfo.preview);
                });
                
                // ===== 2. 检查课表相关的特征元素 =====
                console.log('\n🎯 检查可能的课表元素:');
                
                var selectors = [
                    '#timetable',
                    '#kbtable',
                    '.timetable',
                    '.kbcontent',
                    '[id*="table"]',
                    '[class*="table"]',
                    '[id*="schedule"]',
                    '[class*="schedule"]',
                    'div.kbcontent',
                    'div.item-box'
                ];
                
                selectors.forEach(function(selector) {
                    try {
                        var elements = document.querySelectorAll(selector);
                        if (elements.length > 0) {
                            console.log('✓ 找到: ' + selector + ' (' + elements.length + '个)');
                            report.possibleScheduleElements.push({
                                selector: selector,
                                count: elements.length,
                                sample: elements[0].outerHTML.substring(0, 200)
                            });
                        }
                    } catch (e) {
                        // 忽略无效的选择器
                    }
                });
                
                // ===== 3. 检查课程卡片结构 =====
                console.log('\n📦 检查课程卡片:');
                var courseCards = document.querySelectorAll('.kbcontent, .item-box, [class*="course"]');
                if (courseCards.length > 0) {
                    console.log('找到 ' + courseCards.length + ' 个可能的课程卡片');
                    var firstCard = courseCards[0];
                    console.log('第一个卡片HTML:');
                    console.log(firstCard.outerHTML.substring(0, 500));
                    
                    // 尝试识别内部结构
                    console.log('\n卡片内部元素:');
                    var children = firstCard.querySelectorAll('*');
                    children.forEach(function(child) {
                        if (child.textContent.trim()) {
                            console.log('  <' + child.tagName.toLowerCase() + 
                                       (child.className ? ' class="' + child.className + '"' : '') +
                                       '>: ' + child.textContent.trim().substring(0, 50));
                        }
                    });
                }
                
                // ===== 4. 检查JavaScript变量 =====
                console.log('\n📝 检查JavaScript变量:');
                var possibleVars = ['kbList', 'courseList', 'scheduleData', 'kbData', 'table', 'kbData'];
                possibleVars.forEach(function(varName) {
                    if (typeof window[varName] !== 'undefined') {
                        console.log('✓ 找到变量: ' + varName);
                        console.log('  类型: ' + typeof window[varName]);
                        console.log('  内容预览: ' + JSON.stringify(window[varName]).substring(0, 200));
                    }
                });
                
                // ===== 5. 查找包含"课程"文字的元素 =====
                console.log('\n🔍 查找包含课程信息的元素:');
                var allDivs = document.querySelectorAll('div');
                var courseRelatedDivs = [];
                for (var i = 0; i < Math.min(allDivs.length, 100); i++) {
                    var div = allDivs[i];
                    var text = div.textContent || '';
                    // 查找可能包含课程名的元素（包含常见课程关键词）
                    if (text.length > 5 && text.length < 200 && 
                        (text.indexOf('数学') >= 0 || text.indexOf('英语') >= 0 || 
                         text.indexOf('物理') >= 0 || text.indexOf('化学') >= 0 ||
                         text.indexOf('教师') >= 0 || text.indexOf('周') >= 0 ||
                         text.indexOf('教室') >= 0 || text.indexOf('节') >= 0)) {
                        courseRelatedDivs.push({
                            id: div.id || '无',
                            className: div.className || '无',
                            text: text.substring(0, 100)
                        });
                    }
                }
                console.log('找到 ' + courseRelatedDivs.length + ' 个可能的课程元素');
                if (courseRelatedDivs.length > 0) {
                    console.log('前5个课程相关元素:');
                    for (var i = 0; i < Math.min(5, courseRelatedDivs.length); i++) {
                        console.log('  元素' + (i+1) + ':', courseRelatedDivs[i]);
                    }
                }
                
                // ===== 6. 查找所有带id或特殊class的div =====
                console.log('\n📦 查找关键容器:');
                var keyContainers = document.querySelectorAll('div[id], div[class*="kb"], div[class*="schedule"], div[class*="course"]');
                console.log('找到 ' + keyContainers.length + ' 个关键容器');
                for (var i = 0; i < Math.min(10, keyContainers.length); i++) {
                    var container = keyContainers[i];
                    console.log('容器' + (i+1) + ':', {
                        id: container.id || '无',
                        className: container.className || '无',
                        childCount: container.children.length,
                        textPreview: (container.textContent || '').substring(0, 50)
                    });
                }
                
                // ===== 7. 深入分析 ylkbTable 容器（针对重庆电力高专） =====
                console.log('\n🎯 深入分析 ylkbTable 容器:');
                var ylkbTable = document.getElementById('ylkbTable');
                var ylkbAnalysis = {
                    found: false,
                    children: [],
                    courseElements: [],
                    sampleHTML: ''
                };
                
                if (ylkbTable) {
                    console.log('✓ 找到 ylkbTable');
                    ylkbAnalysis.found = true;
                    
                    // 分析子元素
                    for (var i = 0; i < ylkbTable.children.length; i++) {
                        var child = ylkbTable.children[i];
                        var childInfo = {
                            index: i,
                            tagName: child.tagName,
                            id: child.id || '无',
                            className: child.className || '无',
                            childCount: child.children.length,
                            textLength: (child.textContent || '').length
                        };
                        
                        console.log('子元素' + (i+1) + ':', childInfo);
                        ylkbAnalysis.children.push(childInfo);
                        
                        // 如果有孙子元素，继续分析
                        if (child.children.length > 0) {
                            console.log('  孙子元素:');
                            for (var j = 0; j < Math.min(10, child.children.length); j++) {
                                var grandchild = child.children[j];
                                console.log('    [' + j + '] ' + grandchild.tagName + 
                                           ' (id:' + (grandchild.id || '无') + 
                                           ', class:' + (grandchild.className || '无') + 
                                           ') ' + (grandchild.textContent || '').substring(0, 50));
                            }
                        }
                    }
                    
                    // 查找所有可能的课程元素
                    var allInside = ylkbTable.querySelectorAll('*');
                    console.log('\nylkbTable 内总元素数:', allInside.length);
                    
                    var foundCourses = [];
                    for (var i = 0; i < allInside.length; i++) {
                        var elem = allInside[i];
                        var text = (elem.textContent || '').trim();
                        // 查找包含课程特征的元素
                        if (text.length > 5 && text.length < 200 && 
                            (text.indexOf('周') >= 0 || text.indexOf('教室') >= 0 || 
                             text.indexOf('教师') >= 0 || text.indexOf('节') >= 0 ||
                             text.match(/[一二三四五六日]/))) {
                            foundCourses.push({
                                tagName: elem.tagName,
                                id: elem.id || '无',
                                className: elem.className || '无',
                                text: text.substring(0, 100)
                            });
                            
                            if (foundCourses.length <= 5) {
                                console.log('📚 课程元素' + foundCourses.length + ':', {
                                    tag: elem.tagName,
                                    class: elem.className || '无',
                                    text: text.substring(0, 80)
                                });
                            }
                        }
                    }
                    
                    console.log('\n在 ylkbTable 中找到', foundCourses.length, '个可能的课程元素');
                    ylkbAnalysis.courseElements = foundCourses.slice(0, 20);
                    
                    // 获取第一个课程元素的完整HTML
                    if (foundCourses.length > 0) {
                        var firstCourseElem = ylkbTable.querySelector('.' + foundCourses[0].className.split(' ')[0]);
                        if (firstCourseElem) {
                            ylkbAnalysis.sampleHTML = firstCourseElem.outerHTML.substring(0, 500);
                            console.log('\n📦 第一个课程元素HTML预览:');
                            console.log(ylkbAnalysis.sampleHTML);
                        }
                    }
                    
                    // 查找表格
                    var tables = ylkbTable.querySelectorAll('table');
                    if (tables.length > 0) {
                        console.log('\n📊 在 ylkbTable 中找到', tables.length, '个表格');
                        tables.forEach(function(tbl, idx) {
                            var tableInfo = {
                                id: tbl.id || '无',
                                class: tbl.className || '无',
                                rows: tbl.rows.length,
                                cols: tbl.rows.length > 0 ? tbl.rows[0].cells.length : 0
                            };
                            console.log('表格' + (idx+1) + ':', tableInfo);
                            
                            // 显示前几行的内容
                            if (tbl.rows.length > 0) {
                                console.log('  表格内容预览:');
                                for (var r = 0; r < Math.min(3, tbl.rows.length); r++) {
                                    var row = tbl.rows[r];
                                    var cellTexts = [];
                                    for (var c = 0; c < Math.min(8, row.cells.length); c++) {
                                        cellTexts.push(row.cells[c].textContent.trim().substring(0, 15));
                                    }
                                    console.log('    行' + (r+1) + ': ' + cellTexts.join(' | '));
                                }
                            }
                        });
                    }
                } else {
                    console.log('✗ 未找到 ylkbTable');
                }
                
                report.ylkbAnalysis = ylkbAnalysis;
                
                // ===== 8. 全局搜索：查找所有可能是课表的表格 =====
                console.log('\n🌐 全局搜索所有表格:');
                var allTables = document.querySelectorAll('table');
                var scheduleTableCandidates = [];
                
                allTables.forEach(function(tbl, idx) {
                    var rows = tbl.rows.length;
                    var cols = rows > 0 ? tbl.rows[0].cells.length : 0;
                    
                    // 课表通常是 7-8 列（周一到周日）或 14 行以上（多节课）
                    var isPossibleSchedule = (cols >= 7 && cols <= 9) || (rows >= 10);
                    
                    if (isPossibleSchedule) {
                        var tableData = {
                            index: idx,
                            id: tbl.id || '无',
                            className: tbl.className || '无',
                            rows: rows,
                            cols: cols,
                            parentId: tbl.parentElement ? (tbl.parentElement.id || '无') : '无',
                            preview: ''
                        };
                        
                        // 获取表格内容预览
                        if (rows > 0) {
                            var previewLines = [];
                            for (var r = 0; r < Math.min(3, rows); r++) {
                                var rowCells = [];
                                for (var c = 0; c < Math.min(5, tbl.rows[r].cells.length); c++) {
                                    rowCells.push(tbl.rows[r].cells[c].textContent.trim().substring(0, 10));
                                }
                                previewLines.push(rowCells.join(' | '));
                            }
                            tableData.preview = previewLines.join('\\n');
                        }
                        
                        scheduleTableCandidates.push(tableData);
                        console.log('📋 候选表格' + scheduleTableCandidates.length + ':', tableData);
                    }
                });
                
                console.log('\n找到', scheduleTableCandidates.length, '个可能的课表表格');
                report.scheduleTableCandidates = scheduleTableCandidates;
                
                // ===== 9. 检查 iframe =====
                console.log('\n🖼️ 检查 iframe:');
                var iframes = document.querySelectorAll('iframe');
                report.iframes = [];
                iframes.forEach(function(iframe, idx) {
                    var iframeInfo = {
                        index: idx,
                        id: iframe.id || '无',
                        src: iframe.src || '无',
                        name: iframe.name || '无'
                    };
                    report.iframes.push(iframeInfo);
                    console.log('iframe' + (idx+1) + ':', iframeInfo);
                });
                
                // ===== 10. 检查 JavaScript 全局变量（可能存储课表数据） =====
                console.log('\n📝 深度检查 JavaScript 变量:');
                var jsVars = {};
                var possibleVarNames = [
                    'kbList', 'courseList', 'scheduleData', 'kbData', 
                    'table', 'tableData', 'courses', 'zykbList',
                    'kbxx', 'kcxx', 'xskbcxDataList', 'dataList'
                ];
                
                possibleVarNames.forEach(function(varName) {
                    if (typeof window[varName] !== 'undefined') {
                        var varValue = window[varName];
                        jsVars[varName] = {
                            type: typeof varValue,
                            isArray: Array.isArray(varValue),
                            length: Array.isArray(varValue) ? varValue.length : undefined,
                            preview: JSON.stringify(varValue).substring(0, 200)
                        };
                        console.log('✓ 找到变量 ' + varName + ':', jsVars[varName]);
                    }
                });
                report.jsVars = jsVars;
                
                // ===== 11. 检查所有 script 标签（查找内联数据） =====
                console.log('\n📜 检查 script 标签:');
                var scripts = document.querySelectorAll('script');
                var foundScriptData = false;
                for (var i = 0; i < scripts.length; i++) {
                    var scriptContent = scripts[i].textContent || '';
                    // 查找可能包含课表数据的脚本
                    if (scriptContent.indexOf('kbList') >= 0 || 
                        scriptContent.indexOf('课表') >= 0 ||
                        scriptContent.indexOf('courseList') >= 0) {
                        console.log('找到可能包含课表数据的 script 标签:', scriptContent.substring(0, 200));
                        foundScriptData = true;
                        break;
                    }
                }
                if (!foundScriptData) {
                    console.log('未找到包含课表数据的 script 标签');
                }
                
                // ===== 12. 检查 table1 和 table2 的实际 innerHTML =====
                console.log('\n🔍 深度检查 table1 和 table2:');
                var table1 = document.getElementById('table1');
                var table2 = document.getElementById('table2');
                
                if (table1) {
                    console.log('table1 innerHTML 长度:', (table1.innerHTML || '').length);
                    console.log('table1 innerHTML 预览:', (table1.innerHTML || '').substring(0, 500));
                }
                if (table2) {
                    console.log('table2 innerHTML 长度:', (table2.innerHTML || '').length);
                    console.log('table2 innerHTML 预览:', (table2.innerHTML || '').substring(0, 500));
                }
                
                console.log('\n✅ 检查完成');
                console.log('========================================\n');
                
                report.courseRelatedElements = courseRelatedDivs.slice(0, 10);
                report.keyContainers = [];
                for (var i = 0; i < Math.min(10, keyContainers.length); i++) {
                    report.keyContainers.push({
                        id: keyContainers[i].id || '无',
                        className: keyContainers[i].className || '无',
                        childCount: keyContainers[i].children.length
                    });
                }
                
                return JSON.stringify(report, null, 2);
            })();
        """.trimIndent()
    }
}

