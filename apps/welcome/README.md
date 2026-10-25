# Tessera Welcome

Екран першого запуску: повноекранне «Вітаємо у Tessera OS!» із чотирма кроками —
привітання, тема, обліковий запис, готово. Kotlin + Compose Material 3, динамічна
палітра (Monet) з fallback.

Це **перший** власний застосунок, який повертається в образ після рішення
«прибрати наші застосунки зовсім» (2026-10-25). Причина, з якої він винятковий:
він нічого не робить замість системи, а лише пояснює користувачеві, що система
вже зробила. Домашній екран, телефон, повідомлення, камера, годинник,
калькулятор і «Файли» й далі дає база — див. `build/tessera.mk` §2b.

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

## Чого цей застосунок НЕ робить

Список навмисно довгий: помилка «застосунок є в образі, а обіцянки з нього
виправдані» — той самий клас, від якого захищає `check-app-integration.sh`.

**Не запускається сам замість майстра бази.** Для цього потрібен системний
гачок: категорія `SETUP_WIZARD` або `HOME` з вищим `priority`, ніж у майстра
бази. У маніфесті обидва фільтри заявлені, але **ім'я категорії ще не звірене**
з `frameworks/base` і з тим, хто тримає `HOME` у LineageOS 22.2, — дерево бази
спершу треба завантажити повністю (`repo sync` триває). Наразі стартувати можна
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

## Збірка істановка

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
