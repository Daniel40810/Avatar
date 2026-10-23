# Legt auf dem Desktop eine Verknuepfung "Avatar Studio" an (Pfade relativ zu diesem Ordner)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar  = Join-Path $root 'dist\Avatar.jar'
$ico  = Join-Path $root 'icon\avatar.ico'

if (-not (Test-Path $jar)) { throw "JAR fehlt: $jar (in NetBeans Clean and Build)" }
if (-not (Test-Path $ico)) { throw "ICO fehlt: $ico" }

$javaw = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javaw.exe'))) {
    $javaw = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
} else {
    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($cmd) { $javaw = $cmd.Source }
}
if (-not $javaw) { throw "javaw.exe nicht gefunden - JAVA_HOME setzen oder Java in den PATH aufnehmen." }

$desktop = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desktop 'Avatar Studio.lnk'

$sh = New-Object -ComObject WScript.Shell
$s = $sh.CreateShortcut($lnk)
$s.TargetPath       = $javaw
$s.Arguments        = '-Xmx4g -Dsun.java2d.uiScale.enabled=true --enable-native-access=ALL-UNNAMED --add-exports java.base/java.lang=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED --add-exports java.desktop/sun.java2d=ALL-UNNAMED -jar "' + $jar + '"'
$s.WorkingDirectory = $root
$s.IconLocation     = $ico
$s.Description      = 'Avatar Studio - Ein Gesicht im Regen'
$s.Save()

Write-Host "Verknuepfung angelegt: $lnk"
