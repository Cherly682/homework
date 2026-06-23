@echo off
title Controller
setlocal

set "LOG_DIR=%~dp0logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Controller - Tick Scheduler
echo ============================================
echo/
echo   Log: logs\backend.log
echo/

cd /d "%~dp0InspectionBackend\controller" 2>nul
if errorlevel 1 (
    echo [ERROR] Cannot access controller dir
    pause
    exit /b
)

if not exist target\controller.jar (
    echo [BUILD] Building...
    cd /d "%~dp0InspectionBackend"
    call mvn package -DskipTests -pl controller -am -q
    if errorlevel 1 (echo [ERROR] Build failed & pause & exit /b)
    cd /d "%~dp0InspectionBackend\controller"
    echo [OK] Build complete
    echo/
)

echo [START] Starting Controller...
java "-Dlog.dir=%LOG_DIR%" -jar target\controller.jar
echo/
echo [STOP] Controller stopped
pause
