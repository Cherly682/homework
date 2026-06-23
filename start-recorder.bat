@echo off
title Recorder
setlocal

set "LOG_DIR=%~dp0logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Recorder - Record/Playback
echo ============================================
echo/
echo   Log: logs\backend.log
echo/

cd /d "%~dp0InspectionBackend\recorder" 2>nul
if errorlevel 1 (
    echo [ERROR] Cannot access recorder dir
    pause
    exit /b
)

if not exist target\recorder.jar (
    echo [BUILD] Building...
    cd /d "%~dp0InspectionBackend"
    call mvn package -DskipTests -pl recorder -am -q
    if errorlevel 1 (echo [ERROR] Build failed & pause & exit /b)
    cd /d "%~dp0InspectionBackend\recorder"
    echo [OK] Build complete
    echo/
)

echo [START] Starting Recorder...
java "-Dlog.dir=%LOG_DIR%" -jar target\recorder.jar
echo/
echo [STOP] Recorder stopped
pause
