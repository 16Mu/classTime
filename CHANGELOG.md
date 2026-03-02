# 更新日志

## [未发布] - 2026-03-02

### 重构

#### 课表管理重构（参考小爱课程表设计）
- **涉及文件**: 
  - `ScheduleSelectionDialog.kt` (新增)
  - `BatchCourseCreateViewModel.kt`
  - `BatchCourseCreateScreen.kt`
  - `ImportScheduleViewModel.kt`
  - `ImportScheduleScreen.kt`
  - `InitializationRepository.kt`

- **改动内容**:
  1. **首次使用不再自动创建默认课表**：用户需要在导入/创建课程时手动创建课表
  2. **智能课表状态检查**：
     - 如果没有课表，强制先创建课表（设置昵称+开始日期+总周数）
     - 如果当前课表已过期（结束日期早于今天），提醒用户选择继续使用或创建新课表
     - 如果有有效的当前课表，直接使用
  3. **新增课表选择组件**：
     - `CreateScheduleDialog`: 创建课表对话框，支持智能默认值
     - `ScheduleExpiredDialog`: 课表过期提醒对话框
     - `checkScheduleState()`: 统一的课表状态检查方法
  4. **保留其他默认值**：上课时间配置等默认值保持不变

### 修复

#### 问题1: 教务系统导入闪退
- **文件**: `SmartWebViewImportScreen.kt`
- **原因**: 当schoolId为空或无效时，页面会无限加载或闪退
- **修复**: 
  - 添加schoolId空值检查，显示友好的错误提示
  - 使用remember包装Flow，避免重复收集导致的问题
  - 添加5秒超时检测，防止无限加载

#### 问题2: 批量导入后课表不显示
- **文件**: `MainViewModel.kt`
- **原因**: loadCourses方法每次被调用时启动新协程，但旧协程没有被取消，导致多个协程同时收集不同scheduleId的数据
- **修复**:
  - 添加courseLoadJob和adjustmentLoadJob变量跟踪协程
  - 在加载新课表时取消旧的加载任务
  - 清空旧数据，确保UI不会显示旧课表的数据

#### 问题3: 批量创建课程时周次为空
- **文件**: `BatchCourseCreateViewModel.kt`, `BatchCourseCreateScreen.kt`
- **原因**: 批量创建时每个时间段的周次默认为空，导致课程保存后显示"0周"
- **修复**: 将周次从可选改为必选项，验证时检查周次不能为空

#### 问题4: 时间段插入顺序错误
- **文件**: `BatchCourseCreateViewModel.kt`
- **原因**: 新增时间段被插入到列表开头，导致时间段1变成时间段2
- **修复**: 新增时间段添加到列表末尾，保持正确顺序

#### 优化: 时间段卡片进入动画
- **文件**: `BatchCourseCreateScreen.kt`
- **改动**: 新增时间段时添加从右侧滑入+淡入的动画效果

