<#
.SYNOPSIS
    仅编译前端 View 模块
#>
param()

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ViewDir = Join-Path $ProjectRoot "View\CREAZYTHURSDAY\com.Manny"
$LogDir = Join-Path $ScriptDir "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

$LogFile = Join-Path $LogDir "build-frontend.log"
Start-Transcript -Path $LogFile -Append | Out-Null

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  编译前端 View" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

Push-Location $ViewDir
try {
    mvn clean package -DskipTests -q 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 前端构建失败" -ForegroundColor Red
        Pop-Location
        Stop-Transcript | Out-Null
        exit 1
    }
    Write-Host "[OK] 前端构建完成" -ForegroundColor Green
} finally {
    Pop-Location
}

$viewJar = Join-Path $ViewDir "target\com.Manny-1.0-SNAPSHOT.jar"
if (Test-Path $viewJar) {
    Write-Host "  JAR: $viewJar" -ForegroundColor DarkGray
}

Stop-Transcript | Out-Null
