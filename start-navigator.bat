@echo off
title Navigator
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%InspectionBackend"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Navigator - Path Planner
echo ============================================
echo.
echo   Log: logs\backend.log
echo.

set "JAR=%BACKEND%\navigator\target\navigator.jar"
if not exist "%JAR%" (
    echo [BUILD] Building...
    cd /d "%BACKEND%"
    call mvn package -DskipTests -pl navigator -am -q
    if errorlevel 1 (
        echo [ERROR] Build failed
        goto :end
    )
    echo [OK] Build complete
    echo.
)

echo [START] Starting Navigator...
java "-Dlog.dir=%LOG_DIR%" -jar "%JAR%"
echo.
echo [STOP] Navigator stopped

:end
pause
