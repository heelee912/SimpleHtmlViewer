param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$OutputDirectory = 'verification\android'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$report = Join-Path $root $OutputDirectory
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only the dedicated viewer emulator is allowed.' }
if ((& $adb -s $Serial emu avd name) -notcontains 'SimpleHtmlViewerApi35') { throw 'AVD identity mismatch.' }
New-Item -ItemType Directory -Path $report -Force | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    $result = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return $result
}
function Invoke-Phase([string]$Class, [string]$Name, [int]$Count, [string[]]$Extra = @()) {
    $result = Invoke-Adb (@('shell', 'am', 'instrument', '-w', '-e', 'class', "com.triphtml.viewer.$Class") +
        $Extra + @('com.triphtml.viewer.test/androidx.test.runner.AndroidJUnitRunner'))
    $result | Set-Content -LiteralPath (Join-Path $report "$Name.log") -Encoding utf8
    $result | Select-Object -Last 8
    if (($result -join "`n") -notmatch "OK \($Count tests?\)") { throw "Android phase failed: $Class" }
}
$font = (Invoke-Adb @('shell', 'settings', 'get', 'system', 'font_scale')) -join ''
$size = (Invoke-Adb @('shell', 'wm', 'size')) -join "`n"
$density = (Invoke-Adb @('shell', 'wm', 'density')) -join "`n"
if ($density -notmatch '(?m)^Physical density: 420\s*$' -or $density -match 'Override') {
    throw 'The narrow-screen test requires the dedicated AVD at its original 420 dpi.'
}
$restoreSize = if ($size -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
try {
    Invoke-Adb @('shell', 'wm', 'size', '840x1600') | Out-Null
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'font_scale', '2.0') | Out-Null
    Invoke-Adb @('shell', 'am', 'force-stop', 'com.triphtml.viewer') | Out-Null
    Invoke-Phase 'PresentationTest' 'presentation' 2
    try {
        Invoke-Adb @('shell', 'run-as', 'com.triphtml.viewer.test', 'touch', 'files/deleted') | Out-Null
        Invoke-Phase 'DeletedDocumentTest' 'presentation-deleted' 1 @('-e', 'orientation', 'landscape')
    } finally {
        Invoke-Adb @('shell', 'run-as', 'com.triphtml.viewer.test', 'rm', '-f', 'files/deleted') | Out-Null
    }
} finally {
    Invoke-Adb @('shell', 'wm', 'size', $restoreSize) | Out-Null
    if ($font -eq 'null') { Invoke-Adb @('shell', 'settings', 'delete', 'system', 'font_scale') | Out-Null }
    else { Invoke-Adb @('shell', 'settings', 'put', 'system', 'font_scale', $font) | Out-Null }
}
