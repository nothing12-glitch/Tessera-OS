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

**Вердикт.** ⚠️ механізм живий, але потребує від нас двох речей: додати в образ оголошення фичі (`android.software.live_wallpaper.xml` у `system/etc/permissions`) і застосунок-рушій (LivePicker — лише picker). Чи входить LivePicker у PRODUCT_PACKAGES нашого пристрою — `❓`.

**Що це змінює в планах.** «Живі шпалери» не скасовуємо, але подаємо як конфігурацію збірки + сторонній застосунок, а не «працює з коробки».

## Що лишилося неперевіреним

- Живий образ: чи `numHotseatIcons=5` у 4-стовпцевій сітці не ламає док і чи нашу підміну не перекриває static-RRO бази.
- Чи з'являється обмеження `overlayable` після лінкування (`aapt2 dump resources SystemUI.apk` / `idmap2`), а не з сирцевини.
- Чи є десь у SystemUI src UI вибору «custom clock» (конфіг прочитано, код `plugins.clocks.ClockController` не відкривав).
- PRODUCT_PACKAGES нашого device-tree (чи ставиться LivePicker) і чи `vendor/lineage/overlay/common` не конфліктує з нашим `overlay-packages` у `build/tessera.mk`.

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
