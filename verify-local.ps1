<#
    verify-local.ps1 - the local stand-in for the (currently broken) GitHub Actions CI.

    It runs the SAME checks the CI workflow runs, on your machine, and adds one more:
    a live smoke test of the real HTTP API. Four phases:

      1. environment   Docker is running and the pawzaar-db container is reachable
      2. build+test    .\mvnw.cmd verify   (compile, all tests, package the jar)   <- CI "test" job
      3. docker image  docker build -t pawzaar-api:local .                         <- CI "docker" job
      4. live smoke    start the app, drive real HTTP requests against it, clean up

    The live phase creates ONE throwaway probe user + listing and DELETES them again
    when it finishes (even if a check fails), so your seed data stays exactly as
    Flyway left it. That matters: PetRepositoryTest asserts there are exactly 3 seed
    pets, so a leftover row would break the next test run.

    Usage (from the project root):
      .\verify-local.ps1                      # everything
      .\verify-local.ps1 -SkipDockerImage     # skip the slow image build
      .\verify-local.ps1 -SkipLive            # tests + image only
      .\verify-local.ps1 -SkipTests           # image + live only
#>
[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$SkipDockerImage,
    [switch]$SkipLive
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
Set-Location $root

$script:failures = 0

function Step([string]$name) { Write-Host "`n=== $name ===" -ForegroundColor Cyan }
function Pass([string]$msg)  { Write-Host "  PASS  $msg" -ForegroundColor Green }
function Fail([string]$msg)  { Write-Host "  FAIL  $msg" -ForegroundColor Red; $script:failures++ }
function Info([string]$msg)  { Write-Host "  $msg" -ForegroundColor DarkGray }
function Warn([string]$msg)  { Write-Host "  WARN  $msg" -ForegroundColor Yellow }

# --- Database helpers --------------------------------------------------------
# The compose file names the DB "pawzaar" (container pawzaar-db, host port 5433).
$DbName     = 'pawzaar'
$DbUser     = 'pawzaar'
$DbPassword = 'pawzaar_dev'
$Base       = 'http://localhost:8080'

function Invoke-Db([string]$sql) {
    # -t = tuples only, -A = unaligned: clean, script-friendly output.
    docker exec -e "PGPASSWORD=$DbPassword" pawzaar-db psql -U $DbUser -d $DbName -t -A -c $sql
}

function Get-PetCount {
    return [int]((Invoke-Db 'SELECT count(*) FROM pets;') | Select-Object -First 1)
}

function Remove-ProbeData {
    # Delete only rows created by this script (probe+...@pawzaar.test), children first.
    $sql = @'
DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'probe+%@pawzaar.test');
DELETE FROM pets          WHERE seller_id IN (SELECT id FROM users WHERE email LIKE 'probe+%@pawzaar.test');
DELETE FROM users         WHERE email LIKE 'probe+%@pawzaar.test';
'@
    Invoke-Db $sql | Out-Null
}

# =============================================================================
Step 'Phase 1/4 - environment'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail 'docker is not on PATH; install/start Docker Desktop and rerun'
    exit 1
}

$dbRunning = (docker ps --filter 'name=^pawzaar-db$' --format '{{.Names}}') -join ''
if ($dbRunning -ne 'pawzaar-db') {
    Info 'pawzaar-db is not running - starting it with: docker compose up -d db'
    docker compose up -d db | Out-Null
}

$dbReady = $false
for ($i = 0; $i -lt 45; $i++) {
    docker exec pawzaar-db pg_isready -U $DbUser *> $null
    if ($LASTEXITCODE -eq 0) { $dbReady = $true; break }
    Start-Sleep -Seconds 1
}
if ($dbReady) { Pass 'pawzaar-db is up and accepting connections' }
else { Fail 'pawzaar-db did not become ready in time'; exit 1 }

# Defensive: clear anything a previous interrupted run left behind.
Remove-ProbeData
$pets = Get-PetCount
if ($pets -eq 3) {
    Pass "dev DB has the expected 3 seed pets"
} else {
    Warn "dev DB has $pets pets, but PetRepositoryTest expects exactly 3 seed pets."
    Warn "Reset with:  docker compose down -v ; docker compose up -d"
}

# =============================================================================
$testsOk = $true

if (-not $SkipTests) {
    Step 'Phase 2/4 - build & test  (.\mvnw.cmd verify)'
    & .\mvnw.cmd -B -ntp verify
    if ($LASTEXITCODE -eq 0) { Pass 'compile + all tests + package succeeded' }
    else { Fail 'mvnw verify failed'; $testsOk = $false }
}

if (-not $testsOk) {
    Write-Host "`nTests failed, so the image build and live smoke test were skipped (CI does the same via needs: test)." -ForegroundColor Yellow
}

# =============================================================================
if (-not $SkipDockerImage -and $testsOk) {
    Step 'Phase 3/4 - docker image  (docker build)'
    docker build -t pawzaar-api:local .
    if ($LASTEXITCODE -eq 0) { Pass 'Dockerfile built -> pawzaar-api:local' }
    else { Fail 'docker build failed' }
}

# =============================================================================
if (-not $SkipLive -and $testsOk) {
    Step 'Phase 4/4 - live API smoke test'

    # Refuse to run if something is already on 8080: we would test the wrong app.
    $alreadyUp = $null
    try { $alreadyUp = Invoke-RestMethod -Uri "$Base/api/v1/health" -TimeoutSec 2 } catch {}
    if ($alreadyUp) {
        Fail "something is already serving $Base - stop it and rerun (or use -SkipLive)"
    }
    else {
        Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue
        $client = New-Object System.Net.Http.HttpClient

        function Req([string]$method, [string]$url, [string]$token, $body) {
            $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::$method, $url)
            if ($token) {
                $req.Headers.Authorization =
                    New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token)
            }
            if ($null -ne $body) {
                $json = $body | ConvertTo-Json -Depth 10
                $req.Content = New-Object System.Net.Http.StringContent(
                    $json, [System.Text.Encoding]::UTF8, 'application/json')
            }
            $resp = $client.SendAsync($req).Result
            [pscustomobject]@{
                Status = [int]$resp.StatusCode
                Body   = $resp.Content.ReadAsStringAsync().Result
            }
        }

        # Load .env.dev exactly like run-dev.ps1 does (fall back to the example defaults).
        $envFile = Join-Path $root '.env.dev'
        if (Test-Path $envFile) {
            Get-Content $envFile | ForEach-Object {
                $line = $_.Trim()
                if ($line -and -not $line.StartsWith('#')) {
                    $kv = $line -split '=', 2
                    [System.Environment]::SetEnvironmentVariable($kv[0].Trim(), $kv[1].Trim(), 'Process')
                }
            }
        } else {
            $env:SPRING_PROFILES_ACTIVE = 'dev'
            $env:JWT_SECRET = 'cGF3emFhci1kZXYtb25seS1zZWNyZXQta2V5LWNoYW5nZS1tZS1pbi1wcm9kdWN0aW9uLTAxMjM0NTY3ODk='
        }

        $outLog = Join-Path $root 'target\verify-app.out.log'
        $errLog = Join-Path $root 'target\verify-app.err.log'
        $app = Start-Process -FilePath (Join-Path $root 'mvnw.cmd') -ArgumentList 'spring-boot:run' `
                -WorkingDirectory $root -PassThru -WindowStyle Hidden `
                -RedirectStandardOutput $outLog -RedirectStandardError $errLog

        try {
            $healthy = $false
            for ($i = 0; $i -lt 60; $i++) {
                try {
                    $h = Invoke-RestMethod -Uri "$Base/api/v1/health" -TimeoutSec 3
                    if ($h.status -eq 'ok') { $healthy = $true; break }
                } catch {}
                Start-Sleep -Seconds 2
            }

            if (-not $healthy) {
                Fail 'app did not become healthy on 8080'
                if (Test-Path $outLog) { Info 'last log lines:'; Get-Content $outLog -Tail 20 | ForEach-Object { Info $_ } }
            }
            else {
                Pass 'app started and /api/v1/health is ok'
                $email    = "probe+$([guid]::NewGuid().ToString('N').Substring(0,8))@pawzaar.test"
                $password = 'pawzaar123'
                $create = @{
                    title = 'Verify Probe Pup'; species = 'DOG'; breed = 'Aspin'; ageMonths = 5
                    price = 2500.00; description = 'local verify probe'; city = 'Cebu City'
                    province = 'Cebu'; sex = 'MALE'
                }
                $update = @{
                    title = 'Verify Probe Pup v2'; breed = 'Aspin'; ageMonths = 6; price = 2600.00
                    description = 'updated'; city = 'Cebu City'; province = 'Cebu'; sex = 'MALE'
                    status = 'ACTIVE'
                }

                $r = Req 'Post' "$Base/api/v1/auth/register" $null @{ email = $email; password = $password; displayName = 'Verify Probe' }
                if ($r.Status -eq 201) { Pass 'register a brand-new account -> 201 (role USER)' } else { Fail "register -> $($r.Status): $($r.Body)" }

                $r = Req 'Post' "$Base/api/v1/auth/login" $null @{ email = $email; password = $password }
                $tokens = $r.Body | ConvertFrom-Json
                if ($r.Status -eq 200 -and $tokens.accessToken) { Pass 'login -> 200 with an access token' } else { Fail "login -> $($r.Status): $($r.Body)" }

                $r = Req 'Post' "$Base/api/v1/pets" $tokens.accessToken $create
                $pet = $r.Body | ConvertFrom-Json
                if ($r.Status -eq 201 -and $pet.id) { Pass 'USER may create a listing -> 201' } else { Fail "create -> $($r.Status): $($r.Body)" }

                $r = Req 'Put' "$Base/api/v1/pets/$($pet.id)" $tokens.accessToken $update
                if ($r.Status -eq 403 -and $r.Body -match '"title"\s*:\s*"Access denied"') {
                    Pass 'USER token cannot update yet -> 403 ProblemDetail body'
                } else { Fail "stale-role update -> $($r.Status): $($r.Body)" }

                $r = Req 'Post' "$Base/api/v1/auth/refresh" $null @{ refreshToken = $tokens.refreshToken }
                $refreshed = $r.Body | ConvertFrom-Json
                if ($r.Status -eq 200 -and $refreshed.accessToken -ne $tokens.accessToken) {
                    Pass 'refresh -> 200 with a NEW access token'
                } else { Fail "refresh -> $($r.Status): $($r.Body)" }

                $r = Req 'Put' "$Base/api/v1/pets/$($pet.id)" $refreshed.accessToken $update
                if ($r.Status -eq 200) { Pass 'refreshed (now SELLER) token may update -> 200' } else { Fail "promoted update -> $($r.Status): $($r.Body)" }

                $r = Req 'Post' "$Base/api/v1/pets" $null $create
                if ($r.Status -eq 401) { Pass 'anonymous create -> 401 (URL rule, not 403)' } else { Fail "anonymous create -> $($r.Status)" }

                $r = Req 'Post' "$Base/api/v1/auth/logout" $null @{ refreshToken = $refreshed.refreshToken }
                if ($r.Status -eq 204) { Pass 'logout -> 204' } else { Fail "logout -> $($r.Status): $($r.Body)" }
            }
        }
        finally {
            Remove-ProbeData
            $after = Get-PetCount
            if ($after -eq 3) { Pass 'probe data cleaned up (dev DB back to 3 seed pets)' }
            else { Warn "dev DB has $after pets after cleanup - check for stray probe rows" }

            if ($app -and -not $app.HasExited) { taskkill /PID $app.Id /T /F *> $null }
            Remove-Item $outLog, $errLog -Force -ErrorAction SilentlyContinue
        }
    }
}

# =============================================================================
Write-Host ''
if ($script:failures -eq 0) {
    Write-Host 'ALL LOCAL CHECKS PASSED' -ForegroundColor Green
    exit 0
} else {
    Write-Host "$($script:failures) CHECK(S) FAILED" -ForegroundColor Red
    exit 1
}
