<#
    Налаштування WSL2 під збірку TesseraOS.

    AOSP-клас дерево збирається в пам'яті, а типові ліміти WSL (50% RAM,
    чверть CPU) для нього замалі — збірка вмирає на OOM через дві години.
    Скрипт тому пише %USERPROFILE%\.wslconfig і далі запускає провіжинінг
    всередині Ubuntu (користувач, /etc/wsl.conf, пакунки).

    Запускати ОДИН РАЗ після 'wsl --install' і перезавантаження Windows.
    Документація: docs/BUILD.md, розділ «Шлях B — WSL2».

    Приклади:
        powershell -File build\scripts\setup-wsl.ps1
        powershell -File build\scripts\setup-wsl.ps1 -MemoryGB 10 -Processors 8
#>
param(
    [string]$Distro = 'Ubuntu-24.04',
    [int]$MemoryGB = 0,
    [int]$Processors = 0,
    [int]$SwapGB = 0
)

$ErrorActionPreference = 'Stop'

# Гіпервізор має бути запущений: без нього імпорт дистрибутива й будь-який
# запуск WSL падають з HCS_E_SERVICE_NOT_AVAILABLE. На цьому ноутбуці рядка
# hypervisorlaunchtype у BCD взагалі не було, тому перевірка іде першою —
# до запису .wslconfig і до будь-якого качання.
if (-not (Get-CimInstance Win32_ComputerSystem).HypervisorPresent) {
    Write-Error @'
гіпервізор Windows не запущений (HypervisorPresent = False).
Лікується однією командою в адміністраторській сесії:

    bcdedit /set "{current}" hypervisorlaunchtype Auto

і перезавантаженням. Докладніше: docs/BUILD.md, розділ «Шлях B».
'@
}

$cs = Get-CimInstance Win32_ComputerSystem
$totalGB = [math]::Round($cs.TotalPhysicalMemory / 1GB)
$allCPU = $cs.NumberOfLogicalProcessors
if (-not $allCPU) { $allCPU = 4 }

# Windows лишаємо 4 ГБ: якщо WSL візьме все, оболонка сама починає свопитись
# і збірка уповільнюється сильніше, ніж від меншого -j.
if ($MemoryGB -le 0) { $MemoryGB = [math]::Max(6, $totalGB - 4) }
if ($Processors -le 0) { $Processors = [math]::Max(4, $allCPU - 2) }
if ($SwapGB -le 0) { $SwapGB = $MemoryGB }

"RAM всього $totalGB ГБ: WSL бере $MemoryGB ГБ, Windows — $($totalGB - $MemoryGB) ГБ"
"CPU логічних ${allCPU}: WSL бере $Processors"

$freeGB = [math]::Round((Get-CimInstance Win32_LogicalDisk -Filter 'DeviceID="C:"').FreeSpace / 1GB, 1)
"вільно на C:: $freeGB ГБ"
if ($freeGB -lt 300) {
    Write-Warning "менше 300 ГБ вільно: дерево lineage-22.2 з out/ потребує 300-400 ГБ. Sync може оборватися."
}

$cfgPath = Join-Path $env:USERPROFILE '.wslconfig'
if (Test-Path $cfgPath) {
    $backup = "$cfgPath.bak-$(Get-Date -Format yyyyMMddHHmmss)"
    Copy-Item $cfgPath $backup
    "наявний .wslconfig збережено: $backup"
}

# autoMemoryReclaim — саме в [experimental]: у [wsl2] WSL 3.0.3 такого ключа не
# знає й пише попередження на кожне звертання до wsl.exe. Вимкнене воно тому, що
# інакше WSL відбирає вільну пам'ять посеред збірки і уповільнює її.
@"
[wsl2]
memory=${MemoryGB}GB
processors=${Processors}
swap=${SwapGB}GB
nestedVirtualization=true
localhostForwarding=true

[experimental]
sparseVhd=true
autoMemoryReclaim=disabled
"@ | Set-Content -Path $cfgPath -Encoding ascii

"написано ${cfgPath}:"
Get-Content $cfgPath | ForEach-Object { "  $_" }

# Реєстрацію дистрибутива читаємо з реєстру, а не з виводу `wsl --list`: wsl.exe
# говорить UTF-16, і PowerShell 5.1 повертає з нього «касилю», який уже ні на що
# не схожий. Ключ Lxss — першоджерело, і кодування тут не заважає.
$names = Get-ChildItem 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Lxss' -ErrorAction SilentlyContinue |
    ForEach-Object { (Get-ItemProperty $_.PSPath).DistributionName }
if ($names -notcontains $Distro) {
    Write-Error @"
дистрибутив '$Distro' не зареєстрований. Спершу — офлайн-установка
    powershell -File build\scripts\install-wsl-offline.ps1 -Msi <wsl.msi> -Rootfs <rootfs.tar.gz>
(звичайний 'wsl --install' на цьому хості не качає нічого: CDN Microsoft
відхиляє TLS-з'єднання). Деталі: docs/BUILD.md, «Чому не wsl --install».
Наявні дистрибутиви: $(if ($names) { $names -join ', ' } else { 'немає' })
"@
}

# Шлях до репозиторію мовою WSL: C:\... перекладаємо у /mnt/c/... самі. Через
# `wslpath` було б те саме, що з `wsl --list`: wsl.exe пише UTF-16, і PowerShell
# 5.1 повертає з його виводу текст із зайвими нулями. Обчислюємо тут, до
# перемикача нижче, щоб Write-Error ще зупиняв скрипт.
$repoRoot = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
if ($repoRoot -notmatch '^[A-Za-z]:\\') {
    Write-Error "несподіваний шлях репозиторію '$repoRoot' — мав бути абсолютний шлях диска Windows."
}
$repoWsl = '/mnt/' + $repoRoot.Substring(0, 1).ToLower() + '/' + $repoRoot.Substring(3).Replace('\', '/')
$provisionSh = "$repoWsl/build/scripts/wsl-provision.sh"
"репозиторій у WSL: $repoWsl"

# Нижче — суто зовнішні виклики. wsl.exe пише службове (і будь-яке
# попередження) у stderr, а PowerShell 5.1 при $ErrorActionPreference='Stop'
# рахує це фатальною помилкою й обриває скрипт посеред провіжиніння. Тому тут
# судимо лише за кодом виходу й виходимо самі.
$ErrorActionPreference = 'Continue'

wsl.exe --shutdown
"WSL вимкнено — нові ліміти застосуються при наступному запуску."

wsl.exe --distribution $Distro --user root -- test -f $provisionSh
if ($LASTEXITCODE -ne 0) {
    Write-Error "середині WSL немає $provisionSh — перевір, чи диск C: змонтовано (/mnt/c) і чи репозиторій на місці."
    exit 1
}

wsl.exe --distribution $Distro --user root -- bash $provisionSh
if ($LASTEXITCODE -ne 0) {
    Write-Error "wsl-provision.sh завершився з кодом $LASTEXITCODE — див. вище."
    exit 1
}

wsl.exe --shutdown
""
"Готово. Далі (від користувача 'tessera', в ext4-теці, не на /mnt/c):"
"  wsl -d $Distro -u tessera"
"  cd ~/tessera-os"
"  REPO_DEPTH=1 bash build/scripts/new-tree.sh lineage-22.2"
