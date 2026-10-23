# Збірка TesseraOS

## Вимоги до білд-боксу

| Ресурс | Мінімум | Рекомендовано |
|---|---|---|
| ОС | Linux (Debian 12, Ubuntu 22.04/24.04) | Ubuntu 24.04 LTS |
| RAM | 16 ГБ | 32–64 ГБ |
| Вільне місце | 250 ГБ (SSD) | 400+ ГБ, NVMe |
| CPU | 8 ядер | 16+ |
| Java | JDK 21 (для AOSP 15) | OpenJDK 21 |
| Windows/macOS | так, лише через WSL2 або Docker; `repo sync` у WSL1 не працює | Docker `lineageos4microg/docker-build-env` |

Збірка **не** іде нативно на Windows чи macOS. Якщо бачиш помилку
`case-insensitive filesystem` або `chmod` — це саме про це.

## Покроково

### 1. Інструменти

```bash
sudo apt update && sudo apt install -y git curl gnupg ccache zip unzip \
  repo fontconfig python3
```

або одразу:

```bash
build/scripts/bootstrap.sh
```

### 2. Джерела

```bash
mkdir -p ~/tessera && cd ~/tessera
repo init -u https://github.com/LineageOS/manifest -b lineage-22.2
repo sync -c -j"$(nproc)" --force-sync --no-clone-bundle
```

Кладемо наш репозиторій у `vendor/tessera/os` (шлях очікує `build/tessera.mk`):

```bash
mkdir -p vendor/tessera
git clone https://github.com/<you>/tessera-os vendor/tessera/os
```

### 3. Конфіг продукту

`lunch` бачить таргент лише тоді, коли він оголошений в
`device/**/AndroidProducts.mk`. Маніфест `manifest/tessera.xml` робить це
автоматично через `<linkfile>`: при `repo sync` наші файли з'являються в
каталозі платформи:

| Джерело (у нашому репо) | Призначення в дереві |
|---|---|
| `build/tessera.mk` | `device/tessera/x86_64/tessera_x86_64.mk` |
| `build/board/AndroidProducts.mk` | `device/tessera/x86_64/AndroidProducts.mk` |
| `build/board/BoardConfig.mk` | `device/tessera/x86_64/BoardConfig.mk` |
| `config/branding/tessera.prop` | `vendor/tessera/etc/tessera.prop` |

Якщо ти клонуєш репозиторій вручну (без `repo sync` для нашого шару), зроби
ті самі три посилання сам:

```bash
mkdir -p device/tessera/x86_64
T="$PWD/vendor/tessera/os"
ln -sfn "$T/build/tessera.mk"            device/tessera/x86_64/tessera_x86_64.mk
ln -sfn "$T/build/board/AndroidProducts.mk" device/tessera/x86_64/AndroidProducts.mk
ln -sfn "$T/build/board/BoardConfig.mk"   device/tessera/x86_64/BoardConfig.mk
```

**Чому не повний маніфест.** `manifest/tessera.xml` перелічує лише кілька
проєктів бази — цього достатньо, щоб показати шар і зібрати оверлеї, але не
для повної збірки ROM: справжній образ потребує сотень проєктів і власне
`default.xml` від LineageOS. Надійний шлях: форкнути
[LineageOS/manifest](https://github.com/LineageOS/manifest), додати у його
`default.xml` проєкт `vendor/tessera/os` з чотирма `<linkfile>` з таблиці вище
— і робити `repo init -u` на власний форк. Саме тому команда на кроці 2 вказує
на маніфест LineageOS, а не на наш.

### 4. Збірка

```bash
source build/envsetup.sh
lunch tessera_x86_64-userdebug
m -j"$(nproc)" 2>&1 | tee /tmp/tessera-build.log
```

Або все одразу з кешем і логами:

```bash
build/scripts/build-emulator.sh
```

Перед `m` у дереві має лежати APK нашого лаунчера: модуль
`TesseraLauncher` — це `android_app_import`, а бінарники в git заборонені
політикою. `build-emulator.sh` викликає підготовку сам; вручну це

```bash
build/scripts/prepare-launcher.sh          # зібрати Gradle-ом
build/scripts/prepare-launcher.sh prebuilt /шлях/app-debug.apk
build/scripts/prepare-launcher.sh artifact <id запуску build-launcher>
```

(режим `artifact` потребує `GITHUB_TOKEN`; обійти підготовку —
`TESSERA_SKIP_LAUNCHER=1`, якщо лаунчер у образі не потрібен).

Перша збірка на 16 ядрах — 2–6 годин. Наступні інкрементальні — хвилини
(за умови `ccache`).

### 5. Результат

Image лежать у `out/target/product/tessera_x86_64/`:
`system.img`, `vendor.img`, `ramdisk.img`, `super.img`, `userdata.img`.
Для AVD потрібен `arm64/x86_64`-набір з `run-avd.sh` — див.
[EMULATOR.md](EMULATOR.md).

## Типові помилки

| Помилка | Причина | Рішення |
|---|---|---|
| `No rule to make target 'tessera_x86_64'` | `.mk` не в шляху пошуку | symlink із кроку 3, потім `m clean-outputs` |
| `out of memory` / `Killed` під час `soong` | замало RAM | `-j$(nproc --all)` → `-j4`, вимкнути `USE_CCACHE=0` тимчасово |
| `Android platform requires the host tool 'repo'` | `repo` не в `PATH` | поставити у `~/bin` і експорт |
| `error: cannot find SDK` у own-app | немає Android SDK | `bootstrap.sh --apps` |
| Оверлей не застосований | `isStatic="true"` + не в `PRODUCT_PACKAGES` | додай пакет у `tessera.mk`, перевіри `cmd overlay list` |
| `TesseraLauncher`: apk not found | APK лаунчера готується перед `m`, а не лежить у git | `build/scripts/prepare-launcher.sh` |
| Система щоразу питає, який лаунчер | у образі два HOME-застосунки (сток не перекритий — див. docs/PIXEL-PARITY.md §6) | обери «Завжди» або `cmd package set-home-activity os.tessera.launcher/.LauncherActivity` |

## Відтворюваність

Обов'язково для публічних релізів:

- `BUILD_DATETIME` і `BUILD_DATE_UTC` задаються через `SOURCE_DATE_EPOCH` у CI;
- підпис `releasekey` має лежати в захищеному сховищі, не в репозиторії;
- кожен реліз збирається двічі різними білд-боксами й порівнюється
  `sha256sum` сукупності образів; розбіжність — блокер релізу.

## CI

| Workflow | Де збирається | Що робить |
|---|---|---|
| `build-themes.yml` | GitHub-hosted ubuntu | збірка RRO-оверлеїв і викладка артефактів (швидка, безкоштовна) |
| `build-rom.yml` | `self-hosted, linux, x86_64, rom-build` | повна збірка образу; hosted-раннери її не витягнуть (20 ГБ RAM, 6 год ліміт) |
| `release.yml` | self-hosted + GitHub Releases | підписані образи, checksums, notes |
