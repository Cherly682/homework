@echo off
title Clean
setlocal

set "LOG_DIR=%~dp0logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Tools - Clean Runtime
echo ============================================
echo/
echo   Clears: history, cars, map data
echo/

cd /d "%~dp0InspectionBackend\tools" 2>nul
if errorlevel 1 (
    echo [ERROR] Cannot access tools dir
    pause
    exit /b
)

if not exist target\tools.jar (
    echo [BUILD] Building...
    cd /d "%~dp0InspectionBackend"
    call mvn package -DskipTests -pl tools -am -q
    if errorlevel 1 (echo [ERROR] Build failed & pause & exit /b)
    cd /d "%~dp0InspectionBackend\tools"
    echo [OK] Build complete
    echo/
)

echo [RUN] clean-runtime...
java "-Dlog.dir=%LOG_DIR%" -jar target\tools.jar clean-runtime
if errorlevel 1 (echo [ERROR] Clean failed & pause & exit /b)
echo [OK] Clean complete
pause
