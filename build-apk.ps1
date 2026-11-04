param([string]$AndroidSdk=$env:ANDROID_HOME,[string]$Java=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(-not $AndroidSdk){$AndroidSdk=Join-Path $env:USERPROFILE 'chm-tools\sdk'}
if(-not $Java -or -not(Test-Path -LiteralPath (Join-Path $Java 'bin\java.exe'))){$Java=Join-Path $env:USERPROFILE 'chm-tools\jdk'}
$env:ANDROID_HOME=$AndroidSdk
$env:JAVA_HOME=$Java
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat "-Dorg.gradle.java.home=$($Java.Replace('\','/'))" :core:test :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --console=plain --no-daemon
    if($LASTEXITCODE -ne 0){throw 'Gradle: сборка или проверки не прошли'}
    $version=[regex]::Match((Get-Content -LiteralPath app\build.gradle -Raw),"versionName\s*=\s*'([^']+)'").Groups[1].Value
    if(-not $version){throw 'Не найдена версия приложения'}
    $output=Join-Path dist "CHM-Reader-$version.apk"
    New-Item -ItemType Directory -Force dist | Out-Null
    Copy-Item -LiteralPath app\build\outputs\apk\release\app-release.apk -Destination $output -Force
    & (Join-Path $AndroidSdk 'build-tools\37.0.0\apksigner.bat') verify --verbose $output
    if($LASTEXITCODE -ne 0){throw 'Проверка подписи APK не прошла'}
    & (Join-Path $AndroidSdk 'build-tools\37.0.0\zipalign.exe') -c -P 16 4 $output
    if($LASTEXITCODE -ne 0){throw 'Проверка выравнивания APK не прошла'}
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {Pop-Location}
