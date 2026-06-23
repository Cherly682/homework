@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   Inspection Simulation - Launcher
echo ============================================
echo.
echo   [1] Full start (build + all services)
echo   [2] View frontend only (remote server)
echo   [3] Backend only (no frontend)
echo   [4] All modules separate windows
echo   [5] Full start, keep recording data
echo.
echo   --- Standalone Modules ---
echo   [C] Controller   [N] Navigator   [A] Car
echo   [R] Recorder     [F] Frontend
echo   [I] Init         [X] Clean runtime
echo.
set /p choice="Select option: "

if "%choice%"=="1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
)
if "%choice%"=="2" (
    call "%~dp0start-view.bat"
)
if "%choice%"=="3" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-backend.ps1"
)
if "%choice%"=="4" (
    call "%~dp0start-all.bat"
)
if "%choice%"=="5" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -SkipClean
)
if /i "%choice%"=="C" call "%~dp0start-controller.bat"
if /i "%choice%"=="N" call "%~dp0start-navigator.bat"
if /i "%choice%"=="A" call "%~dp0start-car.bat"
if /i "%choice%"=="R" call "%~dp0start-recorder.bat"
if /i "%choice%"=="F" call "%~dp0start-frontend.bat"
if /i "%choice%"=="I" call "%~dp0start-init.bat"
if /i "%choice%"=="X" call "%~dp0start-clean.bat"
pause
