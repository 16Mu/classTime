#!/usr/bin/env node

const puppeteer = require('puppeteer');
const { program } = require('commander');
const fs = require('fs');
const path = require('path');

const ROOT_DIR = path.resolve(__dirname, '..', '..');
const SCHOOLS_JSON_PATH = path.join(ROOT_DIR, 'app', 'src', 'main', 'assets', 'schools.json');
const REPORTS_DIR = path.join(ROOT_DIR, 'reports');
const SCREENSHOTS_DIR = path.join(REPORTS_DIR, 'screenshots');
const JSON_REPORTS_DIR = path.join(REPORTS_DIR, 'json');

const MOBILE_UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1';
const DESKTOP_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

const DESKTOP_VIEWPORT = { width: 1920, height: 1080 };
const MOBILE_VIEWPORT = { width: 375, height: 812 };

const USERNAME_SELECTORS = [
  'input[id*="username"]', 'input[id*="user"]', 'input[id*="account"]',
  'input[id*="userID"]', 'input[id*="userId"]', 'input[id*="yhm"]',
  'input[name*="username"]', 'input[name*="user"]', 'input[name*="account"]',
  'input[name*="userID"]', 'input[name*="userId"]', 'input[name*="yhm"]',
  'input[placeholder*="用户"]', 'input[placeholder*="账号"]',
  'input[placeholder*="学号"]', 'input[placeholder*="工号"]',
  'input[type="text"]:not([type="hidden"])', 'input:not([type]):not([type="hidden"])'
];

const PASSWORD_SELECTORS = [
  'input[id*="password"]', 'input[id*="passwd"]', 'input[id*="pass"]',
  'input[id*="pwd"]', 'input[id*="mm"]',
  'input[name*="password"]', 'input[name*="passwd"]', 'input[name*="pass"]',
  'input[name*="pwd"]', 'input[name*="mm"]',
  'input[placeholder*="密码"]', 'input[placeholder*="口令"]',
  'input[type="password"]'
];

const CAPTCHA_SELECTORS = {
  image: [
    'img[src*="captcha"]', 'img[src*="verify"]', 'img[src*="code"]',
    'img[src*="rand"]', 'img[src*="yzm"]', 'img[src*="validate"]',
    'img[id*="captcha"]', 'img[id*="verify"]', 'img[id*="code"]',
    'img[class*="captcha"]', 'img[class*="verify"]', 'img[class*="code"]'
  ],
  input: [
    'input[id*="captcha"]', 'input[id*="verifycode"]', 'input[id*="yzm"]',
    'input[name*="captcha"]', 'input[name*="verifycode"]', 'input[name*="yzm"]',
    'input[placeholder*="验证码"]', 'input[placeholder*="验证"]',
    'input[placeholder*="yzm"]', 'input[placeholder*="code"]'
  ],
  iframe: [
    'iframe[src*="captcha"]', 'iframe[src*="verify"]', 'iframe[id*="captcha"]',
    'iframe[class*="captcha"]', 'iframe[class*="verify"]', 'embed[src*="captcha"]'
  ]
};

const SUBMIT_BUTTON_SELECTORS = [
  'button[type="submit"]', 'input[type="submit"]',
  'button[id*="login"]', 'button[id*="submit"]', 'button[id*="btnLogin"]',
  'button[class*="login"]', 'button[class*="submit"]',
  'input[id*="login"]', 'input[id*="submit"]', 'input[value*="登录"]',
  'input[value*="登陆"]', 'button:has-text("登录")', 'button:has-text("登陆")',
  'a[id*="login"]', 'a[class*="login"]', 'div[class*="login-btn"]',
  '[role="button"]:has-text("登录")'
];

function ensureDirs() {
  [REPORTS_DIR, SCREENSHOTS_DIR, JSON_REPORTS_DIR].forEach(dir => {
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
  });
}

function loadSchools() {
  try {
    const content = fs.readFileSync(SCHOOLS_JSON_PATH, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    throw new Error(`无法加载学校数据: ${error.message}`);
  }
}

function logWithTimestamp(message) {
  const now = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  console.log(`[${now}] ${message}`);
}

async function createBrowser(options = {}) {
  const launchOptions = {
    headless: !options.headed,
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--disable-web-security',
      '--disable-features=IsolateOrigins,site-per-process',
      '--window-size=1920,1080'
    ]
  };

  if (options.mobile) {
    launchOptions.args.push('--mobile');
  }

  let browser;
  try {
    browser = await puppeteer.launch(launchOptions);
  } catch (error) {
    throw new Error(`浏览器启动失败: ${error.message}`);
  }

  return browser;
}

async function checkLoginPage(school, options = {}) {
  const { timeout = 30000, mobile = false, headed = false, screenshot = true } = options;
  const schoolId = school.id;
  const schoolName = school.name;
  const loginUrl = school.loginUrl;

  const result = {
    schoolId,
    schoolName,
    loginUrl,
    timestamp: new Date().toISOString(),
    result: {
      pageLoaded: false,
      loadTime: 0,
      formExists: false,
      fields: {
        username: { found: false },
        password: { found: false },
        captcha: { found: false }
      },
      submitBtn: { found: false },
      captchaType: null,
      consoleErrors: [],
      networkErrors: [],
      screenshotPath: null
    }
  };

  let browser = null;
  let page = null;

  try {
    logWithTimestamp(`🔍 正在检测: ${schoolName} (${schoolId})`);
    logWithTimestamp(`   URL: ${loginUrl}`);

    browser = await createBrowser({ headed, mobile });
    page = await browser.newPage();

    const viewport = mobile ? MOBILE_VIEWPORT : DESKTOP_VIEWPORT;
    await page.setViewport(viewport);

    const userAgent = mobile ? MOBILE_UA : DESKTOP_UA;
    await page.setUserAgent(userAgent);

    const consoleMessages = [];
    const networkErrors = [];

    page.on('console', msg => {
      const type = msg.type();
      if (type === 'error') {
        const errorInfo = {
          type: 'console.error',
          message: msg.text(),
          location: msg.location()
        };
        consoleMessages.push(errorInfo);
      } else if (type === 'warning') {
        const warnInfo = {
          type: 'console.warn',
          message: msg.text()
        };
        consoleMessages.push(warnInfo);
      }
    });

    page.on('requestfailed', request => {
      const failure = request.failure();
      if (failure) {
        networkErrors.push({
          url: request.url(),
          method: request.method(),
          errorText: failure.errorText
        });
      }
    });

    page.on('response', async response => {
      try {
        const status = response.status();
        if (status >= 400) {
          networkErrors.push({
            url: response.url(),
            method: request.method(),
            status: status,
            statusText: response.statusText()
          });
        }
      } catch (e) {}
    });

    const startTime = Date.now();

    try {
      await page.goto(loginUrl, {
        waitUntil: 'domcontentloaded',
        timeout: timeout
      });

      try {
        await page.waitForNetworkIdle({ idleTime: 1000, timeout: Math.min(timeout / 2, 10000) });
      } catch (e) {}

      result.result.pageLoaded = true;
      result.result.loadTime = Date.now() - startTime;

      logWithTimestamp(`   ✅ 页面加载成功 (${result.result.loadTime}ms)`);

    } catch (navError) {
      result.result.loadTime = Date.now() - startTime;
      result.result.consoleErrors.push({
        type: 'navigation.error',
        message: `页面导航失败: ${navError.message}`
      });
      logWithTimestamp(`   ⚠️ 页面加载异常: ${navError.message}`);

      if (screenshot && page) {
        try {
          const screenshotPath = await takeScreenshot(page, schoolId, mobile);
          result.result.screenshotPath = screenshotPath;
        } catch (e) {}
      }

      result.result.consoleErrors = consoleMessages;
      result.result.networkErrors = networkErrors;
      return result;
    }

    const formExists = await page.evaluate(() => {
      const forms = document.querySelectorAll('form');
      return forms.length > 0;
    });
    result.result.formExists = formExists;
    logWithTimestamp(`   📋 表单检测: ${formExists ? '✅ 存在' : '❌ 未发现'}`);

    const usernameField = await findField(page, USERNAME_SELECTORS, 'username');
    result.result.fields.username = usernameField;
    logWithTimestamp(`   👤 用户名字段: ${usernameField.found ? `✅ ${usernameField.selector}` : '❌ 未发现'}`);

    const passwordField = await findField(page, PASSWORD_SELECTORS, 'password');
    result.result.fields.password = passwordField;
    logWithTimestamp(`   🔑 密码字段: ${passwordField.found ? `✅ ${passwordField.selector}` : '❌ 未发现'}`);

    const captchaResult = await detectCaptcha(page);
    result.result.fields.captcha = captchaResult.field;
    result.result.captchaType = captchaResult.type;

    if (captchaResult.found) {
      logWithTimestamp(`   🔐 验证码: ✅ 类型=${captchaResult.type}${captchaResult.field.selector ? `, 选择器=${captchaResult.field.selector}` : ''}`);
    } else {
      logWithTimestamp(`   🔐 验证码: ❌ 未发现`);
    }

    const submitBtn = await findSubmitButton(page);
    result.result.submitBtn = submitBtn;
    logWithTimestamp(`   🚀 登录按钮: ${submitBtn.found ? `✅ ${submitBtn.selector}` : '❌ 未发现'}`);

    if (screenshot && page) {
      try {
        const screenshotPath = await takeScreenshot(page, schoolId, mobile);
        result.result.screenshotPath = screenshotPath;
        logWithTimestamp(`   📸 截图已保存: ${screenshotPath}`);
      } catch (screenshotError) {
        logWithTimestamp(`   ⚠️ 截图失败: ${screenshotError.message}`);
        result.result.consoleErrors.push({
          type: 'screenshot.error',
          message: screenshotError.message
        });
      }
    } else if (!page) {
      logWithTimestamp(`   ⚠️ 跳过截图: 页面不可用`);
    }

    result.result.consoleErrors = consoleMessages.slice(0, 50);
    result.result.networkErrors = networkErrors.slice(0, 50);

    if (consoleMessages.length > 0) {
      logWithTimestamp(`   ⚠️ 控制台消息: ${consoleMessages.length} 条`);
    }
    if (networkErrors.length > 0) {
      logWithTimestamp(`   ❌ 网络错误: ${networkErrors.length} 条`);
    }

  } catch (error) {
    result.result.consoleErrors.push({
      type: 'runtime.error',
      message: `检测过程异常: ${error.message}`
    });
    logWithTimestamp(`   💥 检测异常: ${error.message}`);
  } finally {
    try {
      if (page) await page.close();
    } catch (e) {}
    try {
      if (browser) await browser.close();
    } catch (e) {}
  }

  return result;
}

async function findField(page, selectors, fieldType) {
  for (const selector of selectors) {
    try {
      const element = await page.$(selector);
      if (element) {
        const isVisible = await page.evaluate(el => {
          const style = window.getComputedStyle(el);
          const rect = el.getBoundingClientRect();
          return style.display !== 'none' &&
                 style.visibility !== 'hidden' &&
                 style.opacity !== '0' &&
                 rect.width > 0 &&
                 rect.height > 0;
        }, element);

        const inputType = await page.evaluate(el => el.type || el.getAttribute('type') || 'text', element);

        return {
          found: true,
          selector: selector,
          type: inputType,
          visible: isVisible
        };
      }
    } catch (e) {
      continue;
    }
  }

  return { found: false };
}

async function detectCaptcha(page) {
  const result = {
    found: false,
    field: { found: false },
    type: null
  };

  for (const selector of CAPTCHA_SELECTORS.image) {
    try {
      const element = await page.$(selector);
      if (element) {
        const src = await page.evaluate(el => el.src || '', element);
        result.found = true;
        result.field = {
          found: true,
          selector: selector,
          type: 'image',
          src: src
        };
        result.type = 'image';

        const hasSlider = await checkSliderCaptcha(page);
        if (hasSlider) {
          result.type = 'slider';
        }

        return result;
      }
    } catch (e) {
      continue;
    }
  }

  for (const selector of CAPTCHA_SELECTORS.input) {
    try {
      const element = await page.$(selector);
      if (element) {
        result.found = true;
        result.field = {
          found: true,
          selector: selector,
          type: 'text'
        };
        result.type = 'input';
        return result;
      }
    } catch (e) {
      continue;
    }
  }

  for (const selector of CAPTCHA_SELECTORS.iframe) {
    try {
      const element = await page.$(selector);
      if (element) {
        result.found = true;
        result.field = {
          found: true,
          selector: selector,
          type: 'iframe'
        };
        result.type = 'iframe';
        return result;
      }
    } catch (e) {
      continue;
    }
  }

  const hasSlider = await checkSliderCaptcha(page);
  if (hasSlider) {
    result.found = true;
    result.type = 'slider';
    result.field = {
      found: true,
      selector: 'slider-captcha-detected',
      type: 'slider'
    };
  }

  return result;
}

async function checkSliderCaptcha(page) {
  const sliderIndicators = [
    'div[id*="slider"]', 'div[class*="slider"]', 'div[id*="drag"]',
    'div[class*="drag"]', 'div[id*="verify"]', 'div[class*="verify"]',
    'canvas[id*="captcha"]', 'canvas[class*="captcha"]',
    'div[class*="gt_slider"]', 'div[id*="nc_1"]',
    'div[class*="tcaptcha"]', 'div[id*="captcha-container"]'
  ];

  for (const selector of sliderIndicators) {
    try {
      const element = await page.$(selector);
      if (element) {
        const isVisible = await page.evaluate(el => {
          const style = window.getComputedStyle(el);
          const rect = el.getBoundingClientRect();
          return style.display !== 'none' && rect.width > 0 && rect.height > 0;
        }, element);
        if (isVisible) {
          return true;
        }
      }
    } catch (e) {
      continue;
    }
  }

  return false;
}

async function findSubmitButton(page) {
  for (const selector of SUBMIT_BUTTON_SELECTORS) {
    try {
      const element = await page.$(selector);
      if (element) {
        const isVisible = await page.evaluate(el => {
          const style = window.getComputedStyle(el);
          const rect = el.getBoundingClientRect();
          return style.display !== 'none' &&
                 style.visibility !== 'hidden' &&
                 rect.width > 0 &&
                 rect.height > 0;
        }, element);

        if (isVisible) {
          return {
            found: true,
            selector: selector,
            visible: true
          };
        }
      }
    } catch (e) {
      continue;
    }
  }

  return { found: false };
}

async function takeScreenshot(page, schoolId, mobile = false) {
  ensureDirs();
  const schoolScreenshotDir = path.join(SCREENSHOTS_DIR, schoolId);
  if (!fs.existsSync(schoolScreenshotDir)) {
    fs.mkdirSync(schoolScreenshotDir, { recursive: true });
  }

  const filename = mobile ? 'login-page-mobile.png' : 'login-page.png';
  const screenshotPath = path.join(schoolScreenshotDir, filename);

  await page.screenshot({
    path: screenshotPath,
    fullPage: true,
    type: 'png'
  });

  return screenshotPath;
}

function saveJSONReport(results, outputPath) {
  ensureDirs();

  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const defaultFilename = `login-check-${timestamp}.json`;
  const filePath = outputPath || path.join(JSON_REPORTS_DIR, defaultFilename);

  const reportData = {
    generatedAt: new Date().toISOString(),
    totalSchools: results.length,
    summary: {
      success: results.filter(r => r.result.pageLoaded).length,
      failed: results.filter(r => !r.result.pageLoaded).length,
      withForm: results.filter(r => r.result.formExists).length,
      withUsername: results.filter(r => r.result.fields.username.found).length,
      withPassword: results.filter(r => r.result.fields.password.found).length,
      withCaptcha: results.filter(r => r.result.captchaType).length,
      withSubmitBtn: results.filter(r => r.result.submitBtn.found).length,
      captchaTypes: getCaptchaTypeSummary(results)
    },
    schools: results
  };

  fs.writeFileSync(filePath, JSON.stringify(reportData, null, 2), 'utf-8');
  return filePath;
}

function getCaptchaTypeSummary(results) {
  const types = {};
  for (const r of results) {
    if (r.result.captchaType) {
      types[r.result.captchaType] = (types[r.result.captchaType] || 0) + 1;
    }
  }
  return types;
}

function printSummary(results) {
  const total = results.length;
  const success = results.filter(r => r.result.pageLoaded).length;
  const withForm = results.filter(r => r.result.formExists).length;
  const withUsername = results.filter(r => r.result.fields.username.found).length;
  const withPassword = results.filter(r => r.result.fields.password.found).length;
  const withCaptcha = results.filter(r => r.result.captchaType).length;
  const withSubmitBtn = results.filter(r => r.result.submitBtn.found).length;

  console.log('\n' + '='.repeat(70));
  console.log('                    检测结果汇总报告');
  console.log('='.repeat(70));
  console.log(`  总计学校数:     ${String(total).padStart(4)}`);
  console.log(`  页面加载成功:   ${String(success).padStart(4)}  (${(success/total*100).toFixed(1)}%)`);
  console.log(`  发现登录表单:   ${String(withForm).padStart(4)}  (${(withForm/total*100).toFixed(1)}%)`);
  console.log(`  用户名字段:     ${String(withUsername).padStart(4)}  (${(withUsername/total*100).toFixed(1)}%)`);
  console.log(`  密码字段:       ${String(withPassword).padStart(4)}  (${(withPassword/total*100).toFixed(1)}%)`);
  console.log(`  验证码机制:     ${String(withCaptcha).padStart(4)}  (${(withCaptcha/total*100).toFixed(1)}%)`);
  console.log(`  登录按钮:       ${String(withSubmitBtn).padStart(4)}  (${(withSubmitBtn/total*100).toFixed(1)}%)`);

  const captchaTypes = getCaptchaTypeSummary(results);
  if (Object.keys(captchaTypes).length > 0) {
    console.log('\n  验证码类型分布:');
    for (const [type, count] of Object.entries(captchaTypes)) {
      console.log(`    - ${type.padEnd(10)} : ${count} 所学校`);
    }
  }

  console.log('='.repeat(70) + '\n');

  const failedSchools = results.filter(r => !r.result.pageLoaded);
  if (failedSchools.length > 0) {
    console.log('⚠️  加载失败的学校:');
    for (const s of failedSchools) {
      console.log(`   - ${s.schoolName} (${s.schoolId}): ${s.result.consoleErrors[0]?.message || '未知错误'}`);
    }
    console.log('');
  }
}

async function runConcurrentChecks(schools, options) {
  const concurrency = options.concurrency || 3;
  const results = [];
  const total = schools.length;
  let completed = 0;

  console.log(`\n📋 开始检测 ${total} 所学校，并发数: ${concurrency}\n`);

  for (let i = 0; i < total; i += concurrency) {
    const batch = schools.slice(i, i + concurrency);
    const batchPromises = batch.map(school =>
      checkLoginPage(school, options)
        .then(result => {
          completed++;
          results.push(result);
          return result;
        })
        .catch(error => {
          completed++;
          results.push({
            schoolId: school.id,
            schoolName: school.name,
            loginUrl: school.loginUrl,
            timestamp: new Date().toISOString(),
            result: {
              pageLoaded: false,
              loadTime: 0,
              formExists: false,
              fields: { username: { found: false }, password: { found: false }, captcha: { found: false } },
              submitBtn: { found: false },
              captchaType: null,
              consoleErrors: [{ type: 'fatal.error', message: error.message }],
              networkErrors: [],
              screenshotPath: null
            }
          });
          return null;
        })
    );

    await Promise.all(batchPromises);

    if (i + concurrency < total) {
      logWithTimestamp(`📊 进度: ${completed}/${total} (${(completed/total*100).toFixed(1)}%)`);
    }
  }

  return results;
}

program
  .name('login-page-checker')
  .description('教务系统登录页面自动化检测工具')
  .version('1.0.0')
  .option('--school <ids>', '指定学校 ID（逗号分隔，默认全部）')
  .option('--timeout <ms>', '页面加载超时（毫秒）', '30000')
  .option('--mobile', '使用移动端 UA 模拟', false)
  .option('--headed', '显示浏览器窗口（调试用）', false)
  .option('--screenshot', '启用截图（默认启用）', true)
  .option('--no-screenshot', '禁用截图')
  .option('--output <path>', '输出文件路径')
  .option('--concurrency <n>', '并发数（默认 3）', '3')
  .action(async (options) => {
    try {
      const timeoutVal = parseInt(options.timeout, 10);
      if (isNaN(timeoutVal) || timeoutVal <= 0) {
        console.error('❌ 错误: --timeout 必须是正整数');
        process.exit(1);
      }

      const concurrencyVal = parseInt(options.concurrency, 10);
      if (isNaN(concurrencyVal) || concurrencyVal <= 0 || concurrencyVal > 5) {
        console.error('❌ 错误: --concurrency 必须是 1-5 之间的整数（Puppeteer 资源消耗大）');
        process.exit(1);
      }

      ensureDirs();

      const allSchools = loadSchools();
      let targetSchools = allSchools;

      if (options.school) {
        const requestedIds = options.school.split(',').map(id => id.trim().toLowerCase());
        targetSchools = allSchools.filter(s => requestedIds.includes(s.id.toLowerCase()));

        if (targetSchools.length === 0) {
          console.error(`❌ 错误: 未找到匹配的学校 ID: ${options.school}`);
          console.error(`   可用的学校 ID: ${allSchools.map(s => s.id).join(', ')}`);
          process.exit(1);
        }

        console.log(`\n🎯 指定学校: ${targetSchools.map(s => `${s.name}(${s.id})`).join(', ')}`);
      }

      const checkOptions = {
        timeout: timeoutVal,
        mobile: options.mobile,
        headed: options.headed,
        screenshot: options.screenshot !== false,
        concurrency: concurrencyVal
      };

      if (checkOptions.mobile) {
        console.log('📱 移动端模式已启用\n');
      }
      if (checkOptions.headed) {
        console.log('🖥️  有头模式已启用（调试用）\n');
      }

      const startTime = Date.now();
      const results = await runConcurrentChecks(targetSchools, checkOptions);
      const totalTime = ((Date.now() - startTime) / 1000).toFixed(1);

      printSummary(results);

      const reportPath = saveJSONReport(results, options.output);
      console.log(`📄 JSON 报告已保存: ${reportPath}`);
      console.log(`⏱️  总耗时: ${totalTime}s`);

      const failedCount = results.filter(r => !r.result.pageLoaded).length;
      if (failedCount > 0) {
        process.exit(1);
      }

    } catch (error) {
      console.error(`\n💥 致命错误: ${error.message}`);
      if (process.env.DEBUG) {
        console.error(error.stack);
      }
      process.exit(1);
    }
  });

if (process.argv.length <= 2) {
  program.help();
}

program.parse(process.argv);

process.on('unhandledRejection', (reason, promise) => {
  console.error('❌ 未处理的异步错误:', reason);
});

process.on('uncaughtException', (error) => {
  console.error('💥 未捕获的异常:', error.message);
  process.exit(1);
});
