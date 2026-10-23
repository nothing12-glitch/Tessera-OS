# Google-сервіси: що можна, а що ні

## Коротко

У образі TesseraOS **немає** застосунків Google, і так має бути. Причина не
технічна, а правова:

- `com.google.android.gsf`, GMS Core, Play Store, Gmail, Maps — це
  власницькі бінарні пакети, які Google ліцензує **виробнику пристрою** після
  сертифікації (CTS/GMS-тестів).ROM-проєкт не є виробником і ліцензії не має.
- Тому легальний шлях будь-якої кастомної ROM — **користувач сам** ставить
  GApps-пакет, приймаючи умови Google. Так само роблять LineageOS, Pixel
  Experience, crDroid: GApps не входить у прошивку.

## Три варіанти для користувача

### 1. microG — безкоштовно, без сертифікатів (за замовчуванням у TesseraOS)

Вільна реалізація GmsCore: push-сповіщення (Firebase), Locator, OAuth
для «Увійти через Google». Працює без GMS-ліцензії й без закритих бинарів.

```
PRODUCT_PACKAGES += microG (профіль tessera_microg)
```

За потреби — signature spoofing, який LineageOS уже підтримує з меню
*Settings → Apps & notifications → microG → enable signing spoofing*
(або `pm`-командою на userdebug).

### 2. MindTheGApps (MTG) — пакети від партнера спільноти LineageOS

Пакети будуються для конкретних збірок ROM, тому сумісні. Версії:

| Variant | Що входить | Розмір |
|---|---|---|
| pico | GmsCore, GSF, Play Store, Backup | ~130 МБ |
| basic | + Gmail, Maps, YouTube | ~300 МБ |
| stock | набір як на стокових Pixel | ~750 МБ |
| full | + усе, включно з додатковими сервісами | >1 ГБ |

```bash
build/scripts/package-gapps.sh --vendor mtg --variant basic \
  --android 15 --arch x86_64
```

### 3. OpenGApps — **не рекомендуємо**

Проєкт не оновлюється з 2023 року; пакети для Android 14/15 відсутні, а
старі версії викликають bootloop. Скрипт підтримує `--vendor opengapps` лише задля
сумісності зі старими гілками.

## Чому GApps на емуляторі — особливий випадок

x86_64-образ TesseraOS не сертифікований, тому Play Store у ньому:

- встановиться, але може показувати «Device is not Play Protect certified»;
- частина застосунків (BankID-подібні, Netflix, Google Wallet) не запуститься,
  бо перевіряють attestation.

Робочі варіанти:

1. **microG + fake store** (Aurora Store) — для більшості застосунків.
2. Увімкнути `Settings → Security → Device certification` і отримати
   attestation-key через власний Google-акаунт (працює для помірної частини застосунків).
3. Для розробки — стоковий Google-образ AVD замість Tessera, а Tessera-образ
   тримати як ціль UI-тестів.

## Що робить скрипт пакування

`build/scripts/package-gapps.sh`:

1. Завантажує `.zip` обраного вендора з офіційного дзеркала й звіряє
   `sha256` з оголошеного чексума (без цього — зупинка).
2. Розпаковує, будує `vendor/gapps`-дерево й генерує `product.mk`-фрагмент
   списком `PRODUCT_PACKAGES` (щоб образ знав залежності).
3. Для microG: вмикає профіль і **не** вкладає закриті бинари.
4. Пише `config/gapps/installed.json` — журнал для `docs/GAPPS.md` і
   для PR-політики (заборона додавати будь-який GApps-бінар у git).

## Політика репозиторію

- Жодних GApps-бінарів, логотипів Google, шрифтів Product Sans, звукових
  ефектів чи анімацій із власницьких збірок Pixel.
- Назва й айдентика не мають виглядати як «Pixel OS»: це інший бренд, а
  Material Design 3 — відкритий стандарт оформлення, не торговая марка.
- Якщо PR додає бинар без відкритої ліцензії — автоматична відмова
  (`check-no-binary.yml`).
