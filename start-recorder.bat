@echo off
title Recorder
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%InspectionBackend"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Recorder - Record/Playback
echo ============================================
echo.
echo   Log: logs\backend.log
echo.

set "JAR=%BACKEND%\recorder\target\recorder.jar"
if not exist "%JAR%" (
    echo [BUILD] Building...
    cd /d "%BACKEND%"
    call mvn package -DskipTests -pl recorder -am -q
    if errorlevel 1 (
        echo [ERROR] Build failed
        goto :end
    )
    echo [OK] Build complete
    echo.
)

echo [START] Starting Recorder...
java "-Dlog.dir=%LOG_DIR%" -jar "%JAR%"
echo.
echo [STOP] Recorder stopped

:end
pause
