@echo off
echo =========================================
echo 自定义背景功能修复测试脚本 v2.0
echo 使用本地文件存储方案
echo =========================================
echo.

REM 检查 ADB 连接
echo 1. 检查 ADB 连接...
adb devices
if %errorlevel% neq 0 (
    echo ❌ ADB 未连接，请连接设备后重试
    exit /b 1
)
echo ✅ ADB 连接正常
echo.

REM 卸载旧版本
echo 2. 卸载旧版本应用（清除旧数据）...
adb uninstall com.wind.ggbond.classtime
echo ✅ 卸载完成
echo.

REM 编译并安装新版本
echo 3. 编译并安装新版本...
call gradlew.bat installDebug
if %errorlevel% neq 0 (
    echo ❌ 编译失败，请检查错误信息
    exit /b 1
)
echo ✅ 安装完成
echo.

REM 启动应用
echo 4. 启动应用...
adb shell am start -n com.wind.ggbond.classtime/.MainActivity
echo ✅ 应用已启动
echo.

REM 开始监听日志
echo 5. 开始监听日志（按 Ctrl+C 停止）...
echo.
echo =========================================
echo 测试步骤：
echo =========================================
echo 1. 导入一张图片作为背景
echo 2. 观察日志中的 "Copying media from URI"
echo 3. 观察日志中的 "Media copied successfully"
echo 4. 点击"开启莫奈课程取色"（应该不会闪退）
echo 5. 调节模糊和暗化滑块
echo 6. 返回主界面查看背景
echo 7. 在文件管理器中删除原图片
echo 8. 重启应用，背景应该仍然存在 ✅
echo.
echo =========================================
echo 日志输出：
echo =========================================
adb logcat | findstr /C:"MediaFileManager" /C:"BackgroundThemeManager" /C:"BackgroundSettings"
