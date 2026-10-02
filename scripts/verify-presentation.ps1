param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$reportDirectory = if ($OutputDirectory) { $OutputDirectory } else { Join-Path $projectRoot 'verification\codex-review' }
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only the isolated viewer emulator may be used.' }

function Invoke-Adb([string[]]$Arguments) {
    $output = & adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
    return $output
}

$avdName = Invoke-Adb @('emu', 'avd', 'name')
if ($avdName -notcontains 'SimpleHtmlViewerApi35') { throw 'AVD must be named SimpleHtmlViewerApi35.' }
$originalFont = (Invoke-Adb @('shell', 'settings', 'get', 'system', 'font_scale')) -join ''
$originalSize = (Invoke-Adb @('shell', 'wm', 'size')) -join "`n"
$originalDensity = (Invoke-Adb @('shell', 'wm', 'density')) -join "`n"
if ($originalDensity -notmatch '(?m)^Physical density: 420\s*$' -or $originalDensity -match 'Override') {
    throw 'The narrow-screen fixture expects an unmodified 420 dpi viewer AVD.'
}
$restoreSize = if ($originalSize -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
try {
    # 840 pixels at 420 dpi gives a 320dp-wide screen. Landscape is deliberately short.
    Invoke-Adb @('shell', 'wm', 'size', '840x1600') | Out-Null
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'font_scale', '2.0') | Out-Null
    Invoke-Adb @('shell', 'am', 'force-stop', 'com.triphtml.viewer') | Out-Null
    $output = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-e', 'phase', 'presentation',
        'com.triphtml.viewer.test/com.triphtml.viewer.ViewerInstrumentation')
    $output | Set-Content -LiteralPath (Join-Path $reportDirectory 'presentation.log') -Encoding utf8
    $text = $output -join "`n"
    Write-Output $text
    if ($text -notmatch 'Viewer tests: 3 passed\. 0 failed\.') { throw 'Presentation checks failed.' }
    try {
        # The fixture provider belongs to the test APK. Only its UID can mark its document deleted.
        Invoke-Adb @('shell', 'run-as', 'com.triphtml.viewer.test', 'touch', 'files/deleted') | Out-Null
        $output = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-e', 'phase', 'presentation-deleted',
            'com.triphtml.viewer.test/com.triphtml.viewer.ViewerInstrumentation')
        $output | Set-Content -LiteralPath (Join-Path $reportDirectory 'presentation-deleted.log') -Encoding utf8
        $text = $output -join "`n"
        Write-Output $text
        if ($text -notmatch 'Viewer tests: 1 passed\. 0 failed\.') { throw 'Recovery presentation check failed.' }
    } finally {
        Invoke-Adb @('shell', 'run-as', 'com.triphtml.viewer.test', 'rm', '-f', 'files/deleted') | Out-Null
    }
} finally {
    Invoke-Adb @('shell', 'wm', 'size', $restoreSize) | Out-Null
    if ($originalFont -eq 'null') {
        Invoke-Adb @('shell', 'settings', 'delete', 'system', 'font_scale') | Out-Null
    } else {
        Invoke-Adb @('shell', 'settings', 'put', 'system', 'font_scale', $originalFont) | Out-Null
    }
}
