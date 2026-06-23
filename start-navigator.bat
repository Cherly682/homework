@echo off
title Navigator - 路径规划器
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%InspectionBackend"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Navigator - 路径规划器（8 worker）
echo ============================================
echo.
echo   日志: logs\backend.log
echo   关闭此窗口即停止 Navigator
echo.

:: ---- Find JAR ----
set "JAR=%BACKEND%\navigator\target\navigator.jar"
if not exist "%JAR%" (
    echo [BUILD] JAR 不存在，正在构建...
    cd /d "%BACKEND%"
    call mvn package -DskipTests -pl navigator -am -q
    if errorlevel 1 (
        echo [ERROR] 构建失败
        pause
        exit /b 1
    )
    echo [OK] 构建完成
    echo.
)

echo [START] 启动 Navigator...
java "-Dlog.dir=%LOG_DIR%" -jar "%JAR%"

echo.
echo [STOP] Navigator 已退出
pause
