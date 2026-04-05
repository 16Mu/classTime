# 自定义背景功能修复 - 快速开始

## 🎯 核心改进

**从 URI 引用 → 本地文件存储**

用户选择图片后，我们将文件复制到应用私有存储，彻底解决：
- ✅ 图片加载失败
- ✅ 权限问题
- ✅ 用户删除原文件后失效
- ✅ 应用闪退

## 🚀 快速测试

### Windows
```bash
test_background_fix.bat
```

### Linux/Mac
```bash
chmod +x test_background_fix.sh
./test_background_fix.sh
```

## 📝 测试清单

- [ ] 上传图片，立即显示预览
- [ ] 点击"开启莫奈课程取色"，不闪退
- [ ] 调节模糊滑块，预览实时更新
- [ ] 调节暗化滑块，预览实时更新
- [ ] 返回主界面，背景已应用
- [ ] 删除原图片，重启应用，背景仍存在 ⭐

## 🔍 关键日志

成功的日志应该显示：
```
MediaFileManager: Copying media from URI: content://...
MediaFileManager: Media copied successfully to: /data/data/.../files/backgrounds/{uuid}.jpg
BackgroundThemeManager: Extracting seed color from URI: file://...
BackgroundThemeManager: Extracted seed color: #XXXXXXXX
```

## 📂 新增文件

1. **MediaFileManager.kt** - 文件管理器
   - 复制文件到私有存储
   - 删除和清理功能
   - 文件大小统计

2. **MediaFileManagerTest.kt** - 单元测试
   - 测试文件复制
   - 测试删除和清理
   - 测试大小计算

## 🔧 修改的文件

1. **BackgroundSettingsViewModel.kt**
   - 使用 MediaFileManager 复制文件
   - 删除时同时删除本地文件
   - 清空时清理所有文件

2. **BackgroundThemeManager.kt**
   - 增强错误处理
   - 添加详细日志

## 📊 存储位置

```
/data/data/com.wind.ggbond.classtime/files/backgrounds/
├── {uuid1}.jpg  (用户上传的图片1)
├── {uuid2}.gif  (用户上传的GIF)
├── {uuid3}.mp4  (用户上传的视频)
└── ...
```

## ⚠️ 注意事项

1. **首次使用需要卸载旧版本**
   - 清除旧的 URI 引用数据
   - 避免兼容性问题

2. **文件会占用存储空间**
   - 最多10套背景（已限制）
   - 建议单个文件 < 10MB
   - 总占用通常 50-100MB

3. **用户可以随时清理**
   - 点击"恢复默认"清除所有背景
   - 应用卸载时自动清理

## 📖 详细文档

- **BACKGROUND_FIX_SUMMARY.md** - 完整的技术说明
- **BACKGROUND_BUG_FIX_GUIDE.md** - 诊断和修复指南

## 🎉 预期效果

修复后，用户可以：
1. 上传背景，立即看到预览
2. 实时调节模糊和暗化效果
3. 背景正确应用到课表界面
4. 删除原图片后背景仍然有效
5. 应用重启后背景保持不变

## 💡 后续优化

可以考虑：
- 图片压缩（减少存储空间）
- 显示存储空间占用
- 导出/导入背景方案
- 云端备份同步
