#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { program } = require('commander');

const ROOT_DIR = path.resolve(__dirname, '..', '..');
const SCHOOLS_JSON_PATH = path.join(ROOT_DIR, 'app', 'src', 'main', 'assets', 'schools.json');
const EXTRACTOR_DIR = path.join(ROOT_DIR, 'app', 'src', 'main', 'java', 'com', 'wind', 'ggbond', 'classtime', 'util', 'extractor');
const OUTPUT_DIR = path.join(ROOT_DIR, 'output');

const EXTRACTOR_FILE_PATTERN = /^(.+)Extractor\.kt$/;
const BASE_EXTRACTORS = ['SchoolScheduleExtractor', 'UniversalSmartExtractor'];

function ensureOutputDir() {
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  }
}

function parseSchoolsJson() {
  try {
    const content = fs.readFileSync(SCHOOLS_JSON_PATH, 'utf-8');
    const schools = JSON.parse(content);
    if (!Array.isArray(schools)) {
      throw new Error('schools.json 格式错误：根元素应为数组');
    }
    return schools;
  } catch (error) {
    if (error.code === 'ENOENT') {
      throw new Error(`找不到 schools.json 文件：${SCHOOLS_JSON_PATH}`);
    }
    throw error;
  }
}

function scanExtractors() {
  try {
    if (!fs.existsSync(EXTRACTOR_DIR)) {
      throw new Error(`Extractor 目录不存在：${EXTRACTOR_DIR}`);
    }
    const files = fs.readdirSync(EXTRACTOR_DIR);
    const extractors = [];
    for (const file of files) {
      const match = file.match(EXTRACTOR_FILE_PATTERN);
      if (match) {
        const schoolIdUpper = match[1];
        const schoolId = schoolIdUpper.toLowerCase();
        extractors.push({
          id: schoolId,
          fileName: file,
          filePath: path.join(EXTRACTOR_DIR, file)
        });
      }
    }
    return extractors;
  } catch (error) {
    throw error;
  }
}

function analyzeData(schools, extractors) {
  const configuredIds = new Set(schools.map(s => s.id));
  const extractorIds = new Set(extractors.map(e => e.id));
  const extractorMap = new Map(extractors.map(e => [e.id, e]));
  const schoolMap = new Map(schools.map(s => [s.id, s]));

  const pendingList = [];
  for (const extractor of extractors) {
    if (!configuredIds.has(extractor.id)) {
      pendingList.push({
        id: extractor.id,
        name: '',
        extractorFile: extractor.fileName,
        reason: 'has_extractor_no_config'
      });
    }
  }

  const anomalies = [];
  for (const school of schools) {
    if (!extractorIds.has(school.id)) {
      anomalies.push({
        id: school.id,
        name: school.name || '',
        systemType: school.systemType || '',
        reason: 'has_config_no_extractor'
      });
    }
  }

  const bySystemType = {};
  for (const school of schools) {
    const type = school.systemType || 'unknown';
    bySystemType[type] = (bySystemType[type] || 0) + 1;
  }

  const byProvince = {};
  for (const school of schools) {
    const province = school.province || '未知';
    byProvince[province] = (byProvince[province] || 0) + 1;
  }

  const pendingBySystemType = {};
  for (const item of pendingList) {
    pendingBySystemType['unknown'] = (pendingBySystemType['unknown'] || 0) + 1;
  }

  return {
    timestamp: new Date().toISOString(),
    summary: {
      configuredSchools: schools.length,
      extractorsCount: extractors.length,
      pendingSchools: pendingList.length,
      anomalies: anomalies.length
    },
    bySystemType,
    byProvince,
    pendingList,
    anomalies,
    configuredSchools: schools,
    extractors
  };
}

function formatTable(data, options = {}) {
  const { filterKey, filterValue } = options;
  let items = [];

  if (filterKey === 'province') {
    items = data.configuredSchools.filter(s => s.province === filterValue);
  } else if (filterKey === 'systemType') {
    items = data.configuredSchools.filter(s => s.systemType === filterValue);
  } else {
    items = data.configuredSchools;
  }

  if (items.length === 0) {
    return '没有匹配的数据';
  }

  const headers = ['ID', '名称', '简称', '省份', '系统类型'];
  const rows = items.map(s => [
    s.id,
    s.name || '-',
    s.shortName || '-',
    s.province || '-',
    s.systemType || '-'
  ]);

  const colWidths = headers.map((h, i) =>
    Math.max(h.length, ...rows.map(r => (r[i] || '').length))
  );

  const separator = colWidths.map(w => '-'.repeat(w)).join('-+-');

  let output = '\n' + headers.map((h, i) => h.padEnd(colWidths[i])).join(' | ') + '\n';
  output += separator + '\n';
  for (const row of rows) {
    output += row.map((cell, i) => (cell || '').padEnd(colWidths[i])).join(' | ') + '\n';
  }
  return output;
}

function formatSummaryTable(data) {
  const { summary, bySystemType, byProvince } = data;

  let output = '\n╔══════════════════════════════════════════════════════════╗\n';
  output += '║                    学校分析报告摘要                      ║\n';
  output += '╠══════════════════════════════════════════════════════════╣\n';
  output += `║  已配置学校数: ${String(summary.configuredSchools).padStart(4)}                                      ║\n`;
  output += `║  Extractor 数: ${String(summary.extractorsCount).padStart(4)}                                      ║\n`;
  output += `║  待上架学校数: ${String(summary.pendingSchools).padStart(4)}                                      ║\n`;
  output += `║  异常情况数:   ${String(summary.anomalies).padStart(4)}                                      ║\n`;
  output += '╚══════════════════════════════════════════════════════════╝\n';

  output += '\n【按系统类型分布】\n';
  const sortedTypes = Object.entries(bySystemType).sort((a, b) => b[1] - a[1]);
  for (const [type, count] of sortedTypes) {
    output += `  ${type.padEnd(16)} : ${count} 所\n`;
  }

  output += '\n【按省份分布】\n';
  const sortedProvinces = Object.entries(byProvince).sort((a, b) => b[1] - a[1]);
  for (const [province, count] of sortedProvinces) {
    output += `  ${province.padEnd(8)} : ${count} 所\n`;
  }

  return output;
}

function formatPendingTable(data) {
  const { pendingList } = data;

  if (pendingList.length === 0) {
    return '\n✅ 所有已开发的 Extractor 都已配置到 schools.json，无待上架学校！';
  }

  let output = `\n📋 待上架学校清单 (共 ${pendingList.length} 所)\n\n`;

  const headers = ['ID', 'Extractor 文件', '原因'];
  const rows = pendingList.map(p => [p.id, p.extractorFile, p.reason]);

  const colWidths = headers.map((h, i) =>
    Math.max(h.length, ...rows.map(r => (r[i] || '').length))
  );

  const separator = colWidths.map(w => '-'.repeat(w)).join('-+-');

  output += headers.map((h, i) => h.padEnd(colWidths[i])).join(' | ') + '\n';
  output += separator + '\n';
  for (const row of rows) {
    output += row.map((cell, i) => (cell || '').padEnd(colWidths[i])).join(' | ') + '\n';
  }

  return output;
}

function formatDiffTable(data) {
  const { pendingList, anomalies } = data;

  let output = '\n🔍 差异分析报告\n';

  if (pendingList.length > 0) {
    output += `\n⚠️  有 Extractor 但未配置 (${pendingList.length} 所):\n`;
    for (const item of pendingList.slice(0, 20)) {
      output += `   - ${item.id} (${item.extractorFile})\n`;
    }
    if (pendingList.length > 20) {
      output += `   ... 还有 ${pendingList.length - 20} 所\n`;
    }
  } else {
    output += '\n✅ 无待上架学校\n';
  }

  if (anomalies.length > 0) {
    output += `\n❌ 已配置但无 Extractor (${anomalies.length} 所):\n`;
    for (const item of anomalies) {
      output += `   - ${item.id}: ${item.name} [${item.systemType}]\n`;
    }
  } else {
    output += '\n✅ 无异常情况\n';
  }

  return output;
}

function formatExtractorsTable(data) {
  const { extractors } = data;

  let output = `\n📦 Extractor 清单 (共 ${extractors.length} 个)\n\n`;

  const grouped = {};
  for (const e of extractors) {
    const firstChar = e.id.charAt(0).toUpperCase();
    if (!grouped[firstChar]) grouped[firstChar] = [];
    grouped[firstChar].push(e);
  }

  const sortedKeys = Object.keys(grouped).sort();
  for (const key of sortedKeys) {
    output += `【${key}】 `;
    output += grouped[key].map(e => e.id).join(', ');
    output += '\n';
  }

  return output;
}

function generateCSV(data, outputPath) {
  ensureOutputDir();

  const { pendingList } = data;
  let csv = 'id,name,extractorFile,reason\n';

  for (const item of pendingList) {
    csv += `${item.id},${item.name},${item.extractorFile},${item.reason}\n`;
  }

  const filePath = outputPath || path.join(OUTPUT_DIR, 'pending-schools.csv');
  fs.writeFileSync(filePath, csv, 'utf-8');
  return filePath;
}

function generateJSONReport(data, outputPath) {
  ensureOutputDir();

  const reportData = {
    timestamp: data.timestamp,
    summary: data.summary,
    bySystemType: data.bySystemType,
    byProvince: data.byProvince,
    pendingList: data.pendingList,
    anomalies: data.anomalies
  };

  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const defaultPath = `school-analysis-${timestamp}.json`;
  const filePath = outputPath || path.join(OUTPUT_DIR, defaultPath);

  fs.writeFileSync(filePath, JSON.stringify(reportData, null, 2), 'utf-8');
  return filePath;
}

function getFilteredData(data, options) {
  const { filterKey, filterValue } = options;

  if (!filterKey || !filterValue) {
    return data;
  }

  const filteredSchools = data.configuredSchools.filter(s => {
    if (filterKey === 'province') return s.province === filterValue;
    if (filterKey === 'systemType') return s.systemType === filterValue;
    return true;
  });

  return {
    ...data,
    configuredSchools: filteredSchools,
    summary: {
      ...data.summary,
      configuredSchools: filteredSchools.length
    }
  };
}

function outputResult(content, format, options = {}) {
  switch (format) {
    case 'json':
      console.log(JSON.stringify(content, null, 2));
      break;
    case 'csv':
      if (content.pendingList) {
        const csvPath = generateCSV(content, options.output);
        console.log(`\n✅ CSV 文件已生成: ${csvPath}`);
      } else {
        console.log('CSV 格式仅支持 pending 命令');
      }
      break;
    case 'table':
    default:
      console.log(content);
      break;
  }

  if (options.output && format !== 'csv' && format !== 'json') {
    ensureOutputDir();
    const filePath = path.resolve(options.output);
    fs.writeFileSync(filePath, typeof content === 'string' ? content : JSON.stringify(content, null, 2), 'utf-8');
    console.log(`\n📁 输出文件: ${filePath}`);
  }
}

function runAnalysis() {
  console.log('📊 正在分析学校数据...\n');

  const schools = parseSchoolsJson();
  console.log(`✅ 已解析 schools.json: ${schools.length} 所学校`);

  const extractors = scanExtractors();
  console.log(`✅ 已扫描 Extractor 目录: ${extractors.length} 个文件`);

  const data = analyzeData(schools, extractors);
  console.log(`✅ 分析完成\n`);

  return data;
}

program
  .name('school-parser')
  .description('学校配置与 Extractor 分析工具')
  .version('1.0.0');

program
  .command('summary')
  .description('显示总体统计信息')
  .option('--format <fmt>', '输出格式：table|json|csv', 'table')
  .option('--output <path>', '输出文件路径')
  .action((options) => {
    try {
      const data = runAnalysis();
      const filteredData = getFilteredData(data, options);

      if (options.format === 'json') {
        outputResult(filteredData, 'json', options);
      } else {
        const tableContent = formatSummaryTable(filteredData);
        outputResult(tableContent, options.format, options);

        if (options.output) {
          generateJSONReport(filteredData, options.output);
        }
      }
    } catch (error) {
      console.error(`❌ 错误: ${error.message}`);
      process.exit(1);
    }
  });

program
  .command('pending')
  .description('列出待上架学校（有 Extractor 但未配置）')
  .option('--format <fmt>', '输出格式：table|json|csv', 'table')
  .option('--output <path>', '输出文件路径')
  .action((options) => {
    try {
      const data = runAnalysis();

      if (options.format === 'json') {
        outputResult({ pendingList: data.pendingList }, 'json', options);
      } else if (options.format === 'csv') {
        const csvPath = generateCSV(data, options.output);
        console.log(`\n✅ CSV 文件已生成: ${csvPath}`);
      } else {
        const tableContent = formatPendingTable(data);
        outputResult(tableContent, options.format, options);
      }
    } catch (error) {
      console.error(`❌ 错误: ${error.message}`);
      process.exit(1);
    }
  });

program
  .command('configured')
  .description('列出已配置学校')
  .option('--format <fmt>', '输出格式：table|json|csv', 'table')
  .option('--filter <key>', '过滤字段：province|systemType')
  .option('--value <val>', '过滤值')
  .option('--output <path>', '输出文件路径')
  .action((options) => {
    try {
      const data = runAnalysis();
      const filteredData = getFilteredData(data, options);

      if (options.format === 'json') {
        outputResult(filteredData.configuredSchools, 'json', options);
      } else {
        const tableContent = formatTable(filteredData, options);
        outputResult(tableContent, options.format, options);
      }
    } catch (error) {
      console.error(`❌ 错误: ${error.message}`);
      process.exit(1);
    }
  });

program
  .command('extractors')
  .description('列出所有 Extractor')
  .option('--format <fmt>', '输出格式：table|json', 'table')
  .option('--output <path>', '输出文件路径')
  .action((options) => {
    try {
      const data = runAnalysis();

      if (options.format === 'json') {
        outputResult(data.extractors, 'json', options);
      } else {
        const tableContent = formatExtractorsTable(data);
        outputResult(tableContent, options.format, options);
      }
    } catch (error) {
      console.error(`❌ 错误: ${error.message}`);
      process.exit(1);
    }
  });

program
  .command('diff')
  .description('显示差异分析')
  .option('--format <fmt>', '输出格式：table|json', 'table')
  .option('--output <path>', '输出文件路径')
  .action((options) => {
    try {
      const data = runAnalysis();

      if (options.format === 'json') {
        outputResult({
          pendingList: data.pendingList,
          anomalies: data.anomalies
        }, 'json', options);
      } else {
        const tableContent = formatDiffTable(data);
        outputResult(tableContent, options.format, options);
      }

      if (options.output && options.format !== 'json') {
        generateJSONReport(data, options.output);
      }
    } catch (error) {
      console.error(`❌ 错误: ${error.message}`);
      process.exit(1);
    }
  });

program
  .command('report')
  .description('生成完整分析报告（JSON 格式）')
  .option('--output <path>', '输出文件路径')
  .action((options) => {
    try {
      const data = runAnalysis();
      const filePath = generateJSONReport(data, options.output);
      console.log(`\n✅ 完整分析报告已生成: ${filePath}`);
      console.log(`\n📊 报告摘要:`);
      console.log(`   - 已配置学校: ${data.summary.configuredSchools} 所`);
      console.log(`   - Extractor 总数: ${data.summary.extractorsCount} 个`);
      console.log(`   - 待上架学校: ${data.summary.pendingSchools} 所`);
      console.log(`   - 异常情况: ${data.summary.anomalies} 个`);
    } catch (error) {
      console.error(`❌ 错误: ${error.message}`);
      process.exit(1);
    }
  });

if (process.argv.length <= 2) {
  program.help();
}

program.parse(process.argv);
