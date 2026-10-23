# Zirka OS

Вільна (Apache-2.0) мобільна операційна система на базі Android для телефонів та
віртуальних машин. Zirka OS — це дистрибутив: власний UI-шар, оформлений за
Material Design 3, поверх стабільної бази LineageOS 22.x (Android 15).

> **Чому не «з нуля».** Android — це AOSP: мільйони рядків кода, роки роботи
> команд Google. Жоден проєкт не створює ядро й системні сервіси сам — він
> бере вільну базу і додає свій шар: тему, додатки, політики, оновлення.
> Zirka OS робить те саме, легально і з відкритим кодом.

## Що в проєкті

| Шар | Реалізація |
|---|---|
| Ядро, HAL, системні сервіси | LineageOS 22.x (Android 15) |
| Material Design 3 | RRO-оверлеї в `overlay/themes/` |
| Застосунки | `apps/` (Compose + MD3), інтегруються в образ |
| Google-сервіси | не входить у образ; профіль встановлення в `config/gapps/` |
| Ціль першого релізу | x86_64 Android VM (AVD / qemu) |
| Збірка | `build/` + GitHub Actions |

## Швидкий старт

Системна вимога для збірки ROM — **Linux**. На Windows і macOS збірка
неможлива (AOSP build system не працює нативно); використовуй Docker/WSL2
а окремий білд-бокс із 32 ГБ RAM і 400 ГБ вільного місця.

```bash
# 1. Підготувати середовище (Linux, Debian 12 / Ubuntu 22.04+)
build/scripts/bootstrap.sh

# 2. Синхронізувати джерела бази + цей репозиторій
repo init -u https://github.com/LineageOS/manifest -b lineage-22.2
repo sync -c -j"$(nproc)"
mkdir -p vendor/zirka && git clone https://github.com/<you>/zirka-os vendor/zirka/os

# 3. Зібрати image для емулятора
build/scripts/build-emulator.sh

# 4. Запустити
build/scripts/run-avd.sh
```

Деталі: [docs/BUILD.md](docs/BUILD.md), [docs/EMULATOR.md](docs/EMULATOR.md).

## Google Play (GApps)

Zirka OS **не** постачається з пропрієтарними застосунками Google і не
проходить сертифікацію GMS. Це не примха, а юридична рамка: Google видає
ліцензію виробнику пристрою після CTS, тому ROM-проєкт не може легально
вшити Play Store у свій образ.

Користувач має два легальні шляхи:

1. **microG** (вбудовано в образ, опція `zirka_microg`) — вільна реалізація
   Push/Location/Auth, працює без сертифікатів.
2. **GApps-пакет від вендора** (MindTheGApps) — користувач ставить сам,
   приймаючи умови Google. Скрипт `build/scripts/package-gapps.sh`
   збирає для нього коректний профіль і zip.

Повне пояснення й таблиця сумісності: [docs/GAPPS.md](docs/GAPPS.md).

## Material Design 3

Оверлеї — це RRO-пакети (`overlay/themes/`), які змінюють ресурси framework і
Settings без форку AOSP: динамічна палітра від шпалер, тональні поверхні,
радіуси 12/16/28 dp, типографіка Roboto Flex. Їх можна зібрати на
звичайному GitHub-раннері й застосувати до будь-якої ROM.

Як це працює і як додати свій акцент: [docs/THEME-MD3.md](docs/THEME-MD3.md).

## Статус

Каркас проєкту: структура, маніфест, конфіг продукту, MD3-оверлеї, скрипти
збірки, CI й документація. Перший публічний білд — x86_64 образ із microG.
Див. [roadmap у docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#roadmap).

## Ліцензія

Apache License 2.0, як у LineageOS і AOSP. Торгові марки, логотипи й
ресурси Google / Pixel не входять до репозиторію і не можуть бути
додані.
