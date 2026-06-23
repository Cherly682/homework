@echo off
title 全部模块启动器
setlocal enabledelayedexpansion

set "ROOT=%~dp0"

echo ============================================
echo   变电站巡检仿真 - 全部模块独立启动
echo ============================================
echo.
echo   将在 6 个独立终端窗口启动:
echo     1. init         - 初始化（用户+队列）
echo     2. Controller   - 节拍调度器
echo     3. Navigator    - 路径规划器
echo     4. Car          - 小车知识源
echo     5. Recorder     - 录制回放器
echo     6. View         - 前端界面
echo.
echo   各窗口可独立关闭，互不影响。
echo.
echo   按任意键开始...
pause >nul

:: ---- Step 0: 清理残留进程 ----
echo [CLEAN] 清理残留 Java 进程...
taskkill /f /im java.exe >nul 2>&1
timeout /t 2 /nobreak >nul
echo [OK] 已清理
echo.

:: ---- Step 1: Init ----
echo [1/6] 初始化...
start "Init - 初始化" cmd /k ""%~dp0start-init.bat""
timeout /t 3 /nobreak >nul

:: ---- Step 2-5: Backend modules ----
echo [2/6] 启动 Controller...
start "Controller - 节拍调度器" cmd /k ""%~dp0start-controller.bat""
timeout /t 2 /nobreak >nul

echo [3/6] 启动 Navigator...
start "Navigator - 路径规划器" cmd /k ""%~dp0start-navigator.bat""
timeout /t 2 /nobreak >nul

echo [4/6] 启动 Car...
start "Car - 小车知识源" cmd /k ""%~dp0start-car.bat""
timeout /t 2 /nobreak >nul

echo [5/6] 启动 Recorder...
start "Recorder - 录制回放器" cmd /k ""%~dp0start-recorder.bat""
timeout /t 2 /nobreak >nul

:: ---- Step 6: Frontend ----
echo [6/6] 启动前端...
start "View - 前端界面" cmd /k ""%~dp0start-frontend.bat""

echo.
echo ============================================
echo   [OK] 全部模块已启动
echo ============================================
echo.
echo   登录: config / config123 (配置员)
echo         analyst / analyst123 (分析员)
echo         admin / admin123 (管理员)
echo.
echo   关闭前端窗口不会自动停止后端。
echo   需要停止时请逐个关闭各终端窗口。
echo.
echo   本窗口可安全关闭。
echo ============================================
pause
