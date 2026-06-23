@echo off
title Tools - 清理运行时数据
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%InspectionBackend"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Tools - 清理运行时数据
echo ============================================
echo.
echo   将清除: 历史录制、小车状态、地图数据
echo.

:: ---- Find JAR ----
set "JAR=%BACKEND%\tools\target\tools.jar"
if not exist "%JAR%" (
    echo [BUILD] JAR 不存在，正在构建...
    cd /d "%BACKEND%"
    call mvn package -DskipTests -pl tools -am -q
    if errorlevel 1 (
        echo [ERROR] 构建失败
        pause
        exit /b 1
    )
    echo [OK] 构建完成
    echo.
)

echo [RUN] 执行 clean-runtime...
java "-Dlog.dir=%LOG_DIR%" -jar "%JAR%" clean-runtime
if errorlevel 1 (
    echo [ERROR] 清理失败
    pause
    exit /b 1
)
echo [OK] 运行时数据已清理
echo.
pause
