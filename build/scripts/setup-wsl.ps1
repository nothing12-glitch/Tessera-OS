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
"CPU логічних $allCPU: WSL бере $Processors"

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

@"
[wsl2]
memory=${MemoryGB}GB
processors=${Processors}
swap=${SwapGB}GB
nestedVirtualization=true
autoMemoryReclaim=disabled
localhostForwarding=true

[experimental]
sparseVhd=true
"@ | Set-Content -Path $cfgPath -Encoding ascii

"написано $cfgPath:"
Get-Content $cfgPath | ForEach-Object { "  $_" }

$installed = (wsl.exe --list --quiet 2>&1 | Out-String)
if ($installed -notmatch [regex]::Escape($Distro)) {
    Write-Error "дистрибутив '$Distro' не знайдено. Спершу: wsl --install --web-download -d $Distro, потім перезавантаж Windows."
}

wsl.exe --shutdown
"WSL вимкнено — нові ліміти застосуються при наступному запуску."

# Шлях до нашого репозиторію мовою WSL: /mnt/c/... Це потрібно, бо сам
# провіжинінг живе в Ubuntu і запускається під root.
$repoRoot = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$repoWsl = (wsl.exe --distribution $Distro --user root -- wslpath -u "$repoRoot" 2>&1 | Out-String).Trim()
if (-not $repoWsl) {
    Write-Error "wslpath не повернув шлях до $repoRoot — перевір, що диск C: змонтовано (wsl --mount)."
}
"репозиторій у WSL: $repoWsl"

wsl.exe --distribution $Distro --user root -- "bash" "$repoWsl/build/scripts/wsl-provision.sh"
if ($LASTEXITCODE -ne 0) { Write-Error "wsl-provision.sh завершився з кодом $LASTEXITCODE — див. вище." }

wsl.exe --shutdown
""
"Готово. Далі (від користувача 'tessera'):"
"  wsl -d $Distro -u tessera"
"  cd $repoWsl"
"  bash build/scripts/new-tree.sh lineage-22.2"
