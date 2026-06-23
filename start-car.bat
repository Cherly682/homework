@echo off
title Car
setlocal

set "LOG_DIR=%~dp0logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Car - Agent
echo ============================================
echo/
echo   Log: logs\backend.log
echo/

cd /d "%~dp0InspectionBackend\car" 2>nul
if errorlevel 1 (
    echo [ERROR] Cannot access car dir
    pause
    exit /b
)

if not exist target\car.jar (
    echo [BUILD] Building...
    cd /d "%~dp0InspectionBackend"
    call mvn package -DskipTests -pl car -am -q
    if errorlevel 1 (echo [ERROR] Build failed & pause & exit /b)
    cd /d "%~dp0InspectionBackend\car"
    echo [OK] Build complete
    echo/
)

echo [START] Starting Car...
java "-Dlog.dir=%LOG_DIR%" -jar target\car.jar
echo/
echo [STOP] Car stopped
pause
