<# .SYNOPSIS
   变电站巡检仿真系统 - 仅启动前端 View（连接远程服务器）
   适用于其他主机观察巡检过程或回放录制。

   用法:
     .\start-view.ps1 -Host 192.168.1.10
     .\start-view.ps1 -Host 192.168.1.10 -RabbitUser remote -RabbitPass remote123
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$Host,
    [string]$RabbitUser = "guest",
    [string]$RabbitPass = "guest",
    [int]$RedisPort = 6379,
    [int]$RabbitPort = 5672
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ViewDir = Join-Path $Root "View\CREAZYTHURSDAY\com.Manny"
$LogDir = Join-Path $Root "logs"

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  变电站巡检仿真系统 - 前端 View" -ForegroundColor Cyan
Write-Host "  连接远程服务器: ${Host}" -ForegroundColor Cyan
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

# ---- Set remote connection env vars ----
$env:REDIS_HOST = $Host
$env:REDIS_PORT = $RedisPort
$env:RABBITMQ_HOST = $Host
$env:RABBITMQ_PORT = $RabbitPort
$env:RABBITMQ_USERNAME = $RabbitUser
$env:RABBITMQ_PASSWORD = $RabbitPass

Write-Host "[INFO] REDIS_HOST    = $env:REDIS_HOST" -ForegroundColor DarkGray
Write-Host "[INFO] RABBITMQ_HOST = $env:RABBITMQ_HOST" -ForegroundColor DarkGray
Write-Host ""

# ---- Test remote Redis ----
Write-Host "[TEST] 测试远程 Redis 连接..." -ForegroundColor Cyan
$redisOk = try {
    & redis-cli -h $Host -p $RedisPort ping 2>&1
} catch { $false }
if ($redisOk -eq "PONG") {
    Write-Host "[OK] 远程 Redis ${Host}:${RedisPort} 连接正常" -ForegroundColor Green
} else {
    Write-Host "[WARN] 远程 Redis 未响应。请检查防火墙和 Redis bind 配置。" -ForegroundColor Yellow
    Write-Host "        可继续启动 View，但连接可能失败。" -ForegroundColor Yellow
}

# ---- Build frontend ----
Write-Host "[BUILD] 构建前端..." -ForegroundColor Cyan
Push-Location $ViewDir
try {
    mvn package -DskipTests -q 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 前端构建失败。" -ForegroundColor Red
        Pop-Location; Pause; exit 1
    }
    Write-Host "[OK] 前端构建完成" -ForegroundColor Green
} finally {
    Pop-Location
}

# ---- Launch frontend ----
Write-Host "[LAUNCH] 启动 View 前端..." -ForegroundColor Cyan
Write-Host ""
Write-Host "  登录凭据: config / config123 (配置员)" -ForegroundColor White
Write-Host "           analyst / analyst123 (分析员)" -ForegroundColor White
Write-Host "           admin / admin123 (管理员)" -ForegroundColor White
Write-Host ""

$viewJar = Join-Path $ViewDir "target\com.Manny-1.0-SNAPSHOT.jar"
Push-Location $ViewDir
try {
    & java "-Dlog.dir=$LogDir" -jar $viewJar
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[OK] View 前端已退出" -ForegroundColor Green
