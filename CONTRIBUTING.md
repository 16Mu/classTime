# 贡献指南

感谢你对 **时课** 项目的关注！这是一个功能完整的智能校园课表管理应用，支持27所学校的智能课表导入。我们欢迎各种形式的贡献，无论是报告问题、提出建议，还是直接贡献代码。

## 📋 目录

- [如何贡献](#如何贡献)
- [开发环境设置](#开发环境设置)
- [代码规范](#代码规范)
- [提交规范](#提交规范)
- [Pull Request 流程](#pull-request-流程)
- [问题报告](#问题报告)
- [功能请求](#功能请求)
- [学校适配](#学校适配)

## 如何贡献

### 报告问题
如果你发现了 bug，请：
1. 检查 [Issues](https://github.com/yourusername/course-schedule/issues) 是否已有相关问题
2. 如果没有，请创建新的 Issue，包含：
   - 详细的问题描述
   - 重现步骤
   - 设备信息（Android 版本、设备型号）
   - 应用版本
   - 相关截图或日志

### 功能建议
我们欢迎新功能的想法！请：
1. 在 Issues 中详细描述功能需求
2. 说明使用场景和预期效果
3. 如果可能，提供设计草图或原型

### 代码贡献
如果你想直接贡献代码：
1. Fork 项目到你的 GitHub 账户
2. 创建功能分支
3. 编写代码和测试
4. 提交 Pull Request

## 开发环境设置

### 前置要求
- **Android Studio** Arctic Fox 或更高版本
- **JDK** 17 或更高版本
- **Android SDK** API 26+
- **Git**

### 克隆和设置
```bash
# 1. Fork 并克隆项目
git clone https://github.com/yourusername/course-schedule.git
cd course-schedule

# 2. 添加上游仓库
git remote add upstream https://github.com/original-owner/course-schedule.git

# 3. 使用 Android Studio 打开项目
# 4. 同步 Gradle 依赖
```

### 构建项目
```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease

# 运行测试
./gradlew test
```

## 代码规范

### Kotlin 编码规范
我们遵循 [Kotlin 官方编码规范](https://kotlinlang.org/docs/coding-conventions.html)：

#### 命名规范
```kotlin
// 类名使用 PascalCase
class CourseRepository

// 函数和变量使用 camelCase
fun getCourseById(id: Long): Course?
val currentWeekNumber: Int

// 常量使用 UPPER_SNAKE_CASE
const val MAX_WEEK_COUNT = 25

// 私有属性可以使用下划线前缀
private val _courses = MutableStateFlow<List<Course>>(emptyList())
val courses: StateFlow<List<Course>> = _courses.asStateFlow()
```

#### 文件组织
```kotlin
// 1. Package 声明
package com.wind.ggbond.classtime.ui.screen.main

// 2. Import 语句（按字母顺序）
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

// 3. 类定义
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    // 实现
}
```

#### 注释规范
```kotlin
/**
 * 课程数据仓库
 * 
 * 提供课程数据的增删改查操作，支持本地数据库缓存和远程数据同步。
 * 
 * @property courseDao 课程数据访问对象
 * @property apiClient API 客户端
 */
class CourseRepository(
    private val courseDao: CourseDao,
    private val apiClient: ApiClient
) {
    /**
     * 获取指定周次的课程列表
     * 
     * @param weekNumber 周次（1-25）
     * @param semesterId 学期ID
     * @return 课程列表的 Flow
     */
    fun getCoursesForWeek(weekNumber: Int, semesterId: Long): Flow<List<Course>> {
        // 实现
    }
}
```

### Compose 规范
```kotlin
// 组件函数使用 @Composable 注解
@Composable
fun CourseCard(
    course: Course,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    // 使用 modifier 参数作为第一个参数
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCourseClick(course) }
    ) {
        // 实现
    }
}

// 预览函数
@Preview(showBackground = true)
@Composable
private fun CourseCardPreview() {
    CourseScheduleTheme {
        CourseCard(
            course = Course.sample(),
            onCourseClick = {}
        )
    }
}
```

## 提交规范

我们使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

### 提交格式
```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### 类型说明
- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `style`: 代码格式调整（不影响功能）
- `refactor`: 代码重构
- `test`: 测试相关
- `chore`: 构建过程或辅助工具的变动

### 示例
```bash
# 新功能
git commit -m "feat(ui): 添加课程卡片长按操作面板

- 新增课程复制/粘贴功能
- 支持批量删除课程
- 优化操作面板动画效果"

# 修复 bug
git commit -m "fix(crash): 修复切换学期时应用崩溃问题"

# 文档更新
git commit -m "docs(readme): 更新安装说明和功能介绍"
```

## Pull Request 流程

### 1. 创建分支
```bash
# 从 main 分支创建功能分支
git checkout main
git pull upstream main
git checkout -b feature/your-feature-name
```

### 2. 开发和提交
```bash
# 开发功能...
git add .
git commit -m "feat: 添加新功能描述"
```

### 3. 保持同步
```bash
# 定期同步上游仓库
git fetch upstream
git rebase upstream/main
```

### 4. 推送和创建 PR
```bash
# 推送到你的 fork
git push origin feature/your-feature-name

# 在 GitHub 上创建 Pull Request
```

### PR 模板
请在 PR 中包含：
- **变更描述** - 详细说明你的变更
- **解决的问题** - 关联的 Issue 编号
- **测试情况** - 说明如何测试你的变更
- **截图** - 如果是 UI 变更，请提供截图

## 问题报告

### Bug Report 模板
```markdown
## Bug 描述
简要描述遇到的问题

## 设备信息
- 设备型号：
- Android 版本：
- 应用版本：

## 重现步骤
1. 进入...
2. 点击...
3. 观察...

## 期望行为
描述你期望发生的情况

## 截图/日志
如果适用，请添加截图或日志

## 附加信息
任何其他相关信息
```

## 功能请求

### Feature Request 模板
```markdown
## 功能描述
简要描述你希望添加的功能

## 使用场景
描述这个功能的使用场景和价值

## 解决方案
如果有具体的实现想法，请描述

## 设计草图
如果有界面设计，请提供草图或原型

## 替代方案
是否考虑过其他解决方案？
```

## 学校适配

如果你希望为新的学校添加支持：

### 1. 收集信息
- 学校名称
- 教务系统登录地址
- 课表查询地址
- 登录表单结构

### 2. 创建提取器
```kotlin
@SchoolExtractor("your_university")
class YourUniversityExtractor : SchoolScheduleExtractor {
    override suspend fun extractSchedule(
        html: String,
        semesterId: String?
    ): List<ParsedCourse> {
        // 实现提取逻辑
    }
}
```

### 3. 添加配置
在 `schools.json` 中添加学校配置：
```json
{
  "name": "你的大学",
  "loginUrl": "https://教务系统地址/login",
  "scheduleUrl": "https://教务系统地址/schedule",
  "extractor": "YourUniversityExtractor"
}
```

### 4. 测试
- 测试登录流程
- 测试课表导入
- 测试各种边界情况
- 确保与现有27所学校不冲突

## 贡献者指南

### 代码审查
所有 PR 都需要通过代码审查，我们会检查：
- 代码质量和规范
- 功能正确性
- 测试覆盖
- 文档完整性
- 性能影响

### 发布流程
- 功能开发完成后会合并到 `main` 分支
- 定期创建 Release 版本
- 重要更新会发布到 GitHub Releases

## 获取帮助

如果你在贡献过程中遇到问题：

1. 查看 [项目文档](README.md)
2. 搜索 [已有 Issues](https://github.com/yourusername/course-schedule/issues)
3. 在 [Discussions](https://github.com/yourusername/course-schedule/discussions) 中讨论
4. 联系项目维护者

## 致谢

感谢所有为项目做出贡献的开发者！你的贡献让这个项目变得更好。

---

再次感谢你的贡献！
