@echo off
title View Frontend
setlocal

set "LOG_DIR=%~dp0logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   View - Frontend
echo ============================================
echo/

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found - install JDK 19+
    pause
    exit /b
)
echo [OK] Java found

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven not found
    pause
    exit /b
)
echo [OK] Maven found

cd /d "%~dp0View\CREAZYTHURSDAY\com.Manny" 2>nul
if errorlevel 1 (
    echo [ERROR] Cannot access View project dir
    pause
    exit /b
)
if not exist pom.xml (
    echo [ERROR] pom.xml not found
    pause
    exit /b
)
echo [OK] Project dir

echo/
echo   [L] Local  (localhost^)
echo   [R] Remote (other server^)
echo/
set /p mode="Select [L/R]: "
if /i "%mode%"=="R" goto :remote

:local
set "REDIS_HOST=localhost"
set "REDIS_PORT=6379"
set "REDIS_PASSWORD="
set "RABBITMQ_HOST=localhost"
set "RABBITMQ_PORT=5672"
set "RABBITMQ_USERNAME=guest"
set "RABBITMQ_PASSWORD=guest"
echo   Mode: Local
goto :build

:remote
echo/
set /p REDIS_HOST="Server IP: "
if "%REDIS_HOST%"=="" set "REDIS_HOST=localhost"
set /p REDIS_PASSWORD="Redis password (enter=none): "
set /p RABBITMQ_USERNAME="RabbitMQ user [guest]: "
if "%RABBITMQ_USERNAME%"=="" set "RABBITMQ_USERNAME=guest"
set /p RABBITMQ_PASSWORD="RabbitMQ password [guest]: "
if "%RABBITMQ_PASSWORD%"=="" set "RABBITMQ_PASSWORD=guest"
set "RABBITMQ_HOST=%REDIS_HOST%"
set "REDIS_PORT=6379"
set "RABBITMQ_PORT=5672"
echo   Mode: Remote -^> %REDIS_HOST%

:build
set "REDIS_DATABASE=9"
set "RABBITMQ_VHOST=/"

if not exist target\com.Manny-1.0-SNAPSHOT.jar (
    echo/
    echo [BUILD] Building fat JAR...
    call mvn package -DskipTests -q
    if errorlevel 1 (
        echo [ERROR] Build failed - retry with details:
        call mvn package -DskipTests
        pause
        exit /b
    )
    echo [OK] Build complete
) else (
    echo [BUILD] JAR exists, skip build
)

echo/
echo [START] Launching View...
echo   Log:   %LOG_DIR%\frontend.log
echo   Redis: %REDIS_HOST%:%REDIS_PORT%
echo   MQ:    %RABBITMQ_HOST%:%RABBITMQ_PORT%
echo/
echo   Login: config / config123 (Configurator)
echo          analyst / analyst123 (Analyst)
echo          admin / admin123 (Admin)
echo/

java "-Dlog.dir=%LOG_DIR%" -jar target\com.Manny-1.0-SNAPSHOT.jar
echo/
echo [STOP] View exited
pause
