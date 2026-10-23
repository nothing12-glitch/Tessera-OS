# Tessera Файли

Власний файловий застосунок на Kotlin + Compose Material 3. Files by Google у
образ покласти не можна — він пропрієтарний (див.
[docs/PIXEL-PARITY.md](../../docs/PIXEL-PARITY.md) §3), тому список, навігацію
й дії з папками пишемо самі.

У git немає жодного бінарника: ані `gradle-wrapper.jar`, ані APK. Іконка
застосунку — наша векторна (`drawable/ic_launcher_foreground.xml`).

## Чому SAF, а не дозвіл на сховище

Застосунок не бере `MANAGE_EXTERNAL_STORAGE`. Користувач обирає одну папку
через `OpenDocumentTree`, ми тримаємо на неї сталий дозвіл
(`takePersistableUriPermission`) і бачимо рівно те, що обрано. Це та сама
модель, на якій стоїть сучасний Android: без «дозволу на все» ані для користувача,
ані для нас.

Наслідок, який варто знати заздалегідь: системних каталогів на кшталт
`/system/app` застосунок не покаже — не тому що не вміє, а тому що система не
дає. Для розробницьких переглядів лишається сток-DocumentsUI, який у образі
працює далі (ми його не перекриваємо — див. `Android.bp`).

## Стан

| Можливість | Реалізовано | Примітка |
|---|---|---|
| Роль застосунку (`MAIN` + `LAUNCHER`) | ✅ | `AndroidManifest.xml`, жодних дозволів у ньому |
| Вибір папки й сталий дозвіл | ✅ | `OpenDocumentTree` + `takePersistableUriPermission`; після перезапуску відкриваємо найсвіжіший дозвіл |
| Список умісту папки | ✅ | `buildChildDocumentsUriUsingTree`, проєкція з 6 колонок |
| Крихти й стрибок на будь-який рівень | ✅ | `Breadcrumbs` + `jumpTo` |
| Пошук у поточній папці | ✅ | нормалізація NFD і діакритика знімаються, регістр не важливий |
| Сортування | ✅ | назва ↑, назва ↓, дата, розмір; папки завжди попереду |
| Створити папку | ✅ | `createDocument`, ідентифікатор беремо з `getDocumentId` |
| Перейменувати | ✅ | `renameDocument` |
| Видалити | ✅ | `deleteDocument`; підтвердження в діалозі |
| Відкрити файл чужим застосунком | ✅ | `ACTION_VIEW` + `FLAG_GRANT_READ_URI_PERMISSION`; власних декодерів не пишемо |
| Розміри й час у людській формі | ✅ | двійкові одиниці (КіБ/МіБ/ГіБ), «сьогодні / учора / 3 дні тому», українська плюралість |
| Інше дерево не «зʼїдає» попереднє | ✅ | `openTree` звільняє старий дозвіл через `releasePersistableUriPermission` |
| Ескізи фото, виділення кількох, копіювання/переміщення, архіви, кілька коренів одночасно | ⬜ | наступні інкременти |

## Збірка

### 1. Самостійно, через Gradle (розробка)

Wrapper-jar свідомо немає, тому запускаємо Gradle з уже встановленою
дистрибуцією: 8.14.3 або новіша, AGP 8.9.0, Kotlin 2.0.21:

```bash
cd apps/files
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
`TesseraFiles`, `presigned: true`, `system_ext_specific: true` →
`system_ext/app/TesseraFiles/`. У PRODUCT_PACKAGES його додає
`build/tessera.mk`.

Застосунок є в реєстрі `build/scripts/apps.list`, тому його готує й перевіряє
той самий скрипт, що й лаунчер:

```bash
build/scripts/prepare-app.sh files          # зібрати Gradle-ом
build/scripts/prepare-app.sh files prebuilt <шлях>
```

Інваріанти інтеграції тримає `build/scripts/check-app-integration.sh`, а
збірку з тестами — CI-матриця `build-apps`.

## Що перевірено, а що — ні

*Перевірено*: 14 юніт-тестів логіки (`app/src/test/kotlin/.../logic/`)
проходять на JVM — сортування з папками попереду, пошук з нормалізацією,
розміри й відносні дати; `aapt2 dump badging` підтверджує
`package: name='os.tessera.files'`, `minSdkVersion 33`, `targetSdkVersion 35`
і `LAUNCHER`-фільтр; маніфест не містить жодного `<uses-permission>`.

*Не перевірено*: жодного реального діалогу SAF, жодного кадра з пристрою чи
образу — `DocumentsProvider` живий лише на Android. Тобто список, крихти й
панель дій досі 🧩, а не ✅. Окремо: чи прийме Soong `presigned: true` з
debug-ключем на справжньому `m`, теж досі не бачено.
