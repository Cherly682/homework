@echo off
title Car - 小车知识源
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%InspectionBackend"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Car - 小车知识源
echo ============================================
echo.
echo   日志: logs\backend.log
echo   关闭此窗口即停止 Car
echo.

:: ---- Find JAR ----
set "JAR=%BACKEND%\car\target\car.jar"
if not exist "%JAR%" (
    echo [BUILD] JAR 不存在，正在构建...
    cd /d "%BACKEND%"
    call mvn package -DskipTests -pl car -am -q
    if errorlevel 1 (
        echo [ERROR] 构建失败
        pause
        exit /b 1
    )
    echo [OK] 构建完成
    echo.
)

echo [START] 启动 Car...
java "-Dlog.dir=%LOG_DIR%" -jar "%JAR%"

echo.
echo [STOP] Car 已退出
pause
