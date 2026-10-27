# Збірка TesseraOS

Тут описано, як отримати **справжній образ** (`.img`), а не список файлів у
репозиторії. Усе в цьому документі перевірено проти живого дерева бази
`lineage-22.2` (клонуванням і HTTP-кодами, 2026-10-24); те, що ще не перевірено
на зібраному образі, позначено окремо в кінці.

## Чесний статус на сьогодні

| Речі | Стан |
|---|---|
| Дерево бази | справжнє: маніфест `LineageOS/android`, гілка `lineage-22.2` (клонували `default.xml`, 1 000+ проєктів) |
| Наш конфіг продукту | успадковує `device/generic/goldfish/64bitonly/product/sdk_phone64_x86_64.mk` — файл існує, шлях перевірено |
| Застосунки й оверлеї | 7 APK + 3 RRO збираються (Gradle + CI), реєстр перевіляє `check-app-integration.sh` |
| Посилання збірки | перевіряє `check-build-refs.sh` — тепер це крок CI (`policy.yml`), і він ловив три мертві шляхи в попередній версії конфігу |
| **Готовий образ** | **щодня не зібраний**. Жодного `.img` ми ще не мали на руках: збірка потребує Linux-хоста з 400+ ГБ, а ця машина — Windows без WSL |

Саме останній рядок — причина, чому проєкт виглядав як «концепт». Все інше
вже підлаштоване під те, щоб перша ж збірка на Linux дошла до образу і
проіснувала там.

## Вимоги до білд-боксу

Числа — з документації LineageOS для гілок 21+ (не наші оцінки):

| Ресурс | Мінімум | Рекомендовано |
|---|---|---|
| ОС | Linux з APT (Ubuntu 22.04/24.04, Debian 12) | Ubuntu 24.04 LTS |
| Вільне місце | 400 ГБ (SSD/NVMe) | 600+ ГБ |
| RAM | 16 ГБ + 16 ГБ swap | 32–64 ГБ |
| CPU | 8 ядер | 16+ |
| JDK | 17 (дерево тримає свій у `prebuilts/`) | — |
| Windows/macOS | лише через WSL2 або Docker; у WSL1 `repo sync` не працює | — |

Збірка **не** іде нативно на Windows чи macOS: потрібна чутлива до регістру
файлова система і Linux-хости. Якщо бачиш `case-insensitive filesystem` або
помилку `chmod` — це саме про це.

## Твій ноутбук: ASUS TUF Gaming A16 (FA607NU)

Залізо придатне: Ryzen 7 7735HS (8 ядер / 16 потоків), NVMe, x86_64, AMD-V.
Два вузьких місця — 16 ГБ оперативної і вільне місце на диску.

### Шлях A. Dual-boot Ubuntu 24.04 (рекомендую)

Перший прохід на 8 ядрах з 16 ГБ RAM — орієнтовно 5–9 годин (далі, з ccache,
інкрементальні зміни — хвилини). Це найнадійніший варіант: рідна файлова
система, рідний KVM, повна швидкість NVMe.

1. Перевір у UEFI (F2 при завантаженні): `AMD-V / SVM Control` — увімкнено,
   `Secure Boot` — вимкнено (для сторонніх модулів ядра).
2. Звільни 600 ГБ. Окремий диск — ідеально; розділ на тому самому NVMe —
   теж можна (GParted з LIVE-флешки, стисни NTFS).
3. Flash Ubuntu 24.04 на флешку (Rufus / Fedora Media Writer), завантаження F8 →
   USB, установка «Щось інше» / «Install alongside».
4. Після встановлення — 8–16 ГБ swap (файл або розділ): для 16 ГБ RAM це не
   розкіш, а захист від `Killed` під час Soong.

   ```bash
   sudo fallocate -l 16G /swapfile && sudo chmod 600 /swapfile
   sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```

5. Дальше — кроки 1–4 нижче.

### Шлях B. WSL2 на Windows

Три речі варто знати до старту. Перша: WSL ставить лише адміністратор, і після
ввімкнення `VirtualMachinePlatform` обов'язкове перезавантаження. Друга: VHDX
дистрибутива живе на C:, тому весь обсяг дерева й `out/` від'їдається від того
самого тому. Третя, неочевидна: на цьому ноутбуці рядка `hypervisorlaunchtype`
у завантажувачі взагалі не було, тож гіпервізор не стартував і `wsl --import`
падав з `HCS_E_SERVICE_NOT_AVAILABLE` — попри увімкнені функції Windows і SVM
у BIOS. Виправляється однією командою (адміністраторська сесія, потім
перезавантаження):

```powershell
bcdedit /set "{current}" hypervisorlaunchtype Auto
```

Перевірено на ASUS TUF Gaming A16 (FA607NU): 15,3 ГБ RAM. Після видалення двох
найбільших ігор Steam вільно на C: стало 317 ГБ — це нижче за 400+ ГБ із вікі
LineageOS, тому sync робимо з `REPO_DEPTH=1` (без повної історії git, дерево
приблизно вдвічі менше).

#### Чому не `wsl --install`

Типова команда качає пакунок із `dl.delivery.mp.microsoft.com`, а той на цьому
з'єднанні не відповідає (TLS-помилка, у `curl` — exit 60): установка зависає без
жодного завантаженого байта. Працюючий обхід — два файли з адрес, які віддають
трафік:

- пакунок WSL 3.0.3 (MSI): `https://github.com/microsoft/WSL/releases/download/3.0.3/wsl.3.0.3.0.x64.msi` — 367 МБ;
- кореневий образ Ubuntu 24.04 для WSL: `https://cloud-images.ubuntu.com/wsl/releases/noble/current/ubuntu-noble-wsl-amd64-24.04lts.rootfs.tar.gz` — 356 МБ.

GitHub на цьому каналі дає ~0,2 МБ/с на одне з'єднання і ~1,1 МБ/с на чотири,
тому MSI качають діапазонами, а не одним потоком. Перед установкою перевіряють
підпис MSI (`Get-AuthenticodeSignature` має дати `Valid` від «Microsoft
Corporation») і суму образу (має збігатися з `SHA256SUMS` поруч з ним).

```powershell
# адміністраторська сесія: гіпервізор -> підпис -> msiexec /i -> wsl --import
powershell -File build\scripts\install-wsl-offline.ps1 -Msi wsl.3.0.3.0.x64.msi -Rootfs ubuntu-rootfs.tar.gz
```

#### Після встановлення

Один скрипт замість ручного редагування двох конфігів:

```powershell
powershell -File build\scripts\setup-wsl.ps1
```

Він спершу перевіряє, чи гіпервізор запущений (`HypervisorPresent`), інакше
нагадує про `bcdedit` вище — краще дізнатися це до першої збірки, ніж через
двадцять годин качання. Далі пише `C:\Users\<you>\.wslconfig` (RAM мінус 4 ГБ
для Windows, майже всі ядра, своп такого ж розміру, `nestedVirtualization=true`
— без нього всередині WSL не буде `/dev/kvm` і емулятор не запуститься) і
викликає `build/scripts/wsl-provision.sh` від root. Той створює користувача
`tessera`, кладе `/etc/wsl.conf` (найголовніше там — `appendWindowsPath = false`:
інакше `PATH` з Windows підсовує чужі `java` і `git` у Linux-оболонку) і клонує
репозиторій у `~/tessera-os`, тобто в ext4.

Останнє принципово: на `/mnt/c` файлова система не розрізняє регістр літер і не
має прав Unix, тому git там пише «dubious ownership», а Soong збирає повільніше.
Дерево й `out/` — тільки в домашній теці Linux.

#### APK раніше за образ

Установчий пакунок для VM (застосунки + тема MD3 + інсталятори) не чекає на
40–60 ГБ дерева: він збирається з Gradle і публічного Android SDK. Тому перше,
що варто зробити після провіжиніння, — поставити саме цей інструмент:

```bash
bash ~/tessera-os/build/scripts/install-app-toolchain.sh   # SDK 35 + Gradle 8.14.3
bash ~/tessera-os/build/scripts/build-vm-bundle-local.sh   # dist/tessera-vm-<версія>-vega.tar.xz
```

Версії в скрипті навмисно збігаються з `.github/workflows/release.yml`:
«в CI зібралось, удома — ні» — рівно той клас поломки, який ловить локальний
запуск. Пакунок лишається в `dist/` і нікуди не пушиться: оприлюднення — окрема
дія (віха `v<версія>`), бо реліз бачать сторонні люди.

Решта — кроки 1–4 нижче.

## Покроково (спільне для обох шляхів)

### 1. Пакунки

```bash
sudo apt update && sudo apt install -y git curl
git clone https://github.com/nothing12-glitch/Tessera-OS ~/tessera-os
bash ~/tessera-os/build/scripts/bootstrap.sh
```

Скрипт ставить офіційний перелік LineageOS (bc, bison, build-essential,
ccache, erofs-utils, git-lfs, kmod, lib32z1-dev, lz4, lzop, schedtool,
squashfs-tools, xxd, zlib1g-dev …), перевіряє місце на диску, KVM і кладе
`repo` у `~/bin`.

### 2. Дерево: база + наш шар

```bash
~/tessera-os/build/scripts/new-tree.sh lineage-22.2
```

Скрипт робить три речі; дві з них важливо бачити текстом:

```bash
repo init -u https://github.com/LineageOS/android.git -b lineage-22.2 --git-lfs --no-clone-bundle
# + .repo/local_manifests/tessera.xml  →  vendor/tessera/os (наш репо)
repo sync -c -j"$(nproc)" --fail-fast
```

**Наш маніфест — додаток, а не замінник.** Ініціювати збірку власним
`manifest/tessera.xml` не можна: перелічити вручну 1 000+ проєктів бази — це
шлях до збірки, яка падає на 300-му файлі. Рідний механізм repo для
деривативів — `.repo/local_manifests/`, ним і користуємось; форкати чужий
маніфест не треба.

`repo sync` — це сотні ГБ і кілька годин. Обірвався? Просто запусти скрипт
ще раз, він продовжує.

На повільному каналі (домашній, не датацентр) є два важелі:

```bash
REPO_DEPTH=1 REPO_JOBS=4 ~/tessera-os/build/scripts/new-tree.sh lineage-22.2
```

`REPO_DEPTH=1` бере лише верхівку history кожного проєкту — дерево виходить
приблизно удвічі меншим, а історія git для збірки образу не потрібна взагалі.
`REPO_JOBS` обмежує паралельність: виміряно, що далі за чотири одночасні
з'єднання GitHub на цьому каналі не прискорює, а уповільнює (0,55 МБ/с на восьми
проти 1,1 МБ/с на чотирьох), а `--fail-fast` при п'ятому таймауті обірвав би
добову роботу.

### 3. Статична перевірка перед довгою збіркою

```bash
cd ~/tessera            # каталог дерева, не репозиторію
~/tessera-os/build/scripts/check-build-refs.sh --tree .
```

Перевіряє, що кожне `inherit-product` нашого конфіга йде до файла, який
справді є в дереві, що кожен `PRODUCT_PACKAGES`-модуль оголошений (нашим
Android.bp або деревом бази), і що `linkfile` маніфеста створили
`device/tessera/x86_64/*.mk`. Коштує секунди, економить годину.

### 4. Збірка

```bash
~/tessera-os/build/scripts/build-emulator.sh tessera_x86_64-userdebug
```

Всередині: підготовка наших APK (`prepare-app.sh` — без них Soong падає на
`apk not found`), `lunch`, `m -j` з обмеженням за пам'яттю, логи в
`/tmp/tessera-build-*.log`. Готові образи — у
`out/target/product/emu64x/` (каталог названий за `PRODUCT_DEVICE`, а не за
назвою продукту; `kernel-ranchu` там само).

Шукаєш, які таргети бачить дерево? `lunch` без аргументу друкує меню;
у документації LineageOS для інших гілок фігурує stock-назва кшталт
`sdk_phone_x86_64` — вона залежить від гілки, тому орієнтуйся на меню, а не
на документ.

### 5. Запуск

```bash
~/tessera-os/build/scripts/run-avd.sh --image out/target/product/emu64x --api 35
```

Скрипт кладе наші образи + `kernel-ranchu` у
`$SDK/system-images/android-35/tessera/x86_64/`, створює AVD і запускає
`emulator -writable-system -gpu host`. Першим ділом перевір прискорення:
`egrep -c '(vmx|svm)' /proc/cpuinfo` > 0.

Якщо є доступ до `m emu_img_zip` — спробуй його перед `run-avd.sh`: це
упаковка образу у формат, який емулятор очікує, без нашого ручного
копіювання.

## Експеримент другого проходу: продукт-шар `vendor/lineage`

Образ збирається з дерева LineageOS (їхні `build/make`, `frameworks/base`,
`soong`, патчі), але **без** їхнього конфігу продукту — і це свідомо. Причина
перевірена файлом: `vendor/lineage/config/common.mk:119` викликає
`enforce-product-packages-exist-internal` зі списком модулів, яких в образу
емультора немає (`rild`, recovery-скрипти, `Calendar`), тож таке успадкування
зупиняє збірку ще до першого `.img`. Линії самі збирають емулятор stock-таргетом.

Коли перший образ у тебе в руках і збирається чисто — спробуй додати в
`tessera.mk` (після успадкування бази):

```makefile
$(call inherit-product, vendor/lineage/config/common.mk)
```

Очікувані помилки й що означатиме:

| Помилка | Що це значить |
|---|---|
| `Some packages required to exist ... rild` | емуляторний таргет не має телефонного HAL; потрібен `TARGET_DISABLE_...`-прапорець або викинути рядок |
| `No rule to make target '.../recovery.img'` | `common.mk` копіює файли в recovery, якого в емулятора немає |
| `TrebuchetQuickStep ... not found` | шар тягне застосунки бази; перевір, чи вони в `PRODUCT_PACKAGES` емуляторного конфігу |

Тобто: це не «зламане», а неперевірене. Спершу — базовий образ.

## Типові помилки

| Помилка | Причина | Рішення |
|---|---|---|
| `repo: command not found` | `~/bin` не в `PATH` | `echo 'export PATH=$HOME/bin:$PATH' >> ~/.bashrc` |
| `Git LFS objects not found` | немає `git-lfs` | `sudo apt install git-lfs` і повторити `repo init` |
| `No rule to make target 'tessera_x86_64'` | наші `.mk` не з'явилися в `device/tessera/x86_64/` | `check-build-refs.sh --tree .`, потім `m clean-outputs` |
| `TesseraCamera: apk not found` | APK не підготовлені до `m` | `build/scripts/prepare-app.sh` (або `TESSERA_APPS_ARGS="artifact <id>"`) |
| `Killed` / `out of memory` під Soong | замало RAM | `m -j4`, увімкнути swap, `WITH_DEXPREOPT=false` тимчасово |
| `case-insensitive filesystem` | збірка в `/mnt/c` або на NTFS | працюй з ext4-домівки (`~/tessera`) |
| `No space left on device` під sync | диск < 400 ГБ | звільнити місце; `repo sync` потім продовжує сам |
| Емулятор не стартує | немає KVM / віртуалізації в UEFI | увімкнути AMD-V; `egrep -c '(vmx\|svm)' /proc/cpuinfo` |

## Відтворюваність

- `SOURCE_DATE_EPOCH` експортує `build-emulator.sh` (у CI — значення запуску);
- ключі підпису never у git: `*.pk8`, `*.pem`, `*.keystore`, `secrets/` —
  в `.gitignore`, release-ключ лише в CI secrets;
- публічний реліз збирають двічі на різних машинах і порівнюють
  `sha256sum` сукупності образів; розбіжність — блокер релізу.

## CI

| Workflow | Де збирається | Що робить |
|---|---|---|
| `policy.yml` | GitHub-hosted ubuntu | заборона бінарів/чужих брендів, ліцензії, **`check-build-refs.sh`**, **`check-doc-claims.sh`** (дозволи + чорний список екранних обіцянок; `--selftest` — чотири випадки) |
| `build-apps.yml` | GitHub-hosted | наші 7 APK через Gradle |
| `build-themes.yml` | GitHub-hosted | RRO-оверлеї |
| `build-rom.yml` | self-hosted `linux, x86_64, rom-build` | повна збірка образу (hosted-раннери не витягнуть: 400 ГБ, 6-годинний ліміт) |
| `release.yml` | self-hosted + GitHub Releases | образи + APK-пакунок, checksums |

## Що ще не підтверджено

1. Жодного зібраного нами образу ще немає — отже не підтверджено, що
   `tessera_x86_64-userdebug` доходить до `.img` з першого ряду. Перший
   прохід на білд-боксі й дає цю відповідь; логи просимо кладати в issue.
2. `m emu_img_zip` для нашого продукту не перевірено — працює для stock-таргета
   бази, для нашого — потребує підтвердження.
3. Наші застосунки в образі (а не лише як APK) — теж питання першого запуску:
   `adb shell pm list packages | grep os.tessera`.
