<# .SYNOPSIS
   变电站巡检仿真系统 - 仅启动后端（不含前端 View）
   使用场景：仅需要巡检服务运行，其他主机通过 View 远程连接观察。

   用法:
     .\start-backend.ps1
     .\start-backend.ps1 -SkipClean   # 保留上次录制数据
#>

param([switch]$SkipClean)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $Root "InspectionBackend"
$LogDir = Join-Path $Root "logs"

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  变电站巡检仿真系统 - 后端启动" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ---- Check Java ----
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    Write-Host "[ERROR] 未找到 java。" -ForegroundColor Red
    Pause; exit 1
}
Write-Host "[OK] Java 就绪" -ForegroundColor Green

# ---- Check Maven ----
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Host "[ERROR] 未找到 mvn。" -ForegroundColor Red
    Pause; exit 1
}
Write-Host "[OK] Maven 就绪" -ForegroundColor Green

# ---- Check Redis ----
$redisOk = try { (redis-cli ping 2>&1) -eq "PONG" } catch { $false }
if ($redisOk) {
    Write-Host "[OK] Redis 就绪 (localhost:6379)" -ForegroundColor Green
} else {
    Write-Host "[WARN] Redis 未响应。" -ForegroundColor Yellow
}

# ---- Check RabbitMQ ----
$mqOk = try { $null = rabbitmqctl status 2>&1; $true } catch { $false }
if ($mqOk) {
    Write-Host "[OK] RabbitMQ 就绪 (localhost:5672)" -ForegroundColor Green
} else {
    Write-Host "[WARN] RabbitMQ 未响应。" -ForegroundColor Yellow
}

Write-Host ""

# Kill stale Java processes
$staleJava = Get-Process java -ErrorAction SilentlyContinue
if ($staleJava) {
    Write-Host "[CLEAN] 停止残留 Java 进程..." -ForegroundColor Yellow
    taskkill /f /im java.exe 2>$null
    Start-Sleep -Seconds 2
    Write-Host "[OK] 残留进程已清理" -ForegroundColor Green
    Write-Host ""
}

# ---- Build backend ----
Write-Host "[BUILD] 构建后端..." -ForegroundColor Cyan
Push-Location $BackendDir
try {
    mvn clean package -DskipTests -q 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 后端构建失败。" -ForegroundColor Red
        Pop-Location; Pause; exit 1
    }
    Write-Host "[OK] 后端构建完成" -ForegroundColor Green
} finally {
    Pop-Location
}

# ---- Init ----
Write-Host "[INIT] 初始化..." -ForegroundColor Cyan
Push-Location $BackendDir
try {
    $toolsJar = Join-Path $BackendDir "tools\target\tools.jar"
    & java "-Dlog.dir=$LogDir" -jar $toolsJar init 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 初始化失败。" -ForegroundColor Red
        Pop-Location; Pause; exit 1
    }
    if (-not $SkipClean) {
        & java "-Dlog.dir=$LogDir" -jar $toolsJar clean-runtime 2>&1
        Write-Host "[OK] 初始化完成（运行时数据已清理）" -ForegroundColor Green
    } else {
        Write-Host "[OK] 初始化完成（保留上次录制数据）" -ForegroundColor Green
    }
} finally {
    Pop-Location
}

# ---- Launch backend ----
Write-Host "[LAUNCH] 启动后端组件..." -ForegroundColor Cyan
Write-Host ""
Write-Host "  Controller  -> 节拍调度器（单实例）" -ForegroundColor DarkGray
Write-Host "  Navigator   -> 路径规划器（8 worker）" -ForegroundColor DarkGray
Write-Host "  Car         -> 小车知识源" -ForegroundColor DarkGray
Write-Host "  Recorder    -> 录制回放器" -ForegroundColor DarkGray
Write-Host ""

$launcherJar = Join-Path $BackendDir "launcher\target\launcher.jar"
$backendJob = Start-Job -Name "backend" -ScriptBlock {
    param($jarPath, $logDirArg, $homeDir)
    & java "-Dlog.dir=$logDirArg" -jar $jarPath $homeDir 2>&1 | Out-Null
} -ArgumentList $launcherJar, $LogDir, $BackendDir

Start-Sleep -Seconds 5

Write-Host "[OK] 后端已启动" -ForegroundColor Green
Write-Host ""
Write-Host "  各模块以独立 JVM 进程运行中。" -ForegroundColor White
Write-Host "  其他主机可通过 start-view.ps1 连接此主机观察巡检。" -ForegroundColor White
Write-Host "  按 Ctrl+C 停止后端。" -ForegroundColor White
Write-Host ""

# Keep running until Ctrl+C
try {
    while ($true) { Start-Sleep -Seconds 5 }
} finally {
    Write-Host ""
    Write-Host "[INFO] 正在关闭后端..." -ForegroundColor Cyan
    if ($backendJob.State -ne "Failed" -and $backendJob.State -ne "Completed") {
        Stop-Job -Job $backendJob -ErrorAction SilentlyContinue
    }
    Remove-Job -Job $backendJob -ErrorAction SilentlyContinue
    Write-Host "[OK] 后端已停止" -ForegroundColor Green
}
