# 学校系统批量上架测试操作手册

> **文档版本**: v1.0.0
> **适用对象**: QA 测试人员、开发人员、运维人员
> **最后更新**: 2026-04-03

---

## 📋 目录

1. [概述](#1-概述)
2. [环境准备](#2-环境准备)
3. [快速开始](#3-快速开始)
4. [详细使用指南](#4-详细使用指南)
   - 4.1 [URL 可访问性检测](#41-url-可访问性检测)
   - 4.2 [登录页面检测](#42-登录页面检测)
   - 4.3 [课表接口检测](#43-课表接口检测)
   - 4.4 [数据解析验证](#44-数据解析验证)
   - 4.5 [学校配置分析](#45-学校配置分析)
   - 4.6 [批次测试执行](#46-批次测试执行)
   - 4.7 [报告生成与查看](#47-报告生成与查看)
5. [工作流程示例](#5-工作流程示例)
6. [常见问题 FAQ](#6-常见问题-faq)
7. [最佳实践](#7-最佳实践)
8. [附录](#8-附录)

---

## 1. 概述

### 1.1 工具集介绍

本测试工具集专为**课程表应用学校系统集成测试**设计，提供从 URL 可访问性到数据解析完整性的全流程自动化检测能力。

```
┌────────────────────────────────────────────────────────────────────┐
│                        测试工具架构图                               │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│   ┌─────────────────┐    ┌─────────────────┐    ┌───────────────┐  │
│   │  url-checker    │    │login-page-      │    │ schedule-api  │  │
│   │  (可访问性检测)  │───→│ checker         │───→│ checker       │  │
│   │                 │    │ (登录页检测)     │    │ (课表接口)    │  │
│   └─────────────────┘    └─────────────────┘    └───────────────┘  │
│          │                       │                      │          │
│          ↓                       ↓                      ↓          │
│   ┌─────────────────────────────────────────────────────────────┐ │
│   │                   data-parser-validator                      │ │
│   │                    (数据解析验证器)                           │ │
│   └─────────────────────────────────────────────────────────────┘ │
│                              │                                    │
│                              ↓                                    │
│   ┌─────────────────────────────────────────────────────────────┐ │
│   │              school-parser / report-generator                │ │
│   │            (学校分析工具 / 报告生成器)                        │ │
│   └─────────────────────────────────────────────────────────────┘ │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

### 1.2 核心功能

| 功能模块 | 工具名称 | 主要用途 | 耗时 |
|----------|----------|----------|------|
| **URL 可访问性** | `url-checker.js` | 批量检测 loginUrl/scheduleUrl 是否可达 | ~30s (26 所学校) |
| **登录页检测** | `login-page-checker.js` | 自动化检测登录表单完整性 | ~2-5min/所学校 |
| **课表接口** | `schedule-api-checker.js` | 验证课表 API 返回数据有效性 | ~20s (26 所学校) |
| **数据解析** | `data-parser-validator.js` | 验证 Extractor 解析逻辑正确性 | ~10s |
| **学校分析** | `school-parser.js` | 分析 schools.json 与 Extractor 对应关系 | ~2s |

### 1.3 支持的系统类型

当前支持 **8 种教务系统类型**：

| 系统类型 | 中文名称 | 代表学校 | 数据格式 | 特点 |
|:--------:|----------|----------|:--------:|------|
| `zfsoft` | 正方教务系统 | 重庆电力、东华理工 | JSON | 需要 CSRF Token，POST 请求 |
| `qiangzhi` | 强智科教系统 | 重庆机电、南宁师大 | HTML | iframe 嵌入，需解析表格 |
| `shuwei` | 树维教务系统 | 商丘师范、安徽科技 | HTML | EAMS 系统，JavaScript 提取 |
| `kingosoft` | 青果教务系统 | 山东女院、昆明医科 | HTML | 支持多种课表格式 |
| `custom` | 自研系统 | 南京中医、青岛大学 | JavaScript | 各校独立实现 |
| `chengfang` | 乘方系统 | 广州中医药 | JavaScript | API 接口获取数据 |
| `ynsmart` | YN 智慧校园 | 德阳通用电子 | JavaScript | 逐周获取，耗时较长 |
| `chaoxing` | 超星系统 | 成都大学 | JavaScript | 集成超星平台 |

### 1.4 适用场景

- ✅ 新学校 Extractor 开发完成后的验收测试
- ✅ 定期回归测试（建议每周一次）
- ✅ 学校系统升级后的兼容性验证
- ✅ 批量上架前的质量把关
- ✅ 问题排查和根因分析

---

## 2. 环境准备

### 2.1 系统要求

| 项目 | 最低要求 | 推荐配置 |
|------|----------|----------|
| **操作系统** | Windows 10 (64位) | Windows 10/11 (64位) |
| **Node.js** | v18.0+ LTS | v20.x LTS |
| **npm** | v8.0+ | v10.x |
| **内存** | ≥ 8GB | ≥ 16GB |
| **磁盘空间** | ≥ 2GB 可用 | ≥ 5GB (含 Chromium) |
| **网络** | 可访问目标教务系统 | 稳定宽带连接 |

### 2.2 安装步骤

#### 步骤 1: 检查 Node.js 环境

```bash
# 检查 Node.js 版本（需要 >= 18.0）
node --version
# 输出示例: v20.11.0 ✅

# 检查 npm 版本
npm --version
# 输出示例: 10.2.4 ✅
```

> ⚠️ 如果版本过低，请从 [Node.js 官网](https://nodejs.org/) 下载安装 LTS 版本

#### 步骤 2: 进入项目 scripts 目录

```bash
cd e:\KEchengbiao\scripts
```

#### 步骤 3: 安装依赖

```bash
# 安装根目录依赖（包含 puppeteer、axios 等）
npm install

# 安装 accessibility 子目录依赖
cd testing/accessibility
npm install
cd ../..
```

#### 步骤 4: 验证安装

```bash
# 验证 Puppeteer 是否正确安装
node -e "require('puppeteer'); console.log('✅ Puppeteer 安装成功')"

# 验证其他核心依赖
node -e "require('axios'); console.log('✅ Axios 安装成功')"
node -e "require('chalk'); console.log('✅ Chalk 安装成功')"
node -e "require('commander'); console.log('✅ Commander 安装成功')"
```

### 2.3 依赖说明

#### 核心依赖清单

| 包名 | 版本 | 用途 | 是否必须 |
|------|:----:|------|:--------:|
| `puppeteer` | ^22.0.0 | 浏览器自动化（登录页检测） | ✅ 是 |
| `axios` | ^1.7.0 | HTTP 请求库 | ✅ 是 |
| `chalk` | ^4.1.2 | 终端彩色输出 | ✅ 是 |
| `commander` | ^12.0.0 | 命令行参数解析 | ✅ 是 |
| `csv-writer` | ^1.6.0 | CSV 文件导出 | 可选 |

#### Puppeteer 特殊说明

Puppeteer 会自动下载 Chromium 浏览器（约 170MB），首次安装可能较慢：

```
下载进度:
███████████████████████████████████████████ 100%

Chromium downloaded to: C:\Users\<用户名>\AppData\Local\puppeteer\chrome-...
```

如果下载失败，可以尝试以下方法：

```bash
# 方法 1: 设置镜像源（国内用户推荐）
set PUPPETEER_DOWNLOAD_BASE_URL=https://npmmirror.com/mirrors/chrome-for-testing
npm install puppeteer

# 方法 2: 使用系统 Chrome
set PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
npm install puppeteer
# 然后在代码中指定 Chrome 路径
```

### 2.4 代理配置

如果目标学校服务器需要通过代理访问，可以使用以下方式配置：

#### 方式一：命令行参数（推荐）

```bash
# url-checker 支持 --proxy 参数
node testing/accessibility/url-checker.js --proxy http://127.0.0.1:7890
```

支持的代理格式：
- `http://host:port` — HTTP 代理
- `https://host:port` — HTTPS 代理
- `socks5://host:port` — SOCKS5 代理

#### 方式二：环境变量

```bash
# 设置全局 HTTP 代理（影响所有工具）
set HTTP_PROXY=http://127.0.0.1:7890
set HTTPS_PROXY=http://127.0.0.1:7890
```

#### 方式三：Node.js 代码配置

在 `.env` 文件或启动脚本中设置：
```javascript
process.env.HTTP_PROXY = 'http://127.0.0.1:7890';
process.env.HTTPS_PROXY = 'http://127.0.0.1:7890';
```

### 2.5 目录结构说明

```
KEchengbiao/
├── app/
│   └── src/main/assets/
│       └── schools.json          # 学校配置文件（26 所学校）
├── scripts/
│   ├── package.json              # 主依赖配置
│   ├── config/samples/           # 示例数据文件
│   │   ├── zfsoft-sample.json
│   │   ├── qiangzhi-sample.html
│   │   └── ...
│   ├── functional/               # 功能测试工具
│   │   ├── login-page-checker.js # 登录页检测
│   │   ├── schedule-api-checker.js # 课表接口检测
│   │   └── data-parser-validator.js # 数据解析验证
│   ├── testing/accessibility/    # 可访问性测试
│   │   ├── url-checker.js        # URL 检测工具
│   │   └── package.json
│   └── utils/
│       └── school-parser.js      # 学校配置分析工具
├── reports/                      # 输出目录
│   ├── json/                     # JSON 格式报告
│   ├── screenshots/              # 截图文件
│   │   └── <schoolId>/           # 按学校分类
│   ├── comprehensive-report-*.md # 综合报告
│   └── test-report.html          # HTML 报告
└── output/                       # 分析输出
    └── school-analysis-*.json
```

---

## 3. 快速开始

> 🚀 **目标**: 5 分钟内完成第一轮基础检测

### 3.1 最简命令序列

打开终端，依次执行以下命令：

```bash
# ═══════════════════════════════════════════════════════════
# 第 1 步: 进入脚本目录并安装依赖
# ═══════════════════════════════════════════════════════════
cd e:\KEchengbiao\scripts
npm install
cd testing/accessibility && npm install && cd ../..

# ═══════════════════════════════════════════════════════════
# 第 2 步: 查看待上架学校列表（可选）
# ═══════════════════════════════════════════════════════════
node utils/school-parser.js pending

# 输出示例:
# 📋 待上架学校清单 (共 3 所)
#
# ID          Extractor 文件              原因
# ---------- ---------------------------- --------
# new-school  NewSchoolExtractor.kt        has_extractor_no_config

# ═══════════════════════════════════════════════════════════
# 第 3 步: 执行 URL 可访问性检测（最快速）
# ═══════════════════════════════════════════════════════════
node testing/accessibility/url-checker.js

# 输出示例:
# ╔══════════════════════════════════════════════════════════╗
# ║       教务系统 URL 可访问性批量检测工具              ║
# ╚══════════════════════════════════════════════════════════╝
#
#   配置: 并发数: 5  |  超时: 10000ms  |  重试: 3次
#
#   加载了 26 所学校
#   共需检测 50 个 URL
#
#   ── 开始检测 ──
#
#   [1/50] 重庆电力高等专科学校 [登录页] ✓ 可访问  HTTP 200  1234ms
#   [2/50] 重庆电力高等专科学校 [课表页] ✓ 可访问  HTTP 200  892ms
#   ...

# ═══════════════════════════════════════════════════════════
# 第 4 步: 执行课表接口检测（可选，较快）
# ═══════════════════════════════════════════════════════════
node functional/schedule-api-checker.js

# 输出示例:
# ╔═════════════════════════════════════════════════════════════╗
# ║            教务系统课表 API 接口检测工具                  ║
# ╚═════════════════════════════════════════════════════════════╝
#
#   配置: 超时: 15000ms  |  并发: 5  |  深度: 否
#
#   加载了 26 所学校
#   类型分布: zfsoft(6), shuwei(6), kingosoft(4), custom(4), qiangzhi(3), chengfang(1), ynsmart(1), chaoxing(1)
#
#   ── 开始检测 ──
#
#   [1/26] cqepc [zfsoft] ✓ 可访问 HTTP 200  567ms JSON ✓ 有效
#      └─ 接口正常返回 JSON 数据，发现 6 个关键字段: kbList, kcmc, xm, cdmc, xqj, jcs
#      └─ 关键字段: kbList, kcmc, xm, cdmc, xqj, jcs, zcd
#   ...

# ═══════════════════════════════════════════════════════════
# 第 5 步: 执行数据解析验证（快速）
# ═══════════════════════════════════════════════════════════
node functional/data-parser-validator.js

# ═══════════════════════════════════════════════════════════
# 第 6 步: 执行登录页检测（较慢，按需执行）
# ═══════════════════════════════════════════════════════════
# 单个学校检测:
node functional/login-page-checker.js --school cqepc --headed

# 多个学校批量检测:
node functional/login-page-checker.js --school cqepc,sqsfxy --concurrency 2
```

### 3.2 结果查看位置

所有检测结果都会自动保存到 `reports/` 目录：

```
reports/
├── json/                                    # JSON 详细报告
│   ├── url-check-2026-04-03T01-39-28.json   # URL 检测结果
│   ├── login-check-2026-04-03T01-41-45.json # 登录页检测结果
│   ├── schedule-api-check-*.json            # 课表接口检测结果
│   └── parser-validation-*.json             # 数据解析验证结果
├── screenshots/                             # 登录页截图
│   └── cqepc/
│       └── login-page.png
└── comprehensive-report-2026-04-03T01-38-01.md  # 综合汇总报告
```

### 3.3 一键完整检测脚本

创建 `run-full-test.bat` 用于一键执行全部检测：

```bat
@echo off
chcp 65001 >nul
echo ========================================
echo   课程表应用 - 全量测试套件
echo ========================================
echo.

cd /d %~dp0scripts

echo [1/5] 正在执行 URL 可访问性检测...
node testing/accessibility/url-checker.js
if errorlevel 1 (
    echo   ⚠️ URL 检测发现问题，继续执行后续测试...
)

echo.
echo [2/5] 正在执行课表接口检测...
node functional/schedule-api-checker.js

echo.
echo [3/5] 正在执行数据解析验证...
node functional/data-parser-validator.js

echo.
echo [4/5] 正在执行登录页检测（此步骤较慢）...
set /p RUN_LOGIN="是否执行登录页检测？(y/n): "
if /i "%RUN_LOGIN%"=="y" (
    node functional/login-page-checker.js --concurrency 2
) else (
    echo   已跳过登录页检测
)

echo.
echo [5/5] 生成综合报告...
echo 测试完成！请查看 reports/ 目录下的报告文件。
echo.

pause
```

---

## 4. 详细使用指南

### 4.1 URL 可访问性检测

#### 工具信息

- **文件路径**: `scripts/testing/accessibility/url-checker.js`
- **用途**: 批量检测所有学校的 loginUrl 和 scheduleUrl 是否可访问
- **特点**: 速度快，支持并发，自动重试，SSL 证书检测

#### 完整参数列表

```bash
node testing/accessibility/url-checker.js [选项]

选项:
  -c, --concurrency <n>    并发请求数 (默认: 5)
  -t, --timeout <ms>       单次请求超时时间毫秒 (默认: 10000)
  -r, --retries <n>        失败重试次数 (默认: 3)
  -p, --proxy <url>        代理服务器地址
  -s, --school <ids>       指定学校ID (逗号分隔)
  -o, --output <path>      自定义输出文件路径
  -h, --help               显示帮助信息
```

#### 使用示例

**示例 1: 全量检测（默认参数）**
```bash
node testing/accessibility/url-checker.js
```

**示例 2: 检测指定学校**
```bash
# 检测单个学校
node testing/accessibility/url-checker.js --school cqepc

# 检测多个学校
node testing/accessibility/url-checker.js --school cqepc,sqsfxy,njzyydx
```

**示例 3: 高并发 + 短超时（快速筛查）**
```bash
node testing/accessibility/url-checker.js -c 10 -t 5000 -r 1
```

**示例 4: 使用代理**
```bash
node testing/accessibility/url-checker.js --proxy http://127.0.0.1:7890
```

**示例 5: 低并发 + 长超时（网络较差环境）**
```bash
node testing/accessibility/url-checker.js -c 2 -t 30000 -r 5
```

#### 输出结果解读

```
╔══════════════════════════════════════════════════════════╗
║       教务系统 URL 可访问性批量检测工具              ║
╚══════════════════════════════════════════════════════════╝

  配置: 并发数: 5  |  超时: 10000ms  |  重试: 3次

  加载了 26 所学校
  共需检测 50 个 URL

  ── 开始检测 ──

  [1/50] 重庆电力高等专科学校 [登录页] ✓ 可访问  HTTP 200  1234ms  https://jwxt.cqepc.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default
     SSL: 有效 | 剩余180天
  [2/50] 重庆电力高等专科学校 [课表页] ✓ 可访问  HTTP 200  892ms   https://jwxt.cqepc.edu.cn/jwglxt/kbcx/xskbcx_cxXsKb.html
     SSL: 有效 | 剩余180天
  [3/50] 重庆机电职业技术大学 [登录页] ↗ 重定向  HTTP 302  2345ms  https://qzmh.cqvtu.edu.cn/portal/login/

  ...

  ── 检测完成 ──

  统计信息:
    总数:    50
    通过:    42
    失败:    8
    通过率:  84.0%
    平均响应: 1523ms
    总耗时:   12.3s

  报告已保存: reports/json/url-check-1709582400000.json
```

**状态图标说明**:

| 图标 | 含义 | HTTP 状态码范围 |
|:----:|------|----------------|
| ✓ 可访问 | 正常可访问 | 200-399 |
| ↗ 重定向 | 发生重定向 | 300-399 |
| ✗ 客户端错误 | 请求有问题 | 400-499 |
| ✗ 服务端错误 | 服务器异常 | 500-599 |
| ✗ 连接失败 | 网络层错误 | N/A |

#### 断点续测功能

工具会自动记录已检测的 URL，下次运行时会跳过已完成的项：

```
  断点续测: 跳过已检测的 42 个 URL，剩余 8 个
```

如需重新检测全部 URL，删除或移动之前的报告文件即可：

```bash
# 删除旧报告以触发全量重新检测
del reports\json\url-check-*.json
```

---

### 4.2 登录页面检测

#### 工具信息

- **文件路径**: `scripts/functional/login-page-checker.js`
- **用途**: 使用 Puppeteer 自动化浏览器检测登录页面完整性
- **特点**: 真实浏览器渲染，截图保存，表单元素识别

#### 完整参数列表

```bash
node functional/login-page-checker.js [选项]

选项:
  --school <ids>         指定学校ID (逗号分隔，默认全部)
  --timeout <ms>         页面加载超时毫秒 (默认: 30000)
  --mobile               使用移动端 UA 模拟
  --headed               显示浏览器窗口 (调试用)
  --screenshot           启用截图 (默认启用)
  --no-screenshot        禁用截图
  --output <path>        输出文件路径
  --concurrency <n>      并发数，1-5 (默认: 3)
  -h, --help             显示帮助信息
```

#### 使用示例

**示例 1: 单个学校检测（有头模式，方便调试）**
```bash
node functional/login-page-checker.js --school cqepc --headed
```

运行后会弹出 Chrome 窗口，可以实时观察页面加载过程。

**示例 2: 移动端模拟检测**
```bash
node functional/login-page-checker.js --school cqepc --mobile --headed
```

**示例 3: 批量检测多所学校**
```bash
# 检测两所学校，并发数为 2
node functional/login-page-checker.js --school cqepc,sqsfxy --concurrency 2
```

**示例 4: 仅检测不截图（节省磁盘空间）**
```bash
node functional/login-page-checker.js --school cqepc --no-screenshot
```

**示例 5: 自定义超时时间**
```bash
# 页面加载慢的学校，延长超时到 60 秒
node functional/login-page-checker.js --school gzhsxy --timeout 60000
```

#### 输出结果解读

```
[14:30:15] 🔍 正在检测: 重庆电力高等专科学校 (cqepc)
[14:30:15]    URL: https://jwxt.cqepc.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default
[14:30:18]    ✅ 页面加载成功 (2856ms)
[14:30:18]    📋 表单检测: ✅ 存在
[14:30:18]    👤 用户名字段: ✅ input[id*="username"]
[14:30:18]    🔑 密码字段: ✅ input[type="password"]
[14:30:18]    🔐 验证码: ✅ 类型=image, 选择器=img[src*="captcha"]
[14:30:18]    🚀 登录按钮: ✅ button:has-text("登录")
[14:30:19]    📸 截图已保存: reports/screenshots/cqepc/login-page.png
[14:30:19]    ⚠️ 控制台消息: 2 条
[14:30:19]    ❌ 网络错误: 0 条
```

#### 检测内容详解

**1. 表单元素识别策略**

工具使用多层选择器策略查找表单元素：

| 元素 | 选择器优先级（由高到低） |
|------|--------------------------|
| 用户名 | id 包含 username/user/account → name 包含 → placeholder 包含 → 任意 text input |
| 密码 | id 包含 password/pass/pwd → name 包含 → placeholder 包含 → type=password |
| 验证码 | img src 包含 captcha/verify/code → input id 包含 → iframe src 包含 → slider 检测 |
| 按钮 | type=submit → id/class 包含 login/submit → value 包含"登录" → 文本匹配 |

**2. 验证码类型自动识别**

```
检测到的验证码类型:
┌────────────────────────────────────────────────────────┐
│ 类型        │ 说明                  │ 处理难度         │
├─────────────┼───────────────────────┼──────────────────┤
│ image       │ 传统图片验证码         │ ★★☆☆☆ 中等      │
│ slider      │ 滑块验证（极验等）     │ ★★★★☆ 困难      │
│ input       │ 文本输入验证码         │ ★★☆☆☆ 中等      │
│ iframe      │ 第三方嵌入验证码       │ ★★★★★ 极难      │
│ 无          │ 不需要验证码           │ ☆☆☆☆☆ 简单      │
└────────────────────────────────────────────────────────┘
```

**3. 截图存储结构**

```
reports/screenshots/
└── <schoolId>/
    ├── login-page.png          # 桌面端截图 (1920×1080)
    └── login-page-mobile.png   # 移动端截图 (375×812, 仅 --mobile 时)
```

---

### 4.3 课表接口检测

#### 工具信息

- **文件路径**: `scripts/functional/schedule-api-checker.js`
- **用途**: 针对不同系统类型检测课表 API 的可用性和数据有效性
- **特点**: 按系统类型定制检测逻辑，深度模式支持带参数请求

#### 完整参数列表

```bash
node functional/schedule-api-checker.js [选项]

选项:
  -s, --school <ids>      指定学校ID (逗号分隔)
  -t, --type <type>       只检测指定系统类型
                          (zfsoft/qiangzhi/shuwei/kingosoft/custom/chengfang/ynsmart/chaoxing)
  -d, --deep              深度检测（尝试带参数 POST 请求）
  --timeout <ms>          超时时间毫秒 (默认: 15000)
  -c, --concurrency <n>   并发数 (默认: 5)
  -o, --output <path>     输出路径
  -h, --help              显示帮助信息
```

#### 使用示例

**示例 1: 全量检测**
```bash
node functional/schedule-api-checker.js
```

**示例 2: 按系统类型筛选**
```bash
# 只检测正方系统的学校
node functional/schedule-api-checker.js --type zfsoft

# 只检测树维系统的学校
node functional/schedule-api-checker.js --type shuwei
```

**示例 3: 深度检测模式（针对 zfsoft 系统）**
```bash
# 深度模式会携带学年学期参数发送 POST 请求
node functional/schedule-api-checker.js --type zfsoft --deep
```

**示例 4: 组合过滤**
```bash
# 检测特定学校的强智系统接口
node functional/schedule-api-checker.js --school cqvtu,nnsfdx,gzhsxy --type qiangzhi
```

#### 各系统类型检测逻辑详解

##### zfsoft（正方系统）

```
检测流程:
┌──────────────┐
│ 读取 scheduleUrl │
└──────┬───────┘
       ↓
┌──────────────────────────────┐
│ 构建 POST 请求 (--deep 模式)  │
│ 携带 xnm(学年) + xqm(学期)    │
└──────┬───────────────────────┘
       ↓
┌──────────────────────────────┐
│ 解析 JSON 响应                 │
│ 检查 kbList 字段存在           │
│ 验证 jsonMapping 关键字段      │
└──────┬───────────────────────┘
       ↓
┌──────────────────────────────┐
│ 输出: accessible/dataValid    │
│ 关键字段: kcmc,xm,cdmc,      │
│         xqj,jcs,zcd          │
└──────────────────────────────┘
```

**预期响应示例**:
```json
{
  "kbList": [
    {
      "kcmc": "高等数学",
      "xm": "张教授",
      "cdmc": "教学楼A301",
      "xqj": "1",
      "jcs": "1-2",
      "zcd": "1-16周"
    }
  ]
}
```

##### qiangzhi（强智系统）

```
检测流程:
┌──────────────┐
│ GET scheduleUrl │
└──────┬───────┘
       ↓
┌──────────────────────────────┐
│ 解析 HTML 响应                 │
│ 搜索 timetable/xskb 关键词     │
│ 检测 iframe 结构               │
│ 检测 table class 特征          │
└──────┬───────────────────────┘
       ↓
┌──────────────────────────────┐
│ 输出: accessible              │
│ 注意: 通常需要先登录获取 Cookie │
└──────────────────────────────┘
```

##### shuwei（树维系统）

```
特征标识:
- URL 包含: courseTableForStd.action
- HTML 包含: eams.js, courseTable 变量
- 数据提取: JavaScript 动态渲染
```

##### kingosoft（青果系统）

```
特征标识:
- URL 包含: jwglxt/student/xskb 或 sdnzjw/frame/desk
- HTML 包含: frame 结构 (topFrame, mainFrame, menuFrame)
- 数据格式: 二维 HTML 表格
```

##### custom / chengfang / ynsmart / chaoxing（其他系统）

采用通用检测策略，根据各系统的已知特征标识进行匹配。

#### 输出结果解读

```
╔═════════════════════════════════════════════════════════════╗
║            教务系统课表 API 接口检测工具                  ║
╚═════════════════════════════════════════════════════════════╝

  配置: 超时: 15000ms  |  并发: 5  |  深度: 否

  加载了 26 所学校
  类型分布: zfsoft(6), shuwei(6), kingosoft(4), custom(4), qiangzhi(3), chengfang(1), ynsmart(1), chaoxing(1)

  ── 开始检测 ──

  [1/26] cqepc [zfsoft] ✓ 可访问 HTTP 200  567ms JSON ✓ 有效
     └─ 接口正常返回 JSON 数据，发现 6 个关键字段: kbList, kcmc, xm, cdmc, xqj, jcs
     └─ 关键字段: kbList, kcmc, xm, cdmc, xqj, jcs, zcd
  [2/26] cqvtu [qiangzhi] ✓ 可访问 HTTP 200  1234ms HTML ✓ 有效
     └─ 强智系统课表页面可访问，包含课表特征元素
     └─ 关键字段: timetable_keyword, schedule_table_class, iframe_structure
  [3/26] sqsfxy [shuwei] ✓ 可访问 HTTP 200  890ms HTML ✓有效
     └─ 树维系统 courseTableForStd 接口可访问，检测到 EAMS 系统特征
     └─ 关键字段: eams_action, eams_js, course_table_var
  ...

  ── 检测完成 ──

  ── 统计汇总 ──

  总计学校:   26 所
  可访问:     22 所
  不可用:     4 所
  通过率:     84.6%
  数据有效:   18 所
  数据有效率: 69.2%

  按系统类型分组:

    zfsoft           6 所     4 ✓   2 ✗   67%  ████████████████░░░░░░
    shuwei           6 所     6 ✓   0 ✗  100%  ██████████████████████
    kingosoft        4 所     4 ✓   0 ✗  100%  ██████████████████████
    custom           4 所     4 ✓   0 ✗  100%  ██████████████████████
    qiangzhi         3 所     2 ✓   1 ✗   67%  ████████████████░░░░░░
    chengfang        1 所     0 ✓   1 ✗    0%  ░░░░░░░░░░░░░░░░░░░░░░
    ynsmart          1 所     0 ✓   1 ✗    0%  ░░░░░░░░░░░░░░░░░░░░░░
    chaoxing         1 所     0 ✓   1 ✗    0%  ░░░░░░░░░░░░░░░░░░░░░░

  ✓ 所有系统类型运行状况良好 (无低于 50% 的类型且数量≥3)

  总耗时: 8.5s

  报告已保存: reports/json/schedule-api-check-2026-04-03T01-39-54-771Z.json
```

---

### 4.4 数据解析验证

#### 工具信息

- **文件路径**: `scripts/functional/data-parser-validator.js`
- **用途**: 使用示例数据验证各系统类型的数据解析逻辑是否正确
- **特点**: 异常处理测试、质量评分、详细诊断输出

#### 完整参数列表

```bash
node functional/data-parser-validator.js [选项]

选项:
  -s, --school <ids>       指定学校ID (逗号分隔)
  -t, --type <systemType>  指定系统类型过滤
  --sample-dir <path>      示例数据目录 (默认: config/samples/)
  --output <path>          输出文件路径
  --verbose                显示详细调试信息（包含解析出的课程详情）
  -h, --help               显示帮助信息
```

#### 使用示例

**示例 1: 全量验证**
```bash
node functional/data-parser-validator.js
```

**示例 2: 验证指定系统类型**
```bash
# 只验证正方系统的解析逻辑
node functional/data-parser-validator.js --type zfsoft
```

**示例 3: 详细输出模式**
```bash
# --verbose 会显示每门课程的解析详情
node functional/data-parser-validator.js --school cqepc --verbose
```

**示例 4: 使用自定义示例数据**
```bash
node functional/data-parser-validator.js --sample-dir ./my-test-data/
```

#### 评分体系说明

```
┌─────────────────────────────────────────────────────────────┐
│                     评分体系                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  综合评分 = 完整性 × 35% + 有效性 × 35% + 异常处理 × 30%    │
│                                                             │
│  ┌─────────────┬──────────┬──────────┬────────────────────┐ │
│  │   维度       │  权重    │  计算方式  │  说明              │ │
│  ├─────────────┼──────────┼──────────┼────────────────────┤ │
│  │ 字段完整性   │   35%    │ 填充字段数  │ 必填字段是否有值    │ │
│  │             │          │ /总字段数  │                    │ │
│  ├─────────────┼──────────┼──────────┼────────────────────┤ │
│  │ 数据有效性   │   35%    │ 有效字段数  │ 值是否合理（非空、 │ │
│  │             │          │ /总字段数  │ 合理长度、无乱码）  │ │
│  ├─────────────┼──────────┼──────────┼────────────────────┤ │
│  │ 异常处理能力 │   30%    │ 通过测试数  │ 7 种异常数据的处理 │ │
│  │             │          │ /总测试数  │ 能力               │ │
│  └─────────────┴──────────┴──────────┴────────────────────┘ │
│                                                             │
│  等级标准:                                                  │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ 🟢 优秀 (≥80分): 可以直接上架                            ││
│  │ 🟡 良好 (60-79分): 有条件上架                             ││
│  │ 🔴 一般 (40-59分): 需要修复                               ││
│  │ ⛔ 较差 (<40分): 禁止上架                                 ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

#### 异常测试用例

工具会自动对每种解析器进行 7 种异常数据处理测试：

| # | 测试用例 | 测试目的 | 预期行为 |
|---|----------|----------|----------|
| 1 | null 值 | 空引用保护 | 不崩溃，返回空数组 |
| 2 | undefined 值 | 未定义保护 | 不崩溃，返回空数组 |
| 3 | 空字符串 | 空数据处理 | 正确处理，不报错 |
| 4 | HTML 实体编码 | XSS 防护 | 正确转义 `<`, `>`, `&` 等 |
| 5 | 超长文本 (>500字符) | 缓冲区保护 | 合理截断显示 |
| 6 | Unicode/Emoji | 编码兼容 | 正确处理中日韩 Emoji |
| 7 | 嵌套 HTML 标签 | 安全过滤 | 剥离标签，保留文本 |

#### 输出结果解读

```
╔══════════════════════════════════════════════════════════╗
║           数据解析验证器 v1.0.0                      ║
╚══════════════════════════════════════════════════════════╝

  示例数据目录: config/samples/
  报告输出目录: reports/json/

  加载了 26 所学校

  ── 开始验证 ──

  cqepc | 重庆电力高等专科学校 [zfsoft]
    状态: ✅ 优秀
    解析课程数: 15
    字段完整性: 93%
    数据有效性: 91%
    异常处理: 100%
    综合评分: 94分

  sqsfxy | 商丘师范学院 [shuwei]
    状态: ✅ 良好
    解析课程数: 22
    字段完整性: 85%
    数据有效性: 82%
    异常处理: 100%
    综合评分: 88分

  ... (省略中间学校)

  gzzyydx | 广州中医药大学 [chengfang]
    状态: 📭 无样本
    错误: 未找到 chengfang 类型的示例数据

  ── 验证完成 ──

  统计摘要:
    总计学校:     26
    优秀 (≥80分): 18
    良好 (60-79):  0
    一般 (40-59):  0
    较差 (<40):    0
    解析错误:      0
    缺少样本:      8
    平均评分:     86分
    总耗时:       1.25s

  报告已保存: reports/json/parser-validation-2026-04-03T01-37-33.json
```

#### Verbose 模式输出示例

```bash
$ node functional/data-parser-validator.js --school cqepc --verbose

  cqepc | 重庆电力高等专科学校 [zfsoft]
    状态: ✅ 优秀
    解析课程数: 15
    字段完整性: 93%
    数据有效性: 91%
    异常处理: 100%
    综合评分: 94分

    详细解析结果:
      课程 #1:
        courseName    : 高等数学A(上)
        teacher       : 张明华
        classroom     : 教学楼A301
        dayOfWeek     : 1
        startSection  : 1-2
        weekExpression : 1-16周
      课程 #2:
        courseName    : 大学英语(二)
        teacher       : 李婷婷
        classroom     : 外语楼205
        dayOfWeek     : 2
        startSection  : 3-4
        weekExpression : 1-18周
      课程 #3:
        courseName    : 线性代数
        teacher       : 王建国
        classroom     : 理科楼B102
        dayOfWeek     : 3
        startSection  : 5-6
        weekExpression : 1-16周
      ... 还有 12 门课程
```

---

### 4.5 学校配置分析

#### 工具信息

- **文件路径**: `scripts/utils/school-parser.js`
- **用途**: 分析 schools.json 配置与 Extractor 代码的对应关系
- **特点**: 发现待上架学校、差异分析、多格式导出

#### 命令列表

```bash
node utils/school-parser.js <command> [选项]

可用命令:
  summary    显示总体统计信息
  pending    列出待上架学校（有 Extractor 但未配置）
  configured 列出已配置学校
  extractors 列出所有 Extractor
  diff       显示差异分析
  report     生成完整分析报告（JSON 格式）

全局选项:
  --format <fmt>   输出格式: table/json/csv (默认: table)
  --output <path>  输出文件路径
  --filter <key>   过滤字段: province|systemType
  --value <val>    过滤值
```

#### 使用示例

**示例 1: 查看总体统计**
```bash
node utils/school-parser.js summary
```

输出:
```
╔══════════════════════════════════════════════════════════╗
║                    学校分析报告摘要                      ║
╠══════════════════════════════════════════════════════════╣
║  已配置学校数:  26                                      ║
║  Extractor 数: 29                                      ║
║  待上架学校数:   3                                      ║
║  异常情况数:    0                                      ║
╚══════════════════════════════════════════════════════════╝

【按系统类型分布】
  zfsoft          : 6 所
  shuwei          : 6 所
  kingosoft       : 4 所
  custom          : 4 所
  qiangzhi        : 3 所
  chengfang       : 1 所
  ynsmart         : 1 所
  chaoxing        : 1 所

【按省份分布】
  重庆            : 2 所
  山东            : 4 所
  河南            : 3 所
  广东            : 2 所
  四川            : 4 所
  安徽            : 1 所
  陕西            : 1 所
  辽宁            : 1 所
  湖北            : 1 所
  广西            : 1 所
  江苏            : 1 所
  江西            : 1 所
  云南            : 2 所
  贵州            : 2 所
  河北            : 1 所
```

**示例 2: 查看待上架学校**
```bash
node utils/school-parser.js pending
```

输出:
```
📋 待上架学校清单 (共 3 所)

ID          Extractor 文件              原因
---------- ---------------------------- --------
new-school1 NewSchool1Extractor.kt      has_extractor_no_config
new-school2 NewSchool2Extractor.kt      has_extractor_no_config
new-school3 NewSchool3Extractor.kt      has_extractor_no_config
```

**示例 3: 导出为 CSV**
```bash
node utils/school-parser.js pending --format csv --output output/pending.csv
```

**示例 4: 按省份过滤**
```bash
# 查看山东省的所有已配置学校
node utils/school-parser.js configured --filter province --value 山东
```

**示例 5: 生成完整 JSON 报告**
```bash
node utils/school-parser.js report --output output/full-report.json
```

**示例 6: 差异分析**
```bash
node utils/school-parser.js diff
```

输出:
```
🔍 差异分析报告

⚠️  有 Extractor 但未配置 (3 所):
   - new-school1 (NewSchool1Extractor.kt)
   - new-school2 (NewSchool2Extractor.kt)
   - new-school3 (NewSchool3Extractor.kt)

✅ 无异常情况
```

---

### 4.6 批次测试执行

#### 推荐的测试顺序

对于新开发的学校 Extractor，建议按照以下顺序执行测试：

```
阶段 1: 快速预检 (~1分钟)
├── 1.1 school-parser.js pending        # 确认学校状态
├── 1.2 url-checker.js --school <id>     # URL 基础连通性
└── 1.3 schedule-api-checker.js --school <id>  # 接口可用性

阶段 2: 深度检测 (~5分钟)
├── 2.1 data-parser-validator.js --school <id>  # 解析逻辑验证
└── 2.2 login-page-checker.js --school <id> --headed  # 登录页完整检测

阶段 3: 回归测试 (~10分钟)
├── 3.1 url-checker.js                   # 全量 URL 检测
├── 3.2 schedule-api-checker.js          # 全量接口检测
└── 3.3 data-parser-validator.js         # 全量解析验证
```

#### 并发控制建议

| 场景 | 推荐并发数 | 原因 |
|------|:----------:|------|
| URL 检测 | 5-10 | HTTP 请求轻量，可高并发 |
| 接口检测 | 3-5 | 中等负载，适中并发 |
| 登录页检测 | 1-3 | Puppeteer 资源消耗大 |
| 数据解析 | 不适用 | 单线程顺序执行 |

---

### 4.7 报告生成与查看

#### 报告文件位置

所有报告自动保存在 `reports/` 目录下：

```
reports/
├── json/                                        # 机器可读的 JSON 报告
│   ├── url-check-<timestamp>.json               # URL 检测详情
│   ├── login-check-<timestamp>.json             # 登录页检测详情
│   ├── schedule-api-check-<timestamp>.json      # 接口检测详情
│   └── parser-validation-<timestamp>.json       # 解析验证详情
├── screenshots/                                 # 登录页截图
│   └── <schoolId>/
│       └── login-page.png
├── comprehensive-report-<timestamp>.md          # Markdown 综合报告
└── test-report.html                             # HTML 可视化报告
```

#### JSON 报告结构

每个 JSON 报告都遵循统一的结构：

```json
{
  "meta": {
    "generatedAt": "2026-04-03T01:38:01.000Z",
    "tool": "url-checker",
    "version": "1.0.0",
    "options": { }
  },
  "stats": { },
  "results": [ ]
}
```

#### 查看历史报告

```bash
# 列出所有报告文件
dir reports\json\*.json /O-D

# 查看最新的 URL 检测报告
type reports\json\url-check-<最新>.json | more

# 使用 jq 格式化查看（如果安装了 jq）
jq ".stats" reports\json\url-check-<最新>.json
```

#### 报告保留策略

- 建议**保留最近 30 天**的报告用于趋势分析
- 定期清理过期报告释放磁盘空间：
```bash
# 清理 30 天前的报告（PowerShell）
Get-ChildItem reports\json\*.json | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-30) } | Remove-Force
```

---

## 5. 工作流程示例

### 场景：新学校「XX 大学」上架前完整测试流程

#### 背景

开发团队完成了 XX 大学（ID: `xxdx`，系统类型: `zfsoft`）的 Extractor 开发，需要进行上架前质量检测。

#### 完整操作步骤

```
╔══════════════════════════════════════════════════════════════╗
║          XX 大学 (xxdx) 上架前测试完整流程                    ║
╚══════════════════════════════════════════════════════════════╝
```

**Step 1: 环境确认**

```bash
# 确认 Node.js 环境
node --version   # 应该 >= v18.0
npm --version

# 确认依赖已安装
cd e:\KEchengbiao\scripts
npm list puppeteer axios chalk commander
```

**Step 2: 确认学校配置已就绪**

```bash
# 查看 XX 大学是否已在 schools.json 中配置
node utils/school-parser.js configured --filter id --value xxdx

# 或者查看完整的学校列表
node utils/school-parser.js configured | findstr xxdx
```

预期输出应显示 XX 大学的配置信息。

**Step 3: URL 可访问性预检**

```bash
# 仅检测 XX 大学
node testing/accessibility/url-checker.js --school xxdx
```

预期输出：
```
  [1/2] XX大学 [登录页] ✓ 可访问  HTTP 200  1234ms  https://jw.xxu.edu.cn/...
     SSL: 有效 | 剩余365天
  [2/2] XX大学 [课表页] ✓ 可访问  HTTP 200  892ms   https://jw.xxu.edu.cn/...

  统计信息:
    总数:    2
    通过:    2
    失败:    0
    通过率:  100.0%
```

✅ **通过条件**: 两个 URL 都返回 200/301/302

**Step 4: 课表接口检测**

```bash
# 检测 XX 大学的课表接口
node functional/schedule-api-checker.js --school xxdx
```

预期输出：
```
  [1/1] xxdx [zfsoft] ✓ 可访问 HTTP 200  567ms JSON ✓ valid
     └─ 接口正常返回 JSON 数据，发现 6 个关键字段: kbList, kcmc, xm, cdmc, xqj, jcs
     └─ 关键字段: kbList, kcmc, xm, cdmc, xqj, jcs, zcd
```

✅ **通过条件**: accessible=true 且 dataValid=true

**Step 5: 数据解析验证**

```bash
# 验证解析逻辑
node functional/data-parser-validator.js --school xxdx --verbose
```

预期输出：
```
  xxdx | XX大学 [zfsoft]
    状态: ✅ 优秀
    解析课程数: 20
    字段完整性: 95%
    数据有效性: 92%
    异常处理: 100%
    综合评分: 95分
```

✅ **通过条件**: 综合评分 ≥ 80 分

**Step 6: 登录页面检测（可视化确认）**

```bash
# 有头模式，可以看到浏览器窗口
node functional/login-page-checker.js --school xxdx --headed --screenshot
```

检查要点：
- [ ] 页面正常加载，无白屏
- [ ] 用户名、密码输入框可见
- [ ] 验证码正常显示（如有）
- [ ] 登录按钮可点击
- [ ] 截图清晰完整

**Step 7: 填写检查清单**

打开 [checklist-template.md](./checklist-template.md)，逐项填写检测结果。

**Step 8: 最终审核**

根据检测结果做出判定：

| 判定 | 条件 |
|------|------|
| ✅ 可以上架 | 所有必检项通过，评分 ≥ 80 |
| ⚠️ 有条件上架 | 必检项通过，选检项存在问题 |
| ❌ 禁止上架 | 任一必检项未通过 |

---

## 6. 常见问题 FAQ

### Q1: Puppeteer 安装失败怎么办？

**现象**:
```
ERROR: Failed to download Chromium rXXXXX!
Error: connect ETIMEDOUT XXX.XXX.XXX.XXX:443
```

**解决方案**:

```bash
# 方案 1: 使用国内镜像源
set PUPPETEER_DOWNLOAD_BASE_URL=https://npmmirror.com/mirrors/chrome-for-testing
npm install puppeteer

# 方案 2: 跳过下载，使用本地 Chrome
set PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
npm install puppeteer
# 然后修改代码指定 Chrome 路径:
# browser = await puppeteer.launch({ executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' })

# 方案 3: 使用公司/实验室内部镜像
npm config set puppeteer_download_base_url http://internal-mirror.example.com/chromium
npm install puppeteer
```

---

### Q2: 检测超时如何处理？

**现象**:
```
⚠️ 页面加载异常: Navigation timeout of 30000ms exceeded
```

**解决方案**:

```bash
# 增加 timeout 参数
node functional/login-page-checker.js --school <id> --timeout 60000

# URL 检测增加超时
node testing/accessibility/url-checker.js --school <id> -t 30000

# 接口检测增加超时
node functional/schedule-api-checker.js --school <id> --timeout 30000
```

**常见原因及处理**:

| 原因 | 解决方案 |
|------|----------|
| 目标服务器响应慢 | 增大 `--timeout` 值 |
| 网络不稳定 | 减小 `-c` 并发数，增大 `-r` 重试次数 |
| 服务器在国外 | 配置代理 `--proxy` |
| 页面有大量资源加载 | 使用 `--no-screenshot` 减少开销 |

---

### Q3: 如何只检测特定学校？

**方法 1: 使用 `--school` 参数（推荐）**

```bash
# 单个学校
node testing/accessibility/url-checker.js --school cqepc

# 多个学校（逗号分隔，无空格）
node testing/accessibility/url-checker.js --school cqepc,sqsfxy,njzyydx
```

**方法 2: 使用 `--type` 按系统类型筛选**

```bash
# 只检测正方系统的学校
node functional/schedule-api-checker.js --type zfsoft

# 只检测树维系统的学校
node functional/data-parser-validator.js --type shuwei
```

**方法 3: 使用 school-parser 过滤后获取 ID 列表**

```bash
# 先查看某省份的所有学校
node utils/school-parser.js configured --filter province --value 山东

# 然后用筛选出的 ID 进行检测
node testing/accessibility/url-checker.js --school sdnzxy,kmykdx,hnnydx,ytnsxy
```

---

### Q4: 报告在哪里查看？

**报告位置**: `e:\KEchengbiao\reports\`

```
reports/
├── json/                    ← JSON 详细数据报告
│   ├── url-check-*.json
│   ├── login-check-*.json
│   ├── schedule-api-check-*.json
│   └── parser-validation-*.json
├── screenshots/             ← 登录页截图
│   └── <schoolId>/
├── comprehensive-report-*.md ← Markdown 综合报告（人类友好）
└── test-report.html         ← HTML 可视化报告
```

**快速查看最新报告**:

```bash
# PowerShell: 打开最新的综合报告
start (Get-ChildItem reports\comprehensive-report-*.xml | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName

# 或直接打开报告目录
explorer reports\
```

---

### Q5: 如何从断点继续测试？

**原理**: URL Checker 会自动跳过已检测的 URL

```bash
# 直接再次运行，会自动跳过已完成的检测
node testing/accessibility/url-checker.js

# 输出中会显示:
# 断点续测: 跳过已检测的 42 个 URL，剩余 8 个
```

**强制重新检测**:

```bash
# 删除旧的 URL 检测报告
Remove-Item reports\json\url-check-*.json -ErrorAction SilentlyContinue

# 再次运行即为全新检测
node testing/accessibility/url-checker.js
```

---

### Q6: 代理配置不生效？

**排查步骤**:

```bash
# 1. 确认代理服务正在运行
curl -x http://127.0.0.1:7890 https://www.baidu.com -I

# 2. 检查 URL Checker 的代理参数格式
# 正确格式:
node testing/accessibility/url-checker.js --proxy http://127.0.0.1:7890

# 3. 设置环境变量方式
set HTTP_PROXY=http://127.0.0.1:7890
set HTTPS_PROXY=http://127.0.0.1:7890
node testing/accessibility/url-checker.js

# 4. 如果是 SOCKS5 代理
node testing/accessibility/url-checker.js --proxy socks5://127.0.0.1:7890
```

**注意**: 目前仅 `url-checker.js` 支持 `--proxy` 参数。其他工具需通过环境变量配置。

---

### Q7: 并发太高导致被封锁？

**现象**:
```
HTTP 429 Too Many Requests
HTTP 403 Forbidden
连接被重置
```

**解决方案**:

```bash
# 降低并发数
node testing/accessibility/url-checker.js -c 2

# 增加重试间隔（工具内置指数退避，可通过降低并发间接实现）

# 对于登录页检测，降低 Puppeteer 并发
node functional/login-page-checker.js --school <id1>,<id2> --concurrency 1
```

**推荐的并发配置**:

| 场景 | 并发数 | 说明 |
|------|:------:|------|
| 内网/测试环境 | 5-10 | 可适当提高 |
| 公网/生产环境 | 2-3 | 保守策略 |
| 目标服务器较弱 | 1 | 串行执行 |
| 首次检测未知目标 | 3 | 平衡效率与风险 |

---

### Q8: 如何添加新的学校类型？

当遇到新的教务系统时，需要扩展支持：

**Step 1: 在 schools.json 中添加配置**

```json
{
  "id": "new-school-id",
  "name": "新学校名称",
  "systemType": "newsystem",  // 新的系统类型标识
  "loginUrl": "https://...",
  "scheduleUrl": "https://...",
  ...
}
```

**Step 2: 在 schedule-api-checker.js 中添加检测逻辑**

编辑 [schedule-api-checker.js](../scripts/functional/schedule-api-checker.js)，参照现有实现：

```javascript
async function checkNewSystemApi(school) {
  // 实现 newsystem 类型的专用检测逻辑
  const result = { /* 标准结果结构 */ };
  
  // ... 检测代码 ...
  
  return result;
}

// 注册到 CHECKERS 映射表中
const CHECKERS = {
  zfsoft: checkZfsoftApi,
  qiangzhi: checkQiangzhiApi,
  // ...
  newsystem: checkNewSystemApi,  // 添加这一行
};
```

**Step 3: 在 data-parser-validator.js 中添加解析器**

```javascript
function parseNewSystemData(rawData, school) {
  // 实现 newsystem 类型的数据解析逻辑
}

// 在 parseSampleData 函数中添加 case
function parseSampleData(sampleData, systemType, school) {
  switch (systemType) {
    // ...
    case 'newsystem':
      return typeof sampleData === 'object' ? parseNewSystemData(sampleData, school) : [];
  }
}
```

**Step 4: 添加示例数据**

在 `scripts/config/samples/` 目录下创建 `newsystem-sample.json` 或 `newsystem-sample.html`。

---

### Q9: 截图太占磁盘空间怎么办？

**现状**: 每所学校每次检测会产生约 1-5MB 的 PNG 截图

**解决方案**:

```bash
# 方法 1: 检测时禁用截图
node functional/login-page-checker.js --school <id> --no-screenshot

# 方法 2: 定期清理旧截图
# 保留最近 7 天的截图
Get-ChildItem reports\screenshots\ -Recurse -Filter *.png |
  Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-7) } |
  Remove-Force -Verbose

# 方法 3: 压缩已有截图（使用 ImageMagick 或 Python）
# PNG -> JPG (质量 85%)，可减少 70-80% 体积
magick mogrify -quality 85 -format jpg reports\screenshots\*\*\*.png
```

**磁盘占用估算**:

| 学校数量 | 检测频率 | 月增长 |
|----------|----------|--------|
| 26 所 | 每天 1 次 | ~2 GB |
| 26 所 | 每周 1 次 | ~300 MB |
| 50 所 | 每周 1 次 | ~600 MB |

---

### Q10: 问题追踪数据在哪里？

**JSON 报告中的问题数据**:

每个工具生成的 JSON 报告都包含详细的错误信息：

```bash
# URL 检测中的失败项
jq '.results[] | select(.status != "ok")' reports\json\url-check-*.json

# 登录页检测中的控制台错误
jq '.schools[] | select(.result.pageLoaded == false)' reports\json\login-check-*.json

# 数据解析中的低分项
jq '.results[] | select(.scores.overallScore < 80)' reports\json\parser-validation-*.json
```

**综合报告中的问题汇总**:

Markdown 综合报告 (`comprehensive-report-*.md`) 包含：
- 问题列表表格（含严重等级）
- Top 10 问题最多的学校
- 按系统类型的统计分析

---

### Q11: 如何导出问题列表到 Excel？

**方法 1: 使用 CSV 导出功能**

```bash
# school-parser 支持 CSV 导出
node utils/school-parser.js pending --format csv --output issues.csv

# 然后用 Excel 打开 issues.csv
```

**方法 2: 使用脚本转换 JSON 到 CSV**

创建 `json-to-csv.js`:

```javascript
const fs = require('fs');
const report = JSON.parse(fs.readFileSync('reports/json/url-check-latest.json', 'utf-8'));

const failed = report.results.filter(r => r.status !== 'ok');
let csv = 'SchoolId,SchoolName,UrlType,Status,StatusCode,Error\n';

for (const item of failed) {
  csv += `${item.schoolId},${item.schoolName},${item.urlType},${item.status},${item.statusCode || ''},"${item.error || ''}"\n`;
}

fs.writeFileSync('failed-urls.csv', csv);
console.log(`Exported ${failed.length} items to failed-urls.csv`);
```

运行:
```bash
node json-to-csv.js
```

**方法 3: 使用在线工具**

将 JSON 报告内容粘贴到 [ConvertCSV](https://www.convertcsv.com/json-to-csv.htm) 等在线转换工具。

---

### Q12: 批次测试失败了怎么处理？

**分步排查**:

```bash
# Step 1: 查看退出码
echo %ERRORLEVEL%
# 0 = 成功, 1 = 有失败项, 其他 = 致命错误

# Step 2: 查看详细日志
# 开启 DEBUG 模式获取更多输出
set DEBUG=1
node functional/login-page-checker.js --school <id>

# Step 3: 单独复现失败的学校
# 从报告中找出失败的 schoolId
node testing/accessibility/url-checker.js --school <failed-id>

# Step 4: 有头模式调试
node functional/login-page-checker.js --school <failed-id> --headed
```

**常见失败原因**:

| 退出码 | 含义 | 处理 |
|:------:|------|------|
| 0 | 全部通过 | ✅ 正常 |
| 1 | 存在失败项 | 查看报告定位问题 |
| 2 | 参数错误 | 检查命令语法 |
| 127 | 找不到 Node.js | 检查 PATH 环境变量 |

---

### Q13: 如何自定义报告模板？

当前版本使用内置模板生成报告。如需自定义：

**方法 1: 后处理 JSON 报告**

编写脚本读取 JSON 报告并生成自定义格式：

```javascript
// generate-custom-report.js
const fs = require('fs');
const urlReport = JSON.parse(fs.readFileSync('reports/json/url-check-latest.json', 'utf-8'));
const apiReport = JSON.parse(fs.readFileSync('reports/json/schedule-api-check-latest.json', 'utf-8'));

// 自定义合并逻辑和输出格式
const customReport = `# 自定义测试报告\n\n...`;

fs.writeFileSync('custom-report.md', customReport);
```

**方法 2: 修改工具源码**

编辑对应工具的 `saveJSONReport` 或类似函数，修改输出格式。

---

### Q14: Node.js 版本不兼容？

**检查版本兼容性**:

```bash
# 当前要求的最低版本
node --version  # 需要 >= 18.0.0

# 常见不兼容情况:
# - v14.x: 不支持 optional chaining (?.) 和 nullish coalescing (??)
# - v16.x: 可能缺少部分 API
# - v18+: 完全支持
```

**升级 Node.js**:

1. 访问 https://nodejs.org/
2. 下载 LTS 版本（推荐 v20.x）
3. 运行安装程序
4. 验证: `node --version`

**使用 nvm 管理 Node 版本**（推荐开发者）:

```bash
# 安装 nvm-windows
# 下载地址: https://github.com/coreybutler/nvm-windows/releases

# 安装 Node 20
nvm install 20
nvm use 20

# 验证
node --version
```

---

### Q15: Windows 权限问题？

**常见权限错误**:

```
EPERM: operation not permitted, mkdir 'reports/screenshots'
EBUSY: resource locked or in-use
EACCES: permission denied
```

**解决方案**:

```bash
# 1. 以管理员身份运行 PowerShell/终端
# 右键点击 PowerShell → "以管理员身份运行"

# 2. 检查文件夹权限
icacls reports /grant %USERNAME%:F /T

# 3. 关闭占用文件的进程
# 如果 Chromium 进程未关闭:
taskkill /F /IM chrome.exe
taskkill /F /IM node.exe

# 4. 检查杀毒软件
# 某些杀毒软件可能拦截 Puppeteer 的 Chromium 下载或操作
# 将项目目录添加到白名单/排除列表

# 5. 检查磁盘空间
# Puppeteer 需要约 500MB 空间下载 Chromium
wmic logicaldisk get size,freespace,caption
```

---

### Q16: 如何验证 Extractor 代码与配置一致？

```bash
# 使用 diff 命令查看差异
node utils/school-parser.js diff

# 预期输出示例:
# ✅ 无待上架学校
# ✅ 无异常情况

# 如果有差异:
# ⚠️  有 Extractor 但未配置 (N 所):   → 需要补充 schools.json 配置
# ❌ 已配置但无 Extractor (M 所):       → 需要开发或移除配置
```

---

### Q17: 检测结果如何与团队共享？

**方法 1: 共享报告文件**

```bash
# 将 reports 目录打包
Compress-Archive -Path reports\* -DestinationPath test-reports.zip -Force

# 通过企业微信/钉钉/邮件发送
```

**方法 2: 使用 Git 提交报告**

```bash
git add reports/comprehensive-report-*.md
git commit -m "test: 添加 XX大学上架前检测报告"
git push
```

**方法 3: 部署报告服务器**

将 `test-report.html` 部署到内网 Web 服务器，团队成员通过浏览器访问。

---

### Q18: 如何设置定时自动检测？

**方法 1: Windows 任务计划程序**

1. 打开「任务计划程序」（Win+R → `taskschd.msc`）
2. 创建基本任务
3. 触发器: 每天/每周
4. 操作: 启动程序 → `node` → 参数: `e:\KEchengbiao\scripts\testing\accessibility\url-checker.js`

**方法 2: 使用 node-cron（开发环境）**

```javascript
// scheduler.js
const cron = require('node-cron');
const { exec } = require('child_process');

cron.schedule('0 2 * * *', () => {
  console.log('开始执行每日定时检测...');
  exec('node testing/accessibility/url-checker.js', (err, stdout, stderr) => {
    if (err) console.error('检测失败:', err);
    else console.log(stdout);
  });
});

console.log('定时任务调度器已启动');
```

---

### Q19: 如何对比两次测试的结果变化？

```bash
# 获取两次报告的时间戳
$report1 = Get-ChildItem reports\json\url-check-*.json | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$report2 = Get-ChildItem reports\json\url-check-*.json | Sort-Object LastWriteTime -Descending | Select-Object -Skip 1 -First 1

# 对比统计差异
Write-Host "=== 报告对比 ==="
Write-Host "最新: $($report1.Name) - $($report1.LastWriteTime)"
Write-Host "上次: $($report2.Name) - $($report2.LastWriteTime)"
Write-Host ""

$r1 = Get-Content $report1.FullName | ConvertFrom-Json
$r2 = Get-Content $report2.FullName | ConvertFrom-Json

Write-Host "通过率: $($r2.stats.passRate) → $($r1.stats.passRate)"
Write-Host "平均响应: $($r2.stats.avgResponseTime)ms → $($r1.stats.avgResponseTime)ms"
```

---

### Q20: 性能优化建议？

**加速检测的方法**:

| 优化项 | 默认值 | 优化值 | 效果 |
|--------|--------|--------|------|
| URL 并发数 | 5 | 10 | 检测速度提升约 2 倍 |
| 超时时间 | 10s | 5s | 超时失败更快（网络好时） |
| 重试次数 | 3 | 1 | 减少等待时间 |
| 截图 | 启用 | 禁用 | 减少 I/O 时间 |
| Puppeteer 并发 | 3 | 1 | 降低内存占用 |

**权衡提示**: 提高并发可能触发目标服务器限流，请根据实际情况调整。

---

## 7. 最佳实践

### 7.1 效率优化技巧

```
┌─────────────────────────────────────────────────────────────┐
│                     效率优化金字塔                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                    ┌───────────────┐                         │
│                   │   并行执行     │  ← 最高效              │
│                  ┌─┴───────────────┴─┐                       │
│                 │   增量检测        │  ← 高效               │
│                ┌─┴─────────────────┴─┐                      │
│               │   选择性检测         │  ← 中等              │
│              ┌─┴───────────────────┴─┐                     │
│             │   串行全量检测          │  ← 基准              │
│            └─────────────────────────┘                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**技巧 1: 分级检测策略**

```bash
# 日常快速巡检（每天，~30秒）
node testing/accessibility/url-checker.js -c 10 -t 5000 -r 1

# 周度全面检测（每周，~5分钟）
node testing/accessibility/url-checker.js
node functional/schedule-api-checker.js
node functional/data-parser-validator.js

# 月度深度检测（每月，~30分钟）
node functional/login-page-checker.js --concurrency 2
# + 人工审核截图和报告
```

**技巧 2: 利用缓存和断点续测**

- URL Checker 自动跳过已检测项
- 登录页截图可复用，避免重复检测
- JSON 报告支持增量对比

**技巧 3: 批量处理脚本**

参考第 3.3 节的一键检测脚本，将常用命令组合为批处理文件。

### 7.2 故障排查方法论

```
问题发生
    │
    ▼
┌─────────────────┐
│ 1. 复现问题      │ ← 单独运行失败的命令，确认稳定复现
└────────┬────────┘
         ▼
┌─────────────────┐
│ 2. 收集信息      │ ← 查看完整错误输出、JSON 报告、截图
└────────┬────────┘
         ▼
┌─────────────────┐
│ 3. 定位原因      │ ← 分析是网络/配置/代码/目标服务器问题
└────────┬────────┘
         ▼
┌─────────────────┐
│ 4. 应用修复      │ ← 参考本文档 FAQ 或搜索解决方案
└────────┬────────┘
         ▼
┌─────────────────┐
│ 5. 验证修复      │ ← 重新运行检测确认问题解决
└─────────────────┘
```

**常用排查命令速查**:

```bash
# 网络连通性测试
ping jw.target.edu.cn
nslookup jw.target.edu.cn
curl -I https://jw.target.edu.cn

# Puppeteer 调试
node functional/login-page-checker.js --school <id> --headed --timeout 60000

# 详细日志
set DEBUG=1
node <command>

# 检查依赖完整性
npm ls puppeteer axios chalk commander
```

### 7.3 团队协作建议

**角色分工**:

| 角色 | 职责 | 使用的主要工具 |
|------|------|----------------|
| **QA 工程师** | 执行日常检测、填写检查清单 | 全部工具 |
| **开发人员** | 修复问题、新增学校支持 | school-parser, data-parser-validator |
| **技术负责人** | 审核报告、批准上架 | comprehensive-report |
| **运维人员** | 环境维护、定时任务 | 任务计划程序 |

**协作流程**:

```
开发完成 Extractor
       │
       ▼
  QA 执行检测 → 填写 checklist-template.md
       │
       ▼
  生成 comprehensive-report
       │
       ▼
  技术负责人审核 → 批准/打回
       │
       ▼
  ✅ 上架 → 更新 schools.json → 发布版本
```

**沟通规范**:

- 问题反馈格式: `[<schoolId>] <问题描述> - <严重等级> - <复现步骤>`
- 报告命名: `comprehensive-report-YYYY-MM-DD.md`
- 会议纪要: 附带最新检测报告链接

### 7.4 安全注意事项

⚠️ **重要安全提醒**:

1. **敏感信息保护**
   - 测试账号密码不要提交到代码仓库
   - 检查清单中的账号信息需脱敏
   - JSON 报告中避免包含真实凭证

2. **网络安全**
   - 不要在生产高峰期执行高并发检测
   - 遵守目标网站的 robots.txt 规则
   - 代理配置不要泄露内网地址

3. **数据安全**
   - 截图可能包含学生个人信息，妥善保管
   - 定期清理临时文件和过期报告
   - 报告分享前审查敏感内容

---

## 8. 附录

### A. 命令速查表

| 操作 | 命令 | 耗时 |
|------|------|------|
| **URL 全量检测** | `node testing/accessibility/url-checker.js` | ~30s |
| **URL 指定学校** | `node testing/accessibility/url-checker.js --school <id>` | ~5s |
| **URL 高并发** | `node testing/accessibility/url-checker.js -c 10 -t 5000` | ~15s |
| **登录页检测（单个）** | `node functional/login-page-checker.js --school <id> --headed` | ~30s |
| **登录页批量检测** | `node functional/login-page-checker.js --school <id1,id2> -c 2` | ~2min |
| **接口全量检测** | `node functional/schedule-api-checker.js` | ~20s |
| **接口按类型** | `node functional/schedule-api-checker.js --type zfsoft` | ~5s |
| **接口深度检测** | `node functional/schedule-api-checker.js --deep` | ~30s |
| **解析验证全量** | `node functional/data-parser-validator.js` | ~10s |
| **解析验证详细** | `node functional/data-parser-validator.js --verbose` | ~10s |
| **学校概览** | `node utils/school-parser.js summary` | ~2s |
| **待上架列表** | `node utils/school-parser.js pending` | ~2s |
| **差异分析** | `node utils/school-parser.js diff` | ~2s |
| **导出 CSV** | `node utils/school-parser.js pending --format csv` | ~2s |

### B. 退出码说明

| 退出码 | 含义 | 处理建议 |
|:------:|------|----------|
| **0** | 成功（全部通过） | ✅ 正常结束 |
| **1** | 存在失败项 | 查看报告，定位问题学校 |
| **2** | 参数错误 | 检查命令语法和参数值 |
| **126** | 权限不足 | 以管理员身份运行 |
| **127** | 命令未找到 | 检查 Node.js 安装和 PATH |
| **130** | 用户中断 (Ctrl+C) | 正常，可重新运行 |
| **9009** | Windows 系统错误 | 检查文件路径和编码 |

### C. 目录结构速查

```
KEchengbiao/
├── scripts/                      # 测试工具根目录
│   ├── package.json             # 依赖配置
│   ├── functional/              # 功能测试
│   │   ├── login-page-checker.js
│   │   ├── schedule-api-checker.js
│   │   └── data-parser-validator.js
│   ├── testing/accessibility/   # 可访问性测试
│   │   ├── url-checker.js
│   │   └── package.json
│   ├── utils/
│   │   └── school-parser.js
│   └── config/samples/          # 示例数据
├── app/src/main/assets/
│   └── schools.json             # 学校配置 (26 所)
├── reports/                     # 输出目录
│   ├── json/                    # JSON 报告
│   ├── screenshots/             # 截图
│   └── *.md                     # 综合报告
└── output/                      # 分析输出
```

### D. 系统类型与特征对照表

| 系统类型 | URL 特征 | 数据格式 | 鉴权方式 | 特殊处理 |
|:--------:|----------|:--------:|----------|----------|
| **zfsoft** | `/jwglxt/` | JSON | CSRF Token | POST + Session |
| **qiangzhi** | `/jsxsd/` | HTML Cookie | iframe 解析 | |
| **shuwei** | `/eams/` | HTML | Cookie | JS 提取 |
| **kingosoft** | `/jwglxt/\|/sdnzjw/` | HTML | Cookie | frame 结构 |
| **custom** | `/for-std/\|/course-table/` | JS | Cookie | 各校独立 |
| **chengfang** | `/api/` | JS | Token | RESTful API |
| **ynsmart** | `/aixiaoyuan/` | JS | Token | 逐周获取 |
| **chaoxing** | `/fanya/` | JS | SSO | 超星集成 |

### E. 常见 HTTP 状态码速查

| 状态码 | 含义 | 测试中的意义 |
|:------:|------|--------------|
| **200** | OK | ✅ 正常 |
| **301** | 永久重定向 | ✅ 正常（跟随重定向） |
| **302** | 临时重定向 | ✅ 正常（通常跳转到登录页） |
| **304** | 未修改 | ✅ 正常（缓存命中） |
| **400** | 错误请求 | ❌ 参数错误 |
| **401** | 未授权 | ⚠️ 需要登录（对某些接口属正常） |
| **403** | 禁止访问 | ❌ IP/权限限制 |
| **404** | 未找到 | ❌ URL 错误或页面不存在 |
| **429** | 请求过多 | ⚠️ 被限流，降低并发 |
| **500** | 服务器错误 | ❌ 目标服务异常 |
| **502** | 网关错误 | ❌ 代理/网关问题 |
| **503** | 服务不可用 | ❌ 服务器过载或维护 |

### F. 版本历史

| 版本 | 日期 | 作者 | 变更内容 |
|:----:|------|------|----------|
| **v1.0.0** | 2026-04-03 | QA Team | 初始版本，包含完整测试工具文档 |

---

**文档结束**

> 📞 **技术支持**: 如有问题，请联系 QA 团队或查阅项目 Issue Tracker
> 🔄 **更新机制**: 当测试工具有重大变更时，本文档应及时同步更新
