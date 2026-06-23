@echo off
title Controller - 节拍调度器
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%InspectionBackend"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Controller - 节拍调度器（单实例）
echo ============================================
echo.
echo   日志: logs\backend.log
echo   关闭此窗口即停止 Controller
echo.

:: ---- Find JAR ----
set "JAR=%BACKEND%\controller\target\controller.jar"
if not exist "%JAR%" (
    echo [BUILD] JAR 不存在，正在构建...
    cd /d "%BACKEND%"
    call mvn package -DskipTests -pl controller -am -q
    if errorlevel 1 (
        echo [ERROR] 构建失败
        pause
        exit /b 1
    )
    echo [OK] 构建完成
    echo.
)

echo [START] 启动 Controller...
java "-Dlog.dir=%LOG_DIR%" -jar "%JAR%"

echo.
echo [STOP] Controller 已退出
pause
