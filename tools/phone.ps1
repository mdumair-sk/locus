param (
    [Parameter(Position = 0)]
    [ValidateSet("status", "deploy", "test", "logcat", "ssh")]
    [string]$Command = "status",

    [Parameter(Position = 1)]
    [string]$Flavor = "oss",

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ArgsList
)

$Port = 8022
$KeyPath = Join-Path $HOME ".ssh\id_turbotransfer"

function Ensure-Tunnel {
    adb forward tcp:$Port tcp:8022 2>$null
}

function Invoke-PhoneSSH {
    param([string]$RemoteCmd)
    Ensure-Tunnel
    $sshArgs = @("-p", "$Port", "-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null", "-o", "LogLevel=ERROR")
    if (Test-Path $KeyPath) { $sshArgs += @("-i", $KeyPath) }
    $sshArgs += @("localhost", $RemoteCmd)
    & ssh @sshArgs
}

switch ($Command) {
    "status" {
        Write-Host "=== ADB Devices ===" -ForegroundColor Cyan
        adb devices -l
        Write-Host "=== Phone SSH Node ===" -ForegroundColor Cyan
        Ensure-Tunnel
        Invoke-PhoneSSH "uname -a; echo -n 'Cores: '; nproc"
    }
    "deploy" {
        Write-Host "Building $Flavor flavor..." -ForegroundColor Cyan
        if ($Flavor -eq "full") {
            .\gradlew.bat :app:assembleFullDebug
        } else {
            .\gradlew.bat :app:assembleOssDebug
        }
        $apk = "app\build\outputs\apk\$Flavor\debug\app-$Flavor-debug.apk"
        Write-Host "Installing $apk to device..." -ForegroundColor Cyan
        adb install -r $apk
        Write-Host "Launching MainActivity..." -ForegroundColor Cyan
        adb shell am start -n com.locus.app/.MainActivity
    }
    "test" {
        Write-Host "Running connected Android tests..." -ForegroundColor Cyan
        .\gradlew.bat connectedOssDebugAndroidTest
    }
    "logcat" {
        adb logcat -v time -s LocusApplication:V MainActivity:V *:E
    }
    "ssh" {
        Invoke-PhoneSSH ($ArgsList -join " ")
    }
}
