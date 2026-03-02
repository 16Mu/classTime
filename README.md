# 📚 时课 - 智能校园课表管理应用

<div align="center">

![Android](https://img.shields.io/badge/Android-8.0%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.10.01-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

一个功能完整、界面美观的校园课表管理应用，支持智能导入、自动更新、课程提醒等丰富功能。

[下载 APK](#) | [使用文档](docs/USER_GUIDE.md) | [界面预览](#界面预览) | [快速开始](docs/USER_GUIDE.md#快速开始)

</div>

## 核心特性

### 独家功能亮点
- **智能课程冲突检测** - 自动识别时间冲突，支持智能调课避免重叠
- **多教室智能管理** - 同一课程不同时间可设置不同教室，解决教室变动问题
- **快速手动导入** - 独创快速导入功能，大幅降低首次导入时间，提升用户体验
- **桌面小组件** - 今日课程和下节课倒计时小组件，实时查看课程信息

### 智能课表导入
- **27 所学校适配** - 支持全国主流高校教务系统
- **自动登录** - WebView 登录 + Cookie 加密存储
- **多格式解析** - JSON/HTML 双格式课表数据解析
- **自动更新** - 间隔更新 + 定时更新，课表变更自动同步

### 课程管理
- **周视图** - 直观的课程卡片展示，支持周次切换
- **课程编辑** - 完整的课程增删改查功能
- **个性化** - 10种预设颜色，自定义课程外观
- **多学期** - 支持多学期课表管理

### 智能提醒
- **精准提醒** - AlarmManager 精确调度，课前提醒不错过
- **下节课倒计时** - 桌面小组件实时显示下节课信息
- **状态感知** - 自动识别上课中、即将上课等状态

### 桌面小组件
- **今日课程** - 显示当天完整课程安排
- **下节课倒计时** - 实时倒计时 + 课程状态
- **深色模式** - 完美适配系统主题
- **自适应尺寸** - 支持多种小组件尺寸

### 高级功能
- **多格式导出** - ICS日历、JSON、CSV、HTML、TXT
- **临时调课** - 灵活的课程时间调整
- **考试管理** - 考试安排提醒与管理
- **紧凑模式** - 空间优化的界面布局

## 界面预览

### 主要界面
<div align="center">
  <img src="docs/images/main_screen.png" alt="主界面" width="200">
  <img src="docs/images/course_detail.png" alt="课程详情" width="200">
  <img src="docs/images/settings_screen.png" alt="设置界面" width="200">
</div>

### 核心功能
<div align="center">
  <img src="docs/images/course_conflict.png" alt="课程冲突检测" width="200">
  <img src="docs/images/multi_classroom.png" alt="多教室管理" width="200">
  <img src="docs/images/widget_preview.png" alt="桌面小组件" width="200">
</div>

## 🏗️ 技术架构

### 核心技术栈
```
Android Platform
├── Kotlin 2.1.0 - 现代化开发语言
├── Jetpack Compose - 声明式UI框架
├── Material Design 3 - 现代化设计语言
└── minSdk 26, targetSdk 34

Architecture
├── MVVM + Repository Pattern - 清晰的架构分层
├── Hilt - 依赖注入框架
├── Room - 本地数据库
├── DataStore - 配置数据存储
└── Coroutines + Flow - 异步编程

Network & Parsing
├── OkHttp - HTTP客户端
├── Retrofit - REST API框架
├── Jsoup - HTML解析器
└── WebView - 混合登录方案

Background Processing
├── WorkManager - 后台任务调度
├── AlarmManager - 精确时间调度
├── Jetpack Glance - 桌面小组件
└── Foreground Service - 前台服务
```

### 项目结构
```
app/src/main/java/com/wind/ggbond/classtime/
├── data/                    # 数据层
│   ├── local/                  # 本地数据
│   │   ├── entity/            # 数据库实体 (10个)
│   │   ├── dao/               # 数据访问对象 (10个)
│   │   └── database/          # 数据库配置
│   ├── repository/            # 数据仓库 (11个)
│   └── datastore/             # DataStore管理
├── ui/                      # UI层
│   ├── screen/                # 页面 (15个主要页面)
│   │   ├── main/              # 主界面
│   │   ├── course/            # 课程管理
│   │   ├── settings/          # 设置页面
│   │   └── scheduleimport/    # 课表导入
│   ├── components/            # 可复用组件
│   ├── navigation/            # 导航系统
│   └── theme/                 # 主题样式
├── di/                      # 依赖注入
├── service/                 # 后台服务 (9个)
├── widget/                  # 桌面小组件
├── receiver/                # 广播接收器
├── worker/                  # WorkManager任务
└── util/                    # 工具类
```

## 🎨 界面预览

### 主界面
- 周视图课程展示
- 课程卡片设计
- 响应式布局

### 课表导入
- WebView登录
- 导入预览
- 学校配置

### 设置页面
- 提醒设置
- 自动更新配置
- 数据导出

### 桌面小组件
- 今日课程Widget
- 下节课倒计时Widget

## 快速开始

### 环境要求
- **Android Studio** Arctic Fox 或更高版本
- **JDK** 17 或更高版本
- **Android SDK** API 26+ (Android 8.0+)

### 安装步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/yourusername/course-schedule.git
   cd course-schedule
   ```

2. **使用Android Studio打开**
   - 打开Android Studio
   - 选择 "Open an existing project"
   - 选择项目根目录

3. **同步项目**
   ```bash
   ./gradlew build
   ```

4. **运行应用**
   ```bash
   ./gradlew installDebug
   ```

### 配置说明

#### 签名配置 (Release版本)
```kotlin
// 在 local.properties 中添加
RELEASE_STORE_FILE=your.keystore
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=your_alias
RELEASE_KEY_PASSWORD=your_key_password
```

#### 学校配置
在 `app/src/main/assets/schools.json` 中配置学校信息：
```json
{
  "schools": [
    {
      "name": "示例大学",
      "loginUrl": "https://教务系统地址/login",
      "scheduleUrl": "https://教务系统地址/schedule",
      "extractor": "UniversalSmartExtractor"
    }
  ]
}
```

## 使用指南

### 首次使用
1. **启动应用** - 进入引导流程
2. **选择学校** - 从27所支持学校中选择
3. **登录导入** - 通过WebView登录教务系统
4. **课表预览** - 确认导入的课程信息
5. **完成设置** - 配置提醒和自动更新

### 日常使用
- **查看课表** - 主界面查看当前周课程
- **课程管理** - 点击课程卡片编辑详情
- **周次切换** - 左右滑动切换不同周次
- **设置提醒** - 在设置中配置课前提醒时间

### 高级功能
- **自动更新** - 启用后课表会自动同步更新
- **数据导出** - 支持导出为多种格式
- **桌面小组件** - 添加小组件到桌面快速查看

## 开发指南

### 添加新学校支持
1. 在 `schools.json` 中添加学校配置
2. 创建专用的提取器 (可选)
3. 在 `SchoolExtractorFactory` 中注册
4. 测试导入流程

### 自定义主题
在 `ui/theme/Color.kt` 中修改颜色配置：
```kotlin
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    // ... 其他颜色
)
```

### 添加新功能
1. 在 `data/local/entity/` 中定义数据模型
2. 创建对应的 DAO 和 Repository
3. 在 `ui/screen/` 中实现界面
4. 在 `di/` 中配置依赖注入

## 项目统计

### 代码规模
- **总代码行数**: ~73,500  行
- **Kotlin文件**: 262 个
- **数据库实体**: 9 个
- **后台服务**: 8 个
- **主要页面**: 28 个

### 功能完成度
- 独家功能 (100%)
  - 智能课程冲突检测
  - 多教室智能管理
  - 快速手动导入
  - 桌面小组件
- 基础架构 (100%)
- 课表导入 (100%)
- 课程管理 (100%)
- 智能提醒 (100%)
- 数据导出 (100%)
- 自动更新 (100%)

## 贡献指南

我们欢迎所有形式的贡献！

### 贡献方式
- **报告Bug** - 在Issues中提交详细的问题描述
- **功能建议** - 提出新功能的想法和需求
- **代码贡献** - Fork项目并提交Pull Request
- **学校适配** - 帮助适配更多学校的教务系统

### 开发流程
1. Fork 项目到你的GitHub账户
2. 创建功能分支: `git checkout -b feature/amazing-feature`
3. 提交更改: `git commit -m 'Add amazing feature'`
4. 推送分支: `git push origin feature/amazing-feature`
5. 提交Pull Request

### 代码规范
- 遵循 [Kotlin编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 使用有意义的变量和函数命名
- 添加必要的注释和文档
- 确保代码通过现有测试

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

感谢以下开源项目：
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代化UI框架
- [Room](https://developer.android.com/training/data-storage/room) - 数据库持久化
- [Hilt](https://dagger.dev/hilt/) - 依赖注入框架
- [OkHttp](https://square.github.io/okhttp/) - HTTP客户端
- [Jsoup](https://jsoup.org/) - HTML解析器

---

<div align="center">

**如果这个项目对你有帮助，请给个 Star 支持一下！**


</div>
