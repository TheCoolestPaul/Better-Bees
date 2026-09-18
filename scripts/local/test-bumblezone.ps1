param([string]$LogDirectory = 'build/bumblezone-validation')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repo
try {
    New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null
    $lanes = @(
        @{ Name='neo-baseline'; Task=':mc1_21_1:runGameTestServer'; Flags=@() },
        @{ Name='neo-bumblezone'; Task=':mc1_21_1:runGameTestServer'; Flags=@('-PwithBumblezone=true') },
        @{ Name='neo-bumblezone-jade'; Task=':mc1_21_1:runGameTestServer'; Flags=@('-PwithBumblezone=true','-PwithJade=true') },
        @{ Name='neo-create-jade'; Task=':mc1_21_1:runGameTestServer'; Flags=@('-PwithCreate=true','-PwithJade=true','-Pjade_version=15.10.6+neoforge') },
        @{ Name='neo-all'; Task=':mc1_21_1:runGameTestServer'; Flags=@('-PwithBumblezone=true','-PwithCreate=true','-PwithJade=true') },
        @{ Name='fabric-baseline'; Task=':fabricMc1_21_1:runGameTest'; Flags=@('-Pfabric_target=fabricMc1_21_1') },
        @{ Name='fabric-bumblezone'; Task=':fabricMc1_21_1:runGameTest'; Flags=@('-Pfabric_target=fabricMc1_21_1','-PwithBumblezone=true') },
        @{ Name='fabric-bumblezone-jade'; Task=':fabricMc1_21_1:runGameTest'; Flags=@('-Pfabric_target=fabricMc1_21_1','-PwithBumblezone=true','-PwithJade=true') },
        @{ Name='quilt-baseline'; Task=':fabricMc1_21_1:runGameTest'; Flags=@('-Pfabric_target=fabricMc1_21_1','-PwithQuilt=true') },
        @{ Name='quilt-bumblezone'; Task=':fabricMc1_21_1:runGameTest'; Flags=@('-Pfabric_target=fabricMc1_21_1','-PwithQuilt=true','-PwithBumblezone=true') },
        @{ Name='quilt-bumblezone-jade'; Task=':fabricMc1_21_1:runGameTest'; Flags=@('-Pfabric_target=fabricMc1_21_1','-PwithQuilt=true','-PwithBumblezone=true','-PwithJade=true') }
    )
    $results = @()
    foreach ($lane in $lanes) {
        Write-Output "Testing $($lane.Name)"
        $log = Join-Path $LogDirectory "$($lane.Name).log"
        $run = Join-Path $repo "run/compat/matrix/$($lane.Name)"
        $arguments = @($lane.Task, '--no-configuration-cache', "-PupgradeGameDirectory=$run") + $lane.Flags
        & .\gradlew.bat @arguments *> $log
        $code = $LASTEXITCODE
        $text = Get-Content -LiteralPath $log -Raw
        $passed = $code -eq 0 -and $text -match 'All \d+ required tests passed'
        $results += @{ lane=$lane.Name; passed=$passed; exitCode=$code; log=$log }
        $results | ConvertTo-Json | Set-Content (Join-Path $LogDirectory 'results.json')
        if (!$passed) { throw "Compatibility lane failed: $($lane.Name). See $log" }
    }
} finally { Pop-Location }
