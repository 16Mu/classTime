#!/usr/bin/env node

'use strict';

const { program } = require('commander');
const chalk = require('chalk');
const axios = require('axios');
const fs = require('fs');
const path = require('path');

const SCHOOLS_JSON_PATH = path.resolve(__dirname, '../../app/src/main/assets/schools.json');
const REPORT_DIR = path.resolve(__dirname, '../../reports/json');

program
  .name('schedule-api-checker')
  .description('检测不同类型教务系统的课表 API 接口')
  .option('-s, --school <ids>', '指定学校 ID（逗号分隔）')
  .option('-t, --type <type>', '只检测指定系统类型（zfsoft/qiangzhi/shuwei/kingosoft/custom/chengfang/ynsmart/chaoxing）')
 .option('-d, --deep', '深度检测（尝试带参数请求）', false)
 .option('--timeout <ms>', '超时时间（毫秒）', '15000')
  .option('-c, --concurrency <n>', '并发数', '5')
  .option('-o, --output <path>', '输出路径')
  .parse(process.argv);

const opts = program.opts();
const SCHOOL_FILTER = opts.school ? opts.school.split(',').map(s => s.trim()) : null;
const TYPE_FILTER = opts.type || null;
const DEEP_MODE = opts.deep || false;
const TIMEOUT = parseInt(opts.timeout, 10) || 15000;
const CONCURRENCY = parseInt(opts.concurrency, 10) || 5;
const OUTPUT_PATH = opts.output || null;

function logHeader() {
  console.log();
  console.log(chalk.bold.cyan('╔══════════════════════════════════════════════════════════════╗'));
  console.log(chalk.bold.cyan('║') + chalk.bold.white('            教务系统课表 API 接口检测工具                  ') + chalk.bold.cyan('║'));
  console.log(chalk.bold.cyan('╚══════════════════════════════════════════════════════════════╝'));
  console.log();
}

function loadSchools() {
  const raw = fs.readFileSync(SCHOOLS_JSON_PATH, 'utf-8');
  const schools = JSON.parse(raw);
  let result = schools;
  if (SCHOOL_FILTER) {
    result = result.filter(s => SCHOOL_FILTER.includes(s.id));
  }
  if (TYPE_FILTER) {
    result = result.filter(s => s.systemType === TYPE_FILTER);
  }
  return result;
}

function getCurrentAcademicYear() {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  if (month >= 9) {
    return String(year);
  } else if (month >= 2 && month < 9) {
    return String(year - 1);
  }
  return String(year - 1);
}

function getCurrentSemester() {
  const now = new Date();
  const month = now.getMonth() + 1;
  if (month >= 2 && month <= 8) {
    return '3';
  }
  return '12';
}

function buildDefaultParams(scheduleParams) {
  const params = { ...scheduleParams };
  if (!params.xnm) params.xnm = getCurrentAcademicYear();
  if (!params.xqm) params.xqm = getCurrentSemester();
  return params;
}

const DEFAULT_HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  'Accept': 'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8',
  'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
};

async function checkZfsoftApi(school) {
  const result = {
    schoolId: school.id,
    systemType: school.systemType,
    scheduleUrl: school.scheduleUrl,
    scheduleMethod: school.scheduleMethod || 'POST',
    result: {
      accessible: false,
      statusCode: null,
      responseTime: null,
      contentType: null,
      dataType: null,
      dataValid: false,
      keyFieldsFound: [],
      csrfRequired: school.needCsrfToken || false,
      csrfTokenName: school.csrfTokenName || '',
      notes: '',
    },
  };

  if (!school.scheduleUrl) {
    result.result.notes = 'scheduleUrl 为空，无法检测';
    return result;
  }

  try {
    const startTime = Date.now();

    let config = {
      method: school.scheduleMethod || 'GET',
      url: school.scheduleUrl,
      timeout: TIMEOUT,
      validateStatus: () => true,
      headers: { ...DEFAULT_HEADERS },
      maxRedirects: 5,
    };

    if ((school.scheduleMethod || '').toUpperCase() === 'POST' && DEEP_MODE) {
      const params = buildDefaultParams(school.scheduleParams || {});
      config.data = new URLSearchParams(params).toString();
      config.headers['Content-Type'] = 'application/x-www-form-urlencoded';
    }

    const response = await axios(config);

    result.result.responseTime = Date.now() - startTime;
    result.result.statusCode = response.status;
    result.result.contentType = response.headers['content-type'] || '';

    if (response.status === 200 || response.status === 302) {
      result.result.accessible = true;
    }

    const ct = (result.result.contentType || '').toLowerCase();
    if (ct.includes('json')) {
      result.result.dataType = 'json';
      try {
        const data = typeof response.data === 'string' ? JSON.parse(response.data) : response.data;
        const mapping = school.jsonMapping || {};
        const foundFields = [];
        for (const [key, field] of Object.entries(mapping)) {
          if (data[field] !== undefined) {
            foundFields.push(field);
          }
        }
        result.result.keyFieldsFound = foundFields;
        result.result.dataValid = foundFields.length > 0;
        result.result.notes = `接口正常返回 JSON 数据，发现 ${foundFields.length} 个关键字段`;
        if (foundFields.length > 0) {
          result.result.notes += `: ${foundFields.join(', ')}`;
        }
      } catch (e) {
        result.result.notes = '返回内容不是有效 JSON';
      }
    } else if (ct.includes('html')) {
      result.result.dataType = 'html';
      const html = typeof response.data === 'string' ? response.data : '';
      const hasTable = /<table[^>]*>/.test(html);
      const hasKbList = /kbList|kcmc|xskbcx/i.test(html);
      result.result.dataValid = hasTable || hasKbList;
      result.result.keyFieldsFound = [];
      if (hasTable) result.result.keyFieldsFound.push('table');
      if (hasKbList) result.result.keyFieldsFound.push('schedule_keywords');
      result.result.notes = hasTable
        ? '返回 HTML 页面，包含表格结构'
        : '返回 HTML 页面，但未发现明显的课表结构';
    } else {
      result.result.dataType = 'unknown';
      result.result.notes = `未知 Content-Type: ${result.result.contentType}`;
    }

    if (school.needCsrfToken && htmlIncludesCsrf(response.data)) {
      result.result.csrfRequired = true;
      result.result.notes += ' | 检测到 CSRF Token';
    }
  } catch (err) {
    result.result.statusCode = err.response?.status || null;
    result.result.notes = `请求失败: ${err.code || err.message}`;
  }

  return result;
}

function htmlIncludesCsrf(data) {
  if (!data) return false;
  const str = typeof data === 'string' ? data : '';
  return /_csrf|csrf[_-]?token|xsrf[_-]?token/i.test(str);
}

async function checkQiangzhiApi(school) {
  const result = {
    schoolId: school.id,
    systemType: school.systemType,
    scheduleUrl: school.scheduleUrl,
    scheduleMethod: school.scheduleMethod || 'GET',
    result: {
      accessible: false,
      statusCode: null,
      responseTime: null,
      contentType: null,
      dataType: null,
      dataValid: false,
      keyFieldsFound: [],
      csrfRequired: false,
      csrfTokenName: '',
      notes: '',
    },
  };

  if (!school.scheduleUrl) {
    result.result.notes = 'scheduleUrl 为空，可能需要登录后跳转获取';
    return result;
  }

  try {
    const startTime = Date.now();

    const config = {
      method: 'GET',
      url: school.scheduleUrl,
      timeout: TIMEOUT,
      validateStatus: () => true,
      headers: { ...DEFAULT_HEADERS },
      maxRedirects: 5,
    };

    const response = await axios(config);

    result.result.responseTime = Date.now() - startTime;
    result.result.statusCode = response.status;
    result.result.contentType = response.headers['content-type'] || '';

    if (response.status === 200 || response.status === 302) {
      result.result.accessible = true;
    }

    const html = typeof response.data === 'string' ? response.data : '';
    result.result.dataType = 'html';

    const hasTimetable = /timetable|xskb|xsbd|xskbd|课表/i.test(html);
    const hasTableTag = /<table[^>]*class="[^"]*timetable|"[^"]*kb"|"[^"]*schedule"/i.test(html);
    const hasIframe = /<iframe/i.test(html);

    result.result.keyFieldsFound = [];
    if (hasTimetable) result.result.keyFieldsFound.push('timetable_keyword');
    if (hasTableTag) result.result.keyFieldsFound.push('schedule_table_class');
    if (hasIframe) result.result.keyFieldsFound.push('iframe_structure');

    result.result.dataValid = hasTimetable || hasTableTag;

    if (response.status === 302 || response.status === 403) {
      result.result.notes = '需要先登录获取 Cookie 才能访问课表页面';
      result.result.accessible = false;
    } else if (result.result.dataValid) {
      result.result.notes = '强智系统课表页面可访问，包含课表特征元素';
    } else {
      result.result.notes = '页面可访问但未检测到明显课表结构，可能需要登录';
    }

    if (school.loginUrl) {
      result.result.notes += ` | 登录入口: ${school.loginUrl}`;
    }
  } catch (err) {
    result.result.statusCode = err.response?.status || null;
    result.result.notes = `请求失败: ${err.code || err.message}`;
  }

  return result;
}

async function checkShuweiApi(school) {
  const result = {
    schoolId: school.id,
    systemType: school.systemType,
    scheduleUrl: school.scheduleUrl,
    scheduleMethod: school.scheduleMethod || 'GET',
    result: {
      accessible: false,
      statusCode: null,
      responseTime: null,
      contentType: null,
      dataType: null,
      dataValid: false,
      keyFieldsFound: [],
      csrfRequired: false,
      csrfTokenName: '',
      notes: '',
    },
  };

  if (!school.scheduleUrl) {
    result.result.notes = 'scheduleUrl 为空';
    return result;
  }

  try {
    const startTime = Date.now();

    const config = {
      method: 'GET',
      url: school.scheduleUrl,
      timeout: TIMEOUT,
      validateStatus: () => true,
      headers: { ...DEFAULT_HEADERS },
      maxRedirects: 5,
    };

    const response = await axios(config);

    result.result.responseTime = Date.now() - startTime;
    result.result.statusCode = response.status;
    result.result.contentType = response.headers['content-type'] || '';

    if (response.status === 200 || response.status === 302) {
      result.result.accessible = true;
    }

    const html = typeof response.data === 'string' ? response.data : '';
    result.result.dataType = 'html';

    const hasEamsAction = /courseTableForStd\.action/i.test(html) || /courseTableForStd/i.test(school.scheduleUrl);
    const hasEamsJs = /eams.*\.js/i.test(html);
    const hasCourseTableVar = /courseTable|activity[iI]d/i.test(html);

    result.result.keyFieldsFound = [];
    if (hasEamsAction) result.result.keyFieldsFound.push('eams_action');
    if (hasEamsJs) result.result.keyFieldsFound.push('eams_js');
    if (hasCourseTableVar) result.result.keyFieldsFound.push('course_table_var');

    result.result.dataValid = hasEamsAction || hasEamsJs || hasCourseTableVar;

    if (result.result.dataValid) {
      result.result.notes = '树维系统 courseTableForStd 接口可访问，检测到 EAMS 系统特征';
    } else {
      result.result.notes = '页面可访问但未检测到 EAMS 特征元素';
    }
  } catch (err) {
    result.result.statusCode = err.response?.status || null;
    result.result.notes = `请求失败: ${err.code || err.message}`;
  }

  return result;
}

async function checkKingosoftApi(school) {
  const result = {
    schoolId: school.id,
    systemType: school.systemType,
    scheduleUrl: school.scheduleUrl,
    scheduleMethod: school.scheduleMethod || 'GET',
    result: {
      accessible: false,
      statusCode: null,
      responseTime: null,
      contentType: null,
      dataType: null,
      dataValid: false,
      keyFieldsFound: [],
      csrfRequired: false,
      csrfTokenName: '',
      notes: '',
    },
  };

  if (!school.scheduleUrl) {
    result.result.notes = 'scheduleUrl 为空';
    return result;
  }

  try {
    const startTime = Date.now();

    const config = {
      method: 'GET',
      url: school.scheduleUrl,
      timeout: TIMEOUT,
      validateStatus: () => true,
      headers: { ...DEFAULT_HEADERS },
      maxRedirects: 5,
    };

    const response = await axios(config);

    result.result.responseTime = Date.now() - startTime;
    result.result.statusCode = response.status;
    result.result.contentType = response.headers['content-type'] || '';

    if (response.status === 200 || response.status === 302) {
      result.result.accessible = true;
    }

    const html = typeof response.data === 'string' ? response.data : '';
    result.result.dataType = 'html';

    const hasJwglxt = /jwglxt/i.test(school.scheduleUrl) || /jwglxt/i.test(html);
    const hasSdnzjw = /sdnzjw/i.test(school.scheduleUrl) || /sdnzjw/i.test(html);
    const hasFrameStructure = /frame.*desk|desk.*frame/i.test(html) || /topFrame|mainFrame|menuFrame/i.test(html);
    const hasXskbPath = /\/xskb\//i.test(school.scheduleUrl);

    result.result.keyFieldsFound = [];
    if (hasJwglxt) result.result.keyFieldsFound.push('jwglxt_path');
    if (hasSdnzjw) result.result.keyFieldsFound.push('sdnzjw_path');
    if (hasFrameStructure) result.result.keyFieldsFound.push('frame_structure');
    if (hasXskbPath) result.result.keyFieldsFound.push('xskb_path');

    result.result.dataValid = hasJwglxt || hasSdnzjw || hasFrameStructure || hasXskbPath;

    if (result.result.dataValid) {
      result.result.notes = '青果系统接口可访问，检测到青果系统特征';
    } else {
      result.result.notes = '页面可访问但未检测到青果系统特征';
    }
  } catch (err) {
    result.result.statusCode = err.response?.status || null;
    result.result.notes = `请求失败: ${err.code || err.message}`;
  }

  return result;
}

async function checkGenericApi(school) {
  const result = {
    schoolId: school.id,
    systemType: school.systemType,
    scheduleUrl: school.scheduleUrl || '(空)',
    scheduleMethod: school.scheduleMethod || 'GET',
    result: {
      accessible: false,
      statusCode: null,
      responseTime: null,
      contentType: null,
      dataType: null,
      dataValid: false,
      keyFieldsFound: [],
      csrfRequired: school.needCsrfToken || false,
      csrfTokenName: school.csrfTokenName || '',
      notes: '',
    },
  };

  const checkUrls = [];

  if (school.scheduleUrl) {
    checkUrls.push({ type: 'scheduleUrl', url: school.scheduleUrl });
  }
  if (school.loginUrl) {
    checkUrls.push({ type: 'loginUrl', url: school.loginUrl });
  }

  if (checkUrls.length === 0) {
    result.result.notes = `${school.systemType} 系统：无可用 URL 配置，可能需要从登录后动态获取`;
    return result;
  }

  for (const item of checkUrls) {
    try {
      const startTime = Date.now();

      const config = {
        method: 'GET',
        url: item.url,
        timeout: TIMEOUT,
        validateStatus: () => true,
        headers: { ...DEFAULT_HEADERS },
        maxRedirects: 5,
      };

      const response = await axios(config);

      result.result.responseTime = Date.now() - startTime;
      result.result.statusCode = response.status;
      result.result.contentType = response.headers['content-type'] || '';

      if (response.status === 200 || response.status === 302) {
        result.result.accessible = true;
      }

      const data = typeof response.data === 'string' ? response.data : '';
      const ct = (result.result.contentType || '').toLowerCase();

      if (ct.includes('json')) {
        result.result.dataType = 'json';
        try {
          JSON.parse(data);
          result.result.dataValid = true;
          result.result.keyFieldsFound.push('valid_json');
        } catch {
          result.result.notes = '返回内容格式异常';
        }
      } else if (ct.includes('html') || ct.includes('javascript')) {
        result.result.dataType = ct.includes('javascript') ? 'javascript' : 'html';

        const sysMarkers = getSystemMarkers(school.systemType);
        const foundMarkers = sysMarkers.filter(m =>
          new RegExp(m, 'i').test(data)
        );

        result.result.keyFieldsFound = foundMarkers;
        result.result.dataValid = foundMarkers.length > 0;

        if (item.type === 'loginUrl') {
          result.result.notes = `${school.systemType} 系统：登录页可访问`;
          if (foundMarkers.length > 0) {
            result.result.notes += `，检测到系统特征: ${foundMarkers.slice(0, 3).join(', ')}`;
          }
        } else {
          if (result.result.dataValid) {
            result.result.notes = `${school.systemType} 系统课表页可访问，检测到特征标识`;
          } else {
            result.result.notes = `${school.systemType} 系统页面可访问，建议深度检测或手动验证`;
          }
        }
      } else {
        result.result.dataType = 'unknown';
        result.result.notes = `${school.systemType} 系统 (${item.type}) 返回 Content-Type: ${result.result.contentType}`;
      }

      break;
    } catch (err) {
      result.result.statusCode = err.response?.status || null;
      result.result.notes = `${school.systemType} 系统 (${item.type}) 请求失败: ${err.code || err.message}`;
    }
  }

  return result;
}

function getSystemMarkers(systemType) {
  const markers = {
    custom: ['course.table', 'for-std', 'print-data', 'rightFrame', 'course-table'],
    chengfang: ['chengfang', '/api/', '/course/'],
    ynsmart: ['aixiaoyuan', 'ynsmart', '/api/v'],
    chaoxing: ['chaoxing', 'superstar', 'fanya'],
  };
  return markers[systemType] || ['schedule', 'course', 'timetable'];
}

const CHECKERS = {
  zfsoft: checkZfsoftApi,
  qiangzhi: checkQiangzhiApi,
  shuwei: checkShuweiApi,
  kingosoft: checkKingosoftApi,
};

async function checkSchoolApi(school) {
  const checker = CHECKERS[school.systemType] || checkGenericApi;
  return checker(school);
}

function formatResult(result, index, total) {
  const pad = String(total).length;
  const idx = chalk.gray(`[${String(index + 1).padStart(pad, ' ')}/${total}]`);
  const schoolId = chalk.cyan(result.schoolId);
  const sysType = formatSystemType(result.systemType);
  const status = formatAccessibleStatus(result.result);
  const code = result.result.statusCode !== null
    ? formatStatusCode(result.result.statusCode)
    : chalk.gray('----');
  const time = result.result.responseTime !== null
    ? chalk.white(`${result.result.responseTime}ms`)
    : chalk.gray('   -ms');
  const dataType = formatDataType(result.result.dataType);
  const valid = result.result.dataValid
    ? chalk.green.bold('✓ 有效')
    : chalk.red('✗ 无效');

  console.log(`  ${idx} ${schoolId} ${sysType} ${status} ${code} ${time} ${dataType} ${valid}`);

  if (result.result.notes) {
    console.log(chalk.gray(`     └─ ${result.result.notes}`));
  }

  if (result.result.keyFieldsFound && result.result.keyFieldsFound.length > 0) {
    console.log(
      chalk.gray(`     └─ 关键字段: `) +
      chalk.yellow(result.result.keyFieldsFound.join(', '))
    );
  }
}

function formatSystemType(type) {
  const colors = {
    zfsoft: chalk.green.bold,
    qiangzhi: chalk.blue.bold,
    shuwei: chalk.magenta.bold,
    kingosoft: chalk.cyan.bold,
    custom: chalk.yellow.bold,
    chengfang: chalk.white.bold,
    ynsmart: chalk.gray.bold,
    chaoxing: chalk.red.bold,
  };
  const fn = colors[type] || chalk.white;
  return fn(`[${type}]`);
}

function formatAccessibleStatus(result) {
  if (result.accessible) {
    return chalk.green.bold('✓ 可访问');
  }
  return chalk.red.bold('✗ 不可用');
}

function formatStatusCode(code) {
  if (code >= 200 && code < 300) return chalk.green(`HTTP ${code}`);
  if (code >= 300 && code < 400) return chalk.yellow(`HTTP ${code}`);
  if (code >= 400 && code < 500) return chalk.red(`HTTP ${code}`);
  if (code >= 500) return chalk.red.bold(`HTTP ${code}`);
  return chalk.gray(`HTTP ${code}`);
}

function formatDataType(type) {
  if (!type) return chalk.gray('-');
  const map = {
    json: chalk.green.bold('JSON'),
    html: chalk.blue.bold('HTML'),
    javascript: chalk.magenta.bold('JS'),
    unknown: chalk.gray('?'),
  };
  return map[type] || chalk.white(type.toUpperCase());
}

async function runWithConcurrency(items, handler, concurrency) {
  const results = [];
  const executing = new Set();

  for (let i = 0; i < items.length; i++) {
    const promise = handler(items[i], i, items.length).then(r => {
      executing.delete(promise);
      return r;
    });
    executing.add(promise);
    results.push(promise);

    if (executing.size >= concurrency) {
      await Promise.race(executing);
    }
  }

  return Promise.all(results);
}

function generateStats(results) {
  const total = results.length;
  const byType = {};

  for (const r of results) {
    const t = r.systemType;
    if (!byType[t]) byType[t] = { total: 0, passed: 0, failed: 0, dataValid: 0 };
    byType[t].total++;
    if (r.result.accessible) byType[t].passed++;
    else byType[t].failed++;
    if (r.result.dataValid) byType[t].dataValid++;
  }

  const passed = results.filter(r => r.result.accessible).length;
  const dataValidCount = results.filter(r => r.result.dataValid).length;

  return {
    total,
    passed,
    failed: total - passed,
    passRate: total > 0 ? ((passed / total) * 100).toFixed(1) + '%' : '0%',
    dataValidCount,
    dataValidRate: total > 0 ? ((dataValidCount / total) * 100).toFixed(1) + '%' : '0%',
    byType,
  };
}

function printSummary(stats) {
  console.log();
  console.log(chalk.bold.white('  ── 统计汇总 ──'));
  console.log();
  console.log(`  总计学校:   ${chalk.bold(stats.total)} 所`);
  console.log(`  可访问:     ${chalk.green.bold(stats.passed)} 所`);
  console.log(`  不可用:     ${chalk.red.bold(stats.failed)} 所`);
  console.log(`  通过率:     ${stats.passRate}`);
  console.log(`  数据有效:   ${chalk.cyan.bold(stats.dataValidCount)} 所`);
  console.log(`  数据有效率: ${stats.dataValidRate}`);
  console.log();

  console.log(chalk.bold.white('  按系统类型分组:'));
  console.log();

  const sortedTypes = Object.entries(stats.byType)
    .sort((a, b) => b[1].total - a[1].total);

  for (const [type, info] of sortedTypes) {
    const rate = info.total > 0 ? ((info.passed / info.total) * 100).toFixed(0) : 0;
    const rateColor = rate >= 80 ? chalk.green : rate >= 50 ? chalk.yellow : chalk.red;
    const bar = generateBar(rate);
    console.log(
      `    ${formatSystemType(type).replace(/[\[\]]/g, '')}`.padEnd(16) +
      `${String(info.total).padStart(4)} 所  ` +
      `${String(info.passed).padStart(3)} ✓  ` +
      `${String(info.failed).padStart(3)} ✗  ` +
      rateColor(`${rate}%`.padStart(4)) +
      `  ${bar}`
    );
  }

  console.log();
  printRecommendations(stats);
}

function generateBar(percent) {
  const filled = Math.round(percent / 5);
  const empty = 20 - filled;
  return chalk.green('█'.repeat(filled)) + chalk.gray('░'.repeat(empty));
}

function printRecommendations(stats) {
  const issues = [];

  for (const [type, info] of Object.entries(stats.byType)) {
    const rate = info.total > 0 ? (info.passed / info.total) * 100 : 0;
    if (rate < 50 && info.total >= 3) {
      issues.push({
        type,
        issue: `普遍不可用 (${rate.toFixed(0)}% 可访问率)`,
        suggestion: getTypeSuggestion(type),
      });
    }
    if (info.total > 0 && info.dataValid < info.passed * 0.5) {
      issues.push({
        type,
        issue: '数据格式验证通过率低',
        suggestion: '考虑增加深度检测模式 (--deep) 或检查 jsonMapping 配置',
      });
    }
  }

  if (issues.length === 0) {
    console.log(chalk.green.bold('  ✓ 所有系统类型运行状况良好'));
    return;
  }

  console.log(chalk.bold.white('  建议措施:'));
  console.log();
  for (const item of issues) {
    console.log(chalk.yellow(`  ⚠ ${item.type}: ${item.issue}`));
    console.log(chalk.gray(`     → ${item.suggestion}`));
  }
}

function getTypeSuggestion(type) {
  const suggestions = {
    zfsoft: '确认是否需要 CSRF Token 和有效的 Session Cookie',
    qiangzhi: '检查是否需要先访问 loginUrl 获取 Cookie',
    shuwei: '确认 eams 系统是否正常运行',
    kingosoft: '验证 jwglxt/sdnzjw 路径是否正确',
    custom: '自定义系统可能需要特殊处理逻辑',
    chengfang: '乘方系统通常需要登录后才能获取完整 API',
    ynsmart: '云南智慧系统可能使用不同的认证机制',
    chaoxing: '超星系统通常集成在 fanya 平台中',
  };
  return suggestions[type] || '请手动验证该类系统的配置和连接性';
}

async function main() {
  logHeader();

  const optionsLog = [
    [`超时: ${TIMEOUT}ms`, `并发: ${CONCURRENCY}`, `深度: ${DEEP_MODE ? '是' : '否'}`],
  ];
  if (SCHOOL_FILTER) optionsLog[0].push(`学校: ${SCHOOL_FILTER.join(', ')}`);
  if (TYPE_FILTER) optionsLog[0].push(`类型: ${TYPE_FILTER}`);
  console.log(chalk.gray('  配置: ') + optionsLog[0].join('  | '));
  console.log();

  let schools;
  try {
    schools = loadSchools();
  } catch (err) {
    console.error(chalk.red(`无法读取学校列表: ${err.message}`));
    process.exit(1);
  }

  if (schools.length === 0) {
    console.log(chalk.yellow('  没有匹配的学校'));
    return;
  }

  console.log(chalk.white(`  加载了 ${chalk.bold(schools.length)} 所学校`));

  const typeCounts = {};
  for (const s of schools) {
    typeCounts[s.systemType] = (typeCounts[s.systemType] || 0) + 1;
  }
  console.log(chalk.gray('  类型分布: ') + Object.entries(typeCounts)
    .map(([t, c]) => `${t}(${c})`).join(', '));
  console.log();

  console.log(chalk.bold.white('  ── 开始检测 ──'));
  console.log();

  const startTime = Date.now();
  const allResults = await runWithConcurrency(schools, async (school, idx, total) => {
    const result = await checkSchoolApi(school);
    formatResult(result, idx, total);
    return result;
  }, CONCURRENCY);
  const elapsed = Date.now() - startTime;

  console.log();
  console.log(chalk.bold.white('  ── 检测完成 ──'));

  const stats = generateStats(allResults);
  printSummary(stats);

  console.log();
  console.log(`  总耗时: ${chalk.white((elapsed / 1000).toFixed(1) + 's')}`);

  const outputPath = OUTPUT_PATH ||
    path.join(REPORT_DIR, `schedule-api-check-${new Date().toISOString().replace(/[:.]/g, '-')}.json`);

  const report = {
    meta: {
      generatedAt: new Date().toISOString(),
      tool: 'schedule-api-checker',
      version: '1.0.0',
      options: {
        deepMode: DEEP_MODE,
        timeout: TIMEOUT,
        concurrency: CONCURRENCY,
        schoolFilter: SCHOOL_FILTER,
        typeFilter: TYPE_FILTER,
      },
      academicInfo: {
        currentYear: getCurrentAcademicYear(),
        currentSemester: getCurrentSemester(),
      },
    },
    stats,
    results: allResults,
  };

  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, JSON.stringify(report, null, 2), 'utf-8');
  console.log();
  console.log(chalk.green.bold(`  报告已保存: ${outputPath}`));
  console.log();
}

main().catch(err => {
  console.error(chalk.red(`\n  致命错误: ${err.message}`));
  if (process.env.DEBUG) console.error(err.stack);
  process.exit(1);
});
