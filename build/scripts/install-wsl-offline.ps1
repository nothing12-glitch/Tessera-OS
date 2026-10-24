<#
    Офлайн-установка WSL2 без «wsl --install».

    На цьому ноутбуці dl.delivery.mp.microsoft.com не відповідає (TLS-помилка),
    тому типова команда качає пакунок вічно й не завантажує нічого. Тут два
    файли з адрес, які працюють: MSI з релізів microsoft/WSL і rootfs Ubuntu з
    cloud-images.ubuntu.com. Їх спочатку качають окремо (докладно — docs/BUILD.md,
    «Чому не wsl --install»), потім запускають цей скрипт.

    Потрібні права адміністратора: msiexec ставить службу, а wsl --import
    створює VHDX. Скрипт НЕ перезавантажує машину; якщо гіпервізор не запущений,
    він про це скаже.

    Приклад:
        powershell -File build\scripts\install-wsl-offline.ps1 -Msi wsl.3.0.3.0.x64.msi -Rootfs ubuntu-rootfs.tar.gz
#>
param(
    [Parameter(Mandatory)][string]$Msi,
    [Parameter(Mandatory)][string]$Rootfs,
    [string]$Distro = 'Ubuntu-24.04',
    [string]$InstallDir = 'C:\WSL\Ubuntu-24.04'
)

$ErrorActionPreference = 'Stop'

$isAdmin = ([Security.Principal.WindowsPrincipal] `
    [Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Error 'потрібна адміністраторська сесія (msiexec і wsl --import).'
}

# --- 1. гіпервізор ---------------------------------------------------------
# Без запущеного гіпервізора import падає з HCS_E_SERVICE_NOT_AVAILABLE. Це
# легше перевірити заздалегідь, ніж гадати потім по коду помилки.
if (-not (Get-CimInstance Win32_ComputerSystem).HypervisorPresent) {
    Write-Error @'
гіпервізор Windows не запущений (HypervisorPresent = False).
Функції «Платформа віртуальної машини» та «Підсистема Windows для Linux» мають
бути Enabled, а в BCD — рядок hypervisorlaunchtype = Auto:

    bcdedit /set "{current}" hypervisorlaunchtype Auto      # від адміністратора

Після цього — перезавантаження, і лише потім цей скрипт.
'@
}

# --- 2. обидва файли до установлення -------------------------------------
if (-not (Test-Path $Msi))    { Write-Error "немає MSI: $Msi" }
if (-not (Test-Path $Rootfs)) { Write-Error "немає rootfs: $Rootfs" }

$sig = Get-AuthenticodeSignature $Msi
if ($sig.Status -ne 'Valid') {
    Write-Error "підпис MSI не валідний ($($sig.Status)) — не встановлюємо. Скачаний файл зіпсований або з чужого дзеркала."
}
"MSI підписано: $($sig.SignerCertificate.Subject)"

# --- 3. установка пакунка --------------------------------------------------
">>> msiexec /i $Msi"
$p = Start-Process msiexec.exe -ArgumentList "/i `"$Msi`" /qn /norestart" -Wait -PassThru -NoNewWindow
if ($p.ExitCode -ne 0) { Write-Error "msiexec повернув $($p.ExitCode)" }
wsl.exe --version

# --- 4. імпорт дистрибутива ------------------------------------------------
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
">>> wsl --import $Distro (корінь файлової системи: $Rootfs)"
wsl.exe --import $Distro $InstallDir $Rootfs --version 2
if ($LASTEXITCODE -ne 0) { Write-Error "wsl --import повернув $LASTEXITCODE" }

wsl.exe --list --verbose
"Готово. Далі: powershell -File build\scripts\setup-wsl.ps1 -Distro $Distro"
