@echo off
title Clean
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%InspectionBackend"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   Tools - Clean Runtime
echo ============================================
echo.
echo   Clears: history, cars, map data
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

echo [RUN] clean-runtime...
java "-Dlog.dir=%LOG_DIR%" -jar "%JAR%" clean-runtime
if errorlevel 1 (
    echo [ERROR] Clean failed
    goto :end
)
echo [OK] Clean complete

:end
pause
