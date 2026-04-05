#!/bin/bash

echo "========================================="
echo "自定义背景功能修复测试脚本"
echo "========================================="
echo ""

# 检查 ADB 连接
echo "1. 检查 ADB 连接..."
adb devices
if [ $? -ne 0 ]; then
    echo "❌ ADB 未连接，请连接设备后重试"
    exit 1
fi
echo "✅ ADB 连接正常"
echo ""

# 卸载旧版本
echo "2. 卸载旧版本应用..."
adb uninstall com.wind.ggbond.classtime
echo "✅ 卸载完成"
echo ""

# 编译并安装新版本
echo "3. 编译并安装新版本..."
./gradlew installDebug
if [ $? -ne 0 ]; then
    echo "❌ 编译失败，请检查错误信息"
    exit 1
fi
echo "✅ 安装完成"
echo ""

# 启动应用
echo "4. 启动应用..."
adb shell am start -n com.wind.ggbond.classtime/.MainActivity
echo "✅ 应用已启动"
echo ""

# 开始监听日志
echo "5. 开始监听日志（按 Ctrl+C 停止）..."
echo "   请在应用中测试以下功能："
echo "   - 导入背景图片"
echo "   - 点击'开启莫奈课程取色'"
echo "   - 调节模糊程度滑块"
echo "   - 调节暗化程度滑块"
echo "   - 返回主界面查看背景"
echo ""
echo "========================================="
echo "日志输出："
echo "========================================="
adb logcat | grep -E "(BackgroundThemeManager|BackgroundSettings|BackgroundSettingsViewModel)"
