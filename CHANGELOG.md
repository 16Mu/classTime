# 更新日志

## [未发布] - 2026-03-02

### 修复

#### 临时屏蔽4x4小组件和工具页面桌面小组件入口
- **涉及文件**: 
  - `WidgetPinHelper.kt` (更新)
  - `ToolsScreen.kt` (更新)

- **修改内容**:
  1. **禁用4x4大尺寸小组件**：
     - 从`WidgetPinHelper.WidgetType`枚举中移除`LARGE_TODAY_COURSE`类型
     - 更新`requestPinWidget`、`getWidgetDisplayName`、`getWidgetDescription`方法
     - 保留原有代码并添加注释，便于后续恢复
  2. **隐藏工具页面桌面小组件分类**：
     - 注释掉`ToolsScreen.kt`中整个"桌面小组件"分类部分
     - 包括分类标题和"添加桌面小组件"选项
     - 保留代码结构，便于后续恢复

#### 4x4桌面小部件载入问题修复
- **涉及文件**: 
  - `widget_loading.xml` (更新)
  - `LargeTodayCourseWidgetProvider.kt` (更新)
  - `LargeTodayCourseWidgetService.kt` (更新)
  - `WidgetDataProvider.kt` (更新)
  - `WidgetRefreshHelper.kt` (更新)

- **问题修复**:
  1. **初始加载布局优化**：重新设计`widget_loading.xml`布局
     - 模仿最终小部件布局结构，避免布局跳跃
     - 添加进度条和更友好的加载提示
     - 使用与最终布局相同的背景和样式
  2. **异步数据加载优化**：改进`LargeTodayCourseWidgetProvider`数据加载逻辑
     - 使用IO调度器优化数据库操作性能
     - 添加`updateHeaderWithDefaultInfo`方法，先显示默认日期信息
     - 避免加载期间的空白显示
  3. **RemoteViews设置时机优化**：确保数据加载完成后立即更新显示
     - 优化`refreshAllWidgets`方法，先通知数据变更再发送更新广播
     - 改进`LargeTodayCourseWidgetService`的数据加载错误处理
     - 添加详细的错误日志记录
  4. **错误处理和重试机制增强**：
     - `WidgetDataProvider`根据异常类型提供更具体的错误信息
     - `WidgetRefreshHelper`添加自动重试机制，失败后延迟重试
     - 新增`safeRefreshWidget`方法，支持多次重试和递增延迟

- **效果改进**:
  - 小部件添加到桌面时立即显示有意义的布局而非空白
  - 数据加载过程更流畅，用户体验显著提升
  - 错误恢复能力增强，减少加载失败情况

### 新增

#### 列表模式界面优化
- **涉及文件**: 
  - `MainScreen.kt` (更新)
  - `WeekView.kt` (更新)

- **功能优化**:
  1. **紧凑模式快捷切换按钮**：在课表右上角列表模式按钮旁边添加紧凑模式快捷切换按钮
     - 使用 UnfoldLess/UnfoldMore 图标表示紧凑/展开状态
     - 开启时按钮背景变为 primaryContainer 颜色以示区分
     - 支持动画过渡效果和触觉反馈
  2. **列表模式颜色调整**：调整列表模式中课程卡片的颜色透明度，与表格模式保持一致
     - 过去课程透明度从 0.3 调整为 0.6
     - 正在上课的课程恢复为原色（透明度 1.0）
     - 未来课程透明度从 0.6 调整为 0.9
     - 文字颜色也相应调整，确保与表格模式视觉效果一致
  3. **"没有课"显示简化**：大幅减少"这天没有课哦~"的占用高度
     - 从原来的大图标+两行文字改为小图标+单行文字的紧凑样式
     - 垂直占用高度从约 100dp 减少到约 40dp
     - 保持视觉一致性的同时提升空间利用率

### 新增

#### 批量添加课程页面动画优化
- **涉及文件**: 
  - `BatchCourseCreateScreen.kt` (更新)
  - `BatchCourseCreateViewModel.kt` (更新)

- **功能优化**:
  1. **动画速度优化**：参考CourseInfoListScreen的展开/收缩动画效果，提高stiffness参数使动画更快速流畅
  2. **新增课程插入动画改进**：
     - 收缩已有卡片与新课程插入同时进行（联动效果）
     - 新课程从顶部插入，列表自动下滑
     - 滚动到顶部后展开新课程卡片
  3. **基础信息区域独立展开/收缩**：
     - 基础信息区域支持独立的展开/收缩功能
     - 填写完成后可点击"完成基础信息"按钮收缩该区域
     - 收缩时显示摘要信息（课程名称·教师·教室）
     - 点击标题行可重新展开编辑
  4. **数据模型扩展**：BatchCourseItem新增isBasicInfoExpanded字段管理基础信息展开状态

#### 课表导入智能日期识别
- **涉及文件**: 
  - `SchoolConfig.kt` (更新)
  - `SchoolScheduleExtractor.kt` (更新)
  - `SmartImportViewModel.kt` (更新)
  - `SmartWebViewImportScreen.kt` (更新)

- **功能特性**:
  1. **新增ImportedSemesterInfo数据类**：用于存储导入时提取到的学期开始日期、结束日期和总周数
  2. **提取器接口扩展**：SchoolScheduleExtractor接口新增`parseSemesterInfo`方法，支持从课表页面提取学期日期信息
  3. **智能对话框显示**：
     - 如果导入的课表携带开始日期和结束日期/总周数，对话框只显示"课表昵称"输入框，并展示已获取的日期信息
     - 如果没有日期信息，则显示完整的设置界面（昵称+开始日期+总周数）
  4. **优先级逻辑**：导入日期 > 学校配置日期 > 智能推断日期

#### 课表快速编辑功能
- **涉及文件**: 
  - `ScheduleQuickEditDialog.kt` (新增)
  - `MainScreen.kt` (更新)
  - `MainViewModel.kt` (更新)

- **功能特性**:
  1. **点击标题快速编辑**：在主界面点击课表名称区域（红框部分），弹出快速编辑对话框
  2. **编辑课表名称**：支持手动输入或从预设学期选项中选择
  3. **编辑起始日期**：使用日期选择器选择学期第一天上课日期
  4. **编辑持续周次**：从常用周数（16/18/20/22/24周）中选择
  5. **自动计算结束日期**：根据起始日期和周次自动计算并显示结束日期
  6. **编辑图标提示**：课表名称旁显示编辑图标，提示用户可点击编辑
  7. **触觉反馈**：点击时提供触觉反馈，提升交互体验

#### 课表导出功能全面优化
- **文件**: `ExportService.kt`
- **改动内容**:

  **HTML导出**：
  1. 温暖米白色系设计，采用与应用UI一致的配色方案
  2. 双视图展示：课表网格视图 + 课程详情列表
  3. SVG内联图标，保证跨平台一致性
  4. 课程颜色标识，使用应用内10色调色板
  5. 响应式布局 + 打印样式优化
  6. 卡片式设计，悬停动画效果

  **JSON导出**：
  1. 结构化格式，包含meta元数据、schedule课表信息、classTimes时间配置
  2. 添加statistics统计信息（总课程数、总学分、按星期分布）
  3. 使用PrettyPrinting格式化输出，便于阅读
  4. 版本号升级为2.0，支持完整数据还原

  **CSV导出**：
  1. 添加UTF-8 BOM头，确保Excel正确识别中文
  2. 新增字段：序号、上课时间、颜色、提醒状态
  3. 按星期和节次排序输出
  4. 底部添加统计信息（课表名称、学期时间、总学分等）
  5. 正确转义双引号，避免格式错误

  **TXT导出**：
  1. 使用Unicode边框字符创建美观的卡片式布局
  2. 清晰的层次结构：标题区域 → 基本信息 → 按星期分组的课程卡片
  3. 显示具体上课时间（从时间配置获取）
  4. 统一的页眉页脚设计

#### ICS导入功能完善
- **文件**: `ImportService.kt`
- **改动内容**:
  1. **完整的DTSTART/DTEND时间解析**：支持UTC时间、本地时间、带时区格式
  2. **自动推断星期几**：从ICS事件日期自动计算星期几（1=周一，7=周日）
  3. **智能节次匹配**：根据课程时间配置，将ICS事件时间匹配到最接近的节次
  4. **周次自动计算**：根据学期开始日期和事件日期，自动计算周次列表
  5. **描述信息解析**：从DESCRIPTION字段提取教师、节次、周次等信息
  6. **多行折叠支持**：正确处理ICS规范中的多行折叠格式
  7. **重复课程检测**：导入时自动跳过已存在的相同课程
  8. **周次表达式格式化**：将周次列表格式化为"1-4,6-8周"等易读格式

#### 4x4 大尺寸今日课程小组件
- **涉及文件**: 
  - `LargeTodayCourseWidgetProvider.kt` (新增)
  - `LargeTodayCourseWidgetService.kt` (新增)
  - `widget_large_today_course.xml` (新增)
  - `widget_large_course_item.xml` (新增)
  - `large_today_course_widget_info.xml` (新增)
  - `widget_colors.xml` (新增)
  - `WidgetRefreshHelper.kt` (更新)
  - `AndroidManifest.xml` (更新)

- **功能特性**:
  1. **4x4 大尺寸布局**：展示更多今日课程信息
  2. **基于 RemoteViews 实现**：使用传统 AppWidgetProvider + ListView 方案
  3. **支持列表滚动**：课程较多时可滚动查看
  4. **深色模式适配**：通过 values-night 目录自动适配深色主题
  5. **手动刷新按钮**：点击刷新按钮立即更新数据
  6. **点击跳转**：点击小组件或课程项跳转到主应用
  7. **统一刷新机制**：与现有 Glance 小组件共享 WidgetRefreshHelper

#### 应用内小组件快捷添加入口
- **涉及文件**: 
  - `WidgetPinHelper.kt` (新增)
  - `WidgetPinCallbackReceiver.kt` (新增)
  - `ToolsScreen.kt` (更新)
  - `AndroidManifest.xml` (更新)

- **功能特性**:
  1. **工具页面新增入口**：在"桌面小组件"分类下添加"添加桌面小组件"选项
  2. **小组件选择对话框**：列出所有可用小组件类型（今日课程、下节课倒计时、今日课程大尺寸）
  3. **一键添加到桌面**：使用 Android 8.0+ 的 `requestPinAppWidget` API 快速固定小组件
  4. **添加成功提示**：小组件添加成功后显示 Toast 提示

### 重构

#### 手动添加课程页面UI全面重构
- **文件**: `BatchCourseCreateScreen.kt`
- **改动内容**:
  1. **课程卡片现代化设计**：
     - 使用圆角20dp的Card组件，与CourseEditScreen风格统一
     - 序号标识改为圆角方形（12dp），支持课程颜色显示
     - 添加时间段和周次标签，使用半透明容器展示
     - 删除按钮使用DeleteOutline图标，更加柔和
     - 展开/折叠按钮使用圆形Surface包裹
  2. **展开内容区域卡片式分区**：
     - 基础信息卡片：包含课程名称、教师、教室、学分输入
     - 课程颜色卡片：网格式颜色选择器，支持查看更多颜色
     - 剪贴板智能导入：使用FilledTonalButton，更加醒目
     - 时间安排卡片：包含添加按钮和时间段列表
     - 提醒设置卡片：带动画的开关和提醒分钟数输入
     - 备注卡片：独立的备注输入区域
  3. **时间段卡片重构**：
     - 使用Surface替代Card，添加边框线
     - 序号使用圆角方形标识
     - 删除按钮使用圆角容器包裹
     - 星期选择添加日历图标
     - 节次输入添加播放和计时器图标
     - 周次选择改为可点击的Surface，显示更清晰
  4. **统一设计语言**：
     - 所有输入框使用RoundedCornerShape(16.dp)
     - 标题区域使用图标+文字组合，图标包裹在圆角Surface中
     - 颜色使用surfaceVariant.copy(alpha)等半透明背景
     - 添加OutlinedTextFieldDefaults.colors统一边框颜色

#### 调课记录管理页面UI重构
- **文件**: `AdjustmentManagementScreen.kt`
- **改动内容**:
  1. **移除emoji表情**：使用Material Design 3图标替代emoji（SwapHoriz替代🔄）
  2. **统一设计风格**：采用与ProfileScreen、SettingsScreen一致的列表项风格
  3. **时间线布局**：使用清晰的时间线设计展示调课前后变化
  4. **展开/收起交互**：点击记录可展开查看详细信息，支持动画过渡
  5. **课程颜色标识**：图标容器使用课程颜色，便于识别
  6. **触觉反馈**：所有交互操作添加触觉反馈
  7. **优化空状态**：采用与其他页面一致的空状态设计
  8. **底部提示卡片**：添加操作提示，引导用户使用
  9. **简化顶部栏**：移除统计卡片，在标题栏显示记录数量

#### 导出课程表对话框UI重构
- **文件**: `ExportDialog.kt`
- **改动内容**:
  1. **统一对话框风格**：从AlertDialog改为Card+Dialog组合，与WeekPickerDialog等保持一致
  2. **简化动画效果**：移除复杂的旋转、弹性、依次渐入动画，改用简洁的颜色过渡动画
  3. **优化选项卡设计**：使用RadioButton风格的选中指示器，更符合Material Design 3规范
  4. **添加触觉反馈**：所有按钮和选项点击时提供触觉反馈
  5. **优化结果对话框**：居中布局，图标使用圆角容器包裹，文件路径信息更清晰
  6. **清理冗余代码**：移除未使用的动画导入和变量

#### 课表管理重构
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

#### 小组件加载问题修复
- **涉及文件**: 
  - `WidgetDataProvider.kt`
  - `TodayCourseWidget.kt`
  - `NextClassWidget.kt`
  - `MainActivity.kt`
  - `MainScreen.kt`
  - `LargeTodayCourseWidgetProvider.kt` (更新)

- **问题1**: 小组件加载时提示"载入窗口小部件时出现问题"
- **原因**: 数据库操作可能超时或异常，导致 Glance 小组件加载失败
- **修复**:
  1. 在 `WidgetDataProvider` 中添加 5 秒超时机制，防止数据库操作阻塞
  2. 在 `TodayCourseWidget` 和 `NextClassWidget` 的 `provideGlance` 方法中添加 try-catch 异常处理
  3. 异常时返回友好的空数据状态，避免小组件崩溃

- **问题2**: 从小组件点击进入应用时首次看到空课表
- **原因**: `MainViewModel` 的数据加载是异步的，UI 渲染比数据加载更快
- **修复**:
  1. 在 `MainActivity` 初始化完成后调用 `WidgetRefreshHelper.refreshAllWidgets()` 刷新小组件数据
  2. 在 `MainScreen` 中使用 `allCourses.size` 和 `adjustments.size` 作为 `remember` 的 key
  3. 确保课程数据加载完成后能正确触发 UI 重组

- **问题3**: 4x4大尺寸小组件放置到桌面时提示"载入窗口小部件时出现问题"
- **原因**: `updateAppWidget` 方法中协程异步更新与 RemoteAdapter 设置顺序不当，导致小组件初始化失败
- **修复**:
  1. 调整执行顺序：先设置 RemoteAdapter 和点击事件，再调用 `updateAppWidget`
  2. 先更新基础布局确保小组件可以正常显示
  3. 然后异步加载数据更新头部信息
  4. 异步更新时重新创建 RemoteViews 并设置所有必要属性
  5. 数据加载完成后调用 `notifyAppWidgetViewDataChanged` 通知 ListView 刷新

#### 小组件预览图添加
- **涉及文件**: 
  - `widget_large_preview_image.xml` (新增)
  - `widget_preview_image.xml` (新增)
  - `widget_next_class_preview_image.xml` (新增)
  - `large_today_course_widget_info.xml` (更新)
  - `today_course_widget_info.xml` (更新)
  - `next_class_widget_info.xml` (更新)

- **问题**: 小组件在选择器中没有预览图显示
- **修复**:
  1. 为三种小组件创建 XML drawable 预览图资源
  2. 在小组件配置文件中添加 `android:previewImage` 属性
  3. 同时保留 `android:previewLayout` 属性，兼容 Android 12+ 的动态预览

#### 课程信息页面周次显示错误
- **文件**: `CourseInfoListScreen.kt`, `CourseDetailBottomSheet.kt`
- **问题**: 展开课程时间段后，周次显示不正确（如显示"14周"而非"1-5,7-9,11,13-16,19周"）
- **修复**: 
  1. 重写`formatWeeksDisplay`函数，使用区间合并算法正确显示周次信息
  2. 点击时间段编辑按钮后直接进入编辑模式，无需二次点击

#### 下节课小组件显示逻辑优化
- **文件**: `WidgetDataProvider.kt`
- **原因**: 正在上课时，小组件显示的是当前课程信息，而非下一节课信息
- **修复**: 
  - 情况1：正在上课且有下一节课 → 显示下课倒计时 + **下一节课**信息
  - 情况2：正在上课但无下一节课（今日最后一节）→ 显示下课倒计时 + 当前课程信息 + "今日最后一节"提示
  - 情况3：没有在上课 → 显示上课倒计时 + 下一节课信息

#### 问题1: 教务系统导入闪退
- **文件**: `SmartWebViewImportScreen.kt`
- **原因**: 当schoolId为空或无效时，页面会无限加载或闪退
- **修复**: 
  - 添加schoolId空值检查，显示友好的错误提示
  - 使用remember包装Flow，避免重复收集导致的问题
  - 添加5秒超时检测，防止无限加载

#### 问题6: WebView加载页面失败(ERR_CONNECTION_REFUSED)
- **文件**: `AndroidManifest.xml`, `network_security_config.xml`
- **原因**: Android 9+默认禁止HTTP明文流量，`usesCleartextTraffic="false"`和`cleartextTrafficPermitted="false"`导致很多使用HTTP协议的教务系统无法访问
- **修复**: 
  - 将`AndroidManifest.xml`中的`usesCleartextTraffic`改为`true`
  - 将`network_security_config.xml`中的`base-config`的`cleartextTrafficPermitted`改为`true`
  - 移除特定域名白名单配置，改为全局允许HTTP流量（教务系统兼容性需要）

#### 问题5: 从教务系统导入课表100%闪退（Release版本）
- **文件**: `SchoolRepository.kt`, `proguard-rules.pro`
- **原因**: 
  1. `SchoolData`内部类是`private data class`，在Release版本中被R8/Proguard混淆，导致Gson无法正确解析`schools.json`中的字段名
  2. `SchoolData`类缺少`defaultSemesterStartDate`、`fallSemesterStartDate`、`springSemesterStartDate`三个字段，导致学校配置的学期日期信息丢失
- **修复**: 
  - 在`proguard-rules.pro`中添加`SchoolRepository$SchoolData`类的保护规则
  - 在`SchoolData`类中补充三个学期日期字段，并在`toEntity()`方法中正确传递给`SchoolEntity`

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

#### 优化: 文件导入课表时智能分配颜色
- **文件**: `ImportService.kt`
- **改动**: 
  1. **JSON导入**：导入课程时传入已有课程颜色列表，避免新导入的课程与现有课程颜色重复
  2. **CSV导入**：同样实现智能颜色分配，确保每门新课程获得差异明显的颜色
  3. **ICS导入**：修改`parseIcsContentFull`方法，支持传入已有颜色列表进行智能分配
  4. **颜色累积**：导入过程中动态更新已使用颜色列表，确保同一批次导入的课程也不会重复颜色

#### 修复: 删除/撤销课程后课表不立即更新
- **文件**: `MainViewModel.kt`
- **问题**: 删除课程或点击撤销后，课表没有立即刷新，用户需要手动切换页面才能看到变化
- **修复**: 
  1. 新增`forceRefreshCourses()`方法，强制重新加载课程数据触发Flow更新
  2. 在`deleteCourseForWeek()`和`undoLastDelete()`方法中调用强制刷新
  3. 确保删除和撤销操作后UI立即响应数据变化

#### 重构: 临时调课对话框UI紧凑化
- **文件**: `CourseAdjustmentDialog.kt`
- **问题**: 原对话框内容过多需要滚动才能看到所有选项
- **改动**: 
  1. 移除`verticalScroll`，所有内容在一屏内显示
  2. 课程信息和原始时间合并为单行紧凑显示
  3. 周次和节次选择横向排列，节约垂直空间
  4. 星期选择改为单行7天布局
  5. 移除调课原因输入框（可选功能，简化流程）
  6. 整体减少间距，按钮高度从52dp降为48dp

#### 修复: HTML导出课表多课程显示问题
- **文件**: `ExportService.kt`
- **问题**: 当同一时间段（同一天同一节次）存在多个课程时，HTML导出只显示第一个课程
- **修复**: 
  1. 修改课程网格渲染逻辑，支持在同一格子内显示多个课程
  2. 单个课程时保持原有显示方式
  3. 多个课程时使用垂直堆叠布局，每个课程显示名称、教室和周次信息
  4. 新增`multi-course-cell`和`course-item`CSS样式类
  5. 多课程格子使用最大跨行数，确保布局正确

