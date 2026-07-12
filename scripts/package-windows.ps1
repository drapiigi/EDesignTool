# Build a Windows application image with jpackage (JDK 21+ required).
# Run from repo root or scripts/:  powershell -File scripts/package-windows.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

Write-Host "==> Maven package"
mvn -q -DskipTests package

$Jar = Get-ChildItem target\gwire-designer-*.jar | Where-Object { $_.Name -notlike "original-*" } | Select-Object -First 1
if (-not $Jar) { throw "Shaded jar not found in target/" }

$Version = (mvn -q -DforceStdout help:evaluate -Dexpression=project.version).Trim()
$AppVersion = $Version -replace "-SNAPSHOT",""
$AppName = "GhanaWireAI"
$Dest = "target\dist\windows"
$Input = "target\jpackage-input"

Remove-Item -Recurse -Force $Dest,$Input -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Input | Out-Null
New-Item -ItemType Directory -Force -Path $Dest | Out-Null
Copy-Item $Jar.FullName (Join-Path $Input "gwire-designer.jar")
New-Item -ItemType Directory -Force -Path (Join-Path $Input "samples") | Out-Null
Copy-Item "src\main\resources\samples\ghana-3bed-house.gwire" (Join-Path $Input "samples\") -ErrorAction SilentlyContinue

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
  throw "jpackage not found. Install JDK 21+ and ensure it is on PATH."
}

Write-Host "==> jpackage app-image"
jpackage `
  --type app-image `
  --name $AppName `
  --app-version $AppVersion `
  --description "GhanaWire AI electrical wiring design for Ghana (L.I. 2008)" `
  --vendor "GhanaWire AI" `
  --input $Input `
  --main-jar gwire-designer.jar `
  --main-class com.ghana.gwire.Main `
  --dest $Dest `
  --java-options "-Dfile.encoding=UTF-8" `
  --java-options "--enable-native-access=ALL-UNNAMED"

Write-Host ""
Write-Host "Done: $Dest\$AppName"
Write-Host "Optional MSI: jpackage --type msi ... (same flags, --dest $Dest)"
