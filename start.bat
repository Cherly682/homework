@echo off
setlocal

cd /d "%~dp0"

echo ============================================
echo   Inspection Simulation - Launcher
echo ============================================
echo/
echo   [1] Full auto (build + init + all)
echo   [2] Full auto, keep recording data
echo   [3] Backend only (no frontend)
echo   [4] All modules - separate windows
echo   [5] View frontend (local/remote)
echo/
echo   --- Single Module ---
echo   [C] Controller  [N] Navigator  [A] Car
echo   [R] Recorder    [F] Frontend
echo   [I] Init        [X] Clean runtime
echo/
set /p choice="Select option: "

if "%choice%"=="1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
    goto :end
)
if "%choice%"=="2" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -SkipClean
    goto :end
)
if "%choice%"=="3" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-backend.ps1"
    goto :end
)
if "%choice%"=="4" goto :launch_all
if "%choice%"=="5" call "%~dp0start-frontend.bat" & goto :end
if /i "%choice%"=="C" call "%~dp0start-controller.bat" & goto :end
if /i "%choice%"=="N" call "%~dp0start-navigator.bat"  & goto :end
if /i "%choice%"=="A" call "%~dp0start-car.bat"        & goto :end
if /i "%choice%"=="R" call "%~dp0start-recorder.bat"   & goto :end
if /i "%choice%"=="F" call "%~dp0start-frontend.bat"   & goto :end
if /i "%choice%"=="I" call "%~dp0start-init.bat"       & goto :end
if /i "%choice%"=="X" call "%~dp0start-clean.bat"      & goto :end
goto :end

:launch_all
echo/
echo   Opening 6 independent terminal windows...
echo   Close each window to stop that module.
echo/

echo [CLEAN] Killing stale Java processes...
taskkill /f /im java.exe >nul 2>&1
timeout /t 2 /nobreak >nul

echo [1/6] Init...
start "Init" cmd /k "%~dp0start-init.bat"
timeout /t 3 /nobreak >nul

echo [2/6] Controller...
start "Controller" cmd /k "%~dp0start-controller.bat"
timeout /t 2 /nobreak >nul

echo [3/6] Navigator...
start "Navigator" cmd /k "%~dp0start-navigator.bat"
timeout /t 2 /nobreak >nul

echo [4/6] Car...
start "Car" cmd /k "%~dp0start-car.bat"
timeout /t 2 /nobreak >nul

echo [5/6] Recorder...
start "Recorder" cmd /k "%~dp0start-recorder.bat"
timeout /t 2 /nobreak >nul

echo [6/6] Frontend...
start "View" cmd /k "%~dp0start-frontend.bat"

echo/
echo   [OK] All 6 modules launched.
echo   Login: config / config123
echo/

:end
pause
