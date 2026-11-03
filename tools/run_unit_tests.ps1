# Run JVM unit tests directly, bypassing `gradlew testDebugUnitTest`
# (the Gradle test worker cannot start on this machine).
#
# Usage:
#   cd Y:\iji-android
#   $env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
#   .\gradlew :app:compileDebugUnitTestKotlin --console=plain
#   powershell -NoProfile -File .\tools\run_unit_tests.ps1
#
# Notes:
# - Test classes are discovered by scanning the compiled output (*Test.class,
#   skipping inner classes). No need to edit this script when adding tests.
# - Classpath jars are resolved from the Gradle cache: junit / hamcrest /
#   kotlin-stdlib / org.json / coroutines / okhttp / okio. Add another entry
#   here if a new runtime dependency shows up.
# - Keep this file ASCII-only: Windows PowerShell 5.1 reads BOM-less files as
#   ANSI, and non-ASCII text breaks parsing.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path $java)) { throw "java not found: $java (set JAVA_HOME first)" }

$base = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
function Find-Jar([string]$relativeDir, [string]$filter) {
    $hit = Get-ChildItem (Join-Path $base $relativeDir) -Recurse -File -Filter $filter -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if (-not $hit) { throw "jar not found in Gradle cache: $relativeDir\$filter" }
    $hit.FullName
}

$jars = @(
    Find-Jar 'junit\junit\4.13.2' 'junit-4.13.2.jar'
    Find-Jar 'org.hamcrest\hamcrest-core\1.3' 'hamcrest-core-1.3.jar'
    Find-Jar 'org.jetbrains.kotlin\kotlin-stdlib' 'kotlin-stdlib-*.jar'
    Find-Jar 'org.json\json' 'json-*.jar'
    Find-Jar 'org.jetbrains.kotlinx\kotlinx-coroutines-core-jvm' 'kotlinx-coroutines-core-jvm-*.jar'
    Find-Jar 'com.squareup.okhttp3\okhttp' 'okhttp-*.jar'
    Find-Jar 'com.squareup.okio\okio-jvm' 'okio-jvm-*.jar'
)

$main = Join-Path $root 'app\build\intermediates\built_in_kotlinc\debug\compileDebugKotlin\classes'
$test = Join-Path $root 'app\build\intermediates\built_in_kotlinc\debugUnitTest\compileDebugUnitTestKotlin\classes'
foreach ($p in @($main, $test)) {
    if (-not (Test-Path $p)) { throw "missing build output: $p (run :app:compileDebugUnitTestKotlin first)" }
}

$classes = Get-ChildItem $test -Recurse -File -Filter '*Test.class' |
    Where-Object { $_.Name -notmatch '\$' } |
    ForEach-Object {
        $rel = $_.FullName.Substring($test.Length).TrimStart('\', '/')
        ($rel -replace '\\', '.' -replace '/', '.') -replace '\.class$', ''
    } | Sort-Object

if (-not $classes) { throw "no test classes found under $test" }

$cp = (@($test, $main) + $jars) -join ';'
Write-Host "running $($classes.Count) test classes..." -ForegroundColor Cyan
& $java '-Dfile.encoding=UTF-8' -cp $cp org.junit.runner.JUnitCore @classes
exit $LASTEXITCODE
