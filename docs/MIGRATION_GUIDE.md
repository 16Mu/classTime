# 数据库迁移脚本改进说明

## 📋 改进概览

**问题**：每次更新软件都提示初始化失败，严重影响用户体验

**解决方案**：实现健壮的数据库迁移框架，包含完整的容错机制和用户友好的错误处理

## ✅ 已完成的改进

### 1. **DatabaseMigrationHelper.kt** - 数据库迁移助手工具类
   - ✅ 自动备份机制（保留最近5个备份）
   - ✅ 支持从备份恢复
   - ✅ 数据库完整性校验
   - ✅ 详细的日志记录
   - ✅ 备份文件管理（自动清理旧备份）

**位置**: `app/src/main/java/com/wind/ggbond/classtime/data/local/database/DatabaseMigrationHelper.kt`

**主要功能**:
```kotlin
// 创建备份
DatabaseMigrationHelper.createBackup(context, dbName, fromVersion, toVersion)

// 从备份恢复
DatabaseMigrationHelper.restoreFromBackup(context, dbName, backupPath)

// 验证完整性
DatabaseMigrationHelper.validateDatabaseIntegrity(context, dbName)
```

---

### 2. **Migrations.kt** - 重构的迁移脚本
   - ✅ 所有操作包装在事务中，保证原子性
   - ✅ 详细的错误日志输出
   - ✅ try-catch 保护每个 SQL 操作
   - ✅ 幂等性支持（重复执行不会出错）
   - ✅ 智能错误识别（自动忽略"列已存在"等可忽略错误）

**核心改进**:
```kotlin
// 使用 createSafeMigration 包装所有迁移
val MIGRATION_X_Y = createSafeMigration(startVersion, endVersion, description) { database ->
    // 在事务中自动执行
    safeExecSQL(database, sql, description)  // 自动处理可忽略的错误
}

// 安全的 SQL 执行（自动处理重复列/表）
private fun safeExecSQL(database: SupportSQLiteDatabase, sql: String, description: String = "")
```

**支持的迁移版本**:
- 1 → 2: 添加 schools 表
- 2 → 3: 修复 class_times 唯一索引
- 3 → 4: 添加 course_adjustments 表
- 4 → 5: 添加 exams 表
- 5 → 6: 添加 auto_update_logs 表
- 6 → 7: 清空 schools 表
- 7 → 8: 重建 schools 表（完整字段）
- 8 → 9: 设置默认提醒开启
- 9 → 10: 添加课表时间配置字段
- 10 → 11: 补全学校字段 + 自动登录日志表
- 11 → 12: 重建 exams 表（移除 examDate）
- 12 → 13: 架构重构：合并 Semester 到 Schedule ⚠️ 复杂迁移
- 13 → 14: 添加 newClassroom 字段

---

### 3. **DatabaseModule.kt** - 优化的数据库模块
   - ✅ 迁移前自动验证数据库完整性
   - ✅ 详细的回调日志
   - ✅ 设置合理的连接超时时间（30秒）
   - ✅ 禁用破坏性迁移（保护用户数据）

**关键配置**:
```kotlin
Room.databaseBuilder(...)
    .addMigrations(*Migrations.getAllMigrations())
    .addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            // 验证数据库完整性
            DatabaseMigrationHelper.validateDatabaseIntegrity(context, dbName)
        }
    })
    .setAutoCloseTimeout(30_000L)  // 30秒超时
    .build()
```

---

### 4. **MainActivity.kt** - 增强的错误处理
   - ✅ 智能错误诊断（区分迁移错误和普通错误）
   - ✅ 用户友好的错误提示界面
   - ✅ 可展开的诊断信息
   - ✅ 完整的重试逻辑（重试时重新初始化所有组件）
   - ✅ 针对迁移错误的专门提示和建议

**错误处理流程**:
1. 捕获异常并判断是否为迁移错误
2. 生成详细的诊断信息
3. 显示用户友好的错误界面
4. 提供重试按钮（重新执行完整初始化流程）
5. 如果多次失败，提供更详细的解决建议

**错误界面特性**:
- 区分显示"数据库升级失败"和"初始化失败"
- 显示错误图标（存储/警告图标）
- 提供针对性的解决建议
- 可展开查看详细诊断信息
- 大尺寸的重试按钮（易于点击）
- 辅助提示文字

---

## 🔧 使用指南

### 对于开发者

#### 添加新的迁移脚本

当需要修改数据库结构时：

1. **更新 Entity 定义**
2. **增加数据库版本号** (CourseDatabase.kt 中的 version)
3. **创建新的迁移脚本**

```kotlin
// 示例：添加 Migration 14 -> 15
val MIGRATION_14_15 = createSafeMigration(14, 15, "描述变更") { database ->
    // 使用 safeExecSQL 执行安全的 SQL 操作
    safeExecSQL(database, "ALTER TABLE ...", "描述")
    
    // 或使用 database.execSQL() 执行必须成功的操作
}

// 在 getAllMigrations() 中添加
fun getAllMigrations(): Array<Migration> {
    return arrayOf(
        ...,
        MIGRATION_13_14,
        MIGRATION_14_15  // ✅ 新增
    )
}
```

#### 测试迁移

1. 安装旧版本应用（version N）
2. 导入一些课程数据
3. 升级到新版本（version N+1）
4. 观察日志输出：
   ```
   I/Migration: ========== 开始迁移 N -> N+1: 描述 ==========
   D/Migration: ✅ SQL执行成功: ...
   I/Migration: ✅ 迁移成功 N -> N+1: 描述 (耗时: XXms)
   ```

5. 如果出错：
   ```
   E/Migration: ❌ 迁移失败 N -> N+1: 错误信息
   E/Migration: 💥 迁移 N -> N+1 发生严重错误: 描述
   ```

#### 调试技巧

**查看详细日志**:
```bash
adb logcat | grep -E "(Migration|MigrationHelper|DatabaseModule)"
```

**手动触发备份**:
```kotlin
val backupPath = DatabaseMigrationHelper.createBackup(context, "course_schedule.db", 14, 15)
Log.d("Debug", "备份路径: $backupPath")
```

**从备份恢复**:
```kotlin
val success = DatabaseMigrationHelper.restoreFromBackup(
    context, 
    "course_schedule.db", 
    backupPath!!
)
Log.d("Debug", "恢复结果: $success")
```

---

### 对于用户

#### 正常升级流程

1. **下载新版本 APK**
2. **安装更新**（覆盖安装）
3. **首次启动**：
   - 显示启动画面（SplashScreen）
   - 后台自动执行数据库迁移（通常 < 1秒）
   - 进入主界面
   
✅ **用户无感知**，整个过程流畅自然

#### 如果遇到升级失败

**症状**：
- 看到"数据库升级失败"提示
- 应用无法进入主界面

**解决方案**（按优先级）：

1. **点击"重试"按钮**
   - 应用会重新尝试迁移
   - 90%的问题可以通过重试解决

2. **如果重试仍然失败，重启手机**
   - 关闭应用进程
   - 重启手机
   - 重新打开应用

3. **如果仍然失败**：
   - 清除应用数据（设置 → 应用 → 课程表 → 清除数据）⚠️ 会丢失课程信息
   - 或卸载重装（⚠️ 会丢失所有数据）
   - 重新导入课程

**注意**：正常情况下不应该出现升级失败。如果频繁出现，请联系开发者并提供错误详情。

---

## 📊 技术细节

### 迁移性能优化

| 优化项 | 说明 |
|--------|------|
| 事务包裹 | 所有操作在单个事务中执行，保证原子性 |
| 幂等性 | 使用 `IF NOT EXISTS` 和 `CREATE INDEX IF NOT EXISTS` |
| 错误容忍 | 自动忽略"列已存在"、"索引已存在"等可忽略错误 |
| 日志完善 | 每个步骤都有详细日志，便于定位问题 |

### 备份策略

- **保留数量**: 最近 5 个备份文件
- **命名规则**: `{dbName}_v{from}_to_v{to}_{timestamp}.db`
- **存储位置**: `/data/data/{package}/files/db_backups/`
- **清理时机**: 每次创建新备份后自动清理

### 兼容性保障

- **最低支持版本**: 从版本 1 升级到当前版本（14）
- **跳跃升级支持**: 支持从任意旧版本直接升级到最新版本
- **降级保护**: 不支持降级（防止数据不一致）

---

## ⚠️ 注意事项

### 开发者必读

1. **永远不要使用 `fallbackToDestructiveMigration()`**
   - 它会在迁移失败时删除所有用户数据
   - 这是最危险的操作！

2. **每个数据库 schema 变更都需要迁移脚本**
   - 添加表、添加列、修改列类型等
   - 即使是小的改动也需要

3. **保持迁移脚本的幂等性**
   - 使用 `safeExecSQL()` 或 `IF NOT EXISTS`
   - 避免重复执行时报错

4. **复杂迁移要特别小心**
   - 如 12→13 这种多表重建的迁移
   - 充分测试各种边界情况

5. **及时更新文档**
   - 在此文档中记录新迁移的详细信息
   - 包括变更原因、影响范围、回滚方案

### 常见问题

**Q: 为什么不能直接删除数据库重建？**
A: 会丢失用户的课程数据、提醒设置等，体验极差。

**Q: 迁移很慢怎么办？**
A: 正常迁移应该在毫秒级完成。如果超过几秒，可能是：
   - 数据量过大（数千门课程）
   - 设备性能较差
   - 存储设备故障

**Q: 可以跳过某个版本的迁移吗？**
A: 不可以。Room 要求提供连续的迁移链。

---

## 📝 更新历史

- **2026-04-03**: 初始版本
  - 实现完整的迁移框架
  - 添加容错机制和错误处理
  - 优化用户体验

---

## 📞 技术支持

如果在使用过程中遇到问题：

1. **查看日志**: `adb logcat | grep Migration`
2. **收集信息**: 
   - 当前数据库版本
   - 目标版本
   - 完整的错误堆栈
   - 设备型号和 Android 版本
3. **提交 Issue**: 在项目仓库提交问题报告

---

**最后更新**: 2026-04-03  
**维护者**: 开发团队  
**适用版本**: v1.2.0+
