$ErrorActionPreference = 'Stop'

$composeFile = Join-Path $PSScriptRoot 'compose.yaml'
$environmentFile = Join-Path $PSScriptRoot 'chayitong-backend/.env'

if (-not (Test-Path -LiteralPath $environmentFile)) {
  throw '请先把 chayitong-backend/.env.example 复制为 .env，并填写本机数据库密码。'
}

# 原样转发参数，并始终使用正确的 Compose 文件和 .env。
& docker compose --project-directory $PSScriptRoot --env-file $environmentFile -f $composeFile @args
exit $LASTEXITCODE
