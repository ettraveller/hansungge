#Requires -Version 5.1
<#
  로컬 개발 서버 기동 — 한성금거래소

      .\scripts\dev.ps1 db       PostgreSQL 기동 (재부팅하면 꺼져 있다)
      .\scripts\dev.ps1 api      백엔드  http://localhost:8080
      .\scripts\dev.ps1 web      프론트  http://localhost:3000
      .\scripts\dev.ps1 status   지금 뭐가 떠 있는지
      .\scripts\dev.ps1 stop-db  PostgreSQL 정지

  api / web 은 포그라운드로 돈다. 각각 별도 터미널에서 띄우고 Ctrl+C 로 끈다.

  경로를 $env:USERPROFILE 로 잡는 이유 — 개발 PC 마다 사용자 이름이 다르다.
  절대경로를 박아두면 다른 PC 에서 조용히 엉뚱한 곳을 보게 된다.
#>
param(
  [Parameter(Position = 0)]
  [ValidateSet('db', 'api', 'web', 'status', 'stop-db')]
  [string]$Target = 'status'
)

$ErrorActionPreference = 'Stop'
$Root   = Split-Path -Parent $PSScriptRoot
$Tools  = Join-Path $env:USERPROFILE 'tools'
$PgBin  = Join-Path $Tools 'pgsql\bin'
$PgData = Join-Path $Tools 'pgdata'
$PgLog  = Join-Path $Tools 'pgdata.log'

function Test-Port([int]$Port) {
  $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  return $null -ne $c
}

# gradlew 는 JAVA_HOME 이나 PATH 의 java 를 찾는다. 포터블 JDK 는 둘 다 아니므로
# 여기서 직접 짚어 준다. 여러 개가 깔려 있으면 21 중 가장 최신을 고른다.
function Resolve-JavaHome {
  if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    return $env:JAVA_HOME
  }
  $jdk = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
         Where-Object { $_.Name -match 'jdk-21' } |
         Sort-Object Name -Descending |
         Select-Object -First 1
  if (-not $jdk) {
    throw "JDK 21 을 찾지 못했습니다. $env:USERPROFILE\.jdks 아래에 설치하거나 JAVA_HOME 을 지정하세요."
  }
  return $jdk.FullName
}

# 비밀값을 스크립트나 커밋되는 파일에 적지 않는다는 원칙에 따라, 값은 전부
# gitignore 대상인 backend\.env 에만 있다. 여기서는 그 파일을 읽어 주입만 한다.
function Import-DotEnv([string]$Path) {
  if (-not (Test-Path $Path)) {
    throw "$Path 이 없습니다. backend\.env.example 을 복사해 값을 채우세요."
  }
  Get-Content $Path -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $i = $line.IndexOf('=')
    if ($i -lt 1) { return }
    $name  = $line.Substring(0, $i).Trim()
    $value = $line.Substring($i + 1).Trim().Trim('"').Trim("'")
    Set-Item -Path "Env:$name" -Value $value
  }
}

switch ($Target) {

  'db' {
    if (Test-Port 5432) {
      Write-Host 'PostgreSQL: 이미 떠 있습니다 (5432)'
      break
    }
    & (Join-Path $PgBin 'pg_ctl.exe') -D $PgData -l $PgLog start
    Write-Host "PostgreSQL 기동. 로그: $PgLog"
  }

  'stop-db' {
    & (Join-Path $PgBin 'pg_ctl.exe') -D $PgData stop
  }

  'api' {
    if (-not (Test-Port 5432)) {
      throw "PostgreSQL 이 꺼져 있습니다. 먼저 .\scripts\dev.ps1 db 를 실행하세요."
    }
    $env:JAVA_HOME = Resolve-JavaHome
    Import-DotEnv (Join-Path $Root 'backend\.env')
    Write-Host "JAVA_HOME = $env:JAVA_HOME"
    Write-Host "프로파일  = $env:SPRING_PROFILES_ACTIVE"
    Push-Location (Join-Path $Root 'backend')
    try { & .\gradlew.bat bootRun } finally { Pop-Location }
  }

  'web' {
    Push-Location (Join-Path $Root 'frontend')
    try { & npm run dev } finally { Pop-Location }
  }

  'status' {
    foreach ($s in @(
      @{ Name = 'PostgreSQL'; Port = 5432; Url = $null },
      @{ Name = '백엔드    '; Port = 8080; Url = 'http://localhost:8080/api/v1/health' },
      @{ Name = '프론트엔드'; Port = 3000; Url = 'http://localhost:3000' }
    )) {
      if (Test-Port $s.Port) {
        $tail = ''
        if ($s.Url) { $tail = "  $($s.Url)" }
        Write-Host "  [O] $($s.Name)  :$($s.Port)$tail"
      } else {
        Write-Host "  [X] $($s.Name)  :$($s.Port)  꺼짐"
      }
    }
  }
}
