@echo off
title Tools - 初始化
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%InspectionBackend"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Tools - 初始化（用户 + 消息队列）
echo ============================================
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

echo [RUN] 执行 init...
java "-Dlog.dir=%LOG_DIR%" -jar "%JAR%" init
if errorlevel 1 (
    echo [ERROR] 初始化失败
    pause
    exit /b 1
)
echo [OK] 初始化完成（用户 + 消息队列已就绪）
echo.
pause
