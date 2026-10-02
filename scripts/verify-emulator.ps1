param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$reportDirectory = if ($OutputDirectory) { $OutputDirectory } else { Join-Path $projectRoot 'verification' }
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only the isolated viewer emulator may be used.' }
$avdName = & adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0) { throw 'Could not verify the dedicated viewer AVD.' }
if ($avdName -notcontains 'SimpleHtmlViewerApi35') { throw 'AVD must be named SimpleHtmlViewerApi35. Shared devices are not allowed.' }

function Invoke-Adb([string[]]$Arguments) {
    $output = & adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
    return $output
}
function Invoke-ViewerTest([string]$Phase, [string]$ReportName) {
    $output = Invoke-Adb -Arguments @('shell', 'am', 'instrument', '-w', '-e', 'phase', $Phase,
        'com.triphtml.viewer.test/com.triphtml.viewer.ViewerInstrumentation')
    $output | Set-Content -LiteralPath (Join-Path $reportDirectory $ReportName) -Encoding utf8
    $text = $output -join "`n"
    Write-Output $text
    if ($text -notmatch 'Viewer tests: \d+ passed\. 0 failed\.') { throw "Instrumentation phase failed: $Phase" }
}

# Android persists package enabled state lazily. A reboot soon after 'pm enable' can bring back the
# disabled state, so Maps is restored and verified both before and after the reboot.
function Restore-Maps {
    Invoke-Adb -Arguments @('shell', 'pm', 'enable', '--user', '0', 'com.google.android.apps.maps') | Out-Null
    $state = Invoke-Adb -Arguments @('shell', 'pm', 'list', 'packages', '-d', 'com.google.android.apps.maps')
    if (($state -join '') -match 'com\.google\.android\.apps\.maps') { throw 'Google Maps is still disabled after restore.' }
}

Restore-Maps
Invoke-Adb -Arguments @('install', '-r', (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'))
Invoke-Adb -Arguments @('install', '-r', (Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'))
Invoke-Adb -Arguments @('shell', 'wm', 'dismiss-keyguard')
Invoke-ViewerTest 'suite' 'instrumentation-suite.log'
Invoke-Adb -Arguments @('shell', 'am', 'force-stop', 'com.triphtml.viewer')
Invoke-ViewerTest 'relaunch' 'process-restart.log'

try {
    Invoke-Adb -Arguments @('shell', 'run-as', 'com.triphtml.viewer.test', 'touch', 'files/deleted')
    Invoke-ViewerTest 'deleted' 'file-deletion.log'
} finally {
    Invoke-Adb -Arguments @('shell', 'run-as', 'com.triphtml.viewer.test', 'rm', '-f', 'files/deleted')
}

Invoke-ViewerTest 'maps-installed' 'maps-installed.log'
try {
    Invoke-Adb -Arguments @('shell', 'pm', 'disable-user', '--user', '0', 'com.google.android.apps.maps')
    Invoke-ViewerTest 'maps-absent' 'maps-absent.log'
} finally {
    Restore-Maps
}

Invoke-Adb -Arguments @('reboot')
$deadline = (Get-Date).AddMinutes(3)
do {
    Start-Sleep -Seconds 2
    # 'device offline' on stderr is expected while rebooting. Windows PowerShell 5.1 turns redirected
    # native stderr into a terminating error under 'Stop', so only this probe tolerates it.
    $boot = try { & adb -s $Serial shell getprop sys.boot_completed 2>$null } catch { $null }
    if ((Get-Date) -gt $deadline) { throw 'Emulator did not finish rebooting.' }
} until ($boot -eq '1')
Restore-Maps
Invoke-Adb -Arguments @('shell', 'wm', 'dismiss-keyguard')
Invoke-ViewerTest 'relaunch' 'device-reboot.log'
Write-Output 'All viewer emulator checks passed.'

