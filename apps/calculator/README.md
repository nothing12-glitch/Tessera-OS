# Tessera Calculator

Власний калькулятор на Kotlin + Compose Material 3. Калькулятор Google у образ
покласти не можна — він пропрієтарний (див.
[docs/PIXEL-PARITY.md](../../docs/PIXEL-PARITY.md) §3), тому клавіші, показ
результатів і парсер пишемо самі.

У git немає жодного бінарника: ані `gradle-wrapper.jar`, ані APK. Іконка
застосунку — наша векторна (`drawable/ic_launcher_foreground.xml`), а не
чужий ресурс.

## Стан

| Можливість | Реалізовано | Примітка |
|---|---|---|
| Роль застосунку (`MAIN` + `LAUNCHER`) | ✅ | `AndroidManifest.xml` |
| Палітра Monet | ✅ | `dynamicDark/LightColorScheme`, фолбек на статичну MD3-палітру |
| Тональні клавіші як у Material 3 | ✅ | цифри — `surfaceContainerHighest`, оператори — `secondaryContainer`, `=` — `primary`, службові — `surfaceContainerLow`; радіус 28 dp |
| `+ − × ÷ ^ %` і дужки | ✅ | рекурсивний спуск: `unary → power → postfix → primary`; пріоритети й асоціативність підтверджені тестами |
| Правостороння асоціативність степеня | ✅ | `2^3^2 = 512`, а не 64 |
| `%` як постфікс | ✅ | `50%` = `0,5`; `200×50%` = `100` |
| Точна арифметика | ✅ | `BigDecimal` з `MathContext(20, HALF_UP)`; `0,1+0,2` = `0,3` без `0.30000000000000004` |
| Ділення на нуль | ✅ | `CalcException` → рядок помилки в UI, не збій |
| Формат показу | ✅ | десяткова кома, розряди групуємо вузьким пробілом (U+00A0, а не звичайний), перехід у науковий запис поза межами `1E-10 … 1E21` |
| Вираз на екрані | ✅ | `*` показуємо як `×`, `/` як `÷`, `-` як `−` (мінус, не дефіс) |
| Повтор результату | ✅ | оператор одразу після `=` бере попередній результат |
| Історія, окремий науковий режим з кнопками, тригонометрія, константи | ⬜ | наступні інкременти |

Два місця, де точність втрачається свідомо, і це записано в коді:

* дробовий показник степеня (`2^0,5`) рахується через `Math.pow` — `BigDecimal`
  такої операції не має;
* показане число має максимум 10 знаків після коми: читабельність важливіша за
  останні знаки довгого дробу.

## Збірка

### 1. Самостійно, через Gradle (розробка)

Wrapper-jar свідомо немає, тому запускаємо Gradle з уже встановленою
дистрибуцією: 8.14.3 або новіша, AGP 8.9.0, Kotlin 2.0.21:

```bash
cd apps/calculator
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
`TesseraCalculator`, `presigned: true`, `system_ext_specific: true` →
`system_ext/app/TesseraCalculator/`. У PRODUCT_PACKAGES його додає
`build/tessera.mk`.

Застосунок є в реєстрі `build/scripts/apps.list`, тому його готує й перевіряє
той самий скрипт, що й лаунчер:

```bash
build/scripts/prepare-app.sh calculator          # зібрати Gradle-ом
build/scripts/prepare-app.sh calculator prebuilt <шлях>
```

`build/scripts/build-emulator.sh` викликає підготовку для всіх застосунків
реєстра сам; інваріанти інтеграції тримає
`build/scripts/check-app-integration.sh`, а збірку з тестами — CI-матриця
`build-apps`.

## Що перевірено, а що — ні

*Перевірено*: 25 юніт-тестів парсера, редактора введення й формату
(`app/src/test/kotlin/.../logic/`) проходять на JVM; `aapt2 dump badging`
підтверджує `package: name='os.tessera.calculator'`, `minSdkVersion 33`,
`targetSdkVersion 35` і `LAUNCHER`-фільтр.

*Не перевірено*: жодного кадра з пристрою чи образу. Тобто геометричний
розмір клавіш (72 dp), відступи й палітра Monet на реальних шпалерах —
все ще 🧩, а не ✅. Окремо: чи прийме Soong `presigned: true` з debug-ключем
на справжньому `m`, теж досі не бачено.
