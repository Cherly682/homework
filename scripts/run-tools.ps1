<#
.SYNOPSIS
    执行 Tools 操作（初始化 / 清理运行时数据）
#>
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("init", "clean-runtime")]
    [string]$Action
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "env.ps1")
$ProjectRoot = Split-Path -Parent $ScriptDir
$ToolsDir = Join-Path $ProjectRoot "InspectionBackend\tools"
$LogDir = Join-Path $ScriptDir "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Tools - $Action" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Redis : $env:REDIS_HOST`:$env:REDIS_PORT/$env:REDIS_DATABASE" -ForegroundColor DarkGray
Write-Host "  Rabbit: $env:RABBITMQ_HOST`:$env:RABBITMQ_PORT$env:RABBITMQ_VHOST" -ForegroundColor DarkGray
Write-Host ""

$jar = Join-Path $ToolsDir "target\tools.jar"

if (-not (Test-Path $jar)) {
    Write-Host "[BUILD] JAR 不存在，自动编译..." -ForegroundColor Yellow
    $buildScript = Join-Path $ScriptDir "build-backend.ps1"
    & powershell -NoProfile -ExecutionPolicy Bypass -File $buildScript
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 编译失败" -ForegroundColor Red
        Pause; exit 1
    }
}

Write-Host "[RUN] $Action..." -ForegroundColor Green
Push-Location $ToolsDir
try {
    java "-Dlog.dir=$LogDir" -jar $jar $Action
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] $Action 执行失败" -ForegroundColor Red
        Pop-Location; Pause; exit 1
    }
} finally {
    Pop-Location
}
Write-Host "[OK] $Action 完成" -ForegroundColor Green
Pause
