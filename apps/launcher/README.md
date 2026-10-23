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

Ще не зроблено. План: `apps/launcher` як окремий застосунок, який у образ
попадає через `android_app` у `Android.bp` (Soong збирає з сирцевин, без
бінарників у git) і стає `system_ext/priv-app` із дозволом на HOME. До того
моменту образ поставляється зі стоковим Trebuchet, а наш лаунчер — це APK із
CI-артефакту, який можна поставити поверх.

## Тести

`app/src/test/.../HomeLayoutTest.kt` — заповнення сітки (місткість, порожні
комірки, надлишок пінів). Решта потребує пристрою: `PackageManager` і Compose
не імітуються чесно, тому «зелений» CI не означає, що екран виглядає як
задумано — візуальну перевірку робимо на образі.
