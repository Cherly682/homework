<#
.SYNOPSIS
    编译并启动 Navigator 模块（独立终端窗口）
#>
param()

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "env.ps1")
$ProjectRoot = Split-Path -Parent $ScriptDir
$ModuleDir = Join-Path $ProjectRoot "InspectionBackend\navigator"
$LogDir = Join-Path $ScriptDir "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Navigator - 路径规划器" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Redis : $env:REDIS_HOST`:$env:REDIS_PORT/$env:REDIS_DATABASE" -ForegroundColor DarkGray
Write-Host "  Rabbit: $env:RABBITMQ_HOST`:$env:RABBITMQ_PORT$env:RABBITMQ_VHOST" -ForegroundColor DarkGray
Write-Host ""

$jar = Join-Path $ModuleDir "target\navigator.jar"

if (-not (Test-Path $jar)) {
    Write-Host "[BUILD] JAR 不存在，自动编译..." -ForegroundColor Yellow
    $buildScript = Join-Path $ScriptDir "build-backend.ps1"
    & powershell -NoProfile -ExecutionPolicy Bypass -File $buildScript
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 编译失败" -ForegroundColor Red
        Pause; exit 1
    }
}

Write-Host "[START] 启动 Navigator..." -ForegroundColor Green
Write-Host "  日志: scripts\logs\" -ForegroundColor DarkGray
Write-Host ""

Push-Location $ModuleDir
try {
    java "-Dlog.dir=$LogDir" -jar $jar
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[STOP] Navigator 已停止" -ForegroundColor Yellow
Pause
