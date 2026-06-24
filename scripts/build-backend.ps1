<#
.SYNOPSIS
    编译所有后端模块（controller, navigator, car, recorder, tools, launcher）
#>
param()

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$BackendDir = Join-Path $ProjectRoot "InspectionBackend"
$LogDir = Join-Path $ScriptDir "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

$LogFile = Join-Path $LogDir "build-backend.log"
Start-Transcript -Path $LogFile -Append | Out-Null

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  编译后端模块" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

Push-Location $BackendDir
try {
    mvn clean package -DskipTests -q 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 后端构建失败" -ForegroundColor Red
        Pop-Location
        Stop-Transcript | Out-Null
        exit 1
    }
    Write-Host "[OK] 后端构建完成" -ForegroundColor Green
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "  生成 JAR:" -ForegroundColor DarkGray
$modules = @("controller", "navigator", "car", "recorder", "tools", "launcher")
foreach ($m in $modules) {
    $jar = Join-Path $BackendDir "$m\target\$m.jar"
    if (Test-Path $jar) {
        Write-Host "    $m/target/$m.jar" -ForegroundColor DarkGray
    }
}

Stop-Transcript | Out-Null
