# 自定义背景功能问题诊断与修复指南

## 问题描述

1. 导入背景后点击"开启莫奈课程取色"时应用闪退
2. 重新进入后背景显示"图片加载失败"
3. 模糊程度调节没有效果（预览图不变化）
4. 背景没有应用到课表界面

## 快速修复步骤

### 步骤 1: 清除应用数据并重新安装

```bash
# 方法 1: 使用 ADB
adb uninstall com.wind.ggbond.classtime
./gradlew installDebug

# 方法 2: 在设备上手动操作
# 设置 -> 应用 -> 课程表 -> 存储 -> 清除数据
```

### 步骤 2: 检查权限

确保应用在 AndroidManifest.xml 中声明了以下权限：

```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" 
    android:maxSdkVersion="32" />
```

### 步骤 3: 添加 URI 持久化权限

在 `BackgroundSettingsViewModel.kt` 的媒体选择函数中添加权限持久化：

```kotlin
fun onImageSelected(uri: Uri) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        try {
            // 添加这行：持久化 URI 访问权限
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            
            val seedColor = backgroundThemeManager.extractSeedColorFromUri(uri)
            // ... 其余代码
        } catch (e: Exception) {
            Log.e("BackgroundSettings", "Failed to process image", e)
            _uiState.update { it.copy(isLoading = false, showImagePicker = false) }
        }
    }
}
```

### 步骤 4: 修复预览图加载

检查 `BackgroundSettingsScreen.kt` 中的预览组件是否正确使用 Coil 加载图片：

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(uri)
        .crossfade(true)
        .build(),
    contentDescription = "背景预览",
    modifier = Modifier
        .fillMaxSize()
        .blur(blurRadius.dp)  // 确保模糊效果应用
        .graphicsLayer(alpha = 1f - (dimAmount / 100f)),  // 确保暗化效果应用
    contentScale = ContentScale.Crop
)
```

### 步骤 5: 添加错误处理和日志

在关键位置添加日志以诊断问题：

```kotlin
// 在 BackgroundThemeManager.kt 的 extractSeedColorFromUri 中
suspend fun extractSeedColorFromUri(uri: Uri): Int {
    return try {
        Log.d(TAG, "Extracting seed color from URI: $uri")
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI: $uri")
                return DEFAULT_SEED_COLOR
            }
            val color = extractSeedColorFromBitmap(bitmap)
            Log.d(TAG, "Extracted seed color: ${Integer.toHexString(color)}")
            color
        } ?: run {
            Log.e(TAG, "Failed to open input stream for URI: $uri")
            DEFAULT_SEED_COLOR
        }
    } catch (e: Exception) {
        Log.e(TAG, "Exception extracting seed color from URI: $uri", e)
        DEFAULT_SEED_COLOR
    }
}
```

## 可能的根本原因

### 1. URI 权限丢失

当应用重启后，临时的 URI 访问权限会丢失。需要使用 `takePersistableUriPermission()` 来持久化权限。

### 2. 图片加载失败

Coil 可能无法访问 URI，需要确保：
- URI 格式正确（content:// 开头）
- 应用有访问权限
- 文件仍然存在

### 3. 预览效果不生效

可能是因为：
- Modifier 顺序不对（blur 和 graphicsLayer 需要在正确的位置）
- 参数值没有正确传递到 Composable
- 重组时状态没有更新

### 4. 闪退问题

可能是因为：
- 尝试访问已删除的文件
- 权限不足
- 内存不足（图片太大）
- 空指针异常

## 测试步骤

1. 清除应用数据
2. 重新安装应用
3. 打开背景设置
4. 选择一张图片
5. 检查 Logcat 中的日志输出
6. 调节模糊和暗化滑块
7. 返回主界面查看背景是否应用

## 查看日志

```bash
# 过滤相关日志
adb logcat | grep -E "(BackgroundThemeManager|BackgroundSettings|BackgroundSettingsViewModel)"
```

## 如果问题仍然存在

1. 检查 Logcat 中的错误信息
2. 确认 URI 是否正确保存到 DataStore
3. 确认图片文件是否仍然可访问
4. 尝试使用不同的图片（更小的文件）
5. 检查设备存储空间是否充足

## 代码修改建议

如果需要修改代码，重点关注以下文件：

1. `BackgroundSettingsViewModel.kt` - 添加 URI 权限持久化
2. `BackgroundThemeManager.kt` - 添加错误处理和日志
3. `BackgroundSettingsScreen.kt` - 确保预览组件正确应用效果
4. `AndroidManifest.xml` - 确保权限声明完整

## 联系支持

如果以上步骤都无法解决问题，请提供：
1. Logcat 完整日志
2. 复现步骤
3. 设备型号和 Android 版本
4. 使用的图片大小和格式
