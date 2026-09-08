param(
    [string]$OutputDirectory = "$env:LOCALAPPDATA\POS Ticket Bridge\keys"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$propertiesPath = Join-Path $projectRoot "keystore.properties"
$keystorePath = Join-Path $OutputDirectory "posticketbridge-release.jks"

if ((Test-Path -LiteralPath $keystorePath) -or (Test-Path -LiteralPath $propertiesPath)) {
    throw "Ya existe el keystore o keystore.properties. No se sobrescribió ningún archivo."
}

$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
if (-not (Test-Path -LiteralPath $keytool)) {
    $keytool = (Get-Command keytool.exe -ErrorAction Stop).Source
}

function New-SecurePassword {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd('=')
}

$storePassword = New-SecurePassword
$keyPassword = New-SecurePassword
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

& $keytool -genkeypair `
    -keystore $keystorePath `
    -storepass $storePassword `
    -keypass $keyPassword `
    -alias posticketbridge `
    -keyalg RSA `
    -keysize 4096 `
    -storetype JKS `
    -validity 10000 `
    -dname "CN=POS Ticket Bridge, OU=Release, O=Luis Caro Dev, C=PE"

if ($LASTEXITCODE -ne 0) {
    throw "keytool no pudo crear la clave de firma."
}

$normalizedPath = $keystorePath.Replace('\', '/')
$properties = @(
    "storeFile=$normalizedPath"
    "storePassword=$storePassword"
    "keyAlias=posticketbridge"
    "keyPassword=$keyPassword"
) -join [Environment]::NewLine
[IO.File]::WriteAllText($propertiesPath, $properties + [Environment]::NewLine)

Write-Host "Keystore creado en: $keystorePath"
Write-Host "Configuración local creada en: $propertiesPath"
Write-Host "Respalda ambos archivos. No se mostraron las contraseñas en la terminal."
