param(
    [switch]$SkipReset
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"

$envNames = @(
    "SPRING_DATASOURCE_URL",
    "SPRING_DATASOURCE_USERNAME",
    "SPRING_DATASOURCE_PASSWORD",
    "SPRING_DATASOURCE_DRIVER_CLASS_NAME",
    "SPRING_FLYWAY_ENABLED",
    "SPRING_JPA_HIBERNATE_DDL_AUTO",
    "APP_DEV_SEED_ENABLED",
    "APP_DEV_SEED_EMAIL",
    "APP_DEV_SEED_PASSWORD",
    "APP_CLINIC_SLUG",
    "APP_CLINIC_NAME",
    "APP_CLINIC_EXTERNAL_PROVIDER",
    "APP_CLINIC_WHATSAPP_PHONE_NUMBER_ID"
)

try {
    Push-Location $repoRoot
    if (-not $SkipReset) {
        docker compose --profile test down -v
    }
    docker compose --profile test up -d postgres-test

    $health = ""
    for ($i = 0; $i -lt 30; $i++) {
        $health = docker inspect -f "{{.State.Health.Status}}" clinicafemina-postgres-test 2>$null
        if ($health -eq "healthy") {
            break
        }
        Start-Sleep -Seconds 2
    }
    if ($health -ne "healthy") {
        throw "postgres-test nao ficou healthy dentro do tempo esperado"
    }

    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:55432/clinicafemina_test"
    $env:SPRING_DATASOURCE_USERNAME = "postgres"
    $env:SPRING_DATASOURCE_PASSWORD = "postgres_test_pass"
    $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = "org.postgresql.Driver"
    $env:SPRING_FLYWAY_ENABLED = "true"
    $env:SPRING_JPA_HIBERNATE_DDL_AUTO = "validate"
    $env:APP_DEV_SEED_ENABLED = "true"
    $env:APP_DEV_SEED_EMAIL = "gestor-ultra@local.test"
    $env:APP_DEV_SEED_PASSWORD = "senha-local-forte-ultra"
    $env:APP_CLINIC_SLUG = "ultramedical"
    $env:APP_CLINIC_NAME = "UltraMedical"
    $env:APP_CLINIC_EXTERNAL_PROVIDER = "MEDWARE"
    $env:APP_CLINIC_WHATSAPP_PHONE_NUMBER_ID = "phone-ultra"

    Push-Location $backendDir
    .\gradlew.bat test --tests "com.synapse.clinicafemina.BackendApplicationTests" --rerun-tasks
}
finally {
    Pop-Location -ErrorAction SilentlyContinue
    Pop-Location -ErrorAction SilentlyContinue
    foreach ($name in $envNames) {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
}
