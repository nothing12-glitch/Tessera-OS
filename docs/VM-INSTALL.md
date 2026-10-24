# TesseraOS у віртуальній машині

Три шляхи, і вони різняться не «швидкістю», а тим, що саме ти отримуєш.
Обирай за тим, скільки в тебе є місця і чи потрібен тобі справжній образ.

| Шлях | Що отримуєш | Потребує | Час |
|---|---|---|---|
| 1. Пакунок на наявний образ | наші застосунки, тема, ролі поверх будь-якого Android 13+ у VM | adb + 2 ГБ | ~15 хв |
| 2. Збірка образу ROM | самостійний `.img` TesseraOS, який запускається без чужої бази | Linux, 250+ ГБ, 16–32 ГБ RAM | 2–6 год |
| 3. Готовий образ із релізу | те саме, що шлях 2, без збірки | 20–40 ГБ | хвилини завантаження |

Шлях 3 ще не існує: перший образ з'явиться разом із віхою M1
([ARCHITECTURE.md](ARCHITECTURE.md) — roadmap), коли шлях 2
відпрацює на білд-боксі. Поки що в GitHub Release лежить пакунок із шляху 1 —
його збирає `.github/workflows/release.yml`.

## Шлях 1. Пакунок поверх наявного образу

Це не заміна ROM, а те, чим можна скористатися сьогодні: той самий інтерфейс
і ті самі застосунки, але на чужій базі. Гостьова система має бути **Android 13
або новіше (API 33+)** — мінімальний API наших APK; на Android-x86 9 (API 28)
встановлення відхилиться, і це не наша примха, а вимога формату APK.

1. Завантаж пакунок `tessera-vm-<версія>.tar.xz` зі
   [сторінки релізів](https://github.com/nothing12-glitch/Tessera-OS/releases)
   і звір суму з `<...>.tar.xz.sha256`.
2. Розпакуй. Всередині: `apks/`, `apps.list`, `install-on-vm.sh`,
   `install-on-vm.ps1`, `README.txt`.
3. Запусти гостьову систему так, щоб хост бачив її через adb
   (для емулятора — `-ports` або стандартний `emulator-5554`; для VM із мережевим
   adb — `adb connect <IP>:5555`).
4. Встанови:

   ```bash
   ./install-on-vm.sh              # Linux, macOS, Git Bash
   ```
   ```powershell
   .\install-on-vm.ps1             # Windows
   ```

5. Перезавантаж гостьову систему: `adb reboot` — static-оверлеї теми
   перечитуються лише на ранньому завантаженні.

Скрипт робить чотири речі й робить їх найкращими спробами, а не «або все, або
програш»: ставить APK, призначає `HOME`, `ROLE_DIALER` і `ROLE_SMS`, видає
відсутні дозволи й вмикає оверлеї, якщо вони в пакунку. Кожне «увага» в кінці —
це рядок, який варто перечитати: наприклад, роль набору не доступна на образі
без телефонії, і тоді «Телефон» лишається звичайною клавіатурою набору.

**Якщо щось пішло не так**

- `.\install-on-vm.ps1 -DryRun` показує повний план (APK, ролі, дозволи) без
  жодної команди на пристрої — корисно перевірити, чи скрипт взагалі бачить пакунок.
- PowerShell видає «Unexpected token» на українських рядках — файл втратив
  UTF-8 BOM. Перекачай пакунок; редагуй його лише в кодуванні «UTF-8 with BOM».
- `adb` не бачить гостьову систему: `adb kill-server && adb start-server`, для
  мережевого adb — `adb connect <IP>:5555`.
- «наші застосунки потребують API 33+» — образ старий, потрібен Android 13+.

**Що перевірити далі**

```bash
adb shell settings get secure default_home           # -> os.tessera.launcher/.LauncherActivity
adb shell dumpsys package role_manager | grep -A2 ROLE_
adb shell cmd overlay list | grep tessera
```

## Шлях 2. Справжній образ ROM

Образ збирається лише на Linux: AOSP build system не працює на Windows і macOS
нативно (див. [BUILD.md](BUILD.md) — там і повний список вимог). Іронія, яку
варто сказати вголос: **білд-бокс — це теж віртуальна машина**, і для багатьох
це єдиний спосіб отримати образ TesseraOS без виділеної машини.

```bash
# усередині Ubuntu 24.04 з 250+ ГБ диска і 16+ ГБ RAM
build/scripts/bootstrap.sh
repo init -u https://github.com/LineageOS/manifest -b lineage-22.2
repo sync -c -j"$(nproc)" --force-sync --no-clone-bundle
mkdir -p vendor/tessera && git clone https://github.com/nothing12-glitch/Tessera-OS vendor/tessera/os
vendor/tessera/os/build/scripts/build-emulator.sh tessera_x86_64-userdebug
```

Скрипт спершу готує наші APK (`prepare-app.sh` читає реєстр
`build/scripts/apps.list`), потім робить `lunch` і `m`. Образи лягають в
`out/target/product/tessera_x86_64/` разом з `tessera-images-sha256.txt`.

Запуск отриманого образу — `run-avd.sh` (Android Emulator, найнадійніший шлях,
бо образ і зібрано під `androidboot.hardware=ranchu`):

```bash
build/scripts/run-avd.sh --api 35 --image out/target/product/tessera_x86_64
```

### Чому не ISO для VirtualBox чи VMware

`full_x86_64` у нашій конфігу дає партиційні образи (`system.img`,
`vendor.img`, `userdata.img`, `boot.img`), а не завантажувальний ISO з
інсталятором. Щоб образ завантажувався з віртуального CD/DVD, потрібен інший
шар: установник і bootloader у стилі Android-x86. Цього в TesseraOS ще немає, і
робити його «на око» без живого білду — означає писати скрипт, який ніхто не
перевіряв. Тому чесна позиція:

| Гіпервізор | Статус |
|---|---|
| Android Emulator (QEMU всередині) | підтримано: `run-avd.sh` |
| QEMU/KVM напряму | технічно той самий `ranchu`, але cmdline ядра й initrd ще не перевірені на живому образі |
| VirtualBox, VMware | потребує завантажувального ISO — роботи в плані, не обіцянка |

Якщо потрібен саме VirtualBox, наразі єдиний робочий варіант — шлях 1 поверх
наявного образу Android 13+ з цього ж розділу.

Про прискорення на Windows (AEHD / «Платформа гіпервізора Windows») і чому
емулятор без нього не стартує — окремо і детально в
[EMULATOR.md](EMULATOR.md).

## Шлях 3. Готовий образ

Коли перший успішний білд шляху 2 трапиться, `.img` пакуються в
`tessera-x86_64-<ver>.tar.xz` і кладуться в той самий GitHub Release: файли
образу політика репо тримати в гілці забороняє (`policy.yml`), а Release —
легальне місце для них. Розпакуй і вкажи `run-avd.sh --image <каталог>`.
