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

# Реєстр модулів читаємо з apps.list поруч зі скриптом — той самий файл, з
# якого CI будує матрицю і який у пакунок кладе make-vm-bundle.sh. Раніше
# перелік жив ще й тут окремим масивом; такий дубль ловив клас помилки
# «застосунок у реєстрі є, а на Windows його ніхто не ставить». Порожній
# (закоментований) реєстр — легальний стан продукту від 2026-10-25: тоді
# інсталятор ставить лише тему.
$Apps = @()
$RegistryPath = Join-Path $PSScriptRoot "apps.list"
if (Test-Path $RegistryPath) {
    foreach ($line in (Get-Content $RegistryPath -Encoding UTF8)) {
        if ($line -match "^\s*#" -or $line -notmatch "\|") { continue }
        $fields = $line -split "\|"
        if ($fields.Count -lt 3) { continue }
        $Apps += @{ dir = $fields[0].Trim(); module = $fields[1].Trim(); pkg = $fields[2].Trim() }
    }
    Write-Host ("  реєстр: " + $RegistryPath + " — застосунків: " + $Apps.Count)
} else {
    Write-Warning "немає $RegistryPath поруч зі скриптом — ставимо лише тему"
}

# Ролі й дозволи навмисно залишаються списком «package → дія»: вони срацюють
# лише для тих пакетів, які справді встановились вище, тому порожній реєстр
# робить ці блоки мовчазними, а не помилковими.
$Roles = @(
    @{ pkg = "os.tessera.launcher";  role = "HOME";        shell = "cmd package set-home-activity os.tessera.launcher/.LauncherActivity" },
    @{ pkg = "os.tessera.dialer";    role = "ROLE_DIALER"; shell = "cmd role add-role-packages-as-dialer os.tessera.dialer" },
    @{ pkg = "os.tessera.messaging"; role = "ROLE_SMS";    shell = "cmd role add-role-packages-as-sms os.tessera.messaging" }
)

$Permissions = @(
    @{ pkg = "os.tessera.camera";    perm = "android.permission.CAMERA" },
    @{ pkg = "os.tessera.camera";    perm = "android.permission.RECORD_AUDIO" },
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
    # Раніше це був throw. Тепер у пакунку може не бути жодного APK з minSdk 33
    # (власних застосунків у продукті немає), тому зупинятися через застарілу
    # гостьову систему — значення не мати: поставимо тему, а на решту попередимо.
    Write-Warning "гостьова система на API ${api}: тема розрахована на Android 13+, нашому APK (minSdk 33) тут би не стали"
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
# Порожній реєстр не зупиняє установлення: далі йде тема, а саме вона в
# продукті й лишилась.
if ($installed.Count -eq 0) { Write-Host "  власних застосунків у реєстрі немає — далі лише тема" }

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
    if ($installed.Count -eq 0) {
        Add-Warning "ні застосунків, ні теми: пакунок порожній. Перестворіть його make-vm-bundle.sh --themes"
    }
} else {
    # packages.list дає пару «файл APK → package з маніфеста». Команда
    # cmd overlay enable розуміє лише package, а імʼя файла (TesseraIconShapeTheme)
    # для неї нічого не вмикає.
    $map = @{}
    $list = Join-Path $overlayDir "packages.list"
    if (Test-Path $list) {
        foreach ($line in (Get-Content $list)) {
            $pair = $line -split '\|', 2
            if ($pair.Length -eq 2) { $map[$pair[0]] = $pair[1] }
        }
    }
    foreach ($apk in (Get-ChildItem $overlayDir -Filter *.apk)) {
        $name = $apk.BaseName
        $pkg = $map[$name]
        if ($DryRun) {
            if ($pkg) { Write-Host "  [dry-run] adb install -r -t $name.apk; adb shell cmd overlay enable --persistent $pkg" }
            else { Write-Host "  [dry-run] adb install -r -t $name.apk (package не в packages.list)" }
            continue
        }
        & $AdbPath @AdbArgs install -r -t $apk.FullName | Out-Null
        if ($LASTEXITCODE -ne 0) { Add-Warning "$name`: не поставився"; continue }
        if (-not $pkg) {
            Add-Warning "$name`: package немає в overlays/packages.list - увімкни вручну: adb shell cmd overlay enable <package>"
            continue
        }
        if (-not (Invoke-AdbShell "cmd overlay enable --persistent $pkg")) {
            if (-not (Invoke-AdbShell "cmd overlay enable $pkg")) {
                Add-Warning "$pkg`: cmd overlay не зміг увімкнути (приватні ресурси framework вимагають платформного ключа)"
                continue
            }
        }
        Write-Host "  $pkg -> $name"
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
