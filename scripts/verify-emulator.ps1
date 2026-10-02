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
function Invoke-Phase([string]$Class, [string]$Name, [string[]]$Extra = @()) {
    $result = Invoke-Adb (@('shell', 'am', 'instrument', '-w', '-e', 'class', "com.triphtml.viewer.$Class") +
        $Extra + @('com.triphtml.viewer.test/androidx.test.runner.AndroidJUnitRunner'))
    $result | Set-Content -LiteralPath (Join-Path $report "$Name.log") -Encoding utf8
    $result | Select-Object -Last 8
    if (($result -join "`n") -notmatch 'OK \(1 test\)') { throw "Android phase failed: $Class" }
}
& (Join-Path $PSScriptRoot 'verify-itinerary.ps1') -Serial $Serial -OutputDirectory $OutputDirectory
try {
    Invoke-Adb @('shell', 'run-as', 'com.triphtml.viewer.test', 'touch', 'files/deleted') | Out-Null
    Invoke-Phase 'DeletedDocumentTest' 'deleted'
} finally {
    Invoke-Adb @('shell', 'run-as', 'com.triphtml.viewer.test', 'rm', '-f', 'files/deleted') | Out-Null
}
$disabledMaps = (Invoke-Adb @('shell', 'pm', 'list', 'packages', '-d', 'com.google.android.apps.maps')) -join "`n"
try {
    Invoke-Adb @('shell', 'pm', 'disable-user', '--user', '0', 'com.google.android.apps.maps') | Out-Null
    Invoke-Phase 'MapsHandoffTest' 'maps-unavailable' @('-e', 'maps', 'absent')
} finally {
    if ($disabledMaps -notmatch 'package:com.google.android.apps.maps') {
        Invoke-Adb @('shell', 'pm', 'enable', 'com.google.android.apps.maps') | Out-Null
    }
}
& (Join-Path $PSScriptRoot 'verify-presentation.ps1') -Serial $Serial -OutputDirectory $OutputDirectory
Invoke-Adb @('shell', 'input', 'keyevent', 'HOME') | Out-Null
Write-Output 'Ten Android JUnit tests passed. Temporary Maps, font and display settings were restored.'
