# Tessera Welcome — майстер першого запуску

Це **не застосунок**. Іконки в шухляді в нього немає й бути не може: він
єдиний екран, який система показує один раз — поки пристрій не налаштовано.
Чотири кроки: привітання, тема, обліковий запис, готово. Kotlin + Compose
Material 3, динамічна палітра (Monet) з fallback.

Різниця з «звичайним застосунком» — не в дизайні, а в трьох рядках коду, які
без прав рівня системи написати неможливо:

| що робить | чим це виправдане |
|---|---|
| пише `Settings.Global.device_provisioned` і `Settings.Secure.user_setup_complete` | `WRITE_SECURE_SETTINGS` (`signature\|privileged`) |
| перемикає тему всього телефону, а не свого вікна | той самий дозвіл той самий рядок `ui_night_mode`, що й перемикач у Налаштуваннях |
| з'являється саме на першому запуску і більше ніколи | фільтр `CATEGORY_SETUP_WIZARD` + `finish()`, коли система вже налаштована |

Тому модуль лежить у `system_ext/priv-app`, а не в `system_ext/app`, і має
дозвільний запис `privapp-permissions-tessera-welcome.xml`. Без нього образ із
`ro.control_privapp_permissions=enforce` (`build/tessera.mk` §5) не
завантажився б взагалі.

Це **перший** з двох власних застосунків, які повернулися в образ після
рішення «прибрати наші застосунки зовсім» (2026-10-25). Він винятковий тому,
що нічого не робить замість системи: домашній екран, телефон, повідомлення,
камера, годинник, калькулятор і «Файли» й далі дає база — див.
`build/tessera.mk` §2b. Другий виняток — `apps/halo`, світловий край.

У git немає жодного бінарника: ні `gradle-wrapper.jar`, ні APK. Знак на
логотипі — та сама геометрія, що й у `brand/android/tessera_mark.xml`.

## Архітектура

* `logic/WelcomeFlow.kt` — чистий Kotlin без `android.*`-імпорту: перелік
  кроків, «Далі»/«Назад», пропуск облікового запису, прогрес, мапіч «вибір
  теми → нічний режим», маршрут кроку з акаунтом і `shouldShow`, який залежить
  лише від одного булевого — чи система вже налаштована. Усе це перевіряється
  юніт-тестом на JVM (`app/src/test/`) — саме тому порядок кроків живе тут, а
  не в UI.
* `data/SystemFlags.kt` — єдине місце, де торкаються системних прапорців:
  `isProvisioned()`, `nightMode()`, `setNightMode()`, `markProvisionedSafely()`.
  Останнє повертає `false` замість того, щоб зробити вигляд, що вийшло.
* `WelcomeViewModel` — пошук пакунка `com.google.android.gms`, два
  `startActivity`, виклики `SystemFlags`.
* `ui/` — `TesseraWelcomeTheme` (Monet + edge-to-edge) і `WelcomeScreen`
  (один `AnimatedContent` на всі переходи).

Власних `SharedPreferences` у модуля більше немає. Поки вітальня була
застосунком, «пройдено» доводилось ховати у власному файлі — і це була підміна,
а не реалізація: система про той файл не знала.

## Що перевірено наживо

Прогін 2026-10-25 на емуляторі (AVD `TesseraVM`, 1080×2400, Android 17,
`google_apis_playstore`). Знімки — у робочому каталозі `.scratch/shots/`, вони
не в git.

| перевірка | результат | знімок |
|---|---|---|
| у APK немає жодного `LAUNCHER`-фільтра | `cmd package query-activities -a MAIN -c LAUNCHER --components \| grep -c tessera` → `0` | — |
| `WRITE_SECURE_SETTINGS` справді видано | `dumpsys package os.tessera.welcome` → `install permissions: … WRITE_SECURE_SETTINGS: granted=true` | — |
| тап по чіпі «Світла»/«Темна» рухає системний перемикач | `Settings.Secure.ui_night_mode`: `2 → 1 → 2`, і значення пише застосунок, не adb | `82-oobe-2b-light-proof.png`, `83-oobe-2c-dark.png` |
| «Почати» завершує налаштування системи | `device_provisioned = 1`, `user_setup_complete = 1`, фокус — `NexusLauncherActivity` | `86-oobe-done-launcher.png` |
| після проходження майстер не повертається | той самий `am start` → фокус лишається на лаунчері (активність одразу `finish()`) | — |
| «не налаштовано» знову кличе майстра | `settings put global device_provisioned 0` + старт → кроки знову, з першого | `80-oobe-1-greeting.png` |
| усі чотири кроки рендеряться в темній темі | так, із «Крок N із 4» і «Тема — Темна» на останньому | `80-…`, `81-…`, `84-oobe-3-account.png`, `85-oobe-4-finish.png` |
| `resolve-activity` для `MAIN` + `SETUP_WIZARD` | повертає `os.tessera.welcome/.WelcomeActivity` | — |

### Що цей прогін НЕ довів — і це головне

Три речі, які виглядають доведеними, а насправді ні:

**1. Дозвіл тут видано через adb, а не через `priv-app`.** Ми поставили APK
командою `adb install -g`. `-g` видає дозвіл як виняток для відлагодження; у
справжньому образі той самий `granted=true` має дати сукупність «модуль у
`system_ext/priv-app` + рядок у `privapp-permissions-*.xml`». Саме цю
сукупність описує `Android.bp`, але на живому образі її ще не бачили: поточний
AVD — образ `user`/`release-keys`, де `adb root` заборонений
(`adbd cannot run as root in production builds`), тож у `/system_ext` не
записатися. Перевірка — на `google_apis` (userdebug) образі, який качається
зараз, і остаточна — на власному образі LineageOS.

**2. Ми викликали майстра самі.** Увесь прохід ішов із явним `cmp=`. Те, що
система кличе нас **сама** на справжньому першому запусці, не перевірено ніде:
на цьому образі `cmd package query-activities -a MAIN -c SETUP_WIZARD`
повертає `No activities found` — категорію не тримає жоден застосунок, тож
немає з чим порівнювати. У LineageOS свій майстер і там питань більше:

```bash
grep -rn "SETUP_WIZARD" frameworks/base/core/java/android/content/Intent.java
grep -rn "config_deviceProvisioningActivity" frameworks/base/core/res/res/values/config.xml
grep -rn "category.HOME" frameworks/base/core/res/AndroidManifest.xml
```

Третій рядок — не формальність: якщо база заводить майстра не категорією, а
рядком у `config_deviceProvisioningActivity`, то `priority` у нашому маніфесті
не вирішує нічого, і треба буде саме `overrides` у `Android.bp`.

**3. `priority="1000"` — заява, а не результат.** Число стоїть у маніфесті й
не перевірене жодним порівнянням, бо другого кандидата на категорію на
тестовому образі немає.

## Чого цей модуль НЕ робить

Список навмисно довгий: помилка «модуль є в образі, а обіцянки з нього
виправдані» — той самий клас, від якого захищає `check-app-integration.sh`.

**Не перекриває майстра бази.** Поки `overrides` у `Android.bp` немає, у образі
лишаються два кандидати на перший запуск. Рішення ухвалюється після того, як
`repo sync` принесе `frameworks/base` і буде звірено, яким саме механізмом
LineageOS 22.2 заводить свій мастер — категорією чи конфіг-рядком.

**Не показує ані слова, якщо система вже налаштована.** Це не «закривається
помилково»: `skipWhenAlreadyPassed()` читає `device_provisioned`, і поки він
рівний 1, модуль не має права перекривати екран.

**Не пише прапорці мовчки.** Якщо дозволу раптом немає, на екрані з'являється
`welcome_theme_not_applied` / `welcome_provision_failed` — прямий текст про
те, що саме не вийшло. Ховати це за кнопкою, яка «ніби спрацювала», — той
самий клас брехні, від якого захищає цей README.

**Не обіцяє Google-сервісів.** У образі їх немає: `build/tessera.mk` §3
свідомо тримає `PRODUCT_PACKAGES` без GApps і без microG, а
`.github/workflows/policy.yml` блокує власницькі бінарники. Кроку з обліковим
записом вистачає чесного факту: якщо на пристрої є постачальник `GmsCore-API` —
показуємо кнопку «Додати акаунт», якщо немає — кажемо, що додавати нікуди, і
пропонуємо пропустити.

**Не робить того, чого не може OOBE взагалі.** Мову системи, SIM, Wi-Fi й
відновлення з резервної копії на цих кроках немає: перше потребує
системного API, який недоступний застосунку навіть із `WRITE_SECURE_SETTINGS`,
решта — дозволів рівня оператора/мережі. Тому кроків чотири, а не дванадцять.

## Збірка й установка

```bash
# у WSL-хості (Gradle 8.14.3 + Android SDK), див. docs/BUILD.md
cd apps/welcome
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest
# APK → app/build/outputs/apk/debug/app-debug.apk
```

На пісочничному образі `user` (так само, як перевіряли 2026-10-25):

```bash
adb install -r -g app/build/outputs/apk/debug/app-debug.apk   # -g — щоб дозвіл видано було як виняток
adb shell settings put global device_provisioned 0            # «телефон щойно з коробки»
adb shell settings put secure user_setup_complete 0
adb shell am start -a android.intent.action.MAIN \
  -c android.intent.category.SETUP_WIZARD os.tessera.welcome/.WelcomeActivity
# темна тема для публічних знімків:
adb shell cmd uimode night yes
```

Майте на увазі: після `Почати` прапорці піднімуться справді, і наступний
запуск уже нічого не покаже — саме це ми й перевіряли.

У образ модуль іде двома рядками, які перевіряються статично:
`build/scripts/apps.list` (`welcome|TesseraWelcome|os.tessera.welcome|…`) і
`PRODUCT_PACKAGES += TesseraWelcome` у `build/tessera.mk`. `Android.bp` —
`android_app_import` з `presigned: true`, `privileged: true` і
`system_ext_specific: true`, плюс `prebuilt_etc` із дозвільним списком.

Те, що в APK є `SETUP_WIZARD` і немає `LAUNCHER`, вирішує не кожен скрипт
окремо: правило живе в одному файлі `build/scripts/check-apk-manifest.sh`, а
кликать його і `prepare-app.sh`, і job `apk` у `build-apps.yml`. Раніше копія
правила жила в workflow окремо — і саме через це job «apk (welcome, …,
SETUP_WIZARD)» впав на першому ж білді після перетворення вітальні на мастер:
workflow у кожного APK все ще вимагав іконку (виправлено в `ad786c9`).

Окремо, щоб не плутали: `privileged` ≠ підпис платформним ключем. Привілей
дає partition і рядок у дозвільному списку; ключ платформи тут ні при чому, і
він нам не потрібен — тому модулю `presigned` і вистачає.
