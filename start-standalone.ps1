<# .SYNOPSIS
   变电站巡检仿真系统 - 分别启动各后端组件
   四个终端窗口分别启动 Controller、Navigator、Car、Recorder。
   适合演示组件独立性和故障恢复。

   用法:
     .\start-standalone.ps1
     .\start-standalone.ps1 -BackendDir "E:\Desktop\homework\InspectionBackend"
#>

param(
    [string]$BackendDir = $null
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $BackendDir) {
    $BackendDir = Join-Path $Root "InspectionBackend"
}
$LogDir = Join-Path $Root "logs"

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  变电站巡检仿真系统 - 分别启动各组件" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "  此脚本将在4个独立终端窗口分别启动："
Write-Host "    1. Controller  - 节拍调度器（单实例）"
Write-Host "    2. Navigator   - 路径规划器（8 worker）"
Write-Host "    3. Car         - 小车知识源"
Write-Host "    4. Recorder    - 录制回放器"
Write-Host ""

# ---- Check prerequisites ----
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    Write-Host "[ERROR] 未找到 java。" -ForegroundColor Red
    Pause; exit 1
}

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Host "[ERROR] 未找到 mvn。" -ForegroundColor Red
    Pause; exit 1
}
Write-Host "[OK] Java / Maven 就绪" -ForegroundColor Green

# ---- Kill stale processes ----
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
    & java "-Dlog.dir=$LogDir" -jar $toolsJar clean-runtime 2>&1
    Write-Host "[OK] 初始化完成" -ForegroundColor Green
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[LAUNCH] 正在启动各组件..." -ForegroundColor Cyan
Write-Host "  各组件日志: logs/backend.log" -ForegroundColor DarkGray
Write-Host "  关闭各终端窗口即停止对应组件" -ForegroundColor DarkGray
Write-Host ""

# 计算 JAR 所在目录
$jarDir = $BackendDir
$javaBin = (Get-Command java).Source

$modules = @(
    @{Name="controller"; Title="Controller - 节拍调度器"},
    @{Name="navigator";  Title="Navigator - 路径规划器"},
    @{Name="car";        Title="Car - 小车知识源"},
    @{Name="recorder";   Title="Recorder - 录制回放器"}
)

$jobs = @()

foreach ($mod in $modules) {
    $jarPath = Join-Path $jarDir "$($mod.Name)\target\$($mod.Name).jar"
    if (-not (Test-Path $jarPath)) {
        Write-Host "[WARN] JAR 未找到: $jarPath" -ForegroundColor Yellow
        continue
    }

    $job = Start-Job -Name $mod.Name -ScriptBlock {
        param($javaCmd, $jar, $logDir, $appHome, $title)
        $host.UI.RawUI.WindowTitle = $title
        & $javaCmd "-Dlog.dir=$logDir" -jar $jar
    } -ArgumentList $javaBin, $jarPath, $LogDir, $jarDir, $mod.Title

    $jobs += $job
    Write-Host "  [OK] $($mod.Title)  PID: $($job.Id)" -ForegroundColor Green
    Start-Sleep -Milliseconds 500
}

Write-Host ""
Write-Host "[OK] 所有组件已启动（4个独立 JVM 进程）" -ForegroundColor Green
Write-Host ""
Write-Host "  可以手动停止任一组件来演示故障隔离和独立重启。"
Write-Host "  例如: Stop-Job -Id <PID>  来停止 Navigator"
Write-Host "        然后 .\start-standalone-navigator.ps1 单独重启它"
Write-Host ""
Write-Host "  按 Ctrl+C 停止所有组件。" -ForegroundColor White
Write-Host ""

try {
    while ($true) { Start-Sleep -Seconds 5 }
} finally {
    Write-Host ""
    Write-Host "[INFO] 正在停止所有组件..." -ForegroundColor Cyan
    foreach ($job in $jobs) {
        if ($job.State -ne "Failed" -and $job.State -ne "Completed") {
            Stop-Job -Job $job -ErrorAction SilentlyContinue
        }
        Remove-Job -Job $job -ErrorAction SilentlyContinue
    }
    Write-Host "[OK] 所有组件已停止" -ForegroundColor Green
}
