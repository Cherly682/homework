@echo off
title Init
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%InspectionBackend"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Tools - Init (users + queues)
echo ============================================
echo.

set "JAR=%BACKEND%\tools\target\tools.jar"
if not exist "%JAR%" (
    echo [BUILD] Building...
    cd /d "%BACKEND%"
    call mvn package -DskipTests -pl tools -am -q
    if errorlevel 1 (
        echo [ERROR] Build failed
        goto :end
    )
    echo [OK] Build complete
    echo.
)

echo [RUN] init...
java "-Dlog.dir=%LOG_DIR%" -jar "%JAR%" init
if errorlevel 1 (
    echo [ERROR] Init failed
    goto :end
)
echo [OK] Init complete

:end
pause
