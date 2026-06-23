@echo off
title View - 前端界面
setlocal

set "ROOT=%~dp0"
set "VIEW_DIR=%ROOT%View\CREAZYTHURSDAY\com.Manny"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   View - 前端界面
echo ============================================
echo.
echo   日志: logs\frontend.log
echo   关闭此窗口即退出程序
echo.

:: ---- Check Java ----
where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未找到 Java，请安装 JDK 19+
    pause
    exit /b 1
)

:: ---- Check Maven ----
where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未找到 Maven
    pause
    exit /b 1
)

:: ---- Check project ----
if not exist "%VIEW_DIR%\pom.xml" (
    echo [ERROR] 项目未找到: %VIEW_DIR%
    pause
    exit /b 1
)

echo [BUILD] 编译前端...
cd /d "%VIEW_DIR%"
call mvn compile -q
if errorlevel 1 (
    echo [ERROR] 编译失败，重试详细信息:
    call mvn compile
    pause
    exit /b 1
)
echo [OK] 编译完成
echo.

echo [START] 启动前端...
echo   登录: config / config123 (配置员^)
echo         analyst / analyst123 (分析员^)
echo         admin / admin123 (管理员^)
echo.
call mvn exec:java -Dexec.mainClass=Login.Main.Main "-Dlog.dir=%LOG_DIR%"

echo.
echo [STOP] 前端已退出
pause
