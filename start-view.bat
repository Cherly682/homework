@echo off
title View Frontend Launcher

echo ============================================
echo   Inspection Simulation - View Frontend
echo ============================================
echo.

set "HOST=localhost"
set "REDIS_PASS="
set "RABBIT_USER=guest"
set "RABBIT_PASS=guest"

if not "%1"=="" set "HOST=%1"
if not "%4"=="" set "REDIS_PASS=%4"
if not "%2"=="" set "RABBIT_USER=%2"
if not "%3"=="" set "RABBIT_PASS=%3"

if "%1"=="" set /p HOST="Server IP [localhost]: "
if "%4"=="" set /p REDIS_PASS="Redis password (enter=none): "
if "%2"=="" set /p RABBIT_USER="RabbitMQ user [guest]: "
if "%3"=="" set /p RABBIT_PASS="RabbitMQ password [guest]: "

if "%HOST%"=="" set "HOST=localhost"
if "%RABBIT_USER%"=="" set "RABBIT_USER=guest"
if "%RABBIT_PASS%"=="" set "RABBIT_PASS=guest"

echo.
echo   Target: %HOST%
echo.

set "ROOT=%~dp0"
set "VIEW_DIR=%ROOT%View\CREAZYTHURSDAY\com.Manny"
set "LOG_DIR=%ROOT%logs"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

:: ---- Check Java ----
echo [CHECK] Java...
where java >nul 2>&1
if errorlevel 1 goto :nojava
echo [OK] Java found
goto :checkmvn

:nojava
echo [ERROR] Java not found. Install JDK 11+ and add to PATH.
goto :end

:: ---- Check Maven ----
:checkmvn
echo [CHECK] Maven...
where mvn >nul 2>&1
if errorlevel 1 goto :nomvn
echo [OK] Maven found
goto :checkdir

:nomvn
echo [ERROR] Maven not found. Install Maven 3.8+ and add to PATH.
goto :end

:: ---- Check project dir ----
:checkdir
if not exist "%VIEW_DIR%\pom.xml" goto :nodir
goto :build

:nodir
echo [ERROR] pom.xml not found at:
echo   %VIEW_DIR%
goto :end

:: ---- Build ----
:build
echo.
echo [CONFIG] REDIS_HOST=%HOST%  RABBITMQ_HOST=%HOST%
echo.

echo [BUILD] Compiling...
cd /d "%VIEW_DIR%"
call mvn clean compile -q
if errorlevel 1 goto :buildfail
echo [OK] Build complete
echo.
goto :launch

:buildfail
echo [ERROR] Build failed. Running with details:
call mvn clean compile
goto :end

:: ---- Launch ----
:launch
set "REDIS_HOST=%HOST%"
set "REDIS_PORT=6379"
set "REDIS_DATABASE=9"
set "REDIS_PASSWORD=%REDIS_PASS%"
set "RABBITMQ_HOST=%HOST%"
set "RABBITMQ_PORT=5672"
set "RABBITMQ_USERNAME=%RABBIT_USER%"
set "RABBITMQ_PASSWORD=%RABBIT_PASS%"
set "RABBITMQ_VHOST=/"

echo [LAUNCH] Starting View...
echo.
call mvn exec:java -Dexec.mainClass=Login.Main.Main "-Dlog.dir=%LOG_DIR%"

echo.
echo [DONE] View exited.

:end
pause
