#!/usr/bin/env node

'use strict';

const { program } = require('commander');
const chalk = require('chalk');
const axios = require('axios');
const https = require('https');
const tls = require('tls');
const fs = require('fs');
const path = require('path');

const SCHOOLS_JSON_PATH = path.resolve(__dirname, '../../../app/src/main/assets/schools.json');
const REPORT_DIR = path.join(__dirname, 'reports', 'json');

program
  .name('url-checker')
  .description('批量检测学校教务系统 URL 可访问性')
  .option('-c, --concurrency <n>', '并发数', '5')
  .option('-t, --timeout <ms>', '超时时间（毫秒）', '10000')
  .option('-r, --retries <n>', '重试次数', '3')
  .option('-p, --proxy <url>', '代理地址')
  .option('-s, --school <ids>', '指定学校ID（逗号分隔）')
  .option('-o, --output <path>', '输出文件路径')
  .parse(process.argv);

const opts = program.opts();
const CONCURRENCY = parseInt(opts.concurrency, 10) || 5;
const TIMEOUT = parseInt(opts.timeout, 10) || 10000;
const RETRIES = parseInt(opts.retries, 10) || 3;
const PROXY = opts.proxy || null;
const SCHOOL_FILTER = opts.school ? opts.school.split(',').map(s => s.trim()) : null;
const OUTPUT_PATH = opts.output || null;

function logHeader() {
  console.log();
  console.log(chalk.bold.cyan('╔══════════════════════════════════════════════════════════╗'));
  console.log(chalk.bold.cyan('║') + chalk.bold.white('       教务系统 URL 可访问性批量检测工具              ') + chalk.bold.cyan('║'));
  console.log(chalk.bold.cyan('╚══════════════════════════════════════════════════════════╝'));
  console.log();
}

function loadSchools() {
  const raw = fs.readFileSync(SCHOOLS_JSON_PATH, 'utf-8');
  const schools = JSON.parse(raw);
  if (SCHOOL_FILTER) {
    return schools.filter(s => SCHOOL_FILTER.includes(s.id));
  }
  return schools;
}

function buildUrlList(schools) {
  const list = [];
  for (const school of schools) {
    if (school.loginUrl) {
      list.push({ schoolId: school.id, schoolName: school.name, urlType: 'loginUrl', url: school.loginUrl });
    }
    if (school.scheduleUrl) {
      list.push({ schoolId: school.id, schoolName: school.name, urlType: 'scheduleUrl', url: school.scheduleUrl });
    }
  }
  return list;
}

function loadPreviousReport() {
  if (!fs.existsSync(REPORT_DIR)) return null;
  const files = fs.readdirSync(REPORT_DIR)
    .filter(f => f.startsWith('url-check-') && f.endsWith('.json'))
    .sort()
    .reverse();
  if (files.length === 0) return null;
  try {
    return JSON.parse(fs.readFileSync(path.join(REPORT_DIR, files[0]), 'utf-8'));
  } catch { return null; }
}

function getCheckedUrls(report) {
  if (!report || !report.results) return new Set();
  const set = new Set();
  for (const r of report.results) {
    set.add(`${r.schoolId}::${r.urlType}`);
  }
  return set;
}

async function checkSSLCertificate(hostname, port = 443) {
  return new Promise((resolve) => {
    const socket = tls.connect({
      hostname,
      port,
      servername: hostname,
      rejectUnauthorized: false,
    }, () => {
      const cert = socket.getPeerCertificate();
      if (!cert || Object.keys(cert).length === 0) {
        socket.destroy();
        resolve({ valid: false, reason: '无SSL证书' });
        return;
      }
      const now = new Date();
      const validFrom = new Date(cert.valid_from);
      const validTo = new Date(cert.valid_to);
      const isExpired = now > validTo;
      const notYetValid = now < validFrom;
      const isSelfSigned = cert.issuer && cert.subject &&
        cert.issuer.CN === cert.subject.CN &&
        cert.issuer.O === cert.subject.O;

      const authorizationError = socket.authorizationError;

      resolve({
        valid: !isExpired && !notYetValid && !authorizationError,
        subject: cert.subject,
        issuer: cert.issuer,
        validFrom: validToISOString(validFrom),
        validTo: validToISOString(validTo),
        isExpired,
        notYetValid,
        isSelfSigned,
        fingerprint: cert.fingerprint256 || cert.fingerprint,
        daysRemaining: Math.ceil((validTo - now) / (1000 * 60 * 60 * 24)),
        authorizationError: authorizationError || null,
      });
      socket.destroy();
    });
    socket.setTimeout(8000);
    socket.on('timeout', () => { socket.destroy(); resolve({ valid: false, reason: 'SSL握手超时' }); });
    socket.on('error', (err) => { resolve({ valid: false, reason: err.message || String(err) || '连接错误' }); });
  });
}

function validToISOString(d) {
  if (!(d instanceof Date) || isNaN(d)) return '';
  return d.toISOString().replace(/\.\d{3}Z$/, 'Z');
}

async function checkSingleUrl(item, retryCount = RETRIES) {
  const result = {
    schoolId: item.schoolId,
    schoolName: item.schoolName,
    urlType: item.urlType,
    url: item.url,
    status: 'unknown',
    statusCode: null,
    responseTime: null,
    contentType: null,
    sslInfo: null,
    redirectChain: [],
    error: null,
    errorType: null,
    timestamp: new Date().toISOString(),
  };

  for (let attempt = 0; attempt <= retryCount; attempt++) {
    try {
      const startTime = Date.now();

      const config = {
        method: 'GET',
        url: item.url,
        timeout: TIMEOUT,
        maxRedirects: 10,
        validateStatus: () => true,
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
          'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
        },
      };
      if (PROXY) config.proxy = { host: new URL(PROXY).hostname, port: parseInt(new URL(PROXY).port, 10) || 8080 };

      const response = await axios(config);

      result.responseTime = Date.now() - startTime;
      result.statusCode = response.status;
      result.contentType = response.headers['content-type'] || null;
      result.redirectChain = response.request?.res?.responseUrl ? [response.request.res.responseUrl] : [];

      if (item.url.startsWith('https://')) {
        try {
          const urlObj = new URL(item.url);
          result.sslInfo = await checkSSLCertificate(urlObj.hostname, urlObj.port || 443);
        } catch (sslErr) {
          result.sslInfo = { valid: false, reason: sslErr.message };
        }
      }

      if (response.status >= 200 && response.status < 400) {
        result.status = 'ok';
      } else if (response.status >= 400 && response.status < 500) {
        result.status = 'client_error';
      } else if (response.status >= 500) {
        result.status = 'server_error';
      } else {
        result.status = 'redirect';
      }

      return result;
    } catch (err) {
      const errNames = ['ECONNREFUSED', 'ENOTFOUND', 'ETIMEDOUT', 'ECONNRESET', 'UNABLE_TO_VERIFY_LEAF_SIGNATURE', 'CERT_HAS_EXPIRED'];
      result.errorType = err.code || 'UNKNOWN';
      result.error = err.message;

      if (attempt < retryCount && errNames.some(n => err.message.toUpperCase().includes(n) || err.code === n)) {
        await sleep(1000 * (attempt + 1));
        continue;
      }

      result.status = 'error';
      result.responseTime = null;
      result.sslInfo = null;
      return result;
    }
  }
  result.status = 'error';
  return result;
}

function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }

function formatStatus(result) {
  switch (result.status) {
    case 'ok': return chalk.green.bold('✓ 可访问');
    case 'redirect': return chalk.yellow.bold('↗ 重定向');
    case 'client_error': return chalk.red.bold('✗ 客户端错误');
    case 'server_error': return chalk.red.bold('✗ 服务端错误');
    case 'error': return chalk.red.bold('✗ 连接失败');
    default: return chalk.gray('? 未知');
  }
}

function printResult(result, index, total) {
  const pad = String(total).length;
  const idx = chalk.gray(`[${String(index + 1).padStart(pad, ' ')}/${total}]`);
  const school = chalk.cyan(result.schoolName);
  const type = result.urlType === 'loginUrl' ? chalk.magenta('[登录页]') : chalk.blue('[课表页]');
  const status = formatStatus(result);
  const code = result.statusCode !== null ? chalk.yellow(`HTTP ${result.statusCode}`) : chalk.gray('----');
  const time = result.responseTime !== null ? chalk.white(`${result.responseTime}ms`) : chalk.gray('   -ms');

  console.log(`  ${idx} ${school} ${type} ${status}  ${code}  ${time}  ${chalk.gray(result.url.substring(0, 60))}`);

  if (result.sslInfo && result.url.startsWith('https://')) {
    const ssl = result.sslInfo;
    let sslStr = chalk.gray('     SSL: ');
    if (ssl.valid) {
      sslStr += chalk.green('有效');
      if (ssl.isSelfSigned) sslStr += chalk.yellow(' (自签名)');
      if (ssl.daysRemaining !== undefined) {
        if (ssl.daysRemaining <= 30) sslStr += chalk.red(` | 剩余${ssl.daysRemaining}天`);
        else sslStr += chalk.green(` | 剩余${ssl.daysRemaining}天`);
      }
    } else {
      sslStr += chalk.red(`无效`);
      const reasons = [];
      if (ssl.isExpired) reasons.push('已过期');
      if (ssl.notYetValid) reasons.push('尚未生效');
      if (ssl.authorizationError) reasons.push(ssl.authorizationError);
      if (ssl.reason) reasons.push(ssl.reason);
      if (reasons.length > 0) sslStr += chalk.red(` (${reasons.join(', ')})`);
    }
    console.log(sslStr);
  }

  if (result.error) {
    console.log(chalk.gray(`     错误: ${chalk.red(result.errorType)} - ${result.error}`));
  }
}

async function runWithConcurrency(items, handler, concurrency) {
  const results = [];
  const executing = new Set();
  let index = 0;

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

async function main() {
  logHeader();

  const optionsLog = [
    [`并发数: ${CONCURRENCY}`, `超时: ${TIMEOUT}ms`, `重试: ${RETRIES}次`],
  ];
  if (PROXY) optionsLog[0].push(`代理: ${PROXY}`);
  if (SCHOOL_FILTER) optionsLog[0].push(`学校: ${SCHOOL_FILTER.join(', ')}`);
  console.log(chalk.gray('  配置: ') + optionsLog[0].join('  | '));
  console.log();

  let schools;
  try {
    schools = loadSchools();
  } catch (err) {
    console.error(chalk.red(`无法读取学校列表: ${err.message}`));
    process.exit(1);
  }

  console.log(chalk.white(`  加载了 ${chalk.bold(schools.length)} 所学校`));
  if (SCHOOL_FILTER) console.log(chalk.yellow(`  已过滤为指定的 ${schools.length} 所学校`));

  const urlList = buildUrlList(schools);
  console.log(chalk.white(`  共需检测 ${chalk.bold(urlList.length)} 个 URL`));
  console.log();

  const prevReport = loadPreviousReport();
  const checkedUrls = getCheckedUrls(prevReport);
  let skippedCount = 0;
  if (checkedUrls.size > 0) {
    const filtered = urlList.filter(item => !checkedUrls.has(`${item.schoolId}::${item.urlType}`));
    skippedCount = urlList.length - filtered.length;
    if (skippedCount > 0) {
      console.log(chalk.yellow(`  断点续测: 跳过已检测的 ${skippedCount} 个 URL，剩余 ${filtered.length} 个`));
      console.log();
    }
    urlList.length = 0;
    urlList.push(...filtered);
  }

  if (urlList.length === 0) {
    console.log(chalk.green('  没有需要检测的 URL'));
    return;
  }

  console.log(chalk.bold.white('  ── 开始检测 ──'));
  console.log();

  const startTime = Date.now();
  const allResults = await runWithConcurrency(urlList, async (item, idx, total) => {
    const result = await checkSingleUrl(item);
    printResult(result, idx, total);
    return result;
  }, CONCURRENCY);
  const elapsed = Date.now() - startTime;

  console.log();
  console.log(chalk.bold.white('  ── 检测完成 ──'));
  console.log();

  const finalResults = prevReport?.results ? [...prevReport.results, ...allResults] : allResults;
  const okCount = finalResults.filter(r => r.status === 'ok').length;
  const failCount = finalResults.length - okCount;
  const avgResponse = finalResults.filter(r => r.responseTime !== null)
    .reduce((sum, r) => sum + r.responseTime, 0) / Math.max(1, finalResults.filter(r => r.responseTime !== null).length);

  const stats = {
    total: finalResults.length,
    passed: okCount,
    failed: failCount,
    passRate: ((okCount / Math.max(1, finalResults.length)) * 100).toFixed(1) + '%',
    avgResponseTime: Math.round(avgResponse),
    totalElapsedTime: elapsed,
    skippedFromResume: skippedCount,
    timestamp: new Date().toISOString(),
  };

  console.log(chalk.white.bold('  统计信息:'));
  console.log(`    总数:    ${chalk.bold(finalResults.length)}`);
  console.log(`    通过:    ${chalk.green.bold(okCount)}`);
  console.log(`    失败:    ${chalk.red.bold(failCount)}`);
  console.log(`    通过率:  ${stats.passRate >= '80%' ? chalk.green.bold(stats.passRate) : stats.passRate >= '50%' ? chalk.yellow.bold(stats.passRate) : chalk.red.bold(stats.passRate)}`);
  console.log(`    平均响应: ${chalk.white(Math.round(avgResponse) + 'ms')}`);
  console.log(`    总耗时:   ${chalk.white((elapsed / 1000).toFixed(1) + 's')}`);
  if (skippedCount > 0) console.log(`    跳过(续测): ${chalk.yellow(skippedCount)}`);
  console.log();

  const outputPath = OUTPUT_PATH || path.join(REPORT_DIR, `url-check-${Date.now()}.json`);
  const report = {
    meta: {
      generatedAt: new Date().toISOString(),
      tool: 'url-checker',
      version: '1.0.0',
      options: { concurrency: CONCURRENCY, timeout: TIMEOUT, retries: RETRIES, proxy: PROXY, schoolFilter: SCHOOL_FILTER },
    },
    stats,
    results: finalResults,
  };

  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, JSON.stringify(report, null, 2), 'utf-8');
  console.log(chalk.green.bold(`  报告已保存: ${outputPath}`));
  console.log();
}

main().catch(err => {
  console.error(chalk.red(`\n  致命错误: ${err.message}`));
  if (process.env.DEBUG) console.error(err.stack);
  process.exit(1);
});
