<#
.SYNOPSIS
    编译并启动前端 View（GUI 窗口可见）
#>
param()

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "env.ps1")
$ProjectRoot = Split-Path -Parent $ScriptDir
$ViewDir = Join-Path $ProjectRoot "View\CREAZYTHURSDAY\com.Manny"
$LogDir = Join-Path $ScriptDir "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  前端 View" -ForegroundColor Cyan
$redisHost = $env:REDIS_HOST
$mqHost = $env:RABBITMQ_HOST
if ($redisHost -ne "localhost" -or $mqHost -ne "localhost") {
    Write-Host "  Redis : ${redisHost}:$env:REDIS_PORT/$env:REDIS_DATABASE" -ForegroundColor Cyan
    Write-Host "  Rabbit: ${mqHost}:$env:RABBITMQ_PORT$env:RABBITMQ_VHOST" -ForegroundColor Cyan
}
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ---- 环境检查 ----
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    Write-Host "[ERROR] 未找到 java，请安装 JDK 19+" -ForegroundColor Red
    Pause; exit 1
}
Write-Host "[OK] Java 就绪" -ForegroundColor Green

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Host "[ERROR] 未找到 mvn" -ForegroundColor Red
    Pause; exit 1
}
Write-Host "[OK] Maven 就绪" -ForegroundColor Green

if (-not (Test-Path (Join-Path $ViewDir "pom.xml"))) {
    Write-Host "[ERROR] pom.xml 未找到: $ViewDir" -ForegroundColor Red
    Pause; exit 1
}

# ---- 环境变量已由 env.ps1 设置，无需额外操作 ----

Write-Host "  连接: $redisHost" -ForegroundColor DarkGray
Write-Host ""

# ---- 编译 ----
$viewJar = Join-Path $ViewDir "target\com.Manny-1.0-SNAPSHOT.jar"
if (-not (Test-Path $viewJar)) {
    Write-Host "[BUILD] JAR 不存在，自动编译..." -ForegroundColor Yellow
    Push-Location $ViewDir
    try {
        mvn clean package -DskipTests -q 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[ERROR] 前端编译失败" -ForegroundColor Red
            Pop-Location; Pause; exit 1
        }
        Write-Host "[OK] 编译完成" -ForegroundColor Green
    } finally {
        Pop-Location
    }
} else {
    Write-Host "[BUILD] JAR 已存在，跳过编译" -ForegroundColor DarkGray
}

# ---- 启动（前台运行，GUI 可见）----
Write-Host ""
Write-Host "[START] 启动 View 前端..." -ForegroundColor Green
Write-Host "  日志: scripts\logs\" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  凭据: config/config123  admin/admin123  analyst/analyst123" -ForegroundColor White
Write-Host ""

Push-Location $ViewDir
try {
    java "-Dlog.dir=$LogDir" -jar $viewJar
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[STOP] View 已退出" -ForegroundColor Yellow
