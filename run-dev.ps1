# run-dev.ps1 — starts the Pawzaar API in the dev profile.
#
# Usage (from the project root in PowerShell):
#   .\run-dev.ps1
#
# What it does:
#   1. Reads every KEY=VALUE line from .env.dev and sets them as environment variables
#      for this PowerShell session (process scope, not permanently).
#   2. Runs the Spring Boot app via Maven with those variables in scope.
#
# Prerequisites:
#   - Docker must be running with the pawzaar-db container up.
#     Start it with:  docker compose up -d
#   - Java 21+ must be on your PATH (IntelliJ's bundled JDK is fine if you run
#     this from the IntelliJ terminal).

$envFile = Join-Path $PSScriptRoot ".env.dev"

if (-not (Test-Path $envFile)) {
    Write-Error ".env.dev not found at $envFile"
    exit 1
}

# Load each KEY=VALUE line, skip blank lines and comments.
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line -split "=", 2          # split on the FIRST = only
        $key   = $parts[0].Trim()
        $value = $parts[1].Trim()
        [System.Environment]::SetEnvironmentVariable($key, $value, "Process")
        Write-Host "  set $key"
    }
}

Write-Host ""
Write-Host "Starting Pawzaar API (profile: $env:SPRING_PROFILES_ACTIVE)..."
Write-Host "Swagger UI will be at http://localhost:8080/swagger-ui.html"
Write-Host ""

.\mvnw.cmd spring-boot:run
