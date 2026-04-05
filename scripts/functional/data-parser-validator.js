#!/usr/bin/env node

'use strict';

const fs = require('fs');
const path = require('path');
const { program } = require('commander');
const chalk = require('chalk');

const ROOT_DIR = path.resolve(__dirname, '..', '..');
const SCHOOLS_JSON_PATH = path.join(ROOT_DIR, 'app', 'src', 'main', 'assets', 'schools.json');
const SAMPLES_DIR = path.join(__dirname, '..', 'config', 'samples');
const REPORT_DIR = path.join(ROOT_DIR, 'reports', 'json');

const REQUIRED_FIELDS = ['courseName', 'teacher', 'classroom', 'dayOfWeek', 'startSection', 'weekExpression'];

const FIELD_ALIASES = {
  courseName: ['kcmc', 'courseName', 'taskname'],
  teacher: ['xm', 'teacher', 'teacherName'],
  classroom: ['cdmc', 'room', 'location', 'classroom'],
  dayOfWeek: ['xqj', 'weekday', 'dayOfWeek'],
  startSection: ['jcs', 'startPeriod', 'startSection'],
  weekExpression: ['zcd', 'weeks', 'weekExpression']
};

program
  .name('data-parser-validator')
  .description('数据解析验证器 - 验证各学校 Extractor 的数据解析逻辑')
  .version('1.0.0')
  .option('-s, --school <ids>', '指定学校ID（逗号分隔）')
  .option('-t, --type <systemType>', '指定系统类型过滤')
  .option('--sample-dir <path>', '示例数据目录', SAMPLES_DIR)
  .option('--output <path>', '输出文件路径')
  .option('--verbose', '显示详细调试信息')
  .parse(process.argv);

const opts = program.opts();

function logHeader() {
  console.log();
  console.log(chalk.bold.cyan('╔══════════════════════════════════════════════════════════╗'));
  console.log(chalk.bold.cyan('║') + chalk.bold.white('           数据解析验证器 v1.0.0                      ') + chalk.bold.cyan('║'));
  console.log(chalk.bold.cyan('╚══════════════════════════════════════════════════════════╝'));
  console.log();
}

function loadSchools() {
  const raw = fs.readFileSync(SCHOOLS_JSON_PATH, 'utf-8');
  const schools = JSON.parse(raw);
  if (opts.school) {
    const filterIds = opts.school.split(',').map(s => s.trim().toLowerCase());
    return schools.filter(s => filterIds.includes(s.id.toLowerCase()));
  }
  if (opts.type) {
    return schools.filter(s => (s.systemType || '').toLowerCase() === opts.type.toLowerCase());
  }
  return schools;
}

function loadSampleData(systemType) {
  const sampleFiles = {
    zfsoft: 'zfsoft-sample.json',
    qiangzhi: 'qiangzhi-sample.html',
    shuwei: 'shuwei-sample.html',
    kingosoft: 'kingosoft-sample.html',
    custom: 'custom-sample.json'
  };

  const fileName = sampleFiles[systemType];
  if (!fileName) {
    return null;
  }

  const filePath = path.join(opts.sampleDir, fileName);
  if (!fs.existsSync(filePath)) {
    return null;
  }

  try {
    const content = fs.readFileSync(filePath, 'utf-8');
    if (fileName.endsWith('.json')) {
      return JSON.parse(content);
    }
    return content;
  } catch (err) {
    return null;
  }
}

function sanitizeHtml(text) {
  if (!text || typeof text !== 'string') return '';
  return text
    .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
    .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, ' ')
    .trim();
}

function truncateText(text, maxLength = 100) {
  if (!text) return text;
  if (text.length <= maxLength) return text;
  return text.substring(0, maxLength) + '...';
}

function extractValue(data, fieldKey, mapping) {
  const mappedKey = mapping[fieldKey];
  if (!mappedKey) return { value: null, source: null, error: `字段 ${fieldKey} 未配置映射` };

  let value = data;

  if (typeof data === 'object' && !Array.isArray(data)) {
    value = data[mappedKey];
  } else if (Array.isArray(data)) {
    value = data.length > 0 ? data[0][mappedKey] : undefined;
  }

  return {
    value: value !== undefined && value !== null ? String(value) : null,
    source: mappedKey,
    error: value === undefined || value === null ? `字段 ${mappedKey} 值为空` : null
  };
}

function parseZfsoftData(rawData, school) {
  const results = [];
  const mapping = school.jsonMapping || {};
  const courseListKey = mapping.courseList || 'kbList';
  const courses = rawData[courseListKey] || [];

  for (let i = 0; i < courses.length; i++) {
    const course = courses[i];
    const extracted = {};

    for (const field of REQUIRED_FIELDS) {
      const result = extractValue(course, field, mapping);
      extracted[field] = result.value !== null ? sanitizeHtml(result.value) : null;
    }

    results.push({
      index: i + 1,
      raw: course,
      extracted,
      isValid: Object.values(extracted).some(v => v && v.trim() !== '')
    });
  }

  return results;
}

function parseQiangzhiData(htmlContent, school) {
  const results = [];
  const cells = htmlContent.match(/<td[^>]*>([\s\S]*?)<\/td>/gi) || [];

  let cellIndex = 0;
  for (let i = 1; i < cells.length; i += 3) {
    const cell = cells[i];
    if (!cell.includes('coursename')) continue;

    const extracted = {
      courseName: sanitizeHtml((cell.match(/<span class="coursename"[^>]*>([\s\S]*?)<\/span>/i) || [])[1]),
      teacher: sanitizeHtml((cell.match(/<span class="teacher"[^>]*>([\s\S]*?)<\/span>/i) || [])[1]),
      classroom: sanitizeHtml((cell.match(/<span class="classroom"[^>]*>([\s\S]*?)<\/span>/i) || [])[1]),
      dayOfWeek: null,
      startSection: sanitizeHtml(cell.match(/第(\d+)-(\d+)节/)?.[0]?.replace('第', '')),
      weekExpression: sanitizeHtml((cell.match(/周次:\s*([\s\S]*?)(?:<br|<\/td)/i) || [])[1])
    };

    const dayMatch = cellIndex % 5 + 1;
    extracted.dayOfWeek = String(dayMatch);

    results.push({
      index: cellIndex + 1,
      raw: cell,
      extracted,
      isValid: Object.values(extracted).some(v => v && v.trim() !== '')
    });
    cellIndex++;
  }

  return results;
}

function parseShuweiData(htmlContent, school) {
  const results = [];
  const tds = htmlContent.match(/<td\s[^>]*taskname="[^"]*"[\s\S]*?<\/td>/gi) || [];

  for (let i = 0; i < tds.length; i++) {
    const td = tds[i];

    const extracted = {
      courseName: sanitizeHtml(td.match(/taskname="([^"]*)"/)?.[1]),
      teacher: sanitizeHtml(td.match(/teacher="([^"]*)"/)?.[1]),
      classroom: sanitizeHtml(td.match(/room="([^"]*)"/)?.[1]),
      dayOfWeek: null,
      startSection: null,
      weekExpression: sanitizeHtml(td.match(/weeks="([^"]*)"/)?.[1])
    };

    const parentRow = htmlContent.match(new RegExp(`<tr>[\\s\\S]*?${td.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}[\\s\\S]*?</tr>`));
    if (parentRow) {
      const tdsInRow = parentRow[0].match(/<td/g);
      if (tdsInRow) {
        extracted.dayOfWeek = String(tdsInRow.length - 1);
      }
    }

    results.push({
      index: i + 1,
      raw: td,
      extracted,
      isValid: Object.values(extracted).some(v => v && v.trim() !== '')
    });
  }

  return results;
}

function parseKingosoftData(htmlContent, school) {
  const results = [];
  const divs = htmlContent.match(/<div style="padding:[^"]*">[\s\S]*?<\/div>/gi) || [];

  for (let i = 0; i < divs.length; i++) {
    const div = divs[i];

    const extracted = {
      courseName: sanitizeHtml(div.match(/<b>([\s\S]*?)<\/b>/)?.[1]),
      teacher: sanitizeHtml(div.match(/<font color="#0066cc">([\s\S]*?)<\/font>/)?.[1]),
      classroom: sanitizeHtml(div.match(/<i>([\s\S]*?)<\/i>/)?.[1]),
      dayOfWeek: null,
      startSection: null,
      weekExpression: sanitizeHtml(div.match(/<u>([\s\S]*?)<\/u>/)?.[1])
    };

    const tableMatch = htmlContent.match(/<table[^>]*id="Table2"[\s\S]*?<\/table>/i);
    if (tableMatch) {
      const rows = tableMatch[0].match(/<tr align="center">[\s\S]*?<\/tr>/gi) || [];
      for (let r = 0; r < rows.length; r++) {
        if (rows[r].includes(div)) {
          extracted.dayOfWeek = String(r > 0 ? Math.min(r, 7) : 1);
          break;
        }
      }
    }

    const sectionMatch = divs[i].parentElement?.textContent?.match(/第(\d+)-(\d+)节/);
    if (!sectionMatch) {
      const allSections = htmlContent.match(/第(\d+)-(\d+)节<br\/?>/gi) || [];
      extracted.startSection = allSections[Math.floor(i / 2)]?.replace(/<br\/?>/g, '') || null;
    }

    results.push({
      index: i + 1,
      raw: div,
      extracted,
      isValid: Object.values(extracted).some(v => v && v.trim() !== '')
    });
  }

  return results;
}

function parseCustomData(jsonData, school) {
  const results = [];
  const courses = jsonData.courses || [];

  for (let i = 0; i < courses.length; i++) {
    const course = courses[i];

    const weeksStr = Array.isArray(course.weeks)
      ? course.weeks.join(',')
      : (course.weeks || '');

    const extracted = {
      courseName: sanitizeHtml(course.courseName),
      teacher: sanitizeHtml(course.teacherName),
      classroom: sanitizeHtml(course.location),
      dayOfWeek: course.weekday ? String(course.weekday) : null,
      startSection: course.startPeriod ? `${course.startPeriod}-${course.endPeriod || course.startPeriod}` : null,
      weekExpression: weeksStr
    };

    results.push({
      index: i + 1,
      raw: course,
      extracted,
      isValid: Object.values(extracted).some(v => v && v.trim() !== '')
    });
  }

  return results;
}

function parseSampleData(sampleData, systemType, school) {
  switch (systemType) {
    case 'zfsoft':
      return typeof sampleData === 'object' ? parseZfsoftData(sampleData, school) : [];
    case 'qiangzhi':
      return typeof sampleData === 'string' ? parseQiangzhiData(sampleData, school) : [];
    case 'shuwei':
      return typeof sampleData === 'string' ? parseShuweiData(sampleData, school) : [];
    case 'kingosoft':
      return typeof sampleData === 'string' ? parseKingosoftData(sampleData, school) : [];
    case 'custom':
      return typeof sampleData === 'object' ? parseCustomData(sampleData, school) : [];
    default:
      return [];
  }
}

function testAnomalyHandling(parserFn, systemType, school) {
  const anomalies = [
    { name: 'null值', data: null },
    { name: 'undefined值', data: undefined },
    { name: '空字符串', data: systemType === 'custom' ? { courses: [] } : (systemType === 'zfsoft' ? { kbList: [] } : '') },
    { name: 'HTML实体', data: systemType === 'custom' ? { courses: [{ courseName: '&lt;script&gt;XSS&lt;/script&gt;', teacherName: 'Tom&amp;Jerry', location: 'A&amp;B栋', weekday: 1, startPeriod: 1, endPeriod: 2, weeks: [1,2,3] }] } : (systemType === 'zfsoft' ? { kbList: [{ kcmc: '&lt;b&gt;加粗&lt;/b&gt;', xm: '张&amp;李', cdmc: 'A&gt;B', xqj: '1', jcs: '1-2', zcd: '1-16周' }] } : `<span>&lt;script&gt;alert(1)&lt;/script&gt;</span>`) },
    { name: '超长文本', data: systemType === 'custom' ? { courses: [{ courseName: 'A'.repeat(500), teacherName: 'B'.repeat(200), location: 'C'.repeat(300), weekday: 1, startPeriod: 1, endPeriod: 2, weeks: [1] }] } : (systemType === 'zfsoft' ? { kbList: [{ kcmc: 'X'.repeat(500), xm: 'Y'.repeat(200), cdmc: 'Z'.repeat(300), xqj: '1', jcs: '1-2', zcd: '1-16周' }] } : `<span>${'长'.repeat(500)}</span>`) },
    { name: 'Unicode字符', data: systemType === 'custom' ? { courses: [{ courseName: '日语テスト🎓한국어العربية', teacherName: '张三😀', location: '教室🏫', weekday: 1, startPeriod: 1, endPeriod: 2, weeks: [1] }] } : (systemType === 'zfsoft' ? { kbList: [{ kcmc: '测试课程📚', xm: '教师👨‍🏫', cdmc: '教室🏫', xqj: '1', jcs: '1-2', zcd: '1-16周' }] } : `<span>Unicode测试😀🎓</span>`) },
    { name: '嵌套HTML标签', data: systemType === 'custom' ? { courses: [{ courseName: '<div><span><b>嵌套</b></span></div>', teacherName: '<em>斜体</em><strong>粗体</strong>', location: '<a href="#">链接</a>', weekday: 1, startPeriod: 1, endPeriod: 2, weeks: [1] }] } : (systemType === 'zfsoft' ? { kbList: [{ kcmc: '<div><span>课程</span></div>', xm: '<b>老师</b>', cdmc: '<i>教室</i>', xqj: '1', jcs: '1-2', zcd: '1-16周' }] } : `<div><span><b>深层嵌套</b></span></div>`) }
  ];

  const testResults = [];

  for (const anomaly of anomalies) {
    try {
      const result = parserFn(anomaly.data, systemType, school);
      testResults.push({
        anomalyName: anomaly.name,
        passed: true,
        crashed: false,
        resultCount: Array.isArray(result) ? result.length : 0,
        error: null
      });
    } catch (err) {
      testResults.push({
        anomalyName: anomaly.name,
        passed: false,
        crashed: true,
        resultCount: 0,
        error: err.message
      });
    }
  }

  return testResults;
}

function calculateQualityScores(parsedResults, anomalyTests) {
  if (!parsedResults || parsedResults.length === 0) {
    return {
      completenessScore: 0,
      validityScore: 0,
      anomalyScore: 0,
      overallScore: 0
    };
  }

  let totalFields = 0;
  let filledFields = 0;
  let validFields = 0;
  let validCourses = 0;

  for (const item of parsedResults) {
    if (!item.extracted) continue;

    let itemFilled = 0;
    for (const field of REQUIRED_FIELDS) {
      totalFields++;
      const val = item.extracted[field];
      if (val !== null && val !== undefined && String(val).trim() !== '') {
        filledFields++;
        itemFilled++;
        const truncatedVal = truncateText(String(val));
        if (truncatedVal && truncatedVal.trim() !== '') {
          validFields++;
        }
      }
    }
    if (itemFilled >= 4) validCourses++;
  }

  const completenessScore = totalFields > 0 ? Math.round((filledFields / totalFields) * 100) : 0;
  const validityScore = totalFields > 0 ? Math.round((validFields / totalFields) * 100) : 0;
  const passedAnomalies = anomalyTests.filter(a => a.passed && !a.crashed).length;
  const anomalyScore = anomalyTests.length > 0 ? Math.round((passedAnomalies / anomalyTests.length) * 100) : 100;

  const overallScore = Math.round(
    completenessScore * 0.35 +
    validityScore * 0.35 +
    anomalyScore * 0.30
  );

  return {
    completenessScore,
    validityScore,
    anomalyScore,
    overallScore
  };
}

function validateSchool(school) {
  const systemType = school.systemType || 'unknown';
  const sampleData = loadSampleData(systemType);

  if (!sampleData) {
    return {
      schoolId: school.id,
      schoolName: school.name,
      systemType,
      hasSample: false,
      parsedCourses: [],
      anomalyTests: [],
      scores: { completenessScore: 0, validityScore: 0, anomalyScore: 0, overallScore: 0 },
      status: 'no_sample',
      errors: [`未找到 ${systemType} 类型的示例数据`]
    };
  }

  let parsedCourses = [];
  let parseError = null;

  try {
    parsedCourses = parseSampleData(sampleData, systemType, school);
  } catch (err) {
    parseError = err.message;
  }

  const anomalyTests = testAnomalyHandling(parseSampleData, systemType, school);
  const scores = calculateQualityScores(parsedCourses, anomalyTests);

  const status = parseError
    ? 'parse_error'
    : scores.overallScore >= 80
      ? 'excellent'
      : scores.overallScore >= 60
        ? 'good'
        : scores.overallScore >= 40
          ? 'fair'
          : 'poor';

  return {
    schoolId: school.id,
    schoolName: school.name,
    systemType,
    hasSample: true,
    parsedCourses,
    anomalyTests,
    scores,
    status,
    errors: parseError ? [parseError] : []
  };
}

function printValidationResult(result) {
  const { schoolId, schoolName, systemType, hasSample, parsedCourses, scores, status, errors } = result;

  const statusColors = {
    excellent: chalk.green.bold,
    good: chalk.green,
    fair: chalk.yellow,
    poor: chalk.red,
    parse_error: chalk.red.bold,
    no_sample: chalk.gray
  };

  const statusIcons = {
    excellent: '✅',
    good: '✅',
    fair: '⚠️',
    poor: '❌',
    parse_error: '💥',
    no_sample: '📭'
  };

  const statusLabels = {
    excellent: '优秀',
    good: '良好',
    fair: '一般',
    poor: '较差',
    parse_error: '解析错误',
    no_sample: '无样本'
  };

  console.log(`  ${chalk.cyan.bold(schoolId)} | ${schoolName} [${chalk.magenta(systemType)}]`);
  console.log(`    状态: ${statusColors[status](`${statusIcons[status]} ${statusLabels[status]}`)}`);

  if (errors.length > 0) {
    console.log(chalk.red(`    错误: ${errors.join('; ')}`));
  }

  if (hasSample) {
    console.log(`    解析课程数: ${chalk.white(parsedCourses.length)}`);
    console.log(`    字段完整性: ${scores.completenessScore}%`);
    console.log(`    数据有效性: ${scores.validityScore}%`);
    console.log(`    异常处理: ${scores.anomalyScore}%`);
    console.log(`    综合评分: ${scores.overallScore >= 80 ? chalk.green.bold(scores.overallScore + '分') : scores.overallScore >= 60 ? chalk.yellow(scores.overallScore + '分') : chalk.red(scores.overallScore + '分')}`);

    if (opts.verbose && parsedCourses.length > 0) {
      console.log('\n    详细解析结果:');
      for (const course of parsedCourses.slice(0, 3)) {
        console.log(`      课程 #${course.index}:`);
        for (const [key, value] of Object.entries(course.extracted)) {
          const displayValue = value !== null ? truncateText(value, 50) : chalk.gray('(空)');
          console.log(`        ${chalk.gray(key.padEnd(15))}: ${displayValue}`);
        }
      }
      if (parsedCourses.length > 3) {
        console.log(`      ... 还有 ${parsedCourses.length - 3} 门课程`);
      }
    }
  }

  console.log();
}

async function main() {
  logHeader();

  console.log(chalk.gray(`  示例数据目录: ${opts.sampleDir}`));
  console.log(chalk.gray(`  报告输出目录: ${REPORT_DIR}`));

  if (opts.school) console.log(chalk.yellow(`  学校过滤: ${opts.school}`));
  if (opts.type) console.log(chalk.yellow(`  类型过滤: ${opts.type}`));
  console.log();

  let schools;
  try {
    schools = loadSchools();
  } catch (err) {
    console.error(chalk.red(`无法读取学校列表: ${err.message}`));
    process.exit(1);
  }

  console.log(chalk.white(`  加载了 ${chalk.bold(schools.length)} 所学校\n`));
  console.log(chalk.bold.white('  ── 开始验证 ──'));
  console.log();

  const validationResults = [];
  const startTime = Date.now();

  for (const school of schools) {
    const result = validateSchool(school);
    validationResults.push(result);
    printValidationResult(result);
  }

  const elapsed = Date.now() - startTime;

  console.log(chalk.bold.white('  ── 验证完成 ──'));
  console.log();

  const summaryStats = {
    totalSchools: validationResults.length,
    excellent: validationResults.filter(r => r.status === 'excellent').length,
    good: validationResults.filter(r => r.status === 'good').length,
    fair: validationResults.filter(r => r.status === 'fair').length,
    poor: validationResults.filter(r => r.status === 'poor').length,
    parseErrors: validationResults.filter(r => r.status === 'parse_error').length,
    noSamples: validationResults.filter(r => r.status === 'no_sample').length,
    avgScore: validationResults.reduce((sum, r) => sum + r.scores.overallScore, 0) / Math.max(1, validationResults.length)
  };

  console.log(chalk.white.bold('  统计摘要:'));
  console.log(`    总计学校:     ${chalk.bold(summaryStats.totalSchools)}`);
  console.log(`    优秀 (${chalk.green('≥80分')}): ${chalk.green(summaryStats.excellent)}`);
  console.log(`    良好 (${chalk.green('60-79')}): ${chalk.green(summaryStats.good)}`);
  console.log(`    一般 (${chalk.yellow('40-59')}): ${chalk.yellow(summaryStats.fair)}`);
  console.log(`    较差 (${chalk.red('<40')}):   ${chalk.red(summaryStats.poor)}`);
  console.log(`    解析错误:     ${chalk.red.bold(summaryStats.parseErrors)}`);
  console.log(`    缺少样本:     ${chalk.gray(summaryStats.noSamples)}`);
  console.log(`    平均评分:     ${chalk.white(Math.round(summaryStats.avgScore) + '分')}`);
  console.log(`    总耗时:       ${chalk.white((elapsed / 1000).toFixed(2) + 's')}`);
  console.log();

  const report = {
    meta: {
      generatedAt: new Date().toISOString(),
      tool: 'data-parser-validator',
      version: '1.0.0',
      options: {
        schoolFilter: opts.school || null,
        typeFilter: opts.type || null,
        sampleDir: opts.sampleDir
      }
    },
    summary: summaryStats,
    results: validationResults.map(r => ({
      schoolId: r.schoolId,
      schoolName: r.schoolName,
      systemType: r.systemType,
      status: r.status,
      scores: r.scores,
      courseCount: r.parsedCourses.length,
      hasSample: r.hasSample,
      errors: r.errors,
      anomalyTests: r.anomalyTests,
      parsedCourses: r.parsedCourses.map(c => ({
        index: c.index,
        extracted: c.extracted,
        isValid: c.isValid
      }))
    }))
  };

  if (!fs.existsSync(REPORT_DIR)) {
    fs.mkdirSync(REPORT_DIR, { recursive: true });
  }

  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const outputPath = opts.output || path.join(REPORT_DIR, `parser-validation-${timestamp}.json`);

  fs.writeFileSync(outputPath, JSON.stringify(report, null, 2), 'utf-8');
  console.log(chalk.green.bold(`  报告已保存: ${outputPath}`));
  console.log();
}

main().catch(err => {
  console.error(chalk.red(`\n  致命错误: ${err.message}`));
  if (process.env.DEBUG) console.error(err.stack);
  process.exit(1);
});
