param(
    [string]$KeystorePath = "$env:USERPROFILE\Documents\LiveMediaTranslator-upload.jks",
    [string]$Alias = "live-media-translator-upload"
)

$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME is not set. Open this project in Android Studio or set JAVA_HOME to a JDK first."
}

$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
if (-not (Test-Path $keytool)) {
    throw "keytool.exe was not found at $keytool"
}

if (Test-Path $KeystorePath) {
    throw "Keystore already exists: $KeystorePath"
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $KeystorePath) | Out-Null

& $keytool -genkeypair `
    -v `
    -keystore $KeystorePath `
    -alias $Alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000

Write-Host ""
Write-Host "Created upload key:" $KeystorePath
Write-Host "Back this file up somewhere private. If you lose it, app updates become painful."

