$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path $PSScriptRoot -Parent
$composeFile = Join-Path $repositoryRoot 'compose.yaml'
$environmentFile = Join-Path $repositoryRoot 'chayitong-backend/.env'
$mysqlCommand = Get-Command mysql.exe -ErrorAction SilentlyContinue
if (-not $mysqlCommand) {
  throw '找不到 mysql.exe，请将 MySQL 客户端加入 PATH 后重试。'
}

# 解析后的配置含密码，仅在内存中使用，不输出完整配置。
$configurationJson = & docker compose --project-directory $repositoryRoot --env-file $environmentFile -f $composeFile config --format json
if ($LASTEXITCODE -ne 0) {
  throw 'Compose 配置解析失败。'
}
$configuration = ($configurationJson -join "`n") | ConvertFrom-Json
$mysqlService = $configuration.services.mysql
$mysqlPort = ($mysqlService.ports | Where-Object { $_.target -eq 3306 } | Select-Object -First 1).published
$mysqlUser = $mysqlService.environment.MYSQL_USER
$mysqlDatabase = $mysqlService.environment.MYSQL_DATABASE
$previousPassword = $env:MYSQL_PWD

try {
  $env:MYSQL_PWD = $mysqlService.environment.MYSQL_PASSWORD
  Write-Output "检查 127.0.0.1:$mysqlPort，账号 $mysqlUser，数据库 $mysqlDatabase"
  & $mysqlCommand.Source --host=127.0.0.1 "--port=$mysqlPort" "--user=$mysqlUser" "--database=$mysqlDatabase" --connect-timeout=5 --default-character-set=utf8mb4 --batch --execute="SELECT CURRENT_USER() AS authenticated_user, DATABASE() AS selected_database, VERSION() AS server_version, @@server_uuid AS server_uuid; SHOW TABLES;"
  if ($LASTEXITCODE -ne 0) {
    throw '数据库连接或只读查询失败，请检查容器、账号和密码。'
  }
  Write-Output 'PASS：数据库认证、选库和只读查询成功。'
} finally {
  if ($null -eq $previousPassword) {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
  } else {
    $env:MYSQL_PWD = $previousPassword
  }
}
