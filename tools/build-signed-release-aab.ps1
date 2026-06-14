param(
    [string]$KeystorePath = "$env:USERPROFILE\Documents\LiveMediaTranslator-upload.jks",
    [string]$Alias = "live-media-translator-upload",
    [string]$GradleBat = "C:\Users\kmend\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat"
)

$ErrorActionPreference = "Stop"

function Convert-SecureStringToPlainText([securestring]$SecureValue) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

if (-not (Test-Path $KeystorePath)) {
    throw "Keystore not found: $KeystorePath. Run tools\create-upload-key.ps1 first."
}
if (-not (Test-Path $GradleBat)) {
    throw "Gradle was not found: $GradleBat"
}
if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME is not set. Open this project in Android Studio or set JAVA_HOME to a JDK first."
}

$storePassword = Convert-SecureStringToPlainText (Read-Host "Keystore password" -AsSecureString)
$keyPassword = Convert-SecureStringToPlainText (Read-Host "Key password" -AsSecureString)

try {
    $env:LMT_UPLOAD_STORE_FILE = $KeystorePath
    $env:LMT_UPLOAD_STORE_PASSWORD = $storePassword
    $env:LMT_UPLOAD_KEY_ALIAS = $Alias
    $env:LMT_UPLOAD_KEY_PASSWORD = $keyPassword

    & $GradleBat bundleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle bundleRelease failed with exit code $LASTEXITCODE"
    }

    $projectRoot = (Resolve-Path ".").Path
    $bundlePath = Join-Path $projectRoot "app\build\outputs\bundle\release\app-release.aab"
    $brandedBundlePath = Join-Path $projectRoot "LiveMediaTranslator-1.0.0-release.aab"
    Copy-Item -LiteralPath $bundlePath -Destination $brandedBundlePath -Force
    & "$env:JAVA_HOME\bin\jarsigner.exe" -verify -verbose -certs $brandedBundlePath

    Write-Host ""
    Write-Host "Signed release bundle:" $brandedBundlePath
} finally {
    Remove-Item Env:LMT_UPLOAD_STORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:LMT_UPLOAD_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:LMT_UPLOAD_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:LMT_UPLOAD_KEY_PASSWORD -ErrorAction SilentlyContinue
}
