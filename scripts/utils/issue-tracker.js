#!/usr/bin/env node

'use strict';

const { program } = require('commander');
const chalk = require('chalk');
const fs = require('fs');
const path = require('path');

const ROOT_DIR = path.resolve(__dirname, '..', '..');
const DATA_DIR = path.join(ROOT_DIR, 'data');
const ISSUES_FILE = path.join(DATA_DIR, 'issues.json');
const BACKUP_FILE = path.join(DATA_DIR, 'issues.backup.json');

program
  .name('issue-tracker')
  .description('问题追踪系统 - 管理测试过程中的问题和缺陷')
  .version('1.0.0');

function logHeader() {
  console.log();
  console.log(chalk.bold.cyan('╔══════════════════════════════════════════════════════════════╗'));
  console.log(chalk.bold.cyan('║') + chalk.bold.white('              问题追踪系统 v1.0.0                          ') + chalk.bold.cyan('║'));
  console.log(chalk.bold.cyan('╚══════════════════════════════════════════════════════════════╝'));
  console.log();
}

function ensureDataDir() {
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }
}

class IssueTracker {
  constructor(dataPath) {
    this.dataPath = dataPath || ISSUES_FILE;
    this.issues = [];
    this._load();
  }

  _load() {
    ensureDataDir();
    if (fs.existsSync(this.dataPath)) {
      try {
        const raw = fs.readFileSync(this.dataPath, 'utf-8');
        const data = JSON.parse(raw);
        this.issues = data.issues || [];
      } catch (err) {
        console.error(chalk.yellow(`警告: 无法加载问题数据，将创建新文件: ${err.message}`));
        this.issues = [];
      }
    }
  }

  _save() {
    this._backup();
    const data = {
      version: '1.0.0',
      lastUpdated: new Date().toISOString(),
      totalIssues: this.issues.length,
      issues: this.issues
    };
    fs.writeFileSync(this.dataPath, JSON.stringify(data, null, 2), 'utf-8');
  }

  _backup() {
    if (fs.existsSync(this.dataPath)) {
      fs.copyFileSync(this.dataPath, BACKUP_FILE);
    }
  }

  _generateIssueId() {
    const num = String(this.issues.length + 1).padStart(3, '0');
    return `ISSUE-${num}`;
  }

  async create(issueData) {
    const now = new Date().toISOString();

    const issue = {
      issueId: issueData.issueId || this._generateIssueId(),
      schoolId: issueData.schoolId || '',
      schoolName: issueData.schoolName || '',
      type: issueData.type || 'other',
      severity: issueData.severity || 'P2',
      status: issueData.status || 'open',
      title: issueData.title || '未命名问题',
      description: issueData.description || '',
      reproductionSteps: issueData.reproductionSteps || [],
      expectedBehavior: issueData.expectedBehavior || '',
      actualBehavior: issueData.actualBehavior || '',
      screenshotPath: issueData.screenshotPath || null,
      environment: {
        testDate: now.split('T')[0],
        nodeVersion: process.version,
        network: '正常',
        ...issueData.environment
      },
      assignee: issueData.assignee || '',
      createdAt: now,
      updatedAt: now,
      resolvedAt: null,
      comments: []
    };

    this.issues.push(issue);
    this._save();
    return issue;
  }

  list(filters = {}) {
    let results = [...this.issues];

    if (filters.schoolId) {
      results = results.filter(i => i.schoolId === filters.schoolId);
    }
    if (filters.type) {
      results = results.filter(i => i.type === filters.type);
    }
    if (filters.severity) {
      results = results.filter(i => i.severity === filters.severity);
    }
    if (filters.status) {
      results = results.filter(i => i.status === filters.status);
    }
    if (filters.fromDate || filters.toDate) {
      results = results.filter(i => {
        const date = new Date(i.createdAt);
        if (filters.fromDate && date < new Date(filters.fromDate)) return false;
        if (filters.toDate && date > new Date(filters.toDate + 'T23:59:59')) return false;
        return true;
      });
    }

    if (filters.sort) {
      const sortField = filters.sort;
      const sortOrder = filters.sortOrder || 'desc';
      results.sort((a, b) => {
        let valA = a[sortField];
        let valB = b[sortField];

        if (sortField === 'createdAt' || sortField === 'updatedAt' || sortField === 'resolvedAt') {
          valA = new Date(valA || 0).getTime();
          valB = new Date(valB || 0).getTime();
        }

        if (typeof valA === 'string') {
          valA = valA.toLowerCase();
          valB = valB.toLowerCase();
        }

        if (sortOrder === 'asc') {
          return valA > valB ? 1 : -1;
        }
        return valA < valB ? 1 : -1;
      });
    }

    if (filters.limit && filters.limit > 0) {
      results = results.slice(0, filters.limit);
    }

    return results;
  }

  get(issueId) {
    return this.issues.find(i => i.issueId === issueId) || null;
  }

  update(issueId, updates) {
    const index = this.issues.findIndex(i => i.issueId === issueId);
    if (index === -1) throw new Error(`未找到问题: ${issueId}`);

    const allowedFields = [
      'title', 'description', 'type', 'severity', 'status',
      'reproductionSteps', 'expectedBehavior', 'actualBehavior',
      'screenshotPath', 'assignee'
    ];

    for (const [key, value] of Object.entries(updates)) {
      if (allowedFields.includes(key)) {
        this.issues[index][key] = value;
      }
    }

    this.issues[index].updatedAt = new Date().toISOString();

    if (updates.status === 'closed' || updates.status === 'verified') {
      this.issues[index].resolvedAt = new Date().toISOString();
    }

    this._save();
    return this.issues[index];
  }

  close(issueId, reason = '') {
    return this.update(issueId, {
      status: 'closed',
      description: (this.get(issueId)?.description || '') +
        (reason ? `\n\n关闭原因: ${reason}` : '')
    });
  }

  addComment(issueId, comment, author = 'system') {
    const issue = this.get(issueId);
    if (!issue) throw new Error(`未找到问题: ${issueId}`);

    issue.comments.push({
      id: `CMT-${Date.now()}`,
      author,
      content: comment,
      createdAt: new Date().toISOString()
    });

    issue.updatedAt = new Date().toISOString();
    this._save();
    return issue;
  }

  delete(issueId) {
    const index = this.issues.findIndex(i => i.issueId === issueId);
    if (index === -1) throw new Error(`未找到问题: ${issueId}`);

    const deleted = this.issues.splice(index, 1)[0];
    this._save();
    return deleted;
  }

  getStats() {
    const total = this.issues.length;

    const bySeverity = {};
    const byType = {};
    const byStatus = {};
    const bySchool = {};

    let resolvedCount = 0;
    let totalResolutionTime = 0;

    for (const issue of this.issues) {
      bySeverity[issue.severity] = (bySeverity[issue.severity] || 0) + 1;
      byType[issue.type] = (byType[issue.type] || 0) + 1;
      byStatus[issue.status] = (byStatus[issue.status] || 0) + 1;
      bySchool[issue.schoolId] = (bySchool[issue.schoolId] || 0) + 1;

      if (issue.resolvedAt) {
        resolvedCount++;
        const created = new Date(issue.createdAt).getTime();
        const resolved = new Date(issue.resolvedAt).getTime();
        totalResolutionTime += (resolved - created);
      }
    }

    const sortedSchools = Object.entries(bySchool)
      .sort((a, b) => b[1] - a[1]);

    return {
      total,
      bySeverity,
      byType,
      byStatus,
      bySchool: sortedSchools,
      resolutionRate: total > 0 ? ((resolvedCount / total) * 100).toFixed(1) : '0',
      avgResolutionTime: resolvedCount > 0 ? Math.round(totalResolutionTime / resolvedCount / 3600000) : null
    };
  }

  exportCSV(filePath) {
    const headers = [
      'IssueID', '学校ID', '学校名称', '类型', '严重等级', '状态',
      '标题', '描述', '创建时间', '更新时间', '解决时间', '负责人'
    ];

    const rows = this.issues.map(issue => [
      issue.issueId,
      issue.schoolId,
      issue.schoolName,
      issue.type,
      issue.severity,
      issue.status,
      `"${(issue.title || '').replace(/"/g, '""')}"`,
      `"${(issue.description || '').replace(/"/g, '""').replace(/\n/g, ' ')}"`,
      issue.createdAt,
      issue.updatedAt,
      issue.resolvedAt || '',
      issue.assignee
    ]);

    const csvContent = [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    fs.writeFileSync(filePath, '\uFEFF' + csvContent, 'utf-8');
    return filePath;
  }

  exportMarkdown(filePath) {
    const stats = this.getStats();
    const lines = [
      '# 问题追踪报告',
      '',
      `**生成时间**: ${new Date().toISOString()}`,
      `**问题总数**: ${stats.total}`,
      '',
      '## 统计概览',
      '',
      '### 按严重等级',
      '',
      '| 等级 | 数量 |',
      '|------|------|',
      ...Object.entries(stats.bySeverity).map(([k, v]) => `| ${k} | ${v} |`),
      '',
      '### 按类型',
      '',
      '| 类型 | 数量 |',
      '|------|------|',
      ...Object.entries(stats.byType).map(([k, v]) => `| ${k} | ${v} |`),
      '',
      '### 按状态',
      '',
      '| 状态 | 数量 |',
      '|------|------|',
      ...Object.entries(stats.byStatus).map(([k, v]) => `| ${k} | ${v} |`),
      '',
      '### 学校问题排名',
      '',
      '| 学校 | 问题数 |',
      '|------|--------|',
      ...stats.bySchool.map(([k, v]) => `| ${k} | ${v} |`),
      '',
      `- **解决率**: ${stats.resolutionRate}%`,
      stats.avgResolutionTime ? `- **平均解决时间**: ${stats.avgResolutionTime} 小时` : '',
      '',
      '## 问题列表',
      ''
    ];

    for (const issue of this.issues) {
      lines.push(`### ${issue.issueId}: ${issue.title}`);
      lines.push('');
      lines.push(`- **学校**: ${issue.schoolName} (${issue.schoolId})`);
      lines.push(`- **类型**: ${issue.type}`);
      lines.push(`- **严重等级**: ${issue.severity}`);
      lines.push(`- **状态**: ${issue.status}`);
      lines.push(`- **描述**: ${issue.description || '无'}`);
      lines.push(`- **创建时间**: ${issue.createdAt}`);
      if (issue.resolvedAt) {
        lines.push(`- **解决时间**: ${issue.resolvedAt}`);
      }
      lines.push('');
    }

    fs.writeFileSync(filePath, lines.join('\n'), 'utf-8');
    return filePath;
  }

  exportJSON(filePath) {
    const data = {
      exportedAt: new Date().toISOString(),
      tool: 'issue-tracker',
      version: '1.0.0',
      stats: this.getStats(),
      issues: this.issues
    };
    fs.writeFileSync(filePath, JSON.stringify(data, null, 2), 'utf-8');
    return filePath;
  }

  importJSON(filePath) {
    const raw = fs.readFileSync(filePath, 'utf-8');
    const data = JSON.parse(raw);

    if (data.issues && Array.isArray(data.issues)) {
      const imported = data.issues.filter(newIssue =>
        !this.issues.some(existing => existing.issueId === newIssue.issueId)
      );

      this.issues.push(...imported);
      this._save();
      return imported.length;
    }
    throw new Error('无效的导入文件格式');
  }
}

const ISSUE_TYPES = ['url_inaccessible', 'login_failed', 'api_error', 'parse_error', 'ssl_issue', 'timeout', 'other'];
const SEVERITY_LEVELS = ['P0', 'P1', 'P2', 'P3'];
const STATUS_VALUES = ['open', 'in_progress', 'fixed', 'verified', 'closed', 'wontfix'];

function formatSeverity(severity) {
  const colors = {
    P0: chalk.red.bold,
    P1: chalk.red,
    P2: chalk.yellow,
    P3: chalk.gray
  };
  return (colors[severity] || chalk.white)(severity);
}

function formatStatus(status) {
  const icons = {
    open: chalk.red('○ 开放'),
    in_progress: chalk.blue('◐ 处理中'),
    fixed: chalk.yellow('◆ 已修复'),
    verified: chalk.green('✓ 已验证'),
    closed: chalk.gray('● 已关闭'),
    wontfix: chalk.gray('⊘ 不修复')
  };
  return icons[status] || status;
}

function formatType(type) {
  const names = {
    url_inaccessible: 'URL不可访问',
    login_failed: '登录失败',
    api_error: 'API错误',
    parse_error: '解析错误',
    ssl_issue: 'SSL证书问题',
    timeout: '超时',
    other: '其他'
  };
  return names[type] || type;
}

function printIssueTable(issues, options = {}) {
  if (issues.length === 0) {
    console.log(chalk.yellow('  没有找到匹配的问题\n'));
    return;
  }

  console.log(chalk.gray('  ' + '-'.repeat(100)));
  console.log(chalk.gray('  ID'.padEnd(12) + '  学校'.padEnd(14) + '  类型'.padEnd(16) + '  等级'.padEnd(6) + '  状态'.padEnd(12) + '  标题'));
  console.log(chalk.gray('  ' + '-'.repeat(100)));

  for (const issue of issues) {
    const id = chalk.cyan(issue.issueId.padEnd(12));
    const school = (issue.schoolId || '-').padEnd(14);
    const type = formatType(issue.type).padEnd(16);
    const severity = formatSeverity(issue.severity).padEnd(6);
    const status = formatStatus(issue.status).padEnd(12);
    const title = (issue.title || '-').substring(0, 35);

    console.log(`  ${id}  ${school}  ${type}  ${severity}  ${status}  ${title}`);
  }

  console.log(chalk.gray('  ' + '-'.repeat(100)));
  console.log(chalk.white(`\n  共 ${issues.length} 条记录\n`));
}

function printIssueDetail(issue) {
  console.log(chalk.bold.white(`\n  ┌─ ${issue.issueId}: ${issue.title} ─┐\n`));

  console.log(`  学校:     ${chalk.cyan(issue.schoolName || issue.schoolId || '未知')} (${chalk.yellow(issue.schoolId || '-')})`);
  console.log(`  类型:     ${formatType(issue.type)}`);
  console.log(`  严重等级: ${formatSeverity(issue.severity)}`);
  console.log(`  状态:     ${formatStatus(issue.status)}`);
  console.log(`  负责人:   ${issue.assignee || chalk.gray('未分配')}\n`);

  if (issue.description) {
    console.log(chalk.bold('  描述:'));
    console.log(`  ${issue.description}\n`);
  }

  if (issue.expectedBehavior) {
    console.log(chalk.bold('  预期行为:'));
    console.log(`  ${issue.expectedBehavior}\n`);
  }

  if (issue.actualBehavior) {
    console.log(chalk.bold('  实际行为:'));
    console.log(`  ${issue.actualBehavior}\n`);
  }

  if (issue.reproductionSteps && issue.reproductionSteps.length > 0) {
    console.log(chalk.bold('  复现步骤:'));
    issue.reproductionSteps.forEach((step, idx) => {
      console.log(`    ${idx + 1}. ${step}`);
    });
    console.log();
  }

  if (issue.screenshotPath) {
    console.log(`  截图:     ${chalk.magenta(issue.screenshotPath)}`);
  }

  console.log(chalk.gray(`  创建时间: ${issue.createdAt}`));
  console.log(chalk.gray(`  更新时间: ${issue.updatedAt}`));
  if (issue.resolvedAt) {
    console.log(chalk.gray(`  解决时间: ${issue.resolvedAt}`));
  }

  if (issue.comments && issue.comments.length > 0) {
    console.log(chalk.bold('\n  评论:'));
    for (const comment of issue.comments) {
      console.log(`    [${comment.createdAt}] ${chalk.cyan(comment.author)}: ${comment.content}`);
    }
  }

  console.log(chalk.bold.white(`\n  └─ ${issue.issueId} ─┘\n`));
}

function printStats(tracker) {
  const stats = tracker.getStats();

  logHeader();
  console.log(chalk.bold.white('  统计信息\n'));

  console.log(chalk.bold('  总览:'));
  console.log(`    总问题数:       ${chalk.bold(stats.total)}`);
  console.log(`    解决率:         ${chalk.bold(stats.resolutionRate + '%')}`);
  if (stats.avgResolutionTime !== null) {
    console.log(`    平均解决时间:   ${chalk.bold(stats.avgResolutionTime + ' 小时')}`);
  }
  console.log();

  console.log(chalk.bold('  按严重等级:'));
  for (const [level, count] of Object.entries(stats.bySeverity)) {
    const bar = '█'.repeat(count);
    console.log(`    ${formatSeverity(level).padEnd(8)} ${chalk.green(bar)} ${count}`);
  }
  console.log();

  console.log(chalk.bold('  按状态:'));
  for (const [status, count] of Object.entries(stats.byStatus)) {
    console.log(`    ${formatStatus(status).padEnd(20)} ${count}`);
  }
  console.log();

  console.log(chalk.bold('  按类型:'));
  for (const [type, count] of Object.entries(stats.byType)) {
    console.log(`    ${formatType(type).padEnd(18)} ${count}`);
  }
  console.log();

  console.log(chalk.bold('  学校问题排名 TOP 10:'));
  stats.bySchool.slice(0, 10).forEach(([schoolId, count], idx) => {
    console.log(`    ${String(idx + 1).padStart(2)}. ${schoolId.padEnd(12)} ${count} 个问题`);
  });
  console.log();
}

async function interactiveCreate(tracker) {
  const readline = require('readline');
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
  });

  const question = (prompt) => new Promise(resolve => rl.question(prompt, resolve));

  logHeader();
  console.log(chalk.bold.white('  创建新问题（交互模式）\n'));

  try {
    const schoolId = await question(chalk.cyan('  学校ID: '));
    const title = await question(chalk.cyan('  标题: '));
    const type = await question(chalk.cyan(`  类型 (${ISSUE_TYPES.join('/')}): `)) || 'other';
    const severity = await question(chalk.cyan(`  严重等级 (P0-P3): `)) || 'P2';
    const description = await question(chalk.cyan('  描述: ')) || '';

    const issue = await tracker.create({
      schoolId,
      title,
      type: ISSUE_TYPES.includes(type) ? type : 'other',
      severity: SEVERITY_LEVELS.includes(severity) ? severity : 'P2',
      description
    });

    console.log(chalk.green(`\n  ✓ 问题已创建: ${issue.issueId}`));
    printIssueDetail(issue);
  } finally {
    rl.close();
  }
}

program
  .command('create')
  .description('创建新问题（交互式）')
  .option('--school <id>', '学校ID')
  .option('--title <title>', '问题标题')
  .option('--type <type>', '问题类型')
  .option('--severity <level>', '严重等级')
  .option('--desc <desc>', '问题描述')
  .action(async (options) => {
    const tracker = new IssueTracker();

    if (!options.school && !options.title) {
      await interactiveCreate(tracker);
      return;
    }

    const issue = await tracker.create({
      schoolId: options.school || '',
      title: options.title || '未命名问题',
      type: options.type || 'other',
      severity: options.severity || 'P2',
      description: options.desc || ''
    });

    console.log(chalk.green(`  ✓ 问题已创建: ${issue.issueId}`));
    printIssueDetail(issue);
  });

program
  .command('list')
  .description('列出问题')
  .option('--school <id>', '按学校过滤')
  .option('--type <type>', '按类型过滤')
  .option('--severity <level>', '按严重等级过滤')
  .option('--status <status>', '按状态过滤')
  .option('--from <date>', '起始日期 (YYYY-MM-DD)')
  .option('--to <date>', '结束日期 (YYYY-MM-DD)')
  .option('--sort <field>', '排序字段 (createdAt/severity/status)')
  .option('--order <order>', '排序顺序 (asc/desc)', 'desc')
  .option('--limit <n>', '限制数量')
  .option('--format <fmt>', '输出格式 (table/json/csv)', 'table')
  .action((options) => {
    const tracker = new IssueTracker();
    logHeader();

    const filters = {
      schoolId: options.school,
      type: options.type,
      severity: options.severity,
      status: options.status,
      fromDate: options.from,
      toDate: options.to,
      sort: options.sort,
      sortOrder: options.order,
      limit: options.limit ? parseInt(options.limit, 10) : undefined
    };

    const issues = tracker.list(filters);

    if (options.format === 'json') {
      console.log(JSON.stringify(issues, null, 2));
    } else if (options.format === 'csv') {
      const tmpFile = path.join(DATA_DIR, `temp-export-${Date.now()}.csv`);
      tracker.exportCSV(tmpFile);
      console.log(fs.readFileSync(tmpFile, 'utf-8'));
      fs.unlinkSync(tmpFile);
    } else {
      printIssueTable(issues);
    }
  });

program
  .command('show <issueId>')
  .description('查看问题详情')
  .action((issueId) => {
    const tracker = new IssueTracker();
    logHeader();

    const issue = tracker.get(issueId);
    if (!issue) {
      console.error(chalk.red(`  ✗ 未找到问题: ${issueId}`));
      process.exit(1);
    }

    printIssueDetail(issue);
  });

program
  .command('update <issueId>')
  .description('更新问题')
  .option('--title <title>', '更新标题')
  .option('--type <type>', '更新类型')
  .option('--severity <level>', '更新严重等级')
  .option('--status <status>', '更新状态')
  .option('--desc <desc>', '更新描述')
  .option('--assignee <name>', '指定负责人')
  .action((issueId, options) => {
    const tracker = new IssueTracker();
    logHeader();

    const updates = {};
    if (options.title) updates.title = options.title;
    if (options.type) updates.type = options.type;
    if (options.severity) updates.severity = options.severity;
    if (options.status) updates.status = options.status;
    if (options.desc) updates.description = options.desc;
    if (options.assignee) updates.assignee = options.assignee;

    if (Object.keys(updates).length === 0) {
      console.error(chalk.red('  ✗ 请至少指定一个要更新的字段'));
      process.exit(1);
    }

    try {
      const updated = tracker.update(issueId, updates);
      console.log(chalk.green(`  ✓ 问题已更新: ${issueId}`));
      printIssueDetail(updated);
    } catch (err) {
      console.error(chalk.red(`  ✗ ${err.message}`));
      process.exit(1);
    }
  });

program
  .command('close <issueId>')
  .description('关闭问题')
  .option('--reason <reason>', '关闭原因')
  .action((issueId, options) => {
    const tracker = new IssueTracker();
    logHeader();

    try {
      const closed = tracker.close(issueId, options.reason || '');
      console.log(chalk.green(`  ✓ 问题已关闭: ${issueId}${options.reason ? ` (原因: ${options.reason})` : ''}`));
    } catch (err) {
      console.error(chalk.red(`  ✗ ${err.message}`));
      process.exit(1);
    }
  });

program
  .command('comment <issueId>')
  .description('添加评论')
  .option('--text <content>', '评论内容')
  .option('--author <name>', '评论者', 'system')
  .action(async (issueId, options) => {
    const tracker = new IssueTracker();
    logHeader();

    if (!options.text) {
      console.error(chalk.red('  ✗ 请使用 --text 指定评论内容'));
      process.exit(1);
    }

    try {
      const updated = tracker.addComment(issueId, options.text, options.author);
      console.log(chalk.green(`  ✓ 评论已添加到 ${issueId}`));
      printIssueDetail(updated);
    } catch (err) {
      console.error(chalk.red(`  ✗ ${err.message}`));
      process.exit(1);
    }
  });

program
  .command('delete <issueId>')
  .description('删除问题')
  .action((issueId) => {
    const tracker = new IssueTracker();
    logHeader();

    try {
      const deleted = tracker.delete(issueId);
      console.log(chalk.green(`  ✓ 问题已删除: ${deleted.issueId} - ${deleted.title}`));
    } catch (err) {
      console.error(chalk.red(`  ✗ ${err.message}`));
      process.exit(1);
    }
  });

program
  .command('stats')
  .description('统计信息')
  .action(() => {
    const tracker = new IssueTracker();
    printStats(tracker);
  });

program
  .command('export <format>')
  .description('导出数据 (json/csv/markdown)')
  .option('-o, --output <path>', '输出路径')
  .action((format, options) => {
    const tracker = new IssueTracker();
    logHeader();

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const defaultPaths = {
      json: path.join(ROOT_DIR, 'reports', `issues-${timestamp}.json`),
      csv: path.join(ROOT_DIR, 'reports', `issues-${timestamp}.csv`),
      markdown: path.join(ROOT_DIR, 'reports', `issues-report-${timestamp}.md`)
    };

    const outputPath = options.output || defaultPaths[format.toLowerCase()];
    if (!outputPath) {
      console.error(chalk.red(`  ✗ 不支持的导出格式: ${format}`));
      console.error(chalk.gray('  支持的格式: json, csv, markdown'));
      process.exit(1);
    }

    ensureDataDir();

    try {
      switch (format.toLowerCase()) {
        case 'json':
          tracker.exportJSON(outputPath);
          break;
        case 'csv':
          tracker.exportCSV(outputPath);
          break;
        case 'markdown':
        case 'md':
          tracker.exportMarkdown(outputPath);
          break;
        default:
          throw new Error(`不支持的格式: ${format}`);
      }

      console.log(chalk.green(`  ✓ 数据已导出: ${outputPath}`));
    } catch (err) {
      console.error(chalk.red(`  ✗ 导出失败: ${err.message}`));
      process.exit(1);
    }
  });

program
  .command('import <filePath>')
  .description('导入数据 (JSON格式)')
  .action((filePath) => {
    const tracker = new IssueTracker();
    logHeader();

    try {
      const count = tracker.importJSON(path.resolve(filePath));
      console.log(chalk.green(`  ✓ 成功导入 ${count} 条新问题`));
    } catch (err) {
      console.error(chalk.red(`  ✗ 导入失败: ${err.message}`));
      process.exit(1);
    }
  });

if (process.argv.length <= 2) {
  logHeader();
  program.help();
}

program.parse(process.argv);

module.exports = { IssueTracker };

process.on('unhandledRejection', (reason) => {
  console.error(chalk.red('  未处理的异步错误:', reason));
});

process.on('uncaughtException', (error) => {
  console.error(chalk.red('  未捕获的异常:', error.message));
  process.exit(1);
});
