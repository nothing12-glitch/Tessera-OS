# Tessera Welcome

Екран першого запуску: повноекранне «Вітаємо у Tessera OS!» із чотирма кроками —
привітання, тема, обліковий запис, готово. Kotlin + Compose Material 3, динамічна
палітра (Monet) з fallback.

Це **перший** з двох власних застосунків, які повернулися в образ після рішення
«прибрати наші застосунки зовсім» (2026-10-25). Причина, з якої він винятковий:
він нічого не робить замість системи, а лише пояснює користувачеві, що система
вже зробила. Домашній екран, телефон, повідомлення, камера, годинник,
калькулятор і «Файли» й далі дає база — див. `build/tessera.mk` §2b. Другий
виняток — `apps/halo`, світловий край на сповіщення.

У git немає жодного бінарника: ні `gradle-wrapper.jar`, ні APK. Знак на іконці —
та сама геометрія, що й у `brand/android/tessera_mark.xml`.

## Архітектура

* `logic/WelcomeFlow.kt` — чистий Kotlin без `android.*`-імпорту: перелік кроків,
  «Далі»/«Назад», пропуск облікового запису, прогрес, мапіч «вибір теми → чи
  темна», маршрут кроку з акаунтом. Усе це перевіряється юніт-тестом на JVM
  (`app/src/test/`) — саме тому порядок кроків живе тут, а не в UI.
* `WelcomeViewModel` — єдине місце, де торкаються Android: пошук пакунка
  `com.google.android.gms`, два `startActivity`, запис уподобань.
* `data/WelcomePrefs` — `SharedPreferences` власного файла: тема й «яку версію
  вітальні вже пройдено».
* `ui/` — `TesseraWelcomeTheme` (Monet + edge-to-edge) і `WelcomeScreen`
  (один `AnimatedContent` на всі переходи).

## Що перевірено наживо

Два прогони 2026-10-25 на емуляторі Android 15 (AVD `TesseraVM`, 1080×2400,
темна тема). Знімки — у робочому каталозі `.scratch/shots/`, вони не в git.

| перевірка | результат | знімок |
|---|---|---|
| усі чотири кроки рендеряться | так: Привітання → Тема → Обліковий запис → Готово | `63-welcome-live-wizard-dark.png`, `65-…-theme-picked-dark.png`, `66-…-account-dark.png`, `67-…-finish-dark.png` |
| запуск інтентом майстра (`MAIN` + `SETUP_WIZARD` + явний компонент) | `ActivityTaskManager: Displayed os.tessera.welcome/.WelcomeActivity: +4s659ms`, повноекранно | `63-welcome-live-wizard-dark.png` |
| вибір «Темна» перефарбовує вікно одразу | так, і доходить до кроку «Готово» з «Тема — Темна» | `65-…-theme-picked-dark.png`, `67-…-finish-dark.png` |
| «Почати» закриває активність і зберігає вибір | фокус повертається до лаунчера; `shared_prefs/tessera_welcome.xml`: `theme=DARK`, `acknowledged_version=1` | `68-welcome-live-after-done-dark.png` |
| повторний запуск тим самим інтентом після проходження | активність стартує й одразу `finish()` — фокус лишається на лаунчері | — |
| крок з обліковим записом на пристрої з `com.google.android.gms` | маршрут `SETTINGS` показав живий екран; «провайдера немає» — дефолт, накритий юніт-тестом | `66-…-account-dark.png` |
| пакунок образу зібраний і не порожній | `tessera-vm-15.22.2.3-vega.tar.xz` (11 МБ, sha256 `6be9560c…`): 2 APK + 3 RRO-оверлеї + інсталятори | — |

### Головне, що знайшов прогін інтентом без компонента

`am start -a android.intent.action.MAIN -c android.intent.category.SETUP_WIZARD`
**без** `cmp=` дає `Error: Activity not started, unable to resolve Intent` — і
навіть явне додавання `CATEGORY_DEFAULT` другим категорією не допомагає. При
цьому `cmd package resolve-activity` з тим самим інтентом спокійно повертає
саме `os.tessera.welcome/.WelcomeActivity`.

Різниця не суперечність, а механіка: `startActivity` бачить лише фільтри з
`CATEGORY_DEFAULT`, а наш фільтр `SETUP_WIZARD` (`dumpsys package` показує в
ньому тільки `MAIN` + `SETUP_WIZARD`) його не містить. Тобто:

- резолвер інтента нас **знаходить** — отже ім'я категорії справді існує й
  маніфест написаний правильно;
- але жодна система, яка просто «посилає інтент», нас **не запустить**.
  Справжній майстер налаштування викликає застосунок за явним іменем
  компонента, а не сподівається на імплемітний резолвінг.

Це і є пряме підтвердження пункту «не запускається сам» нижче — тепер уже не
за описом, а за текстом помилки з пристрою.

## Чого цей застосунок НЕ робить

Список навмисно довгий: помилка «застосунок є в образі, а обіцянки з нього
виправдані» — той самий клас, від якого захищає `check-app-integration.sh`.

**Не запускається сам замість майстра бази.** Для цього потрібен системний
гачок: категорія `SETUP_WIZARD` або `HOME` з вищим `priority`, ніж у майстра
бази. У маніфесті обидва фільтри заявлені, але **ім'я категорії ще не звірене**
з `frameworks/base` і з тим, хто тримає `HOME` у LineageOS 22.2, — дерево бази
спершу треба завантажити повністю (`repo sync` триває). Додавання
`CATEGORY_DEFAULT` у фільтр теж не допомогло б самостійно: воно лише відкрило б
імплемітний запуск, а чергу на старті все одно дістає той, хто вищий за
`priority` — тобто майстер бази. Наразі стартувати можна
двома перевіреними шляхами:

```bash
# зі звичайного лаунчера (іконка «Ласкаво просимо»)
# або з adb — обидва фільтри:
adb shell am start -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER os.tessera.welcome/.WelcomeActivity
adb shell am start -a android.intent.action.MAIN \
  -c android.intent.category.SETUP_WIZARD os.tessera.welcome/.WelcomeActivity
```

Як звірити, коли `frameworks/base` буде в дереві:

```bash
grep -rn "SETUP_WIZARD" frameworks/base/core/java/android/content/Intent.java
grep -rn "category.HOME" frameworks/base/core/res/AndroidManifest.xml
grep -rn "priority" packages/apps/LineageSetupWizard/AndroidManifest.xml  # ім'я модуля теж перевірити
```

**Не перемикає системну тему.** `UiModeManager.setNightMode()` вимагає дозвіл
рівня системи, а `presigned`-застосунок (не підписаний ключем платформи) його не
має. Тому вибір теми тут керує лише цим вікном — це чесне «попередження», яке
користувач бачить на екрані, — а далі застосунок відкриває
Налаштування → Дисплей, де перемикач справді системний. Рядок
`welcome_theme_preview_note` каже про це прямісінько, а не ховається за
кнопкою, яка «не спрацювала».

**Не пише `device_provisioned`.** `Settings.Global.DEVICE_PROVISIONED` —
`WRITE_SECURE_SETTINGS` (`signature|privileged`); без підпису платформним
ключем і без `priv-app` це не наш рівень прав. Поки що «пройдено» зберігається у
власних `SharedPreferences`, і саме тому `WelcomeFlow.shouldShow` працює з
нашою версією вітальні, а не з прапорцем системи.

**Не обіцяє Google-сервісів.** У образі їх немає: `build/tessera.mk` §3
свідомо тримає `PRODUCT_PACKAGES` без GApps і без microG, а
`.github/workflows/policy.yml` блокує власницькі бінарники. Кроку з обліковим
записом вистачає чесного факту: якщо на пристрої є постачальник `GmsCore-API` —
показуємо кнопку «Додати акаунт», якщо немає — кажемо, що додавати нікуди, і
пропонуємо пропустити.

## Збірка й установка

```bash
# у WSL-хості (Gradle 8.14.3 + Android SDK), див. docs/BUILD.md
cd apps/welcome
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest
# APK → app/build/outputs/apk/debug/app-debug.apk

# на емулятор пісочниці (AVD TesseraVM, див. docs/EMULATOR.md)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd uimode night yes          # темна тема: публічні знімки лише так
```

У образ застосунок іде двома рядками, які перевіряються статично:
`build/scripts/apps.list` (`welcome|TesseraWelcome|os.tessera.welcome|…`) і
`PRODUCT_PACKAGES += TesseraWelcome` у `build/tessera.mk`. `Android.bp` —
`android_app_import` з `presigned: true` і `system_ext_specific: true`;
`privileged` там немає свідомо: без платформного ключа це лише ілюзія прав
(див. два пункти вище).
