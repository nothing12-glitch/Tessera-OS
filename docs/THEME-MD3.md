# Material Design 3 у TesseraOS

## Механіка: RRO замість правки AOSP

Runtime Resource Overlay — пакет `.apk`, що не містить коду, лише ресурси й
маніфест з `android:targetPackage`. Система при запуску підміняє значення
ресурсів цільового пакету. Для наших оверлеїв важливі три властивості:

- `android:isStatic="true"` — оверлей активний від першого завантаження, без
  `cmd overlay enable`;
- `android:priority` — вищий пріоритет перемагає при конфлікті;
- `android:requiredSystemPropertyName` + `Value` — умова активності; саме так
  тематичний оверлей стає fallback, а не переможцем.

TesseraOS постачає три static-оверлеї.

| Пакет | Ціль | Що змінює |
|---|---|---|
| `os.tessera.theme.framework` | `android` | fallback-палітра DeviceDefault — **лише коли динамічна палітра вимкнена** |
| `os.tessera.theme.iconshape` | `android` | форма adaptive-іконок: `config_useRoundIcon`, `config_icon_mask` |
| `os.tessera.theme.settings` | `com.android.settings` | `icon_accent` — колір акцентних іконок Налаштувань |

Раніше тут було ще `os.tessera.theme.launcher3` і `os.tessera.theme.systemui`
(сітка лаунчера й колонки Quick Settings). Вони прибрані: імена ресурсів,
які вони підміняли, не існують у базі, тобто пакети були інертними.
Деталі — §8 документа [UI.md](UI.md), повторити тест:
`build/scripts/check-overlay-names.sh`.

## Токени, які ми задаємо — і які не можемо

Material Design 3 оперує ролями, а не сирими кольорами. Але framework
(пакет `android`) має далеко не всі ролі MD3. Перевірено по `attrs.xml` і
`public-final.xml` бази:

| Роль MD3 | Чи можна задати framework-оверлеєм |
|---|---|
| `colorPrimary`, `colorSecondary`, `colorAccent`, `colorError`, `colorBackground` | ✅ public-атрибути, задаємо |
| `colorSurface`, `colorSurfaceVariant` | ⚠️ існують, але private — лише збірка всередині дерева AOSP |
| `colorOnPrimary`, `onSurface`, `primaryContainer`, `secondaryContainer`, `surfaceContainer*`, `surfaceDim`, `outline`, `outlineVariant` | ❌ таких атрибутів у framework немає взагалі |

Останній рядок — принциповий: повна тональна шкала й `on*`-пари живуть у
бібліотеці Material (com.google.android.material) і в SettingsLib, тобто їх
фарбує **застосунок**, а не система. Повноцінного theme-оверлею на всю
MD3-палітру з framework не зробити: перекрашуємо сім ролей DeviceDefault,
решту дають самі застосунки.

MD3 у темній темі не інвертує світлу: поверхні йдуть по тональній шкалі
(surface → surfaceContainerLowest…Highest), тому картка відрізняється від
фона тоном, а не яскравістю. Цю шкалу у власних застосунках задаємо самі
(Compose Material 3), у системній темі доступні лише `surface` і
`surfaceVariant`.

**Форми (геометрія)**

| Елемент | Радіус | Де живе |
|---|---|---|
| панель Quick Settings | 28 dp | уже так у базі (`qs_corner_radius`) |
| картки, діалоги | 12–16 dp | у застосунку (Material attrs) |
| кнопки, поля | 12 dp | у застосунку |
| іконки застосунків | коло | `config_useRoundIcon` + `config_icon_mask` у **framework** (`core/res/res/values/config.xml:4526`, `:4626`) — наш `iconshape`-оверлей |

**Типографіка** — масштаб Roboto Flex: display/headline/title/body/label з
навантаженням 400/500, міжрядковий крок за MD3. Roboto Flex — частина AOSP;
власних шрифтових замін не робимо.

**Підняття (elevation)** — тіні в MD3 не «сірі», а tonal: на поверхні
використовується `surfaceContainer` замість підняття з тінню, тінь лишається
для FAB і меню. Це теж рівень застосунку, не framework-оверлею.

## Динамічна палітра (Material You)

Android 12+ уже вміє генерувати палітру від шпалер, і саме це робить стоковий
інтерфейс «живим». Наш оверлей із цим не бореться:

- за замовчуванням динамічна палітра увімкнена, тому framework-оверлей
  **не** активний (його умова — `persist.tessera.dynamic_color=0`);
- якщо динамічну палітру вимкнуто — перший запуск, AOD, безпечний режим або
  свідомий вибір користувача — застосовується базова палітра Tessera
  (див. [colors.xml](../overlay/themes/TesseraFrameworkTheme/res/values/local_palette.xml));
- `theme_customization_overlay_packages` дозволяє перемикати акцент у
  *Налаштування → Шпалери й стиль*.

Тобто: типово система динамічна, а наші значення — це fallback і каркас, а не
жорстке обмеження.

## Перевірка на зібраному образі

Збірка валідується без пристрою, у два кроки: спершу перевірка імен проти
бази, потім — сам пакет. На локальному SDK (build-tools 36.0.0, android.jar 36)
вийодять три підписані APK, і в кожному — рівно два файли, які мають значення:

```bash
build/scripts/check-overlay-names.sh     # вихід 1 = є мертві імена
build/scripts/build-themes.sh
for apk in overlay/out/*.apk; do unzip -l "$apk"; done
# → AndroidManifest.xml + resources.arsc, без .dex і без lib/
```

`aapt2 dump resources overlay/out/TesseraIconShapeTheme.apk` показує значення
в таблиці з правильними типами:

```
bool/config_useRoundIcon   () true
string/config_icon_mask    () "M50 0C77.6 0 100 22.4 100 50C…"
```

Це підтверджує пакет, але **не** те, що ці імена підміняють щось у цільовому
пакеті — для цього й існує `check-overlay-names.sh`. Зверни увагу: `styles.xml`
поза деревом AOSP скрипт видаляє (private-посилання framework не резолвляться
через публічний `android.jar`), тож у CI перевіряється лише палітра й форми.

На пристрої:

```bash
adb shell getprop persist.tessera.dynamic_color   # 1 => тематичний оверлей вимкнений
adb shell cmd overlay list | grep tessera         # [x] активні, [ ] неактивні
adb shell cmd overlay enable os.tessera.theme.framework   # вручну, для тесту fallback
adb shell cmd overlay disable os.tessera.theme.framework
adb shell settings put secure theme_customization_accent_color 0xFF3B5BDB
adb shell uiautomator dump /sdcard/ui.xml         # знімок структури для тестів
```

Якщо після `enable` нічого не змінилося — майже напевно помилкове ім'я
ресурсу: aapt2 link з `--auto-add-overlay` не скаржиться на невідому назву,
вона просто нікуди не підміняється. Звіряй імена зі своїм checkout.

## Як додати свою палітру

```bash
cd overlay/themes/TesseraFrameworkTheme
python3 tools/derive_palette.py --seed "#3B5BDB" --out res/values/local_palette.xml
```

Скрипт (у наступному milestone) робить tonal palette за MD3: тони 0–100 для
кожної ролі, і пише обидва варіанти — `values/` і `values-night/`.

Поки що правіть значення вручну, дотримуючись правил контрасту:
колір `on*` на своїй поверхні ≥ 4.5:1, іконки ≥ 3:1.

## Чому це не чужий бренд

Стоковий вигляд Android і Pixel збігаються, бо Pixel — це та сама AOSP,
поверх Material Design 3. Ми відтворюємо **стандарт оформлення й компоновку**,
визначені у відкритих специфікаціях Material Design і в коді AOSP під
Apache-2.0. Не беремо ми лише те, що є ресурсами та марками Google:

- APK Pixel Launcher замість AOSP Launcher3;
- шпалери, звуки й анімації завантаження з прошивок Pixel;
- набір іконок Google як бінарний ресурс;
- Product Sans / Google Sans, логотип «G», сама назва «Pixel».

Перевірка цього правила живе в CI: [`.github/workflows/policy.yml`](../.github/workflows/policy.yml)
блокує власницькі бінари й чужі торгові марки в репо.
