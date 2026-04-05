#!/usr/bin/env node

'use strict';

const { program } = require('commander');
const chalk = require('chalk');
const fs = require('fs');
const path = require('path');
const { execSync, spawn } = require('child_process');

const ROOT_DIR = path.resolve(__dirname, '..');
const SCRIPTS_DIR = __dirname;
const CONFIG_DIR = path.join(SCRIPTS_DIR, 'config');
const BATCHES_FILE = path.join(CONFIG_DIR, 'batches.json');
const SCHOOLS_FILE = path.join(ROOT_DIR, 'app', 'src', 'main', 'assets', 'schools.json');
const DATA_DIR = path.join(ROOT_DIR, 'data');
const PROGRESS_FILE = path.join(DATA_DIR, 'progress.json');
const REPORTS_DIR = path.join(ROOT_DIR, 'reports');

program
  .name('batch-controller')
  .description('批次执行控制器 - 管理和编排学校测试流程')
  .version('1.0.0');

function logHeader() {
  console.log();
  console.log(chalk.bold.cyan('╔══════════════════════════════════════════════════════════════╗'));
  console.log(chalk.bold.cyan('║') + chalk.bold.white('              批次执行控制器 v1.0.0                      ') + chalk.bold.cyan('║'));
  console.log(chalk.bold.cyan('╚══════════════════════════════════════════════════════════════╝'));
  console.log();
}

function ensureDirs() {
  [DATA_DIR, REPORTS_DIR].forEach(dir => {
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
  });
}

function loadBatchesConfig() {
  try {
    const raw = fs.readFileSync(BATCHES_FILE, 'utf-8');
    return JSON.parse(raw);
  } catch (err) {
    throw new Error('无法加载批次配置: ' + err.message);
  }
}

function loadSchools() {
  try {
    const raw = fs.readFileSync(SCHOOLS_FILE, 'utf-8');
    return JSON.parse(raw);
  } catch (err) {
    throw new Error('无法加载学校数据: ' + err.message);
  }
}

function loadProgress() {
  if (!fs.existsSync(PROGRESS_FILE)) return null;
  try {
    const raw = fs.readFileSync(PROGRESS_FILE, 'utf-8');
    return JSON.parse(raw);
  } catch (err) {
    return null;
  }
}

function saveProgress(progress) {
  ensureDirs();
  fs.writeFileSync(PROGRESS_FILE, JSON.stringify(progress, null, 2), 'utf-8');
}

function resetProgress() {
  if (fs.existsSync(PROGRESS_FILE)) {
    fs.unlinkSync(PROGRESS_FILE);
    console.log(chalk.green('  ✓ 进度已重置'));
  } else {
    console.log(chalk.yellow('  ⚠ 没有找到进度文件'));
  }
}

function getSchoolById(schoolId) {
  const schools = loadSchools();
  return schools.find(s => s.id === schoolId);
}

function getBatchById(batchId, config) {
  const batches = config.batches;
  return batches.find(b => b.id === parseInt(batchId));
}

function formatStatus(status) {
  const statusMap = {
    pending: chalk.gray('待测试'),
    testing: chalk.blue('测试中'),
    completed: chalk.green('已完成'),
    failed: chalk.red('失败'),
    skipped: chalk.yellow('已跳过')
  };
  return statusMap[status] || status;
}

function formatPriority(priority) {
  const priorityMap = {
    high: chalk.red.bold('高'),
    medium: chalk.yellow.bold('中'),
    low: chalk.gray('低')
  };
  return priorityMap[priority] || priority;
}

function listBatches(config) {
  const batches = config.batches;
  console.log(chalk.bold.white('  批次列表:\n'));
  console.log(chalk.gray('  ' + '-'.repeat(80)));
  console.log(chalk.gray('  ID   名称'.padEnd(40) + '学校数  优先级  状态'));
  console.log(chalk.gray('  ' + '-'.repeat(80)));

  for (const batch of batches) {
    const id = chalk.bold.white(String(batch.id).padStart(2));
    const name = chalk.cyan(batch.name.padEnd(36));
    const count = chalk.white(String(batch.schools.length).padStart(4));
    const priority = formatPriority(batch.priority).padStart(6);
    const status = formatStatus(batch.status);

    console.log('  ' + id + '  ' + name + count + '  ' + priority + '  ' + status);
  }

  console.log(chalk.gray('  ' + '-'.repeat(80)));
  console.log(chalk.white('\n  共 ' + batches.length + ' 个批次'));

  const totalSchools = batches.reduce((sum, b) => sum + b.schools.length, 0);
  console.log(chalk.white('  总计 ' + totalSchools + ' 所学校\n'));
}

function showBatchDetail(batchId, config) {
  const batch = getBatchById(batchId, config);
  if (!batch) {
    console.error(chalk.red('  ✗ 未找到批次 ID: ' + batchId));
    process.exit(1);
  }

  const schools = loadSchools();
  const schoolList = batch.schools.map(id => schools.find(s => s.id === id)).filter(Boolean);

  console.log(chalk.bold.white('  批次 #' + batch.id + ': ' + batch.name + '\n'));
  console.log(chalk.gray('  描述: ' + batch.description));
  console.log(chalk.gray('  系统类型: ' + batch.systemType));
  console.log(chalk.gray('  优先级: ' + formatPriority(batch.priority)));
  console.log(chalk.gray('  状态: ' + formatStatus(batch.status) + '\n'));

  console.log(chalk.bold.white('  学校列表:\n'));
  console.log(chalk.gray('  ' + '#'.padStart(3) + '  ID'.padEnd(10) + '  学校名称'.padEnd(30) + '  系统'));
  console.log(chalk.gray('  ' + '-'.repeat(60)));

  for (let i = 0; i < schoolList.length; i++) {
    const school = schoolList[i];
    const idx = String(i + 1).padStart(3);
    const id = chalk.cyan(school.id.padEnd(10));
    const name = chalk.white(school.name.padEnd(30));
    const system = chalk.magenta(school.systemType);

    console.log('  ' + idx + '  ' + id + '  ' + name + '  ' + system);
  }

  console.log(chalk.gray('  ' + '-'.repeat(60)));
  console.log(chalk.white('\n  共 ' + schoolList.length + ' 所学校\n'));
}

function showOverallStatus(config) {
  const progress = loadProgress();
  const batches = config.batches;

  logHeader();
  console.log(chalk.bold.white('  整体测试进度\n'));

  let totalCompleted = 0;
  let totalFailed = 0;
  let totalTesting = 0;
  let totalPending = 0;

  for (const batch of batches) {
    var completed = progress && progress.completed && progress.completed[batch.id] ? progress.completed[batch.id].length : 0;
    var failed = progress && progress.failed && progress.failed[batch.id] ? progress.failed[batch.id].length : 0;
    var total = batch.schools.length;

    var statusIcon = completed === total ? chalk.green('✓') :
                       failed > 0 ? chalk.red('✗') :
                       completed > 0 ? chalk.blue('◐') : chalk.gray('○');

    var barLength = 20;
    var filled = Math.round((completed / total) * barLength);
    var empty = barLength - filled;
    var progressBar = '[' + chalk.green('█'.repeat(filled)) + chalk.gray('░'.repeat(empty)) + ']';

    console.log('  ' + statusIcon + ' 批次#' + String(batch.id).padStart(2) + ' ' + chalk.cyan(batch.name.padEnd(24)) + ' ' + progressBar + ' ' + chalk.white(String(completed).padStart(2)) + '/' + total);

    totalCompleted += completed;
    totalFailed += failed;
    totalPending += (total - completed - failed);
  }

  console.log();
  var grandTotal = batches.reduce(function(sum, b) { return sum + b.schools.length; }, 0);
  console.log(chalk.bold.white('  汇总统计:'));
  console.log('    总计学校:     ' + chalk.bold(grandTotal));
  console.log('    已完成:       ' + chalk.green.bold(totalCompleted));
  console.log('    失败:         ' + chalk.red.bold(totalFailed));
  console.log('    待测试:       ' + chalk.yellow.bold(totalPending));
  console.log('    完成率:       ' + chalk.bold(((totalCompleted / Math.max(grandTotal, 1)) * 100).toFixed(1) + '%') + '\n');

  if (progress) {
    console.log(chalk.gray('  最后更新: ' + (progress.lastUpdated || '未知')));
    console.log(chalk.gray('  当前批次: ' + (progress.currentBatch || '无') + '\n'));
  }
}

async function runTestStep(stepId, schoolIds, options) {
  const config = loadBatchesConfig();
  const step = config.testSteps.find(function(s) { return s.id === stepId; });
  if (!step) throw new Error('未找到步骤 ID: ' + stepId);

  console.log(chalk.bold.white('\n  ┌─ Step ' + stepId + ': ' + step.name + ' ─┐'));
  console.log(chalk.gray('  ' + step.description + '\n'));

  if (!step.script) {
    if (stepId === 5) {
      console.log(chalk.green('  ✓ 综合报告生成完成（由主程序汇总）'));
      return { success: true, results: [] };
    }
    return { success: true, results: [] };
  }

  const scriptPath = path.join(SCRIPTS_DIR, step.script);
  if (!fs.existsSync(scriptPath)) {
    console.log(chalk.yellow('  ⚠ 脚本不存在: ' + scriptPath + ', 跳过此步骤'));
    return { success: true, results: [], skipped: true };
  }

  const schoolParam = schoolIds.join(',');
  const args = [scriptPath];

  if (schoolIds.length > 0) {
    args.push('--school', schoolParam);
  }

  if (options.dryRun) {
    console.log(chalk.yellow('  [DRY-RUN] 将执行: node ' + args.join(' ')));
    return { success: true, results: [], dryRun: true };
  }

  return new Promise(function(resolve) {
    const startTime = Date.now();

    const child = spawn('node', args, {
      cwd: SCRIPTS_DIR,
      stdio: ['pipe', 'pipe', 'pipe'],
      shell: true
    });

    let stdout = '';
    let stderr = '';

    child.stdout.on('data', function(data) {
      const output = data.toString();
      stdout += output;
      process.stdout.write(output);
    });

    child.stderr.on('data', function(data) {
      const output = data.toString();
      stderr += output;
      process.stderr.write(output);
    });

    child.on('close', function(code) {
      const elapsed = Date.now() - startTime;
      const success = code === 0;

      if (!success) {
        console.log(chalk.red('\n  ✗ Step ' + stepId + ' 执行失败 (退出码: ' + code + ')'));
      } else {
        console.log(chalk.green('\n  ✓ Step ' + stepId + ' 完成 (' + elapsed + 'ms)'));
      }

      resolve({
        success: success,
        exitCode: code,
        elapsed: elapsed,
        stdout: stdout,
        stderr: stderr
      });
    });

    child.on('error', function(err) {
      console.log(chalk.red('  ✗ Step ' + stepId + ' 执行错误: ' + err.message));
      resolve({
        success: false,
        error: err.message
      });
    });
  });
}

async function testSingleSchool(schoolId, options) {
  const school = getSchoolById(schoolId);
  if (!school) {
    console.error(chalk.red('  ✗ 未找到学校: ' + schoolId));
    process.exit(1);
  }

  logHeader();
  console.log(chalk.bold.white('  单所学校测试: ' + chalk.cyan(school.name) + ' (' + chalk.yellow(schoolId) + ')\n'));

  const stepsToRun = options.steps || [1, 2, 3, 4, 5];
  const results = [];
  const startTime = Date.now();

  for (const stepId of stepsToRun) {
    const result = await runTestStep(stepId, [schoolId], options);
    results.push({ stepId: stepId, ...result });

    if (!result.success && !options.continueOnError) {
      console.log(chalk.yellow('\n  ⚠ 步骤 ' + stepId + ' 失败，停止后续步骤（使用 --continue 忽略失败继续执行）'));
      break;
    }
  }

  const totalTime = Date.now() - startTime;

  console.log(chalk.bold.white('\n  ── 测试结果汇总 ──\n'));
  for (const r of results) {
    const icon = r.success ? chalk.green('✓') : chalk.red('✗');
    console.log('  ' + icon + ' Step ' + r.stepId + ': ' + (r.success ? '成功' : '失败') + (r.elapsed ? ' (' + r.elapsed + 'ms)' : ''));
  }

  console.log(chalk.white('\n  总耗时: ' + (totalTime / 1000).toFixed(1) + 's\n'));

  saveSchoolReport(schoolId, results, totalTime);

  return results;
}

async function runBatchTest(batchId, options) {
  const config = loadBatchesConfig();
  const batch = getBatchById(batchId, config);
  if (!batch) {
    console.error(chalk.red('  ✗ 未找到批次: ' + batchId));
    process.exit(1);
  }

  const progress = loadProgress();
  const stepsToRun = options.steps || [1, 2, 3, 4, 5];
  const schools = batch.schools;

  logHeader();
  console.log(chalk.bold.white('  开始批次 #' + batchId + ': ' + chalk.cyan(batch.name)));
  console.log(chalk.gray('  学校数量: ' + schools.length));
  console.log(chalk.gray('  执行步骤: ' + stepsToRun.map(function(s) { return 'Step ' + s; }).join(', ')));
  if (options.dryRun) console.log(chalk.yellow('  模式: 试运行（不实际执行）'));
  console.log();

  const results = {};
  const completedSchools = [];
  const failedSchools = [];

  const initialCompleted = (progress && progress.completed && progress.completed[batchId]) || [];
  const initialFailed = (progress && progress.failed && progress.failed[batchId]) || [];

  let startFromIndex = 0;
  if (options.continue && initialCompleted.length > 0) {
    startFromIndex = initialCompleted.length;
    console.log(chalk.yellow('  断点续测: 从第 ' + (startFromIndex + 1) + ' 所学校开始（已跳过 ' + startFromIndex + ' 所）\n'));
  }

  const schoolsToTest = schools.slice(startFromIndex);
  const total = schoolsToTest.length;

  if (total === 0) {
    console.log(chalk.green('  所有学校已测试完成！\n'));
    return;
  }

  const batchStartTime = Date.now();

  for (let i = 0; i < total; i++) {
    const schoolId = schoolsToTest[i];
    const school = getSchoolById(schoolId);
    const currentNum = startFromIndex + i + 1;
    const totalNum = schools.length;

    console.log(chalk.bold.white('\n  ════════════════════════════════════════════'));
    console.log(chalk.bold.white('  [' + currentNum + '/' + totalNum + '] ' + chalk.cyan(school ? school.name : schoolId) + ' (' + chalk.yellow(schoolId) + ')'));
    console.log(chalk.bold.white('  ════════════════════════════════════════════\n'));

    const schoolStartTime = Date.now();
    const schoolResults = [];
    let schoolSuccess = true;

    for (const stepId of stepsToRun) {
      const result = await runTestStep(stepId, [schoolId], options);
      schoolResults.push({ stepId: stepId, ...result });

      if (!result.success) {
        schoolSuccess = false;
        if (!options.continueOnError) {
          break;
        }
      }
    }

    const schoolElapsed = Date.now() - schoolStartTime;
    results[schoolId] = {
      success: schoolSuccess,
      results: schoolResults,
      elapsed: schoolElapsed
    };

    if (schoolSuccess) {
      completedSchools.push(schoolId);
      console.log(chalk.green('\n  ✓ ' + (school ? school.name : schoolId) + ' 测试完成 (' + (schoolElapsed / 1000).toFixed(1) + 's)\n'));
    } else {
      failedSchools.push(schoolId);
      console.log(chalk.red('\n  ✗ ' + (school ? school.name : schoolId) + ' 测试失败 (' + (schoolElapsed / 1000).toFixed(1) + 's)\n'));
    }

    if (!options.dryRun) {
      const newProgress = {
        currentBatch: batchId,
        lastUpdated: new Date().toISOString(),
        completed: Object.assign({}, progress ? progress.completed : {}, {}),
        failed: Object.assign({}, progress ? progress.failed : {}, {}),
        totalInBatch: schools.length
      };
      newProgress.completed[batchId] = [...initialCompleted, ...completedSchools];
      newProgress.failed[batchId] = [...initialFailed, ...failedSchools];
      saveProgress(newProgress);
    }

    showProgressBar(currentNum, totalNum, completedSchools.length, failedSchools.length);
  }

  const batchElapsed = Date.now() - batchStartTime;

  console.log(chalk.bold.white('\n  ════════════════════════════════════════════'));
  console.log(chalk.bold.white('  批次测试完成'));
  console.log(chalk.bold.white('  ════════════════════════════════════════════\n'));

  printBatchSummary(batch, completedSchools, failedSchools, batchElapsed);

  generateBatchReport(batchId, batch, results, batchElapsed);

  updateBatchStatus(batchId, failedSchools.length === 0 ? 'completed' : 'failed');
}

function showProgressBar(current, total, passed, failed) {
  const width = 30;
  const filled = Math.round((current / total) * width);
  const empty = width - filled;
  const percent = ((current / total) * 100).toFixed(1);

  const bar = '[' + chalk.green('█'.repeat(filled)) + chalk.gray('░'.repeat(empty)) + ']';
  process.stdout.write('\r  进度: ' + bar + ' ' + percent + '% (' + chalk.green(passed) + ' 通过 ' + chalk.red(failed) + ' 失败)');
  if (current === total) process.stdout.write('\n');
}

function printBatchSummary(batch, completed, failed, elapsed) {
  const total = batch.schools.length;
  const passRate = ((completed.length / total) * 100).toFixed(1);

  console.log(chalk.bold.white('  批次统计:'));
  console.log('    批次ID:     #' + batch.id);
  console.log('    名称:       ' + batch.name);
  console.log('    总数:       ' + chalk.bold(total));
  console.log('    成功:       ' + chalk.green.bold(completed.length));
  console.log('    失败:       ' + chalk.red.bold(failed.length));
  var passRateStr = passRate >= '80' ? chalk.green.bold(passRate + '%') : passRate >= '50' ? chalk.yellow.bold(passRate + '%') : chalk.red.bold(passRate + '%');
  console.log('    通过率:     ' + passRateStr);
  console.log('    总耗时:     ' + chalk.white((elapsed / 1000).toFixed(1) + 's') + '\n');

  if (failed.length > 0) {
    console.log(chalk.red('  失败学校:'));
    for (const schoolId of failed) {
      const school = getSchoolById(schoolId);
      console.log('    ✗ ' + chalk.red(school ? school.name : schoolId) + ' (' + schoolId + ')');
    }
    console.log();
  }
}

function saveSchoolReport(schoolId, results, totalTime) {
  ensureDirs();
  const reportDir = path.join(REPORTS_DIR, 'json');

  const report = {
    schoolId: schoolId,
    generatedAt: new Date().toISOString(),
    tool: 'batch-controller',
    version: '1.0.0',
    totalTime: totalTime,
    steps: results.map(function(r) {
      return {
        stepId: r.stepId,
        success: r.success,
        elapsed: r.elapsed || null,
        exitCode: r.exitCode || null
      };
    })
  };

  const filename = 'single-test-' + schoolId + '-' + Date.now() + '.json';
  const filepath = path.join(reportDir, filename);
  fs.writeFileSync(filepath, JSON.stringify(report, null, 2), 'utf-8');

  console.log(chalk.gray('  报告已保存: ' + filepath + '\n'));
}

function generateBatchReport(batchId, batch, results, elapsed) {
  ensureDirs();
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);

  const report = {
    meta: {
      generatedAt: new Date().toISOString(),
      tool: 'batch-controller',
      version: '1.0.0',
      batchId: batchId,
      batchName: batch.name
    },
    summary: {
      totalSchools: batch.schools.length,
      completed: Object.values(results).filter(function(r) { return r.success; }).length,
      failed: Object.values(results).filter(function(r) { return !r.success; }).length,
      totalElapsedTime: elapsed
    },
    schools: Object.entries(results).map(function(entry) {
      var schoolId = entry[0];
      var data = entry[1];
      return {
        schoolId: schoolId,
        schoolName: getSchoolById(schoolId) ? getSchoolById(schoolId).name : schoolId,
        success: data.success,
        elapsed: data.elapsed,
        steps: data.results
      };
    })
  };

  const jsonPath = path.join(REPORTS_DIR, 'json', 'batch-' + batchId + '-' + timestamp + '.json');
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2), 'utf-8');

  const mdPath = path.join(REPORTS_DIR, 'batch-report-' + batchId + '-' + timestamp + '.md');
  generateMarkdownReport(mdPath, report);

  console.log(chalk.green('  JSON 报告: ' + jsonPath));
  console.log(chalk.green('  Markdown 报告: ' + mdPath + '\n'));
}

function generateMarkdownReport(filePath, report) {
  const lines = [
    '# 批次测试报告 - ' + report.meta.batchName,
    '',
    '**生成时间**: ' + report.meta.generatedAt,
    '**工具版本**: ' + report.meta.tool + ' v' + report.meta.version,
    '',
    '## 概览',
    '',
    '| 指标 | 数值 |',
    '|------|------|',
    '| 总学校数 | ' + report.summary.totalSchools + ' |',
    '| 成功 | ' + report.summary.completed + ' |',
    '| 失败 | ' + report.summary.failed + ' |',
    '| 通过率 | ' + ((report.summary.completed / report.summary.totalSchools) * 100).toFixed(1) + '% |',
    '| 总耗时 | ' + (report.summary.totalElapsedTime / 1000).toFixed(1) + 's |',
    '',
    '## 各校详情',
    ''
  ];

  for (var si = 0; si < report.schools.length; si++) {
    var school = report.schools[si];
    var status = school.success ? '✅ 成功' : '❌ 失败';
    lines.push('### ' + school.schoolName + ' (' + school.schoolId + ') - ' + status);
    lines.push('');
    lines.push('- 耗时: ' + (school.elapsed / 1000).toFixed(1) + 's');
    lines.push('- 步骤结果:');
    for (var st = 0; st < school.steps.length; st++) {
      var step = school.steps[st];
      var stepIcon = step.success ? '✓' : '✗';
      lines.push('  - ' + stepIcon + ' Step ' + step.stepId + ': ' + (step.success ? '通过' : '失败') + (step.elapsed ? ' (' + step.elapsed + 'ms)' : ''));
    }
    lines.push('');
  }

  fs.writeFileSync(filePath, lines.join('\n'), 'utf-8');
}

function updateBatchStatus(batchId, status) {
  const config = loadBatchesConfig();
  const batch = getBatchById(batchId, config);
  if (batch) {
    batch.status = status;
    fs.writeFileSync(BATCHES_FILE, JSON.stringify(config, null, 2), 'utf-8');
  }
}

function parseStepsInput(stepsStr) {
  if (!stepsStr) return null;
  if (stepsStr.indexOf('-') !== -1) {
    const parts = stepsStr.split('-');
    const start = parseInt(parts[0], 10);
    const end = parseInt(parts[1], 10);
    const steps = [];
    for (let i = start; i <= end; i++) steps.push(i);
    return steps;
  }
  return stepsStr.split(',').map(function(s) { return parseInt(s, 10); });
}

program
  .command('list')
  .description('列出所有批次')
  .action(function() {
    logHeader();
    const config = loadBatchesConfig();
    listBatches(config);
  });

program
  .command('show <batchId>')
  .description('显示批次详情')
  .action(function(batchId) {
    logHeader();
    const config = loadBatchesConfig();
    showBatchDetail(batchId, config);
  });

program
  .command('test')
  .description('执行测试')
  .option('--batch <n>', '批次号（默认 1）', '1')
  .option('--school <id>', '单所学校测试')
  .option('--steps <steps>', '执行步骤（如 "1-3" 或 "1,2,5"，默认全部）')
  .option('--continue', '从断点继续')
  .option('--continue-on-error', '某步失败后继续执行后续步骤')
  .option('--dry-run', '试运行（不实际执行）')
  .action(async function(options) {
    try {
      ensureDirs();
      const steps = parseStepsInput(options.steps);
      const opts = {
        steps: steps,
        continue: !!options.continue,
        continueOnError: !!options.continueOnError,
        dryRun: !!options.dryRun
      };

      if (options.school) {
        await testSingleSchool(options.school, opts);
      } else {
        await runBatchTest(options.batch, opts);
      }
    } catch (err) {
      console.error(chalk.red('\n  致命错误: ' + err.message));
      if (process.env.DEBUG) console.error(err.stack);
      process.exit(1);
    }
  });

program
  .command('status')
  .description('查看整体进度')
  .action(function() {
    const config = loadBatchesConfig();
    showOverallStatus(config);
  });

program
  .command('reset')
  .description('重置测试进度')
  .action(function() {
    logHeader();
    resetProgress();

    const config = loadBatchesConfig();
    for (var bi = 0; bi < config.batches.length; bi++) {
      config.batches[bi].status = 'pending';
    }
    fs.writeFileSync(BATCHES_FILE, JSON.stringify(config, null, 2), 'utf-8');
    console.log(chalk.green('  ✓ 批次状态已重置为待测试\n'));
  });

if (process.argv.length <= 2) {
  logHeader();
  program.help();
}

program.parse(process.argv);

process.on('unhandledRejection', function(reason) {
  console.error(chalk.red('  未处理的异步错误:', reason));
});

process.on('uncaughtException', function(error) {
  console.error(chalk.red('  未捕获的异常:', error.message));
  process.exit(1);
});
