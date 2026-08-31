$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$testOutput = Join-Path $projectRoot 'build/performance-policy-tests'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$sources = @(
    (Join-Path $projectRoot 'src/main/java/com/betterbees/hive/HiveRuntimeState.java'),
    (Join-Path $projectRoot 'src/main/java/com/betterbees/audio/BeeLoopSelector.java'),
    (Join-Path $projectRoot 'src/test/java/com/betterbees/PerformancePolicyTest.java')
)
& javac -d $testOutput @sources
if ($LASTEXITCODE -ne 0) { throw 'Performance policy test compilation failed' }
& java -cp $testOutput com.betterbees.PerformancePolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Performance policy tests failed' }
