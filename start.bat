@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   Inspection Simulation - Launcher
echo ============================================
echo.
echo   [1] Full start (build + init + all services)
echo   [2] View frontend only (remote server)
echo   [3] Backend only (no frontend)
echo   [4] Backend components separately
echo   [5] Full start, keep previous recording data
echo.
set /p choice="Select option (1-5): "

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
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-standalone.ps1"
)
if "%choice%"=="5" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -SkipClean
)
pause
