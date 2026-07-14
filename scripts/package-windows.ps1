# Build a Windows application image with jpackage (JDK 21+ required).
# MSI is best-effort and requires WiX Toolset on PATH.
# Run:  powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1

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
Copy-Item "LICENSE" $Input -ErrorAction SilentlyContinue
Copy-Item "docs\USER_GUIDE.md" $Input -ErrorAction SilentlyContinue

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
  throw "jpackage not found. Install JDK 21+ and ensure it is on PATH."
}

$Signed = if ($env:GWIRE_BUILD_SIGNED) { $env:GWIRE_BUILD_SIGNED } else { "false" }

Write-Host "==> jpackage app-image (version $AppVersion)"
jpackage `
  --type app-image `
  --name $AppName `
  --app-version $AppVersion `
  --description "GhanaWire AI electrical wiring design for Ghana (L.I. 2008) — design aid" `
  --vendor "GhanaWire AI" `
  --input $Input `
  --main-jar gwire-designer.jar `
  --main-class com.ghana.gwire.Main `
  --dest $Dest `
  --java-options "-Dfile.encoding=UTF-8" `
  --java-options "--enable-native-access=ALL-UNNAMED" `
  --java-options "-Dgwire.build.signed=$Signed"

$Versioned = Join-Path $Dest "$AppName-$AppVersion-windows-x64"
if (Test-Path (Join-Path $Dest $AppName)) {
  if (Test-Path $Versioned) { Remove-Item -Recurse -Force $Versioned }
  Copy-Item -Recurse (Join-Path $Dest $AppName) $Versioned
  Write-Host "Versioned copy: $Versioned"
}

Write-Host ""
Write-Host "Done: $Dest\$AppName"
Write-Host "Artifact: GhanaWireAI-$AppVersion-windows-x64\"

# Best-effort MSI (requires WiX Toolset — typically 3.x/4.x per jpackage docs)
$Wix = Get-Command candle,light,wix -ErrorAction SilentlyContinue
if ($Wix) {
  Write-Host "==> WiX detected — attempting MSI"
  try {
    jpackage `
      --type msi `
      --name $AppName `
      --app-version $AppVersion `
      --description "GhanaWire AI electrical design (L.I. 2008 practice)" `
      --vendor "GhanaWire AI" `
      --input $Input `
      --main-jar gwire-designer.jar `
      --main-class com.ghana.gwire.Main `
      --dest $Dest `
      --java-options "-Dfile.encoding=UTF-8" `
      --java-options "--enable-native-access=ALL-UNNAMED" `
      --win-menu `
      --win-shortcut
    Write-Host "MSI output under $Dest (e.g. GhanaWireAI-$AppVersion.msi)"
  } catch {
    Write-Host "Note: MSI packaging failed: $_"
    Write-Host "Ship the app-image zip if MSI is not required for this beta."
  }
} else {
  Write-Host "Note: WiX Toolset not found — MSI skipped (best-effort)."
  Write-Host "Install WiX 3.x/4.x and re-run for GhanaWireAI-$AppVersion.msi"
}

Write-Host ""
Write-Host "QA smoke: launch app → Help → Sample → Recalculate → Export PDF"
Write-Host "Unsigned beta by default; set GWIRE_BUILD_SIGNED=true for signed release builds."
