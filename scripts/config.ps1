<#
.SYNOPSIS
    连接配置交互模块。
    由 menu.ps1 在启动组件前调用，也可独立运行。
    保存上次输入到 scripts/connection.conf，下次作为默认值。
#>
param()

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFile = Join-Path $ScriptDir "connection.conf"

# ---- 加载已保存的配置 ----
$saved = @{}
if (Test-Path $ConfigFile) {
    Get-Content $ConfigFile -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^([^=]+)=(.*)$') {
            $saved[$Matches[1]] = $Matches[2]
        }
    }
}

function Read-WithDefault($prompt, $default) {
    $input = Read-Host "$prompt [$default]"
    if ([string]::IsNullOrWhiteSpace($input)) { return $default }
    return $input.Trim()
}

function Get-Default($key, $fallback) {
    if ($saved.ContainsKey($key) -and $saved[$key]) { return $saved[$key] }
    return $fallback
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  连接配置（留空使用默认值）" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  上次输入将保存，下次直接回车即可" -ForegroundColor DarkGray
Write-Host ""

Write-Host "--- Redis 服务器 ---" -ForegroundColor Yellow
$redisHost = Read-WithDefault "  主机地址" (Get-Default 'REDIS_HOST' 'localhost')
$redisPort = Read-WithDefault "  端口"     (Get-Default 'REDIS_PORT' '6379')
$redisDb   = Read-WithDefault "  数据库"   (Get-Default 'REDIS_DATABASE' '9')
$redisPwd  = Read-WithDefault "  密码"     (Get-Default 'REDIS_PASSWORD' '')

Write-Host ""
Write-Host "--- RabbitMQ 服务器 ---" -ForegroundColor Yellow
$mqHost  = Read-WithDefault "  主机地址" (Get-Default 'RABBITMQ_HOST' 'localhost')
$mqPort  = Read-WithDefault "  端口"     (Get-Default 'RABBITMQ_PORT' '5672')
$mqUser  = Read-WithDefault "  用户名"   (Get-Default 'RABBITMQ_USERNAME' 'guest')
$mqPass  = Read-WithDefault "  密码"     (Get-Default 'RABBITMQ_PASSWORD' 'guest')
$mqVhost = Read-WithDefault "  vHost"    (Get-Default 'RABBITMQ_VHOST' '/')

# ---- 保存配置 ----
@"
REDIS_HOST=$redisHost
REDIS_PORT=$redisPort
REDIS_DATABASE=$redisDb
REDIS_PASSWORD=$redisPwd
RABBITMQ_HOST=$mqHost
RABBITMQ_PORT=$mqPort
RABBITMQ_USERNAME=$mqUser
RABBITMQ_PASSWORD=$mqPass
RABBITMQ_VHOST=$mqVhost
"@ | Set-Content $ConfigFile -Encoding UTF8

Write-Host ""
Write-Host "[OK] 配置已就绪: Redis=$redisHost`:$redisPort/$redisDb  RabbitMQ=$mqHost`:$mqPort$mqVhost" -ForegroundColor Green
Write-Host ""
