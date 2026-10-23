# Tessera Годинник

Власний годинник на Kotlin + Compose Material 3: секундомір, таймер і
будильники. Годинник Google у образ покласти не можна — він пропрієтарний
(див. [docs/PIXEL-PARITY.md](../../docs/PIXEL-PARITY.md) §3), тому механізм
відліку, зворотний таймер, розклад будильників і планування через `AlarmManager`
пишемо самі.

У git немає жодного бінарника: ані `gradle-wrapper.jar`, ані APK. Іконка
застосунку й чотири службові знаки — наші векторні (`res/drawable/ic_*.xml`),
а не чужий шрифт іконок Material.

## Архітектура

Три призначення за MD3 `NavigationBar`: **Секундомір / Таймер / Будильники**.

Уся точність живе в чистому Kotlin під `logic/` (жодного `android.*`-імпорту),
тож перевіряється юніт-тестом на JVM:

* `ClockFormat` — показ `mm:ss.d` / `h:mm:ss` / `час доби` / «через скільки»;
* `StopWatch` — машина станів секундоміра на інʼєктованому поставачі часу;
* `CountDown` — зворотний відлік із продовженням під час роботи;
* `AlarmSpec` — `nextTriggerMillis` для щоденного й одноразового;
* `AlarmCodec` — розділювально-безпечне кодування списку будильників.

Момент часу для лічильників — `SystemClock.elapsedRealtime` (монотонний
апаратний годинник), а не стінковий час. Тому зміна дати, години чи поясу не
рухає показ, а відлік не «дрейфує»: рахуємо лише різниці двох зчитувань.
Стінковий `System.currentTimeMillis()` вживаємо лише там, де треба абсолютний
момент у epoch — планування `setAlarmClock` і рядок «наступний через…».

## Стан

| Можливість | Реалізовано | Примітка |
|---|---|---|
| Роль застосунку (`MAIN` + `LAUNCHER`) | ✅ | `AndroidManifest.xml` |
| Палітра Monet | ✅ | `dynamicDark/LightColorScheme`, фолбек на статичну MD3-палітру (`ui/TesseraTheme.kt`) |
| Тональні ролі Material 3 | ✅ | `secondaryContainer`/`surfaceContainerLow`/`surfaceContainerHighest`/`errorContainer`, радіус 28 dp, сітка відступів 8 dp; жодного фіксованого кольору в коді |
| Секундомір `mm:ss.d` → `h:mm:ss.d` | ✅ | `StopWatch` на `elapsedRealtime`; коло фіксується лише під час руху, розбивка + загальний час, найновіше угорі |
| Таймер `h:mm:ss` | ✅ | `CountDown`; «+ хвилина/година» під час роботи продовжує ціль, а не перезапускає; «−» і «Скинути» є; чіткий стан «Час вийшов» |
| Будильники: список, вимк/увімк, підпис, «щодня», видалення | ✅ | `AlarmRow` + `Switch` + `IconButton`; рядок «наступний через 8 год 12 хв» |
| Редактор без модального вікна | ✅ | панель простої у контенті (`AlarmEditor`) на MD3 `TimePicker`, не діалог |
| `nextTriggerMillis` | ✅ | щоденний і одноразовий; вимкнений → `null`; «рівно в now» = вже минуле → завтра |
| Персистенція | ✅ | `SharedPreferences` + `AlarmCodec` з роздільниками U+001F/U+001E (керуючі символи, яких немає в підписі); зламаний запис пропускаємо, а не падаємо |
| `setAlarmClock` + `BroadcastReceiver` | ✅ | канал створюємо до першого post; `USE_EXACT_ALARM`; одноразовий вимикається після сигналу |
| `POST_NOTIFICATIONS` у runtime | ✅ | запит в активності; без дозволу — банер, не збій |
| `canScheduleExactAlarms() == false` | ✅ | банер + кнопка в системні налаштування, без падіння |
| Пере-постановка після перезавантаження | ✅ | `BootReceiver` (`RECEIVE_BOOT_COMPLETED`): `setAlarmClock` зникає на reboot |
| Тригонометричний світовий час, секундомір із кількома паралельними колами | ⬜ | наступні інкременти |

Два місця, де свідомо обираємо консервативне, і це записано в коді:

* межі `nextTriggerMillis`: спрацювання рівно в `now` вважаємо «минулим» і
  беремо завтра — `AlarmManager` уже не встигає поставити на «зараз»;
* одноразовий і щоденний дають той самий *наступний* пік; різницю робить
  приймач: після сигналу одноразовий стає `enabled = false`, тож наступний
  виклик уже `null`.

## Збірка

### 1. Самостійно, через Gradle (розробка)

Wrapper-jar свідомо немає, тому запускаємо Gradle з уже встановленою
дистрибуцією: 8.14.3 або новіша, AGP 8.9.0, Kotlin 2.0.21:

```bash
cd apps/clock
# локальний шлях до Android SDK — цей файл не комітиться (.gitignore)
echo "sdk.dir=/шлях/до/Android/Sdk" > local.properties
gradle --console=plain :app:testDebugUnitTest :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. На емулятор чи пристрій
з образом бази:

```bash
gradle :app:installDebug
```

### 2. Всередині образу ROM

Модуль оголошений: [Android.bp](Android.bp) — `android_app_import` з іменем
`TesseraClock`, `presigned: true`, `system_ext_specific: true` →
`system_ext/app/TesseraClock/`. У PRODUCT_PACKAGES його додає
`build/tessera.mk`.

Застосунок готує й перевіряє той самий скрипт, що й інші наші застосунки:

```bash
build/scripts/prepare-app.sh clock          # зібрати Gradle-ом
build/scripts/prepare-app.sh clock prebuilt <шлях>
```

Інваріанти інтеграції тримає `build/scripts/check-app-integration.sh`, а
збірку з тестами — CI-матриця `build-apps`.

## Що перевірено, а що — ні

*Перевірено*: 39 юніт-тестів чистої логіки
(`app/src/test/kotlin/.../logic/`) проходять на JVM — формати показу,
кола секундоміра з розбивкою, продовження відліку «+» під час роботи й
 перехід у «Час вийшов», `nextTriggerMillis` на фіксованому поясі `GMT+02:00`
без переходу на літній час (сьогодні/завтра/рівно-в-now/через-кінець-місяця),
round-trip `AlarmCodec` із підписами, що містять коми, лапки й керуючі
символи; `assembleDebug` збирається; `aapt2 dump badging` підтверджує
`package: name='os.tessera.clock'`, `minSdkVersion 33`, `targetSdkVersion 35`
й `launchable-activity` = `.ClockActivity`; `aapt2 dump xmltree` показує
фільтр `android.intent.category.LAUNCHER`, обидва `<receiver>` і дозволи
`USE_EXACT_ALARM` / `POST_NOTIFICATIONS` / `RECEIVE_BOOT_COMPLETED`.

*Не перевірено*: жодного кадра з пристрою чи емулятора — на цій машині немає
апаратного прискорення, тому не видно реального Monet на шпалерах, геометрії
`TimePicker`, ніби-які 8 dp відступи на екрані. Не перевірено й «живу»
поведінку сигналу: чи `setAlarmClock` розбудить пристрій із вимкненим
екраном, чи банер точних дозволів зʼявиться саме тоді, коли треба, чи
пере-постановка після reboot спрацює. Тобто UI й усе, що залежить від
Android-сервісів — це 🧩 «реалізовано й компілюється», а не «бачено на
екрані». Окремо: чи прийме Soong `presigned: true` з debug-ключем на
справжньому `m`, теж досі не бачено.
