# Реалізуємість: чи можна це зробити штатними засобами бази

Технічна перевірка п'яти пунктів опитування 2026-10-26, до того як будь-що з них потрапить у README чи релізні нотатки. Метод той самий, що §8 `docs/UI.md`: файли бази (гілка `lineage-22.2`) дістаються HTTP, і кожне ім'я ресурсу, тега чи класу супроводжується адресою (і рядком, коли вдалося порахувати). Неперевірене позначено `❓`, а не пом'якшене. Скорочення в URL: `TB=` `https://raw.githubusercontent.com/LineageOS/android_packages_apps_Trebuchet/lineage-22.2`, `FB=` `https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-22.2`, `API=` `https://api.github.com/repos/LineageOS`.

## 1. Сітка 4×6

**Питання.** Чи поточна build-time підміна `device_profiles.xml` дає 4×6 і чи може користувач вибрати сітку сам.

**Що знайдено** (`$TB/res/xml/device_profiles.xml`: 8 `<grid-option>` + 16 `<display-option>`; тега `default-property` у 22.2 немає):
- Розміри — атрибути `<grid-option>`: `numColumns`, `numRows`, `numHotseatIcons`, `numFolderRows/Columns`, `dbFile`, `defaultLayoutId`, `deviceCategory` (styleable `GridDisplayOption` у `$TB/res/values/attrs.xml`); `canBeDefault` — атрибут `<display-option>` (там же, `:534`). Тобто для 4×6: `numColumns="4" numRows="6"` на `grid-option`, `canBeDefault="true"` на `display-option`.
- Жорстка вимога: хоча б один `display-option` з `canBeDefault=true`, інакше `RuntimeException("No display option with canBeDefault=true")` — `$TB/src/com/android/launcher3/InvariantDeviceProfile.java:669` (фільтр `:654-663`, вибір найближчого за габаритами `:395-400`).
- Ім'я профілю — довільний рядок: пишеться у `LauncherPrefs.GRID_NAME` (`:404`) і шукається при наступних запусках з перевіркою `deviceCategory` (`:651-653`, `:1216-1223`), тому `deviceCategory` мусить включати `phone`.
- Обмежень на кількість колонок чи іконок дока немає: `numColumns`/`numRows` — прості `getInt` (`:1073-1074`), `numHotseatIcons` за замовчуванням = `numColumns` (`:1092-1093`), `numShownHotseatIcons = numHotseatIcons` (`:490`). Отже 4×6 з `numHotseatIcons="5"` легальне; з `"4"` п'ятої комірки в доці не буде.
- Інтерфейсу вибору сітки в базі **немає**: `$TB/res/xml/launcher_preferences.xml` (106 рядків) — лише блокування розкладки, беджі, автодобавляння, поворот, мінус-один, Trust apps; у `$TB/res/values/strings.xml` (536) входжень «grid» — 0; `SettingsActivity.java` сітку не обробляє. Твердження `docs/PIXEL-PARITY.md` §4, що після першого завантаження «у Налаштуваннях лаунчера вибрано «4 × 6»», джерелом 22.2 не підтверджується — вибирати там нічого.

**Вердикт.** ✅ 4×6 як **стан образу** (поточний файл побудований правильно); ❌ «користувач сам перемикає сітку» — без власного коду неможливо.

**Що це змінює в планах.** Пункт закривається чинною підміною; обіцянку вибору сітки знімаємо й переносимо у «потрібен власний код», поряд з жестами.

## 2. Док «4 іконки + папка»

**Питання.** Чи може профіль пристрою покласти теку в док без втручання в код лаунчера.

**Що знайдено.**
- Файл профілів теку не створює — атрибутів теки в `GridDisplayOption` немає.
- Стартовий вміст задає **інший** файл: `defaultLayoutId` → `@xml/default_workspace_4x5`, який читається лише коли БД порожня — `$TB/src/com/android/launcher3/model/ModelDbController.java:628-665`.
- Теки в стартовому layout підтримуються штатно: `DefaultLayoutParser.java:40` (`TAG_FOLDER = "folder"`), `:76` (`MyFolderParser`), `:243` (атрибут `folderItems`) — усі в `$TB/src/com/android/launcher3/`; у базовому `$TB/res/xml/default_workspace_4x5.xml:82-114` уже лежить `<folder launcher:title="@string/google_folder_title" …>`.
- Док: `launcher:container="-101"`, позиція слота — `launcher:screen` (коментар бази `:19`; `AutoInstallsLayout.java:170, 268-277` і override `DefaultLayoutParser.java:82-88`, там же). Тобто `<folder launcher:container="-101" launcher:screen="4">` — штатний запис, але саме у `default_workspace_*.xml`, не у профілі.
- Обмеження: тека з менше ніж двома доданими застосунками **видаляється**, а її єдиний вміст «випливає» на її місце — `AutoInstallsLayout.java:630-648`; broadcast-шлях тек не створює — `ItemInstallQueue.java:21-23, 248-268` знає лише `APPLICATION`/`DEEP_SHORTCUT`/`APPWIDGET`.

**Вердикт.** ⚠️ можна, але **не профілем**: потрібна друга build-time підміна `res/xml/default_workspace_*.xml` і застосунки, які справді є в образі.

**Що це змінює в планах.** Пункт лишається реализуємим і стає пунктом збірки з прив'язкою до складу застосунків: без GApps гугл-тека бази розпадається, тож у dock кладемо свою теку з двома нашими застосунками.

## 3. Форма іконок, яку обирає користувач

**Питання.** Чи існує в LineageOS 22.2 штатний перемикач форми іконок у Налаштуваннях.

**Що знайдено.**
- Іконки бази **вже** круглі: `config_icon_mask` (коло) і `config_useRoundIcon=true` стоять у статичному оверлеї бази `https://raw.githubusercontent.com/LineageOS/android_vendor_lineage/lineage-22.2/overlay/common/frameworks/base/core/res/res/values/config.xml:78, :86`. Наш `TesseraIconShapeTheme` для «кола» тому дублює стан бази; ключі live у пакеті `android`, не в лаунчері (підтверджено §8 `docs/UI.md`).
- Перемикача немає: `API/android_packages_apps_LineageParts/git/trees/lineage-22.2?recursive=1` (489 шляхів) — 0 входжень `overlay|theme|icon_shape`, у `res/xml/` 20 файлів (livedisplay, statusbar, buttons, gestures, profiles, trust); `API/android_lineage-sdk/git/trees/lineage-22.2?recursive=1` (511) — єдиний слід теми = залишений `lineage/res/res/drawable/ic_qs_themes.xml`; `https://raw.githubusercontent.com/LineageOS/android_packages_apps_Settings/lineage-22.2/src/com/android/settings/development/OverlaySettingsPreferenceController.java:35-88` — developer option, яка пише `Settings.Secure`-ключ `overlay_settings` і стосується лише дозволу не-системним оверлеям впливати на Settings; списку RRO у Settings немає.
- Лаунчер довільну форму не малює: `IconShape.pickBestShape()` (`$TB/src/com/android/launcher3/graphics/IconShape.java:86-125`) бере маску з `AdaptiveIconDrawable.getIconMask()` і обирає **найближчу** з внутрішніх (Circle/RoundedSquare/TearDrop/Squircle + `TriangleShape.java`; радіуси тек — `$TB/res/xml/folder_shapes.xml`). Список «нових» форм є, але вимкнений: `AppShapesProvider.kt:23-24` гейтиться прапорцем `new_customization_picker_ui` (`https://raw.githubusercontent.com/LineageOS/android_frameworks_libs_systemui/lineage-22.2/aconfig/systemui.aconfig:66-71`, без `default_value`).
- Ціна власного перемикача: `android.permission.CHANGE_OVERLAY_PACKAGES` = `signature|privileged` (`$FB/core/res/AndroidManifest.xml:5052-5053`) → наш застосунок має бути привілейованим + в allowlist, а RRO — **не** static (порівняння: базовий `TrebuchetOverlay` у `android_vendor_lineage/overlay/rro_packages/` має `android:isStatic="true"`, `priority=100`, ціль `com.android.launcher3` — його в рантаймі не перемкнути).

**Вердикт.** ❌ штатного перемикача форми в 22.2 немає; ⚠️ керування маскою через RRO існує, отже вибір форми = другий/третій рівень (наш застосунок + привілейований перемичник). `Settings.Secure`-ключ для цього база не дає.

**Що це змінює в планах.** «Круглі іконки» подаємо як властивість образу (вони й так круглі), вибір форми — у беклог власного коду, в обіцянках його немає.

## 4. RRO в екрані блокування

**Питання.** Наскільки далеко дістає runtime-resource-overlay у Keyguard Android 15.

**Що знайдено.**
- `overlayable.xml` у базі **немає**: `API/android_frameworks_base/git/trees/lineage-22.2?recursive=1` (59 088 записів) містить `overlayable` лише в тестових/`cmds` даних; і `$FB/packages/SystemUI/res/values/overlayable.xml`, і `$FB/core/res/res/values/overlayable.xml` — HTTP 404. Те саме у AOSP-дзеркалі `aosp-mirror/platform_frameworks_base` для `android15-release` і `android14-release` (404 за наявного `config.xml` = 200). У Trebuchet, Settings, WallpaperPicker2 — теж 0. Тобто формального дозвол-списку для RRO в `android`/`SystemUI` у 22.2 немає.
- Живі імена, які я прочитав (їх можна підмінювати):

| Ключ | Файл у `$FB` |
|---|---|
| `status_bar_clock_size`, `status_bar_clock_starting_padding`, `status_bar_system_icons_height` | `packages/SystemUI/res/values/dimens.xml` |
| `keyguard_max_notification_count`, `config_keyguardRefreshRate`, `config_keyguardQuickAffordanceSlots` | `packages/SystemUI/res/values/config.xml` |
| `keyguard_security_view_top_margin`, `below_clock_padding_start`, `widget_vertical_padding_clock`, `weather_clock_smartspace_scale` | `packages/SystemUI/res-keyguard/values/dimens.xml` |
| `kg_show_ime_at_screen_on`, `kg_use_all_caps` | `packages/SystemUI/res-keyguard/values/bools.xml` |
| `config_enableLockScreenCustomClocks` (= `true`) | `packages/SystemUI/res-keyguard/values/config.xml` |

- Розміру/шрифту годинника немає: `clock_font_family`, `config_clockFontFile`, `keyguard_clock_size` — 0 входжень у всіх `packages/SystemUI/res-keyguard/values/*.xml`; мертві й `qs_columns_portrait`, `quick_settings_tile_corner_radius`, `qs_detail_corner_radius_top` (§8).
- Причина системна: локскрін у 15 — Compose/Scene (`packages/SystemUI/compose/features/src/com/android/systemui/keyguard/ui/composable/section/DefaultClockSection.kt`, `.../LockscreenScene.kt`, `src/com/android/systemui/keyguard/MigrateClocksToBlueprint.kt`), а `res-keyguard/layout/keyguard_clock_switch.xml` — лише контейнер `KeyguardClockSwitch`/`KeyguardClockFrame`, не сам годинник.

**Вердикт.** ⚠️ RRO тягне відступи, розміри статус-бара, кількість сповіщень і поведінкові bool-и; «повний редизайн локскріну оверлеєм» — ❌.

**Що це змінює в планах.** З опитування лишаються лише пункти-значення з таблиці; композицію годинника не обіцяємо ніде, окрім власного коду.

## 5. Живі шпалери

**Питання.** Чи тягне база `WallpaperService` і чи є в ній застосунок живих шпалер.

**Що знайдено.**
- API на місці: `$FB/core/java/android/service/wallpaper/WallpaperService.java` і `IWallpaperService.aidl` є у дереві; `FEATURE_LIVE_WALLPAPER = "android.software.live_wallpaper"` — `$FB/core/java/android/content/pm/PackageManager.java:4280`.
- Інтеграція в picker є: у `API/android_packages_apps_WallpaperPicker2/git/trees/lineage-22.2?recursive=1` ~15 класів з `Live…` (`LivePreviewFragment.java`, `ThirdPartyLiveWallpaperCategory.java`, `LiveWallpaperThumbAsset.java`, `picker/category/client/LiveWallpapersClientImpl.kt`), але категорія показується лише за `hasSystemFeature(FEATURE_LIVE_WALLPAPER)` — `https://raw.githubusercontent.com/LineageOS/android_packages_apps_WallpaperPicker2/lineage-22.2/src/com/android/wallpaper/module/DefaultCategoryProvider.java:242` (і `:297` — посилання на `com.android.wallpaper.livepicker`).
- Оголошення фичі в базі немає: перелік `API/android_frameworks_native/git/trees/lineage-22.2?recursive=1` для `data/etc/` містить `android.software.live_tv.xml`, а `android.software.live_wallpaper.xml` — HTTP 404, тобто з коробки `hasSystemFeature` = false і категорії не буде.
- Рушіїв живих шпалер у 22.2 немає: `LineageOS/android_packages_wallpapers_LivePicker` має гілку `lineage-22.2` (200 на `AndroidManifest.xml`, пакет `com.android.wallpaper.livepicker`), а `_Basic`, `_MagicSmoke`, `_PhotoPhase`, `_MusicVisualization`, `apps_CMWallpapers` на тій гілці — 404.

**Що дерево 27.10 додало до цього.**
- Те саме підтверджується на диску, а не лише через GitHub: `~/tessera/packages/wallpapers/` містить **рівно одну** теку — `LivePicker`. Жодного рушія там немає, тож «404» перекладається як «фізично відсутні в образі».
- Назва модуля — **не** `LivePicker`, а `LiveWallpapersPicker` (`packages/wallpapers/LivePicker/Android.bp:41`), і він `privileged: true` + `platform_apis: true`. Рядок `required: ["android.software.live_wallpaper.xml"]` (`Android.bp:43`) тягне за собою `prebuilt_etc` з `sub_dir: "permissions"` (`:34-37`). Тобто один рядок `PRODUCT_PACKAGES += LiveWallpapersPicker` дає одразу і picker, і `system/etc/permissions/android.software.live_wallpaper.xml` — окремого `PRODUCT_COPY_FILES` для фичі не потрібне.
- Пошук по всьому дереві: `android.software.live_wallpaper.xml` існує рівно в одному місці — у теці LivePicker. У `frameworks/native/data/etc/` його справді немає, як і стверджував рядок вище.

**Вердикт.** ⚠️ механізм живий, але потребує від нас одного рядка в образ:
`PRODUCT_PACKAGES += LiveWallpapersPicker` — саме так, назвою модуля; написати
`LivePicker` (назву теки) означало б падіння Soong на «module not found».
Оголошення фичі приїжджає разом з ним через `required:`. Рушія це не дає:
єдиний живий рушій — сторонній застосунок з `android.service.wallpaper.WallpaperService`.
Чи входить `LiveWallpapersPicker` у PRODUCT_PACKAGES нашої бази — досі `❓`, але
причина тепер інша і точна: вирішальний файл живе у `vendor/lineage/config/*.mk`,
а `vendor/lineage` у нашому дереві ще не зсинхронізовано.

**Що це змінює в планах.** «Живі шпалери» не скасовуємо, але подаємо як конфігурацію збірки + сторонній застосунок, а не «працює з коробки».

## 6. Чим саме система заводить майстра першого запуску

Питання з §«Чого досі не доведено» в `apps/welcome/README.md`: чисті завантаження з
`device_provisioned=0` лишають фокус на лаунчері, а `ProvisionController` не дає жодного
рядка. Досі причина не називалась. 26.10.2026 її витягнуто з точного теґу, який тримає
маніфест бази (`revision="refs/tags/android-15.0.0_r32"`), і підтверджено двома живими
boot-прогонами на `TesseraUD`.

**Як це зроблено в базі.** `packages/apps/Provision` — не майстер із екранами, а
безіменна активність на один `onCreate`. Її фільтр:

```xml
<intent-filter android:priority="1">
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.SETUP_WIZARD" />
</intent-filter>
```

— і `finishSetup()` у дві ходи: `setProvisioningState()` пише ті самі два прапорці, що й
наш крок 10 (`Settings.Global.DEVICE_PROVISIONED = 1`, `Settings.Secure.USER_SETUP_COMPLETE = 1`),
а `disableSelfAndFinish()` робить
`pm.setComponentEnabledSetting(name, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)`.
Тобто мастер **сам вимикає свій компонент**, щоби більше ніколи не вигравав `HOME`.
У нашій базі він стоїть у `PRODUCT_PACKAGES` рядком `Provision` —
`build/make/target/product/handheld_system_ext.mk:27`.

**Що виміряно наживо** (два повні буфери журналу за один boot кожен — 21 506 і 28 085
рядків, `logcat -d -v threadtime` одразу після `sys.boot_completed=1`):

- літерала `SETUP_WIZARD` у обох буферах — **0 рядків**. За весь boot система зробила рівно два
  старти активностей, і обидва — `CATEGORY_HOME` від uid 0:
  `com.android.settings/.FallbackHome` (…:37.041), потім лаунчер (…:51.512).
- `dumpsys package` → `Known Packages → Setup Wizard: none`. Образ узагалі не знає, хто
  його OOBE.
- `cmd package query-receivers -a android.intent.action.USER_UNLOCKED` → `No receivers
  found`; пакета `com.android.provision` на образі немає; `ro.setupwizard.mode` не заданий.
  Тобто `ProvisionController` ніхто не просив працювати — і це остаточно пояснює нуль
  рядків теги, який ми раніше не вміли пояснити.
- `resolve-activity MAIN + HOME + DEFAULT` → лаунчер з `isDefault=true`, і match-прапорці
  `0x200` / `0x80000` / `0x80200` цього не змінюють: `HOME` тримає роль (`dumpsys role` →
  `android.app.role.HOME`, `holders=com.google.android.apps.nexuslauncher`), а не
  `priority`. Записів `preferred-activities` на HOME при цьому немає — тож на цьому образі
  кандидата за пріоритетом побачити неможливо в принципі. `com.android.settings/.FallbackHome`
  тримає `priority=-1000`: це останній дім, а не перший.
- Рядок `E PackageManager: There should probably be exactly one setup wizard; found 2`
  пише system_server у момент сканування пакетів (одразу після `Finished scanning system
  apps`), а не під час запуску. Це перевірка під час реєстрації пакетів, а не спроба щось стартувати —
  попередня інтерпретація була хибною.
- Дослід «залишити рівно одного кандидата» (`pm disable` Google-майстра → ребут):
  **гіпотеза не підтвердилась** — PMS знову написав «found 2» (його скан-тайм запит не
  зважає на вимкнений стан), кеш лишився `none`. Компонент і пакет повернуті у
  `default` (`enabled=0`), видимий з shell кандидат знову один — образ рівно такий, як до
  досліду.
- Неявний `am start -a MAIN -c SETUP_WIZARD` без `cmp=` не резолвить **не тільки наш**, а й
  комітний `com.google.android.googlesdksetup/.DefaultActivity`, і навіть від uid 0. Тож
  «не резолвиться» було властивістю шляху старту, а не нашим дефектом.
- Наш фільтр тим часом правильний у своїх межах: `resolve-activity` бачить
  `os.tessera.welcome/.WelcomeActivity`, `priority=1000`, `exported=true`,
  `enabled=0` (DEFAULT, а не `DISABLED_UNTIL_USED`). Додавання `CATEGORY_DEFAULT` до
  запиту, навпаки, ламає резолвінг (`No activity found`) — тож `DEFAULT` у поточний
  однокатегорійний фільтр вписувати просто так не можна.
- Чи блокує наша вітальня домашній екран уже сьогодні — ні: з модуля на кроці 1 явний
  `am start -a MAIN -c HOME` перевів фокус на лаунчер (`mCurrentFocus` →
  `com.google.android.apps.nexuslauncher/.NexusLauncherActivity t6`, тогочасний
  `mFocusedApp` — теж він). Тобто вийти з майстра на поточному маніфесті є куди, і
  правка з `HOME` не успадковує «вітальню, яку не можна закрити».
- Побічно виміряна межа самого методу: `input keyevent 3` (`KEYCODE_HOME`) і
  `KEYCODE_APP_SWITCH` з тієї ж вітальні **не змінили нічого** — фокус лишився
  `os.tessera.welcome/.WelcomeActivity`, хоча домашній таск у `dumpsys activity`
  живий (`Task #6 type=home … visible=false`). Оскільки з того самого стану той самий
  явний intent переводить фокус на лаунчер, нульовий ефект належить інʼєкції системних
  клавіш із shell на цьому образі, а не поведінці фреймворку. Для майбутнього прогону
  правки з `HOME` мірити треба явним intentом, а не `keyevent`.

**Вердикт.** ❌ Автозавпуску в поточній формі не буде ніколи, і причина тепер відома
точно: у Android 15 перший екран бере той, хто оголошений `HOME`, а не той, хто оголошений
`SETUP_WIZARD`. Наш модуль — тільки `SETUP_WIZARD`, тому його ніхто не кличе. Це не
«невідома причина», а конкретна відсутність двох категорій і само-вимкнення.

**Що додало дерево 27.10 ввечері.** Конкурента нарешті названо на ім'я, а не
«ніхто». Цільовий образ `build/tessera.mk:18` успадковує
`device/generic/goldfish/64bitonly/product/sdk_phone64_x86_64.mk`, той тягне
`device/generic/goldfish/product/phone.mk` → `generic.mk:98`, а там у
`PRODUCT_PACKAGES` стоїть `SdkSetup`. `com.android.sdksetup` оголошує
`MAIN + HOME + DEFAULT + SETUP_WIZARD` із `priority="3"`
(`device/generic/goldfish/provision/SdkSetup/AndroidManifest.xml:40-45`) і в
`Android.bp:18` перекриває модуль `Provision`. Тобто на goldfish-образі майстра
бази з одиницею (`packages/apps/Provision/AndroidManifest.xml:38`) не буде
взагалі — буде SdkSetup із трійкою. На тестовому `google_apis`-образі ми його не
бачили (`dumpsys package` → `Setup Wizard: none`), тому досі й не називали.

**Що це змінює в планах.** Копія механізму бази — три правки в одному файлі:
`HOME` + `DEFAULT` у фільтр, `priority` вищий за `3` (не «як у бази»: одиниця
`Provision` програла б трійці `SdkSetup`, а `1000` нічого не виграє, бо `HOME`
вирішує роль), і
`setComponentEnabledSetting(..., DISABLED, ...)` після кроку 10. Без третього пункту
телефон залишається з вітальнею як домашнім екраном назавжди.

Двох речей копіювати не можна, і це треба перевіряти, а не вгадувати:

- `directBootAware="true"` — база має, ми свідомо не копіюємо: наші кроки пишуть у файл
  модуля на CE-сховищі, яке до розблокування недоступне. Отже чи взагалі ми виграємо
  `HOME`-резолвінг у вікні між `FallbackHome` і лаунчером — питання до живого прогону.
- `HOME` у фільтрі змінює поведінку кнопки «додому» під час майстра. Це помітна зміна
  поведінки для користувача, тому правку не вносимо мовчки: спершу прогін, потім рішення.

## 7. Автояскравість «увімкнена за замовчуванням»

**Питання.** Опитування №2 просить, щоб автояскравість у образі була ввімкнена. Чи
досягається це штатним записом налаштування — тобто чи може це зробити наш модуль?

**Що знайдено.** Виміряно 27.10 на двох живих пристроях; логи
`.scratch/auto-brightness-2026-10-27.log` і `.scratch/real-phone-2026-10-27.log`.

- Ключ — `screen_brightness_mode` у таблиці `system` (`0` — вручну, `1` — автоматично).
  Старий `automatic_brightness` на Android 15 вже нічого не означає: на емуляторі
  `settings get system automatic_brightness` дає `null`.
- Емулятор `emulator-5554` (`sdk_gphone64_x86_64-userdebug 15`, API 35) **має**
  оголошений датчик: `pm list features` → `android.hardware.sensor.light`, а
  `dumpsys sensorservice` перелічує «Goldfish Light sensor» тип `android.sensor.light(5)`.
  Проте конфіг дисплея кривої не має: `mBrightnessLevelsNits= []`,
  `mBrightnessLevelsMap= {default_normal=[]}`, а датчик у ньому порожній —
  `mAmbientLightSensor=SensorData{type= , name= }`. Підсумок той самий у рядку
  `mAutoBrightnessAvailable= false`.
- Запис утримується, ефекту немає: `settings put system screen_brightness_mode 1`
  (з сесії `adb root`) через 6 с читається знову як `1`, однак `dumpsys display`
  показує `mIsAutoBrightnessEnabled=false`, а `mScreenBrightness` лишається
  `0.39763778` — рівно `mScreenBrightnessDefault`. Тобто на образі без кривої такий
  запис був би цинною обіцянкою: значення інше, поведінка та сама.
- Реальний телефон (`SM-A366B`, Android 17 / API 37) на тому ж полі дає
  `mAutoBrightnessAvailable= true`. Тобто різниться не версія Android і не наші APK,
  а конфігурація дисплея конкретного образу.

**Вердикт.** ⚠️ «увімкнена за замовчуванням» — це конфігурація образу (крива
люкс→яскравість і посилання на датчик у конфігу дисплея), а не рядок у `Settings`.
З нашого модуля доступне лише значення; зробити, щоб це значення щось означало,
з модуля неможливо в принципі.

**Що це змінює в планах.**

- У вітальні автояскравість не вмикаємо й не показуємо «увімкнено»: це той самий
  принцип чесності, через що ми не застосовуємо наживо навігацію, профіль живлення й
  тип MAC-адреси.
- Вимогу переносимо у план збірки образу: покласти оверлей у
  `overlay/themes/TesseraFrameworkTheme` — він уже існує для теми, тож механізм той
  самий, що з `local_palette.xml`.
- Імена ключів **підтверджені 27.10 з дерева**, а не з догадки: `frameworks/base`
  нарешті синхронізовано, і в `core/res/res/values/config.xml` лежать
  `config_automatic_brightness_available` (`:500`, значення за замовчуванням —
  **false**), `config_autoBrightnessLevels` (`:1722`) і
  `config_screenBrightnessNits` (`:1876`). Тобто `mAutoBrightnessAvailable= false`
  на емуляторі — не зламаний датчик і не рядок у `Settings`, а саме цей дефолт:
  `true` у всьому дереві `device/` ставлять лише оверлеї car-емулятора
  (`device/generic/car/emulator/overlay/…/config.xml:21`) та cuttlefish-auto
  (`device/google/cuttlefish/shared/auto/overlay/…/config.xml:39`), а до
  телефонного образу жоден з них не доїжджає. Нашому оверлею перекривати рівно
  ці три ключі.

## 8. Годинник на лок-скріні: механізм є, вбудованого вибору — немає (з дерева 27.10)

**Питання.** Дорожня карта хоче «годинник по центру» і вибір стилю годинника.
Чи дає база цей вибір як налаштування, чи це окремий застосунок?

**Що знайдено** (у зсинхронізованому `~/tessera`, з рядками самого дерева):
- У базі механізм увімкнено за замовчуванням:
  `$FB/packages/SystemUI/res-keyguard/values/config.xml:32` —
  `<bool name="config_enableLockScreenCustomClocks">true</bool>`, і саме цей
  ресурс живить флаг `lockscreen_custom_clocks`
  (`$FB/packages/SystemUI/src/com/android/systemui/flags/Flags.kt:77`).
- API плагіна ціле: `$FB/packages/SystemUI/plugin/src/com/android/systemui/plugins/clocks/`
  — 15 файлів, серед них `ClockProviderPlugin.kt`, `ClockPickerConfig.kt`,
  `ClockController.kt`, `ClockFaceLayout.kt`. Теки `plugins/clocks` у `src/`
  нема (там лише старий `statusbar/phone/ClockController.java` для годинника
  у статус-барі), чому §4 і не міг відкрити `plugins.clocks.ClockController`.
- Хто читає вибір користувача:
  `$FB/packages/SystemUI/customization/src/com/android/systemui/shared/clocks/ClockRegistry.kt`
  підписується на `Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE` (`:431`, `:440`;
  сам ключ — `$FB/core/java/android/provider/Settings.java:8629`,
  `"lock_screen_custom_clock_face"`), тримає `PluginLifecycleManager<ClockProviderPlugin>`
  (`:633`) і віддає список через `getClocks()` (`:563`) / `getClockPickerConfig()` (`:568`).
- **У самих Налаштуваннях вибору годинника немає**: `packages/apps/Settings` не
  згадує `ClockRegistry` у жодному файлі (нуль влучань), а єдиний
  «годинниковий» перемикач там — дворядковий режим:
  `packages/apps/Settings/res/xml/security_lockscreen_settings.xml:88-91`
  (ключ `lockscreen_double_line_clock_switch`, контролер
  `com.android.settings.display.LockscreenClockPreferenceController`).
- Сировина сама говорить, як обирають годинник поза жодним UI:
  `$FB/packages/SystemUI/docs/clock-plugins.md:60` дає рядок
  `adb shell settings put secure lock_screen_custom_clock_face '{"clockId":"ID"}'`.

**Вердикт.** Вибір стилю годинника — це **не перемикач і не RRO**, а список
плагінів: `getClocks()` наповнюється лише з `ClockProviderPlugin`, тому без
окремого APK, який такий плагін оголошує, вибирати нічого — списки порожні, а
рядок «вибрати годинник» у меню не з'явиться (для нього ще й `ClockPickerConfig`
має бути в тому ж плагіні). Це третій власний модуль, а рамку продукту ми
домовились тримати в двох модулях — тому тут не правка, а **рішення власника**.

**Що це змінює в планах.** Пункт «годинник по центру» лишається тим, чим і
був: стоковими `Settings.Secure`/параметрами екрана блокування (§UI.md, рядок
про лок-скрін). «Вибір стилю годинника» перетворюється з «залишилося перевірити»
на «окремий застосунок із плагін-API» — і перестає бути боргом документації.

## Що лишилося неперевіреним

- Живий образ: чи `numHotseatIcons=5` у 4-стовпцевій сітці не ламає док і чи нашу підміну не перекриває static-RRO бази.
- Чи з'являється обмеження `overlayable` після лінкування (`aapt2 dump resources SystemUI.apk` / `idmap2`), а не з сирцевини. Зі сторони сирцевини питання майже зняте: `find ~/tessera/frameworks/base -name 'overlayable*.xml'` дає 12 файлів, і **всі** вони — тестові дані (`core/tests/…`, `services/tests/…`, `libs/androidfw/tests/…`, `cmds/idmap2/tests/…`, `apct-tests/…`, `packages/CredentialManager/wear`); у `packages/SystemUI` такого файлу нуль. Тобто списку «ось це можна перекривати» в наших цілях просто немає — лишається єдине, що сирцевина не показує: чи не згенерував aapt2 обмеження вже в зібраному APK.
- PRODUCT_PACKAGES нашого device-tree: §5 назвав точне ім'я модуля (`LiveWallpapersPicker`) і довів, що фіча приїжджає разом з ним, але **чи додає його база — досі невідомо**, бо вирішальні `*.mk` живуть у `vendor/lineage`, а його ще не зсинхронізовано. Те саме стосується конфлікту з `vendor/lineage/overlay/common` і нашим `overlay-packages` у `build/tessera.mk`: перевіряється одним `ls` після syncу.
- Чи оверлей кривої люкс→яскравість взагалі застосовується на власному
  x86_64-образі — для цього потрібен зібраний образ, якого ще немає. Самі імена
  ключів від 27.10 підтверджені з дерева (§7), тож це єдине, що там лишалося.
- Чи дає `check-overlay-names.sh` право казати «працює»: ні — він перевіряє лише
  існування імені. Дерево 27.10 знайшло контрприклад усередині самої бази
  (`opaque_scrim`, `dual_shade_scrim_alpha`: оголошені, але в `frameworks/base`
  не читаються ніде). Тому кожен новий оверлей потребує живого знімка, і це
  єдиний спосіб відрізнити «ім'я знайдене» від «ім'я працює».

## Як повторити перевірку

```sh
T=https://raw.githubusercontent.com/LineageOS/android_packages_apps_Trebuchet/lineage-22.2
F=https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-22.2
curl -sf $T/res/xml/device_profiles.xml | grep -c '<grid-option'      # 8
curl -sf $T/src/com/android/launcher3/InvariantDeviceProfile.java | sed -n '654,670p;1092,1095p'
curl -sf $T/src/com/android/launcher3/AutoInstallsLayout.java | sed -n '268,277p;630,648p'
curl -sf $T/src/com/android/launcher3/DefaultLayoutParser.java | sed -n '36,46p;76,88p'
curl -sf $T/res/xml/launcher_preferences.xml | grep -ci grid           # 0
curl -sf -o /dev/null -w '%{http_code}\n' $F/packages/SystemUI/res/values/overlayable.xml  # 404
curl -sf $F/core/java/android/content/pm/PackageManager.java | grep -n FEATURE_LIVE_WALLPAPER
curl -sf https://raw.githubusercontent.com/LineageOS/android_vendor_lineage/lineage-22.2/overlay/common/frameworks/base/core/res/res/values/config.xml | grep -n 'icon_mask\|useRoundIcon'
curl -sf -o /dev/null -w '%{http_code}\n' https://raw.githubusercontent.com/LineageOS/android_frameworks_native/lineage-22.2/data/etc/android.software.live_wallpaper.xml  # 404
# перелік файлів репо — замість grep, який через HTTP неможливий:
curl -s "https://api.github.com/repos/LineageOS/android_frameworks_base/git/trees/lineage-22.2?recursive=1" | grep -o '"path": "[^"]*"' | grep -i overlayable
curl -s "https://api.github.com/repos/LineageOS/android_packages_apps_LineageParts/git/trees/lineage-22.2?recursive=1" | grep -o '"path": "[^"]*"' | grep -iE 'theme|overlay|shape'
```

Від 27.10 є дерево локально (`~/tessera`, 82 ГБ), і воно покриває все, де raw
сліпий: пошук по вмісту багатьох файлів і застосунок, якого немає в `build/make`.
Команди, які реально дають відповіді §5 і §8:

```sh
T=~/tessera; FB=$T/frameworks/base
# §5: чи є рушії живих шпалер на диску (рівно одна тека — немає)
ls $T/packages/wallpapers
# §5: як фіча приїжджає в образ (назва модуля + required)
grep -n 'name:\|required:\|sub_dir' $T/packages/wallpapers/LivePicker/Android.bp
# §5: де взагалі лежить xml оголошення фичі
find $T -name 'android.software.live_wallpaper.xml'
# §8: флаг custom clock і його дефолт
grep -rn 'config_enableLockScreenCustomClocks' $FB/packages/SystemUI | head
# §8: хто читає вибір годинника і чи Налаштування мають до нього руки
grep -rn 'LOCK_SCREEN_CUSTOM_CLOCK_FACE' $FB/packages/SystemUI/customization | head
grep -rl 'ClockRegistry' $T/packages/apps/Settings      # порожньо = вибору немає
# гарант: які взагалі overlayable-файли є в базі (усі 12 — тестові)
find $FB -name 'overlayable*.xml' | sed "s|$T/||"
```

Пастка двох із цих команд: `grep -rln Provision $T/build/make/target/product`
ловить `handheld_system.mk`, де насправді стоїть `ManagedProvisioning` — тобто
треба `-w` (§6). І тека `packages/wallpapers/LivePicker` — це не назва модуля:
Soong знає `LiveWallpapersPicker`, а `LivePicker` збірку б не пройшов (§5).

Для §7 — зчитувальні команди на живому пристрої (запис повертаємо назад, це єдине,
що ми взагалі чіпаємо):
```sh
ADB=~/Library/Android/sdk/platform-tools/adb   # або …/AppData/Local/Android/Sdk/…
D=emulator-5554                                # чи serial справжнього телефону
$ADB -s $D shell pm list features | grep -i sensor.light
$ADB -s $D shell settings get system screen_brightness_mode
$ADB -s $D shell dumpsys display | grep -oE 'mAutoBrightnessAvailable= ?[a-z]+|mBrightnessLevelsNits= ?\[[^]]*\]'
$ADB -s $D shell dumpsys display | grep -oE 'mIsAutoBrightnessEnabled=[a-z]+|mScreenBrightness=[0-9.]+|mScreenBrightnessDefault=[0-9.]+'
$ADB -s $D shell settings put system screen_brightness_mode 1; sleep 6
$ADB -s $D shell settings get system screen_brightness_mode   # 1 — значення тримається
$ADB -s $D shell dumpsys display | grep -oE 'mIsAutoBrightnessEnabled=[a-z]+'   # false — ефекту немає
$ADB -s $D shell settings put system screen_brightness_mode 0                    # повернули як було
```
