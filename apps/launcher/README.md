# Tessera Launcher

Власний лаунчер на Kotlin + Compose Material 3. Він потрібен для того, чого
оверлеї не дають: рядок «дата + погода» угорі, поведінка дока й шухляди,
жест відкриття. Таблиця в [docs/PIXEL-PARITY.md](../../docs/PIXEL-PARITY.md)
позначає ці рядки як «потребує власного коду» — це той код.

У git немає жодного бінарника: ані `gradle-wrapper.jar`, ані APK. Іконки
застосунків лаунчер бере з `PackageManager` під час запуску, тому в APK лежать
лише наші ресурси.

## Стан

| Можливість | Реалізовано | Примітка |
|---|---|---|
| Роль HOME (`MAIN` + `HOME` + `DEFAULT`) | ✅ | `AndroidManifest.xml` |
| Палітра Monet із шпалер | ✅ | `dynamicDark/LightColorScheme`, фолбек на статичну палітру |
| Сітка 5 × 6 | ✅ | `GridSpec.LARGE_PHONE` |
| Док із 5 слотів | ✅ | підписів під іконками немає, як на стоці |
| Рядок «дата + погода» | ⚠️ дата | погода потребує джерела; власницький віджет Google не вставляємо |
| Шухляда з пошуком | ✅ | `LazyVerticalGrid`, 5 колонок, пошук по встановлених застосунках |
| Жест свайпу вгору/вниз | ✅ | 96 dp вгору — відкрити, 112 dp вниз — закрити |
| Закріпити / прибрати | ✅ | довгий натиск → меню |
| Перетягування, папки, кілька сторінок, віджети | ⬜ | наступні інкременти |
| Бейджі непрочитаних, робочий профіль | ⬜ | потребують `NotificationListener` / `ManagedProfiles` |

Геометрію не вигадували: `columns`, `rows`, `dockSlots`, `iconSizeDp`,
`labelTextSp`, `drawerCellHeightDp`, `drawerBorderSpaceDp` — це значення
профілю `5_by_6` з `res/xml/device_profiles.xml` бази — той самий профіль,
який ми робимо дефолтним build-time підміною. Профіль не задає розмір іконки
в шухляді окремо, тому там використовуємо розмір головного екрана.

## Збірка

### 1. Самостійно, через Gradle (розробка)

Wrapper-jar свідомо немає, тому запускаємо Gradle з уже встановленою
дистрибуцією: 8.14.3 або новіша, AGP 8.9.0, Kotlin 2.0.21:

```bash
cd apps/launcher
# локальний шлях до Android SDK — цей файл не комітиться (.gitignore)
echo "sdk.dir=/шлях/до/Android/Sdk" > local.properties
gradle --console=plain :app:testDebugUnitTest :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. На емулятор чи пристрій
з образом бази:

```bash
gradle :app:installDebug
adb shell cmd package set-home-activity os.tessera.launcher/.LauncherActivity
```

`minSdk = 33`, бо реєстрація приймача змін пакунків зроблена через
`RECEIVER_NOT_EXPORTED` — без цього чужий застосунок міг би підслуховувати
події встановлення.

### 2. Всередині образу ROM

Модуль оголошений: [Android.bp](Android.bp) — `android_app_import` з іменем
`TesseraLauncher`, `presigned: true`, `system_ext_specific: true` →
`system_ext/app/TesseraLauncher/`. У PRODUCT_PACKAGES його додає
`build/tessera.mk`.

APK у git немає (політика репо), тому перед `m` файл готує скрипт:

```bash
build/scripts/prepare-launcher.sh                 # зібрати Gradle-ом
build/scripts/prepare-launcher.sh prebuilt <шлях> # узяти вже зібраний
build/scripts/prepare-launcher.sh artifact <id>   # завантажити апек CI (GITHUB_TOKEN)
```

Він пише `apps/launcher/prebuilt/TesseraLauncher.apk` (ігнорується git через
`*.apk`), перевіряє через `aapt2`, що це наш package з HOME-фільтром, і
контролює складові APK. `build/scripts/build-emulator.sh` викликає його сам,
щоб помилка «apk not found» не з'являлася посеред години збірки; обійти —
`TESSERA_SKIP_LAUNCHER=1`. Інваріанти інтеграції тримає CI:
`build/scripts/check-launcher-integration.sh`.

Чому `android_app_import`, а не `android_app` із сирцевин — чесно: у бази
Compose збирається Soong-ом лише за прапорцем
`release_enable_compose_in_launcher`, і там перелічені тільки
`androidx.compose.runtime_runtime` і `androidx.compose.material3_material3`
(`packages/apps/Trebuchet/Android.bp:225-249`). Для нашого набору залежностей треба ще
`ui` / `foundation` / `activity-compose` / `lifecycle` і Compose-компілятор
Kotlin 2.0 — чи є вони в дереві під тими іменами, на білд-боксі ще не
перевірено. Шлях «Gradle → APK → import» єдиний, який ми вже бачили зеленим.

Чому `overrides` немає: сток у базі — `TrebuchetQuickStep`
(`vendor/lineage/config/common_mobile.mk:31-38`), і він дає не лише HOME, а й
перегляд недавніх (`frameworks/base/core/res/res/values/config.xml:3622`
`config_recentsComponentName`). Наш лаунчер Recents ще не реалізує, тому
перекриття зламало б жестову навігацію. У образі два HOME-застосунки; вибір
один раз робить користувач (або `cmd package set-home-activity` вище).
Коли з'явиться власний Recents — `overrides` повернеться, і CI вимагатиме
запису про це в `docs/PIXEL-PARITY.md`.

## Тести

`app/src/test/.../HomeLayoutTest.kt` — заповнення сітки (місткість, порожні
комірки, надлишок пінів). Решта потребує пристрою: `PackageManager` і Compose
не імітуються чесно, тому «зелений» CI не означає, що екран виглядає як
задумано — візуальну перевірку робимо на образі.
