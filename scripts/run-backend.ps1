<#
.SYNOPSIS
    一键启动全部后端模块（不含前端）
    每个模块在独立终端窗口中运行，关闭窗口即停止对应模块。
#>
param([switch]$SkipClean)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "env.ps1")
$ProjectRoot = Split-Path -Parent $ScriptDir
$LogDir = Join-Path $ScriptDir "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  启动全部后端模块" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Redis : $env:REDIS_HOST`:$env:REDIS_PORT/$env:REDIS_DATABASE" -ForegroundColor DarkGray
Write-Host "  Rabbit: $env:RABBITMQ_HOST`:$env:RABBITMQ_PORT$env:RABBITMQ_VHOST" -ForegroundColor DarkGray
Write-Host ""

# ---- 环境检查 ----
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    Write-Host "[ERROR] 未找到 java，请安装 JDK 11+" -ForegroundColor Red
    Pause; exit 1
}
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Host "[ERROR] 未找到 mvn" -ForegroundColor Red
    Pause; exit 1
}
Write-Host "[OK] Java / Maven 就绪" -ForegroundColor Green

# ---- 检查中间件 ----
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

# ---- 构建 ----
Write-Host "[BUILD] 构建后端..." -ForegroundColor Cyan
$buildScript = Join-Path $ScriptDir "build-backend.ps1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $buildScript
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] 构建失败" -ForegroundColor Red
    Pause; exit 1
}

# ---- 初始化 ----
Write-Host "[INIT] 初始化用户和消息队列..." -ForegroundColor Cyan
$toolsJar = Join-Path $ProjectRoot "InspectionBackend\tools\target\tools.jar"
$initLogDir = $LogDir
& java "-Dlog.dir=$initLogDir" -jar $toolsJar init 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] 初始化失败" -ForegroundColor Red
    Pause; exit 1
}
if (-not $SkipClean) {
    & java "-Dlog.dir=$initLogDir" -jar $toolsJar clean-runtime 2>&1 | Out-Null
    Write-Host "[OK] 初始化完成（运行时数据已清理）" -ForegroundColor Green
} else {
    Write-Host "[OK] 初始化完成（保留上次录制数据）" -ForegroundColor Green
}
Write-Host ""

# ---- 在独立终端窗口中启动四个后端模块 ----
Write-Host "[LAUNCH] 在独立终端窗口启动后端模块..." -ForegroundColor Cyan
Write-Host "  日志目录: $LogDir" -ForegroundColor DarkGray
Write-Host "  关闭各终端窗口即停止对应模块" -ForegroundColor DarkGray
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
Write-Host "[OK] 全部后端模块已启动（4 个独立 JVM 进程）" -ForegroundColor Green
Write-Host ""
Write-Host "  其他主机通过 run-frontend.ps1 连接即可远程观察巡检。" -ForegroundColor White
Write-Host "  按任意键关闭本窗口（不影响已启动的模块）..." -ForegroundColor DarkGray
Pause
