param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$OutputDirectory = 'verification\itinerary-20261003'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$report = Join-Path $projectRoot $OutputDirectory
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if ($Serial -notmatch '^emulator-\d+$') { throw 'This check only accepts the dedicated viewer emulator.' }
$avd = & $adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0 -or $avd -notcontains 'SimpleHtmlViewerApi35') { throw 'Dedicated viewer AVD identity mismatch.' }
New-Item -ItemType Directory -Path $report -Force | Out-Null

function Invoke-Adb([string[]]$Arguments) {
    $result = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return $result
}

function Invoke-Phase([string]$Class, [string]$Name) {
    $result = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-e', 'class', "com.triphtml.viewer.$Class",
        'com.triphtml.viewer.test/androidx.test.runner.AndroidJUnitRunner')
    $result | Set-Content -LiteralPath (Join-Path $report "$Name.log") -Encoding utf8
    $result | Select-Object -Last 8
    # Instrumentation can report a failed assertion while adb itself returns exit code zero.
    if (($result -join "`n") -notmatch 'OK \(1 test\)') { throw "Android phase failed: $Class" }
    # The runner may already have ended its process. Optional log collection must not fail a passed test.
    $viewerProcess = ((& $adb -s $Serial shell pidof com.triphtml.viewer 2>$null) -join '').Trim()
    if ($viewerProcess -match '^\d+$') {
        Invoke-Adb @('logcat', '-d', '--pid', $viewerProcess, '-s', 'ItineraryTest:I', 'ViewerJourney:I', '*:S') |
            Set-Content -LiteralPath (Join-Path $report "$Name-steps.log") -Encoding utf8
    }
}

# Build with the existing Gradle setup first. This script changes only this AVD's viewer and test app.
Invoke-Adb @('install', '-r', (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'))
Invoke-Adb @('install', '-r', (Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'))
# The test provider materializes assets once. Refresh only the three generated fixture copies.
Invoke-Adb @('shell', 'run-as', 'com.triphtml.viewer.test', 'rm', '-f',
    'files/itinerary-five.html', 'files/itinerary-eight.html', 'files/itinerary-template.html') | Out-Null
Invoke-Phase 'ItineraryJourneyTest' 'final-itinerary'
Invoke-Adb @('shell', 'am', 'force-stop', 'com.triphtml.viewer') | Out-Null
Invoke-Phase 'ItineraryRelaunchTest' 'final-itinerary-relaunch'
Invoke-Phase 'ReaderJourneyTest' 'final-reader'
Invoke-Adb @('shell', 'am', 'force-stop', 'com.triphtml.viewer') | Out-Null
Invoke-Phase 'RelaunchTest' 'final-reader-relaunch'
Invoke-Phase 'MapsHandoffTest' 'final-reader-maps'
Write-Output 'Five JUnit tests passed, including the itinerary and reader journeys.'
