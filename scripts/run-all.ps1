<#
.SYNOPSIS
    变电站巡检仿真系统 - 一键启动全部组件
    后端模块在独立终端窗口中运行，前端在前台显示 GUI 窗口。
    关闭前端窗口即停止所有进程。

   用法:
     .\run-all.ps1
     .\run-all.ps1 -SkipClean   # 保留上次录制数据
#>
param([switch]$SkipClean)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "env.ps1")
$ProjectRoot = Split-Path -Parent $ScriptDir
$BackendDir = Join-Path $ProjectRoot "InspectionBackend"
$ViewDir = Join-Path $ProjectRoot "View\CREAZYTHURSDAY\com.Manny"
$LogDir = Join-Path $ScriptDir "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

$LogFile = Join-Path $LogDir "run-all.log"
Start-Transcript -Path $LogFile -Append | Out-Null

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  变电站巡检仿真系统 - 一键启动" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Redis : $env:REDIS_HOST`:$env:REDIS_PORT/$env:REDIS_DATABASE" -ForegroundColor DarkGray
Write-Host "  Rabbit: $env:RABBITMQ_HOST`:$env:RABBITMQ_PORT$env:RABBITMQ_VHOST" -ForegroundColor DarkGray
Write-Host ""

# ---- 环境检查 ----
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    Write-Host "[ERROR] 未找到 java，请安装 JDK 11+（后端）/ JDK 19+（前端）" -ForegroundColor Red
    Pause; exit 1
}
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Host "[ERROR] 未找到 mvn" -ForegroundColor Red
    Pause; exit 1
}
$javaVer = cmd /c "java -version 2>&1" | Select-Object -First 1
Write-Host "[OK] Java: $javaVer" -ForegroundColor Green
Write-Host "[OK] Maven 就绪" -ForegroundColor Green

$redisTest = try {
    $r = [Net.Sockets.TcpClient]::new()
    if ($r.ConnectAsync($env:REDIS_HOST, [int]$env:REDIS_PORT).Wait(3000)) { $r.Close(); $true } else { $false }
} catch { $false }
if ($redisTest) {
    Write-Host "[OK] Redis 可达: $env:REDIS_HOST`:$env:REDIS_PORT" -ForegroundColor Green
} else {
    Write-Host "[WARN] Redis 不可达: $env:REDIS_HOST`:$env:REDIS_PORT" -ForegroundColor Yellow
}

$mqTest = try {
    $r = [Net.Sockets.TcpClient]::new()
    if ($r.ConnectAsync($env:RABBITMQ_HOST, [int]$env:RABBITMQ_PORT).Wait(3000)) { $r.Close(); $true } else { $false }
} catch { $false }
if ($mqTest) {
    Write-Host "[OK] RabbitMQ 可达: $env:RABBITMQ_HOST`:$env:RABBITMQ_PORT" -ForegroundColor Green
} else {
    Write-Host "[WARN] RabbitMQ 不可达: $env:RABBITMQ_HOST`:$env:RABBITMQ_PORT" -ForegroundColor Yellow
}
Write-Host ""

# ---- 清理残留 ----
$staleJava = Get-Process java -ErrorAction SilentlyContinue
if ($staleJava) {
    Write-Host "[CLEAN] 停止残留 Java 进程..." -ForegroundColor Yellow
    taskkill /f /im java.exe 2>$null
    Start-Sleep -Seconds 2
    Write-Host "[OK] 残留进程已清理" -ForegroundColor Green
    Write-Host ""
}

# ---- Step 1: 构建 ----
Write-Host "[1/3] 构建全部模块..." -ForegroundColor Cyan
$buildScript = Join-Path $ScriptDir "build-all.ps1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $buildScript
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] 构建失败" -ForegroundColor Red
    Stop-Transcript | Out-Null
    Pause; exit 1
}

# ---- Step 2: 初始化 ----
Write-Host "[2/3] 初始化..." -ForegroundColor Cyan
$toolsJar = Join-Path $BackendDir "tools\target\tools.jar"
$initLogDir = $LogDir
& java "-Dlog.dir=$initLogDir" -jar $toolsJar init 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] 初始化失败" -ForegroundColor Red
    Stop-Transcript | Out-Null
    Pause; exit 1
}
if (-not $SkipClean) {
    & java "-Dlog.dir=$initLogDir" -jar $toolsJar clean-runtime 2>&1 | Out-Null
    Write-Host "[OK] 初始化完成（运行时数据已清理）" -ForegroundColor Green
} else {
    Write-Host "[OK] 初始化完成（保留上次录制数据）" -ForegroundColor Green
}
Write-Host ""

# ---- Step 3: 启动后端模块（独立终端窗口）----
Write-Host "[3/3] 启动全部组件..." -ForegroundColor Cyan
Write-Host ""

$modules = @(
    @{Name="controller"; Title="Controller - 节拍调度器"},
    @{Name="navigator";  Title="Navigator - 路径规划器"},
    @{Name="car";        Title="Car - 小车知识源"},
    @{Name="recorder";   Title="Recorder - 录制/回放"}
)

foreach ($mod in $modules) {
    $runScript = Join-Path $ScriptDir "run-$($mod.Name).ps1"
    Start-Process pwsh -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$runScript`"" -WindowStyle Normal
    Write-Host "  [OK] $($mod.Title)" -ForegroundColor Green
    Start-Sleep -Milliseconds 500
}

Write-Host ""
Write-Host "[OK] 后端模块已启动（4 个独立终端窗口）" -ForegroundColor Green
Write-Host ""

# ---- Step 4: 启动前端（前台运行，GUI 可见）----
Write-Host "[FRONTEND] 启动 View 前端..." -ForegroundColor Cyan
Write-Host "  日志目录: $LogDir" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  凭据: config/config123  admin/admin123  analyst/analyst123" -ForegroundColor White
Write-Host "  关闭前端窗口即停止全部进程" -ForegroundColor DarkGray
Write-Host ""

$viewJar = Join-Path $ViewDir "target\com.Manny-1.0-SNAPSHOT.jar"

Push-Location $ViewDir
try {
    java "-Dlog.dir=$LogDir" -jar $viewJar
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[INFO] 前端已退出，停止后端进程..." -ForegroundColor Cyan
taskkill /f /im java.exe 2>$null
Write-Host "[OK] 全部进程已停止" -ForegroundColor Green

Stop-Transcript | Out-Null
Pause
