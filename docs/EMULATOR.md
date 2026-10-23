# Ціль першого релізу: Android-віртуальна машина

Чому емулятор, а не телефон: образ, який можна запустити за 5 хвилин без
розблокування bootloader і ризику «цегли», доступний кожному, хто хоче
подивитися на TesseraOS. Телефон — наступний milestone, бо там потрібні
device tree і vendor-бібліотеки конкретного заліза.

## Два шляхи запуску

### A. QEMU / AVD зі зібраними образами (для тих, хто зібрав ROM)

```bash
build/scripts/run-avd.sh --api 35 --abi x86_64 --image out/target/product/tessera_x86_64
```

Скрипт робить AVD-каталог, копіює `system.img`, `vendor.img`, `userdata.img`,
`ramdisk.img` у `system-images/tessera/35/x86_64/` і запускає
`emulator -avd Tessera_X86_64 -writable-system -gpu host -no-snapshot`.

Вимоги на хості: Android SDK (`cmdline-tools`, `emulator`), KVM для x86_64
(`egrep -c '(vmx|svm)' /proc/cpuinfo` > 0), інакше буде повільна емуляція.

#### Windows: чому емулятор може не стартувати

x86_64-гостьова система вимагає апаратного прискорення. Перевіряємо першим
ділом, до будь-якої спроби запуску:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator-check.exe" accel-check
```

Код `0` — прискорення є. Код `6` — його немає, і звичайний запуск обривається
на `x86_64 emulation currently requires hardware acceleration!` (саме так на
хості 2026-10-23: віртуалізація в увімкнена в UEFI, але AEHD не встановлено,
роль Hyper-V вимкнена, «Платформа гіпервізора Windows» не увімкнена, WSL
немає, і сесія не адміністратор).

Два шляхи, обидва — одноразово і з перезавантаженням:

| Варіант | Що робити |
|---|---|
| Платформа гіпервізора Windows (WHPX) | `optionalfeatures.exe` → «Платформа гіпервізора Windows» → перезавантаження. Потрібні права адміністратора |
| Android Emulator Hypervisor Driver (AEHD) | SDK Manager → «Performance → Android Emulator Hypervisor Driver», далі запуск його інсталятора з адміністраторськими правами |

Без прискорення гостеву систему все ж можна підняти в чистій емуляції —
прапорцем `-no-accel`. Це не «швидкий шлях», а крайній: перший кадр SurfaceFlinger
з'являється замість хвилин — десятки хвилин, а `-gpu host` при цьому не
працює, треба програмний рендерер (`-gpu swangle` або `-gpu lavapipe`; на
тому ж хості `-gpu swiftshader_indirect` друкує `Failed to load opengl32sw`).
Для повного образу TesseraOS це, зрозуміло, не замінює нормальний запуск.

### B. Готовий образ зі сторінки релізів

У GitHub Release лежить `tessera-x86_64-<ver>.tar.xz` + `sha256sum.txt`.
Розпакуй і вкажи `run-avd.sh --image <dir>`. Жодної збірки не потрібно.

## Що працює в VM-образі

| Функція | Статус |
|---|---|
| UI, оверлеї MD3, динамічна палітра | так |
| microG, push через фактичний MQTT | так |
| Застосунки з F-Droid / Aurora | так |
| Апаратне прискорення графіки | так, `-gpu host` |
| Камера, NFC, телефонія | ні (немає заліза) |
| Play Protect attestation | ні, див. [GAPPS.md](GAPPS.md) |
| Widevine | L3 (програмний); DRM-потоки можуть не працювати |

## Налаштування для розробки теми

Швидкий цикл «правимо ресурс → бачимо результат» без повної збірки ROM:

```bash
# зібрати лише оверлей
build/scripts/build-themes.sh
# накласти на вже запущений емулятор
adb install -r overlay/out/os.tessera.theme.framework.apk
adb shell cmd overlay enable os.tessera.theme.framework
adb shell cmd overlay disable os.tessera.theme.framework
```

Для static-оверлеїв зміни потребують `adb reboot` через early-boot-завантаження
ресурсів.

## Тестування

`tools/emulator-smoke.sh` на запущеному образі перевіряє:

1. `sys.boot_completed == 1`;
2. обидва Tessera-оверлеї в `cmd overlay list` активні;
3. базові екрани доступні: launcher, Settings → Display, About (версія
   `ro.tessera.version`);
4. колір `?attr/colorSurface` у світлій і темній темі відрізняється від
   стокового (порівняння пікселя в точці 10,10 скріншота);
5. `pm list packages` містить microG-компоненти.

## Обмеження, які варто знати заздалегідь

- Архітектурне обмеження: x86_64-образ не запускає ARM-бібліотеки без
  translation layer (`libhoudini`/`libndk` — власницькі, у TesseraOS їх
  немає). Частина застосунків у VM не стартує. Для ARM-обладнання це
  не проблема.
- 35 API — умовний рівень Android 15; звір із `build/tessera.mk`.
- Перевір розмір `userdata` (за замовчуванням 6 ГБ) перед тестами оновлень.
