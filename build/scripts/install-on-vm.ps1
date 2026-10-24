# Ставлямо TesseraOS на вже запущену віртуальну машину з Android 13+ (Windows).
#
# Дзеркало build/scripts/install-on-vm.sh: та сама послідовність, ті самі
# найкращі спроби. Окремий файл, а не «один скрипт на дві ОС», бо з-під
# PowerShell bash-обгортка Git Bash ламає кавички в `adb shell` із пробілами.
#
#   .\install-on-vm.ps1                       # APK з ./apks поруч зі скриптом
#   .\install-on-vm.ps1 -Apks C:\tmp\tessera  # інший каталог
#   .\install-on-vm.ps1 -Serial emulator-5554 # якщо пристроїв кілька
#   .\install-on-vm.ps1 -DryRun               # показати, не виконувати
[CmdletBinding()]
param(
    # $PSScriptRoot у Windows PowerShell 5.1 під час обчислення значень за
    # замовчуванням ще порожній, тому каталог APK розвʼязуємо в тілі скрипта.
    [string]$Apks = "",
    [string]$Serial = "",
    [string]$Adb = "",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

# Консоль Windows типово в кодовій сторінці, де українські літери перетворюються
# на «?», тому вимикаємо це в самому скрипті, а не просимо користувача міняти chcp.
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

# Реєстр модулів тримаємо тут, а не в apps.list: PowerShell читає той самий
# формат, але трійка «каталог — модуль — package» має збігатися з реєстром,
# інакше adb поставить APK під одним іменем, а роль дасть іншому.
$Apps = @(
    @{ dir = "launcher";  module = "TesseraLauncher";  pkg = "os.tessera.launcher" },
    @{ dir = "calculator"; module = "TesseraCalculator"; pkg = "os.tessera.calculator" },
    @{ dir = "clock";     module = "TesseraClock";     pkg = "os.tessera.clock" },
    @{ dir = "dialer";    module = "TesseraDialer";    pkg = "os.tessera.dialer" },
    @{ dir = "files";     module = "TesseraFiles";     pkg = "os.tessera.files" },
    @{ dir = "messaging"; module = "TesseraMessaging"; pkg = "os.tessera.messaging" }
)

$Roles = @(
    @{ pkg = "os.tessera.launcher";  role = "HOME";        shell = "cmd package set-home-activity os.tessera.launcher/.LauncherActivity" },
    @{ pkg = "os.tessera.dialer";    role = "ROLE_DIALER"; shell = "cmd role add-role-packages-as-dialer os.tessera.dialer" },
    @{ pkg = "os.tessera.messaging"; role = "ROLE_SMS";    shell = "cmd role add-role-packages-as-sms os.tessera.messaging" }
)

$Permissions = @(
    @{ pkg = "os.tessera.dialer";    perm = "android.permission.CALL_PHONE" },
    @{ pkg = "os.tessera.dialer";    perm = "android.permission.READ_CALL_LOG" },
    @{ pkg = "os.tessera.dialer";    perm = "android.permission.READ_CONTACTS" },
    @{ pkg = "os.tessera.messaging"; perm = "android.permission.READ_SMS" },
    @{ pkg = "os.tessera.messaging"; perm = "android.permission.READ_CONTACTS" }
)

function Resolve-Adb {
    if ($Adb) { return $Adb }
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")) {
        if ($root -and (Test-Path (Join-Path $root "platform-tools\adb.exe"))) {
            return (Join-Path $root "platform-tools\adb.exe")
        }
    }
    $onPath = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    throw "не знайдено adb.exe — постав Android SDK (platform-tools) або вкажи -Adb"
}

$AdbPath = Resolve-Adb
$AdbArgs = @()
if ($Serial) { $AdbArgs = @("-s", $Serial) }

function Invoke-AdbShell([string]$command) {
    if ($DryRun) { Write-Host "  [dry-run] adb shell $command"; return $true }
    # -r не в shell, а тут: повертаємо код, щоб виклик міг сказати «увага».
    & $AdbPath @AdbArgs shell $command 2>&1 | Out-Null
    return $LASTEXITCODE -eq 0
}

$warnings = 0
function Add-Warning([string]$message) {
    $script:warnings++
    Write-Warning $message
}

Write-Host ">>> Пристрій"
$text = (& $AdbPath @AdbArgs devices) -join "`n"
$online = @($text -split "`n" | Where-Object { $_ -match "`tdevice$" } | ForEach-Object { ($_ -split "`t")[0] })
if ($online.Count -eq 0) { throw "немає підключеного пристрою — запусти VM і дочекайся завантаження" }
Write-Host ("  серійні: " + ($online -join ", "))

$apiText = (& $AdbPath @AdbArgs shell "getprop ro.build.version.sdk") -join ""
$api = 0
if (-not [int]::TryParse(($apiText -replace "\D", ""), [ref]$api)) {
    Write-Warning "не прочитали SDK гостьової системи — мінимося на свою відповідальність"
} elseif ($api -lt 33) {
    throw "наші застосунки потребують API 33+, у гостьовій системі — $api"
}
Write-Host "  API гостьової системи: $api"

if (-not $Apks) { $Apks = Join-Path $PSScriptRoot "apks" }
if (-not (Test-Path $Apks)) { throw "немає каталогу з APK: $Apks (розпакуй пакунок tessera-vm-*.tar.xz)" }

Write-Host ">>> Застосунки"
$installed = @()
foreach ($app in $Apps) {
    $apk = Join-Path $Apks ($app.module + ".apk")
    if (-not (Test-Path $apk)) {
        Add-Warning "$($app.dir): немає $apk — пропустити чи перестворити пакунок?"
        continue
    }
    if ($DryRun) { Write-Host "  [dry-run] adb install -r -t $apk"; $installed += $app.pkg; continue }
    & $AdbPath @AdbArgs install -r -t $apk | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  $($app.pkg)"
        $installed += $app.pkg
    } else {
        Add-Warning "$($app.pkg): adb install відмовив (підпис, ABI або вільне місце)"
    }
}
if ($installed.Count -eq 0) { throw "нічого не встановлено" }

Write-Host ">>> Ролі"
foreach ($row in $Roles) {
    if ($installed -notcontains $row.pkg) { continue }
    if (Invoke-AdbShell $row.shell) {
        Write-Host "  $($row.role) -> $($row.pkg)"
    } else {
        Add-Warning "$($row.role) для $($row.pkg) не призначено. Зроби вручну: adb shell $($row.shell)"
    }
}

Write-Host ">>> Дозволи"
foreach ($row in $Permissions) {
    if ($installed -notcontains $row.pkg) { continue }
    if (-not (Invoke-AdbShell "pm grant $($row.pkg) $($row.perm)")) {
        Add-Warning "$($row.pkg): $($row.perm) не видано"
    } else {
        Write-Host "  $($row.perm) -> $($row.pkg)"
    }
}

Write-Host ">>> Тема"
$overlayDir = Join-Path (Split-Path $Apks -Parent) "overlays"
if (-not (Test-Path $overlayDir)) {
    Write-Host "  оверлеїв у пакунку немає — образ виглядатиме стоково"
} else {
    foreach ($apk in (Get-ChildItem $overlayDir -Filter *.apk)) {
        $name = $apk.BaseName
        if ($DryRun) { Write-Host "  [dry-run] overlay $name"; continue }
        & $AdbPath @AdbArgs install -r -t $apk.FullName | Out-Null
        if ($LASTEXITCODE -ne 0) { Add-Warning "$name`: не поставився"; continue }
        if (-not (Invoke-AdbShell "cmd overlay enable $name")) {
            Add-Warning "$name`: cmd overlay не зміг увімкнути"
        }
    }
    Write-Host "  static-оверлеї перечитуються лише після перезавантаження: adb reboot"
}

Write-Host ""
if ($warnings -gt 0) {
    Write-Host "Готово з увагами ($warnings). Перевір рядки вище — зазвичай роль не доступна на образі без телефонії."
} else {
    Write-Host "Готово."
}
Write-Host "Швидка перевірка з VM:"
Write-Host "  adb shell settings get secure default_home"
Write-Host "  adb shell dumpsys package role_manager | grep -A2 ROLE_"
Write-Host "  adb shell cmd overlay list | grep tessera"
