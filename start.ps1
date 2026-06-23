<# .SYNOPSIS
   变电站巡检仿真系统 - 完整一键启动脚本（构建 + 初始化 + 启动全部后端 + 前端）
   启动 Redis (localhost:6379) 和 RabbitMQ (localhost:5672) 后运行。
   Logs are written to <root>/logs/backend.log and <root>/logs/frontend.log

   参数: -SkipClean  跳过 clean-runtime，保留上次录制数据
#>

param([switch]$SkipClean)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $Root "InspectionBackend"
$ViewDir = Join-Path $Root "View\CREAZYTHURSDAY\com.Manny"
$LogDir = Join-Path $Root "logs"

# Ensure log directory exists
if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  变电站巡检仿真系统 - 完整一键启动" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ---- Check Java ----
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    Write-Host "[ERROR] 未找到 java。请安装 JDK 11+ 并添加到 PATH。" -ForegroundColor Red
    Pause; exit 1
}
Write-Host "[OK] Java 就绪" -ForegroundColor Green

# ---- Check Maven ----
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Host "[ERROR] 未找到 mvn。请安装 Maven 3.8+ 并添加到 PATH。" -ForegroundColor Red
    Pause; exit 1
}
Write-Host "[OK] Maven 就绪" -ForegroundColor Green

# ---- Check Redis ----
$redisOk = try { (redis-cli ping 2>&1) -eq "PONG" } catch { $false }
if ($redisOk) {
    Write-Host "[OK] Redis 就绪 (localhost:6379)" -ForegroundColor Green
    redis-cli CONFIG SET stop-writes-on-bgsave-error no 2>&1 | Out-Null
} else {
    Write-Host "[WARN] Redis 未响应 (localhost:6379)。请确保 Redis 已启动。" -ForegroundColor Yellow
}

# ---- Check RabbitMQ ----
$mqOk = try { $null = rabbitmqctl status 2>&1; $true } catch { $false }
if ($mqOk) {
    Write-Host "[OK] RabbitMQ 就绪 (localhost:5672)" -ForegroundColor Green
} else {
    Write-Host "[WARN] RabbitMQ 未响应 (localhost:5672)。请确保 RabbitMQ 已启动。" -ForegroundColor Yellow
}

Write-Host ""

# Kill stale Java processes from previous runs (avoids RabbitMQ message routing conflicts)
$staleJava = Get-Process java -ErrorAction SilentlyContinue
if ($staleJava) {
    Write-Host "[CLEAN] 停止残留 Java 进程..." -ForegroundColor Yellow
    taskkill /f /im java.exe 2>$null
    Start-Sleep -Seconds 2
    Write-Host "[OK] 残留进程已清理" -ForegroundColor Green
}
Write-Host ""

# ---- Step 1: Build backend ----
Write-Host "[1/4] 构建 InspectionBackend..." -ForegroundColor Cyan
Push-Location $BackendDir
try {
    mvn clean package -DskipTests -q 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 后端构建失败。" -ForegroundColor Red
        Pop-Location; Pause; exit 1
    }
    Write-Host "[OK] 后端构建完成    -> controller/navigator/car/recorder/launcher/tools.jar" -ForegroundColor Green
} finally {
    Pop-Location
}

# ---- Step 2: Build frontend ----
Write-Host "[2/4] 构建 View 前端..." -ForegroundColor Cyan
Push-Location $ViewDir
try {
    mvn clean compile -q 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 前端构建失败。" -ForegroundColor Red
        Pop-Location; Pause; exit 1
    }
    Write-Host "[OK] 前端构建完成" -ForegroundColor Green
} finally {
    Pop-Location
}

# ---- Step 3: Init (users + queues) ----
Write-Host "[3/4] 初始化用户和消息队列..." -ForegroundColor Cyan
Push-Location $BackendDir
try {
    $toolsJar = Join-Path $BackendDir "tools\target\tools.jar"
    $initLogDir = $LogDir
    & java "-Dlog.dir=$initLogDir" -jar $toolsJar init 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 初始化失败。" -ForegroundColor Red
        Pop-Location; Pause; exit 1
    }
    if (-not $SkipClean) {
        # 清理运行时数据（历史记录、小车状态等），确保每次启动为干净状态
        & java "-Dlog.dir=$initLogDir" -jar $toolsJar clean-runtime 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[WARN] 运行时清理失败。" -ForegroundColor Yellow
        }
        Write-Host "[OK] 初始化完成（运行时数据已清理）" -ForegroundColor Green
    } else {
        Write-Host "[OK] 初始化完成（保留上次录制数据）" -ForegroundColor Green
    }
} finally {
    Pop-Location
}

# ---- Step 4: Launch backend + frontend ----
Write-Host "[4/4] 启动后端和前端..." -ForegroundColor Cyan
Write-Host ""
Write-Host "  后端日志  -> logs/backend.log" -ForegroundColor DarkGray
Write-Host "  前端日志  -> logs/frontend.log" -ForegroundColor DarkGray
Write-Host "  关闭前端窗口即停止所有进程" -ForegroundColor DarkGray
Write-Host ""

# Start backend in background
$launcherJar = Join-Path $BackendDir "launcher\target\launcher.jar"
$backendJob = Start-Job -Name "backend" -ScriptBlock {
    param($jarPath, $logDirArg, $homeDir)
    & java "-Dlog.dir=$logDirArg" -jar $jarPath $homeDir 2>&1 | Out-Null
} -ArgumentList $launcherJar, $LogDir, $BackendDir

# Wait for backend to start listening
Start-Sleep -Seconds 5
$backendStarted = Get-Content (Join-Path $LogDir "backend.log") -Tail 10 -ErrorAction SilentlyContinue | Select-String "Launcher" -Quiet
if (-not $backendStarted) {
    Write-Host "[WARN] 后端可能仍在启动中，请查看 logs/backend.log" -ForegroundColor Yellow
}

# Start frontend
$frontendJob = Start-Job -Name "frontend" -ScriptBlock {
    param($dir, $logDirArg)
    Set-Location $dir
    $args = @(
        "exec:java",
        "-Dexec.mainClass=Login.Main.Main",
        "-Dlog.dir=$logDirArg"
    )
    mvn @args 2>&1 | Out-Null
} -ArgumentList $ViewDir, $LogDir

Write-Host "[OK] 后端 + 前端已启动" -ForegroundColor Green
Write-Host ""
Write-Host "  登录凭据: config / config123" -ForegroundColor White
Write-Host "  查看日志: logs/ 目录" -ForegroundColor White
Write-Host ""

# Monitor frontend job; kill backend when frontend exits
while ($frontendJob.State -ne "Failed" -and $frontendJob.State -ne "Completed") {
    Start-Sleep -Seconds 3
}

Write-Host ""
Write-Host "[INFO] 前端已退出，正在关闭后端..." -ForegroundColor Cyan

# Gracefully stop backend job
if ($backendJob.State -ne "Failed" -and $backendJob.State -ne "Completed") {
    Stop-Job -Job $backendJob -ErrorAction SilentlyContinue
}
Remove-Job -Job $backendJob -ErrorAction SilentlyContinue
Remove-Job -Job $frontendJob -ErrorAction SilentlyContinue

Write-Host "[OK] 所有进程已停止" -ForegroundColor Green
