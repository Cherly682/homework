@echo off
title All Modules
setlocal

set "ROOT=%~dp0"

echo ============================================
echo   Inspection Simulation - All Modules
echo ============================================
echo.
echo   6 independent terminal windows:
echo     1. Init         - users + queues
echo     2. Controller   - tick scheduler
echo     3. Navigator    - path planner
echo     4. Car          - car agent
echo     5. Recorder     - record/playback
echo     6. View         - frontend GUI
echo.
echo   Press any key to start...
pause >nul

echo [CLEAN] Killing stale Java processes...
taskkill /f /im java.exe >nul 2>&1
timeout /t 2 /nobreak >nul
echo [OK] Cleaned
echo.

echo [1/6] Init...
start "Init" cmd /k ""%~dp0start-init.bat""
timeout /t 3 /nobreak >nul

echo [2/6] Controller...
start "Controller" cmd /k ""%~dp0start-controller.bat""
timeout /t 2 /nobreak >nul

echo [3/6] Navigator...
start "Navigator" cmd /k ""%~dp0start-navigator.bat""
timeout /t 2 /nobreak >nul

echo [4/6] Car...
start "Car" cmd /k ""%~dp0start-car.bat""
timeout /t 2 /nobreak >nul

echo [5/6] Recorder...
start "Recorder" cmd /k ""%~dp0start-recorder.bat""
timeout /t 2 /nobreak >nul

echo [6/6] View...
start "View" cmd /k ""%~dp0start-frontend.bat""

echo.
echo ============================================
echo   [OK] All modules launched
echo ============================================
echo.
echo   Login: config / config123
echo          analyst / analyst123
echo          admin / admin123
echo.
echo   Close each window to stop that module.
echo   This window can be closed safely.
echo ============================================
pause
