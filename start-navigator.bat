@echo off
title Navigator
setlocal

set "LOG_DIR=%~dp0logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Navigator - Path Planner
echo ============================================
echo/
echo   Log: logs\backend.log
echo/

cd /d "%~dp0InspectionBackend\navigator" 2>nul
if errorlevel 1 (
    echo [ERROR] Cannot access navigator dir
    pause
    exit /b
)

if not exist target\navigator.jar (
    echo [BUILD] Building...
    cd /d "%~dp0InspectionBackend"
    call mvn package -DskipTests -pl navigator -am -q
    if errorlevel 1 (echo [ERROR] Build failed & pause & exit /b)
    cd /d "%~dp0InspectionBackend\navigator"
    echo [OK] Build complete
    echo/
)

echo [START] Starting Navigator...
java "-Dlog.dir=%LOG_DIR%" -jar target\navigator.jar
echo/
echo [STOP] Navigator stopped
pause
