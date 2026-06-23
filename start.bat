@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   Inspection Simulation - Launcher
echo ============================================
echo.
echo   [1] 一键启动（构建 + 全部模块）
echo   [2] 前端 View（连接远程服务器）
echo   [3] 仅后端（不含前端）
echo   [4] 全部模块独立窗口
echo   [5] 保留录制数据启动
echo.
echo   --- 独立模块 ---
echo   [C] Controller  节拍调度器
echo   [N] Navigator   路径规划器
echo   [A] Car         小车知识源
echo   [R] Recorder    录制回放器
echo   [F] Frontend    前端界面
echo   [I] Init        初始化
echo   [X] Clean       清理运行时
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
