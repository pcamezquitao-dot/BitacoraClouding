[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProjectDir = 'C:\Bitacora\BitacoraClouding'
$Server = 'root@161.22.47.89'
$KeyDir = Join-Path $ProjectDir '.susy6_ssh'
$PrivateKey = Join-Path $KeyDir 'id_ed25519'
$PublicKey = "$PrivateKey.pub"

if ((Resolve-Path -LiteralPath (Get-Location)).Path -ne $ProjectDir) {
    throw "Debe ejecutarse desde $ProjectDir"
}

New-Item -ItemType Directory -Force -Path $KeyDir | Out-Null
if (-not (Test-Path -LiteralPath $PrivateKey -PathType Leaf)) {
    & ssh-keygen -t ed25519 -N '""' -f $PrivateKey -C 'susy6-deploy'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la llave ed25519 dedicada.' }
}
if (-not (Test-Path -LiteralPath $PublicKey -PathType Leaf)) {
    throw "Falta la llave pública: $PublicKey"
}

$publicValue = (Get-Content -LiteralPath $PublicKey -Raw).Trim()
if ($publicValue -notmatch '^ssh-ed25519\s+') {
    throw 'La llave pública dedicada no tiene formato ed25519 válido.'
}

Write-Host 'ÚNICO PASO MANUAL: escriba ahora la contraseña SSH de root directamente en esta terminal.'
$oldPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& ssh -o ConnectTimeout=30 $Server `
    "umask 077; mkdir -p /root/.ssh; touch /root/.ssh/authorized_keys; grep -qxF '$publicValue' /root/.ssh/authorized_keys || printf '%s\n' '$publicValue' >> /root/.ssh/authorized_keys"
$sshInstallExit = $LASTEXITCODE
$ErrorActionPreference = $oldPreference
if ($sshInstallExit -ne 0) { throw 'No se pudo autorizar la llave pública en el servidor.' }

& ssh -i $PrivateKey -o BatchMode=yes -o ConnectTimeout=15 $Server 'hostname'
if ($LASTEXITCODE -ne 0) { throw 'La comprobación SSH BatchMode falló.' }

$probe = Join-Path $KeyDir 'scp_probe.txt'
[System.IO.File]::WriteAllText($probe, 'susy6-scp-ok')
& scp -i $PrivateKey -o BatchMode=yes $probe "${Server}:/tmp/susy6_scp_probe.txt"
if ($LASTEXITCODE -ne 0) { throw 'La comprobación SCP BatchMode falló.' }
& ssh -i $PrivateKey -o BatchMode=yes $Server "test -s /tmp/susy6_scp_probe.txt"
if ($LASTEXITCODE -ne 0) { throw 'El archivo de comprobación SCP no llegó correctamente.' }

Write-Host 'SSH sin contraseña: OK'
Write-Host "Llave dedicada: $PrivateKey"
