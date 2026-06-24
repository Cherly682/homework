<#
.SYNOPSIS
    变电站巡检仿真系统 - 交互式菜单
    由 start.bat 调用，避免 CMD 编码问题。
    支持多选：逗号分隔，如 "3,A" 同时启动前端和 Car。
    启动组件前自动询问 Redis/RabbitMQ 连接地址。
#>
param()

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Host.UI.RawUI.WindowTitle = "Inspection Simulation"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$LogDir = Join-Path $ScriptDir "logs"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  变电站巡检仿真系统" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  脚本目录: $ScriptDir" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  请选择操作（可多选，逗号分隔）：" -ForegroundColor White
Write-Host ""
Write-Host "  --- 预设（含编译 + 初始化，一键启动）---" -ForegroundColor DarkGray
Write-Host "  [1] 一键启动全部（后端 + 前端）"
Write-Host "  [2] 一键启动全部后端"
Write-Host ""
Write-Host "  --- 独立组件 ---" -ForegroundColor DarkGray
Write-Host "  [3] 前端 View (GUI)"
Write-Host "  [C] Controller    [N] Navigator    [A] Car    [R] Recorder"
Write-Host ""
Write-Host "  --- 编译与工具 ---" -ForegroundColor DarkGray
Write-Host "  [4] 编译全部      [5] 初始化        [6] 清理运行时数据"
Write-Host ""
Write-Host "  示例: 3,A    → 启动前端 + Car" -ForegroundColor DarkGray
Write-Host "        C,N,A,R → 启动全部后端组件" -ForegroundColor DarkGray
Write-Host "        4,5,C   → 编译 + 初始化 + 启动 Controller" -ForegroundColor DarkGray
Write-Host ""

$choice = Read-Host "选择"

# ---- 解析多选 ----
$rawChoices = $choice -split '[,，\s]+' | Where-Object { $_ -ne '' } | Select-Object -Unique

if ($rawChoices.Count -eq 0) {
    Write-Host "未输入任何选择" -ForegroundColor Red
    exit 0
}

# ---- 分类 ----
$presets = @()        # 1, 2（一键预设，自带编译+初始化）
$frontend = $false    # 3（前端，需前台运行）
$backends = @()       # C, N, A, R（后端组件，各自独立终端）
$tools = @()          # 4, 5, 6（编译/初始化/清理）

foreach ($c in $rawChoices) {
    switch -CaseSensitive ($c) {
        '1' { $presets += '1' }
        '2' { $presets += '2' }
        '3' { $frontend = $true }
        'C' { $backends += 'C' }
        'N' { $backends += 'N' }
        'A' { $backends += 'A' }
        'R' { $backends += 'R' }
        '4' { $tools += '4' }
        '5' { $tools += '5' }
        '6' { $tools += '6' }
        default {
            Write-Host "无效选项（已忽略）: $c" -ForegroundColor Yellow
        }
    }
}

# ---- 连接配置（启动组件前询问 Redis/RabbitMQ 地址）----
$hasComponent = $presets.Count -gt 0 -or $frontend -or $backends.Count -gt 0
if ($hasComponent) {
    Write-Host ""
    $configScript = Join-Path $ScriptDir "config.ps1"
    & powershell -NoProfile -ExecutionPolicy Bypass -File $configScript
}

# ---- 预设优先：如果选了 1 或 2，直接执行预设脚本 ----
if ($presets.Count -gt 0) {
    if ($frontend -or $backends.Count -gt 0 -or $tools.Count -gt 0) {
        Write-Host "[INFO] 检测到预设选项 [$($presets -join ',')]，将忽略其他单独选择" -ForegroundColor Yellow
    }
    $presetScript = if ($presets -contains '1') { "run-all.ps1" } else { "run-backend.ps1" }
    $scriptPath = Join-Path $ScriptDir $presetScript
    Write-Host "[RUN] 执行 $presetScript ..." -ForegroundColor Green
    & powershell -NoProfile -ExecutionPolicy Bypass -File $scriptPath
    exit 0
}

$hasAnyComponent = $frontend -or ($backends.Count -gt 0)

# ---- 执行工具 ----
$toolScripts = @{
    '4' = @{ Script = "build-all.ps1"; Desc = "编译全部" }
    '5' = @{ Script = "run-tools.ps1"; Desc = "初始化"; Args = @("init") }
    '6' = @{ Script = "run-tools.ps1"; Desc = "清理运行时数据"; Args = @("clean-runtime") }
}

foreach ($t in $tools) {
    $info = $toolScripts[$t]
    Write-Host "[RUN] $($info.Desc)..." -ForegroundColor Green
    $sp = Join-Path $ScriptDir $info.Script
    if ($info.Args) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $sp -Action $info.Args[0]
    } else {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $sp
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] $($info.Desc) 失败" -ForegroundColor Red
        if (-not $hasAnyComponent) { Pause; exit 1 }
        Write-Host "[WARN] 继续启动组件..." -ForegroundColor Yellow
    }
}

if (-not $hasAnyComponent) {
    Write-Host "[OK] 工具执行完毕" -ForegroundColor Green
    Pause
    exit 0
}

# ---- 启动后端组件（各自独立终端窗口）----
$backendInfo = @{
    'C' = @{ Script = "run-controller.ps1"; Title = "Controller - 节拍调度器" }
    'N' = @{ Script = "run-navigator.ps1";  Title = "Navigator - 路径规划器" }
    'A' = @{ Script = "run-car.ps1";        Title = "Car - 小车知识源" }
    'R' = @{ Script = "run-recorder.ps1";   Title = "Recorder - 录制/回放" }
}

if ($backends.Count -gt 0) {
    Write-Host ""
    Write-Host "[LAUNCH] 启动后端组件（独立终端窗口）..." -ForegroundColor Cyan
    Write-Host "  日志目录: $LogDir" -ForegroundColor DarkGray
    Write-Host ""

    foreach ($b in $backends) {
        $info = $backendInfo[$b]
        $runScript = Join-Path $ScriptDir $info.Script
        Start-Process pwsh -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$runScript`"" -WindowStyle Normal
        Write-Host "  [OK] $($info.Title)" -ForegroundColor Green
        Start-Sleep -Milliseconds 500
    }
}

# ---- 启动前端（前台运行，最后启动）----
if ($frontend) {
    if ($backends.Count -gt 0) {
        Write-Host ""
        Write-Host "[INFO] 后端已启动，正在启动前端..." -ForegroundColor Cyan
        Start-Sleep -Seconds 1
    }

    Write-Host ""
    Write-Host "[FRONTEND] 启动 View 前端..." -ForegroundColor Cyan
    Write-Host "  凭据: config/config123  admin/admin123  analyst/analyst123" -ForegroundColor White
    Write-Host "  关闭前端窗口即停止全部进程" -ForegroundColor DarkGray
    Write-Host ""

    $frontendScript = Join-Path $ScriptDir "run-frontend.ps1"
    & powershell -NoProfile -ExecutionPolicy Bypass -File $frontendScript

    Write-Host ""
    Write-Host "[INFO] 前端已退出，停止后端进程..." -ForegroundColor Cyan
    taskkill /f /im java.exe 2>$null
    Write-Host "[OK] 全部进程已停止" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "[OK] 全部后端组件已启动" -ForegroundColor Green
    Write-Host ""
    Write-Host "  关闭各终端窗口即停止对应模块" -ForegroundColor DarkGray
    Write-Host "  按任意键关闭本菜单（不影响已启动的模块）..." -ForegroundColor DarkGray
    Pause
}
