@echo off
title View Frontend
setlocal

set "ROOT=%~dp0"
set "VIEW_DIR=%ROOT%View\CREAZYTHURSDAY\com.Manny"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   View - Frontend
echo ============================================
echo.
echo   Log: logs\frontend.log
echo.

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found (JDK 19+ required)
    goto :end
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven not found
    goto :end
)

if not exist "%VIEW_DIR%\pom.xml" (
    echo [ERROR] Project not found
    goto :end
)

echo [BUILD] Compiling...
cd /d "%VIEW_DIR%"
call mvn compile -q
if errorlevel 1 (
    echo [ERROR] Compile failed:
    call mvn compile
    goto :end
)
echo [OK] Compile complete
echo.

echo [START] Launching View...
echo   Login: config / config123 (Configurator)
echo          analyst / analyst123 (Analyst)
echo          admin / admin123 (Admin)
echo.
call mvn exec:java -Dexec.mainClass=Login.Main.Main "-Dlog.dir=%LOG_DIR%"
echo.
echo [STOP] View stopped

:end
pause
