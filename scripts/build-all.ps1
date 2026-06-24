<#
.SYNOPSIS
    编译全部模块（后端 + 前端）
#>
param()

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogDir = Join-Path $ScriptDir "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

$LogFile = Join-Path $LogDir "build-all.log"
Start-Transcript -Path $LogFile -Append | Out-Null

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  编译全部模块（后端 + 前端）" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# 后端
Write-Host "[1/2] 构建后端..." -ForegroundColor Cyan
$buildBackend = Join-Path $ScriptDir "build-backend.ps1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $buildBackend
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] 后端构建失败" -ForegroundColor Red
    Stop-Transcript | Out-Null
    exit 1
}

# 前端
Write-Host "[2/2] 构建前端..." -ForegroundColor Cyan
$buildFrontend = Join-Path $ScriptDir "build-frontend.ps1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $buildFrontend
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] 前端构建失败" -ForegroundColor Red
    Stop-Transcript | Out-Null
    exit 1
}

Write-Host ""
Write-Host "[OK] 全部模块构建完成" -ForegroundColor Green

Stop-Transcript | Out-Null
