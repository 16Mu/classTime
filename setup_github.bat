@echo off
chcp 65001 >nul
echo ========================================
echo 时课项目 GitHub 仓库创建脚本
echo ========================================
echo.

REM 检查是否在正确的目录
if not exist "app\build.gradle.kts" (
    错误：请在项目根目录运行此脚本
    pause
    exit /b 1
)

echo 准备步骤...
echo.

REM 备份原文件
if exist "README.md" (
    echo 备份原 README.md...
    move "README.md" "README_OLD.md" >nul
)

if exist ".gitignore" (
    echo 备份原 .gitignore...
    move ".gitignore" ".gitignore_OLD" >nul
)

REM 使用新文件
if exist "README_NEW.md" (
    echo 使用新的 README.md...
    move "README_NEW.md" "README.md" >nul
)

if exist ".gitignore_new" (
    echo 使用新的 .gitignore...
    move ".gitignore_new" ".gitignore" >nul
)

echo.
echo 清理临时文件...

REM 删除临时文件
if exist "*.log" del /Q *.log >nul 2>&1
if exist "build_log.txt" del /Q build_log.txt >nul 2>&1
if exist "hs_err_pid*" del /Q hs_err_pid* >nul 2>&1

REM 删除构建产物
if exist "build" rmdir /S /Q build >nul 2>&1
if exist "app\build" rmdir /S /Q app\build >nul 2>&1
if exist ".gradle" rmdir /S /Q .gradle >nul 2>&1

echo.
echo 文件准备完成！
echo.

REM 询问仓库地址
set /p repo_url="请输入你的 GitHub 仓库地址 (例如: https://github.com/username/course-schedule.git): "

if "%repo_url%"=="" (
    错误：仓库地址不能为空
    pause
    exit /b 1
)

echo.
echo 初始化 Git 仓库...

REM 初始化 Git
git init >nul 2>&1

REM 添加远程仓库
git remote add origin %repo_url% >nul 2>&1

if %errorlevel% neq 0 (
    错误：无法添加远程仓库，请检查地址是否正确
    pause
    exit /b 1
)

echo.
echo 添加文件到 Git...

REM 添加所有文件
git add . >nul 2>&1

echo.
echo 创建首次提交...

REM 提交文件
git commit -m "Initial commit: 智能校园课表管理应用

主要功能：
- 27 所学校智能课表导入
- Jetpack Compose 现代化界面
- 精准课程提醒系统
- 桌面小组件支持
- 自动更新功能
- 多格式数据导出

技术栈：
- Kotlin 2.1.0 + Jetpack Compose
- MVVM + Repository 架构
- Room + Hilt + Coroutines
- OkHttp + Jsoup + WebView

支持 Android 8.0+ (API 26+)" >nul 2>&1

if %errorlevel% neq 0 (
    错误：提交失败，请检查 Git 配置
    echo 请运行以下命令配置 Git：
    echo    git config --global user.name "你的用户名"
    echo    git config --global user.email "你的邮箱"
    pause
    exit /b 1
)

echo.
echo 推送到 GitHub...

REM 推送到远程仓库
git branch -M main >nul 2>&1
git push -u origin main >nul 2>&1

if %errorlevel% neq 0 (
    错误：推送失败
    echo 可能的原因：
    echo    1. 仓库地址错误
    echo    2. 没有推送权限
    echo    3. 需要身份验证
    echo.
    echo 请手动运行以下命令：
    echo    git push -u origin main
    pause
    exit /b 1
)

echo.
echo ========================================
echo 成功！项目已上传到 GitHub
echo ========================================
echo.
echo 仓库地址: %repo_url%
echo.
echo 接下来的步骤：
echo 1. 访问你的 GitHub 仓库页面
echo 2. 检查文件是否正确上传
echo 3. 设置仓库描述和标签
echo 4. 在仓库 Settings 中启用 GitHub Pages (可选)
echo.
echo 建议添加的标签：
echo    android, kotlin, jetpack-compose, course-schedule
echo    mobile-app, material-design, mvvm-architecture
echo.
echo 贡献指南：查看 CONTRIBUTING.md 文件
echo 使用文档：查看 README.md 文件
echo.
echo 感谢使用时课项目！
echo.

pause
