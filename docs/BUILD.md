# Збірка Zirka OS

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
mkdir -p ~/zirka && cd ~/zirka
repo init -u https://github.com/LineageOS/manifest -b lineage-22.2
repo sync -c -j"$(nproc)" --force-sync --no-clone-bundle
```

Кладемо наш репозиторій у `vendor/zirka/os` (шлях очікує `build/zirka.mk`):

```bash
mkdir -p vendor/zirka
git clone https://github.com/<you>/zirka-os vendor/zirka/os
```

### 3. Конфіг продукту

Конфіг продукту має бути у списку, який зчитує `lunch`. Маніфест
`manifest/zirka.xml` робить це автоматично через `<linkfile>`: при
`repo sync` наш `build/zirka.mk` з'являється як
`build/make/target/product/zirka_x86_64.mk`, а `BoardConfig.mk` — у
`device/zirka/x86_64/`. Якщо ти клонуєш репозиторій вручну (без `repo`),
створи symlink вручну:

```bash
ln -sfn "$PWD/vendor/zirka/os/build/zirka.mk" \
        build/make/target/product/zirka_x86_64.mk
```

### 4. Збірка

```bash
source build/envsetup.sh
lunch zirka_x86_64-userdebug
m -j"$(nproc)" 2>&1 | tee /tmp/zirka-build.log
```

Або все одразу з кешем і логами:

```bash
build/scripts/build-emulator.sh
```

Перша збірка на 16 ядрах — 2–6 годин. Наступні інкрементальні — хвилини
(за умови `ccache`).

### 5. Результат

Image лежать у `out/target/product/zirka_x86_64/`:
`system.img`, `vendor.img`, `ramdisk.img`, `super.img`, `userdata.img`.
Для AVD потрібен `arm64/x86_64`-набір з `run-avd.sh` — див.
[EMULATOR.md](EMULATOR.md).

## Типові помилки

| Помилка | Причина | Рішення |
|---|---|---|
| `No rule to make target 'zirka_x86_64'` | `.mk` не в шляху пошуку | symlink із кроку 3, потім `m clean-outputs` |
| `out of memory` / `Killed` під час `soong` | замало RAM | `-j$(nproc --all)` → `-j4`, вимкнути `USE_CCACHE=0` тимчасово |
| `Android platform requires the host tool 'repo'` | `repo` не в `PATH` | поставити у `~/bin` і експорт |
| `error: cannot find SDK` у own-app | немає Android SDK | `bootstrap.sh --apps` |
| Оверлей не застосований | `isStatic="true"` + не в `PRODUCT_PACKAGES` | додай пакет у `zirka.mk`, перевіри `cmd overlay list` |

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
