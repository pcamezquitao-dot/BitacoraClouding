[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProjectDir = 'C:\Bitacora\BitacoraClouding'
$Server = 'root@161.22.47.89'
$RemoteDir = "/tmp/despliegue_susy6_$PID"
$RemoteReport = '/root/deploy_susy6_resultado.txt'
$AndroidDir = Join-Path $ProjectDir 'android\bitacora-android'
$LocalApk = Join-Path $ProjectDir 'susy6.apk'
$LogDir = Join-Path $ProjectDir '.susy6_logs'

$summary = [ordered]@{
    'pruebas locales' = 'FALLÓ'
    'respaldo remoto creado' = 'NO CREADO'
    'backend' = 'NO EJECUTADO'
    'servicio FastAPI' = 'NO EJECUTADO'
    'cantidad de bitácoras recibidas' = 'NO DISPONIBLE'
    'presencia de id_bitacora=68' = 'NO DISPONIBLE'
    'cantidad de evidencias de la bitácora 68' = 'NO DISPONIBLE'
    'APK' = 'NO GENERADA'
    'ruta de susy6.apk' = $LocalApk
    'SHA-256' = 'NO DISPONIBLE'
    'siguiente acción física en el celular' = 'Ninguna; revisar el error informado.'
}

function Show-Summary {
    Write-Host ''
    foreach ($item in $summary.GetEnumerator()) {
        Write-Host ("{0}: {1}" -f $item.Key, $item.Value)
    }
}

function Invoke-NativeLogged {
    param(
        [Parameter(Mandatory)][string]$Command,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$LogFile,
        [switch]$AllowFailure
    )
    $stderrFile = "$LogFile.stderr"
    $process = Start-Process -FilePath $Command -ArgumentList $Arguments `
        -Wait -PassThru -NoNewWindow `
        -RedirectStandardOutput $LogFile -RedirectStandardError $stderrFile
    if (Test-Path -LiteralPath $stderrFile) {
        Get-Content -LiteralPath $stderrFile | Add-Content -LiteralPath $LogFile
        Remove-Item -LiteralPath $stderrFile -Force
    }
    $exitCode = $process.ExitCode
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "Falló $Command (código $exitCode). Registro: $LogFile"
    }
    return $exitCode
}

try {
    if ((Resolve-Path -LiteralPath (Get-Location)).Path -ne $ProjectDir) {
        throw "Debe ejecutarse desde $ProjectDir"
    }
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

    $requiredLocalFiles = @(
        'app\routers\bitacora_uc03.py',
        'app\schemas\bitacora.py',
        'deploy_backend_susy6.sh'
    )
    foreach ($relativePath in $requiredLocalFiles) {
        if (-not (Test-Path -LiteralPath (Join-Path $ProjectDir $relativePath) -PathType Leaf)) {
            throw "Falta el archivo requerido: $relativePath"
        }
    }

    Invoke-NativeLogged -Command (Join-Path $ProjectDir 'venv\Scripts\python.exe') `
        -Arguments @('-m', 'unittest', 'discover', '-s', 'tests', '-v') `
        -LogFile (Join-Path $LogDir 'backend_tests.log') | Out-Null

    Push-Location $AndroidDir
    try {
        Invoke-NativeLogged -Command '.\gradlew.bat' -Arguments @('testDebugUnitTest') `
            -LogFile (Join-Path $LogDir 'android_tests.log') | Out-Null
    } finally {
        Pop-Location
    }
    $summary['pruebas locales'] = 'OK'

    $sshCheckLog = Join-Path $LogDir 'ssh_preflight.log'
    $sshExit = Invoke-NativeLogged -Command 'ssh' -Arguments @(
        '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', $Server, 'true'
    ) -LogFile $sshCheckLog -AllowFailure
    if ($sshExit -ne 0) {
        throw "SSH sin contraseña no está disponible. Configure una llave SSH antes del despliegue. Registro: $sshCheckLog"
    }

    Invoke-NativeLogged -Command 'ssh' -Arguments @(
        '-o', 'BatchMode=yes', $Server,
        "mkdir -p '$RemoteDir/app/routers' '$RemoteDir/app/schemas'"
    ) -LogFile (Join-Path $LogDir 'remote_prepare.log') | Out-Null

    Invoke-NativeLogged -Command 'scp' -Arguments @(
        '-o', 'BatchMode=yes',
        (Join-Path $ProjectDir 'app\routers\bitacora_uc03.py'),
        "${Server}:$RemoteDir/app/routers/bitacora_uc03.py"
    ) -LogFile (Join-Path $LogDir 'scp_router.log') | Out-Null
    Invoke-NativeLogged -Command 'scp' -Arguments @(
        '-o', 'BatchMode=yes',
        (Join-Path $ProjectDir 'app\schemas\bitacora.py'),
        "${Server}:$RemoteDir/app/schemas/bitacora.py"
    ) -LogFile (Join-Path $LogDir 'scp_schema.log') | Out-Null
    Invoke-NativeLogged -Command 'scp' -Arguments @(
        '-o', 'BatchMode=yes',
        (Join-Path $ProjectDir 'deploy_backend_susy6.sh'),
        "${Server}:$RemoteDir/deploy_backend_susy6.sh"
    ) -LogFile (Join-Path $LogDir 'scp_deploy_script.log') | Out-Null

    $deployExit = Invoke-NativeLogged -Command 'ssh' -Arguments @(
        '-o', 'BatchMode=yes', $Server,
        "bash '$RemoteDir/deploy_backend_susy6.sh' '$RemoteDir'"
    ) -LogFile (Join-Path $LogDir 'remote_deploy.log') -AllowFailure

    $localReport = Join-Path $LogDir 'deploy_susy6_resultado.txt'
    Invoke-NativeLogged -Command 'scp' -Arguments @(
        '-o', 'BatchMode=yes', "${Server}:$RemoteReport", $localReport
    ) -LogFile (Join-Path $LogDir 'scp_report.log') | Out-Null

    $report = @{}
    Get-Content -LiteralPath $localReport | ForEach-Object {
        if ($_ -match '^([^=]+)=(.*)$') { $report[$Matches[1]] = $Matches[2] }
    }
    $summary['respaldo remoto creado'] = $report['RESPALDO_REMOTO']
    $summary['backend'] = $report['BACKEND']
    $summary['servicio FastAPI'] = $report['FASTAPI']
    $summary['cantidad de bitácoras recibidas'] = $report['BITACORAS']
    $summary['presencia de id_bitacora=68'] = $report['ID_BITACORA_68']
    $summary['cantidad de evidencias de la bitácora 68'] = $report['EVIDENCIAS_68']

    if ($deployExit -ne 0 -or $report['BACKEND'] -ne 'ACTUALIZADO' -or $report['FASTAPI'] -ne 'ACTIVO') {
        throw "El backend no superó las validaciones y no se generará la APK. $($report['MENSAJE'])"
    }

    Push-Location $AndroidDir
    try {
        Invoke-NativeLogged -Command '.\gradlew.bat' -Arguments @('assembleProductionDebug') `
            -LogFile (Join-Path $LogDir 'android_build.log') | Out-Null
    } finally {
        Pop-Location
    }

    $builtApk = Join-Path $AndroidDir 'app\build\outputs\apk\production\debug\app-production-debug.apk'
    if (-not (Test-Path -LiteralPath $builtApk -PathType Leaf)) {
        throw "Gradle terminó pero no produjo la APK esperada: $builtApk"
    }
    if ((Get-Item -LiteralPath $builtApk).Length -le 0) {
        throw 'La APK generada tiene tamaño cero.'
    }
    Copy-Item -LiteralPath $builtApk -Destination $LocalApk -Force
    if ((Get-Item -LiteralPath $LocalApk).Length -le 0) {
        throw 'susy6.apk no existe o tiene tamaño cero.'
    }
    $summary['APK'] = 'GENERADA'
    $summary['SHA-256'] = (Get-FileHash -LiteralPath $LocalApk -Algorithm SHA256).Hash
    $summary['siguiente acción física en el celular'] = 'Instalar susy6.apk y verificar 64 bitácoras, la bitácora 68 y sus 2 evidencias.'
} catch {
    $summary['siguiente acción física en el celular'] = "Ninguna; $($_.Exception.Message)"
    Show-Summary
    exit 1
} finally {
    if (Test-Path -LiteralPath $LogDir) {
        # Los registros se conservan para auditoría; no se elimina ninguna APK.
    }
}

Show-Summary
exit 0
