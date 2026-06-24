<#
.SYNOPSIS
    环境变量加载器。
    从 connection.conf 读取连接配置并注入环境变量。
    所有运行脚本统一 dot-source 引用：
        . (Join-Path $PSScriptRoot "env.ps1")
#>

$envConfigFile = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "connection.conf"

$saved = @{}
if (Test-Path $envConfigFile) {
    Get-Content $envConfigFile -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^([^=]+)=(.*)$') {
            $saved[$Matches[1]] = $Matches[2]
        }
    }
} else {
    Write-Host "[WARN] 连接配置文件不存在: $envConfigFile，使用 localhost 默认值" -ForegroundColor Yellow
}

$env:REDIS_HOST     = if ($saved.ContainsKey('REDIS_HOST') -and $saved['REDIS_HOST'])     { $saved['REDIS_HOST']     } else { "localhost" }
$env:REDIS_PORT     = if ($saved.ContainsKey('REDIS_PORT') -and $saved['REDIS_PORT'])     { $saved['REDIS_PORT']     } else { "6379" }
$env:REDIS_DATABASE = if ($saved.ContainsKey('REDIS_DATABASE') -and $saved['REDIS_DATABASE']) { $saved['REDIS_DATABASE'] } else { "9" }
$env:REDIS_PASSWORD = if ($saved.ContainsKey('REDIS_PASSWORD')) { $saved['REDIS_PASSWORD'] } else { "" }
$env:RABBITMQ_HOST     = if ($saved.ContainsKey('RABBITMQ_HOST') -and $saved['RABBITMQ_HOST'])     { $saved['RABBITMQ_HOST']     } else { "localhost" }
$env:RABBITMQ_PORT     = if ($saved.ContainsKey('RABBITMQ_PORT') -and $saved['RABBITMQ_PORT'])     { $saved['RABBITMQ_PORT']     } else { "5672" }
$env:RABBITMQ_USERNAME = if ($saved.ContainsKey('RABBITMQ_USERNAME') -and $saved['RABBITMQ_USERNAME']) { $saved['RABBITMQ_USERNAME'] } else { "guest" }
$env:RABBITMQ_PASSWORD = if ($saved.ContainsKey('RABBITMQ_PASSWORD') -and $saved['RABBITMQ_PASSWORD']) { $saved['RABBITMQ_PASSWORD'] } else { "guest" }
$env:RABBITMQ_VHOST    = if ($saved.ContainsKey('RABBITMQ_VHOST') -and $saved['RABBITMQ_VHOST'])    { $saved['RABBITMQ_VHOST']    } else { "/" }
