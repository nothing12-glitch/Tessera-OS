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

TesseraOS постачає чотири static-оверлеї. Два перші — тематичні (кольори,
форми), два інші — структурні (компоновка).

| Пакет | Ціль | Що змінює |
|---|---|---|
| `os.tessera.theme.framework` | `android` | палітра framework, форми, кути, типографіка — **лише коли динамічна палітра вимкнена** |
| `os.tessera.theme.settings` | `com.android.settings` | картки, відступи, стиль списків Settings |
| `os.tessera.theme.launcher3` | `com.android.launcher3` | маска іконок, сітка 5×6, док, підписи |
| `os.tessera.theme.systemui` | `com.android.systemui` | колонки Quick Settings, радіуси плиток |

Кольори в двох останніх рядках не перезаписуються: палітру їм дає система.

## Токени, які ми визначаємо

Material Design 3 оперує ролями, а не сирими кольорами. У framework-оверлеї
ми задаємо:

**Кольорові ролі (світла й темна тема)**

| Роль | Призначення |
|---|---|
| `colorPrimary` / `onPrimary` / `primaryContainer` | головна дія, верхня панель |
| `colorSecondaryContainer` | чіпи, фон перемикачів |
| `colorSurface` + `surfaceVariant` | фон екрана і тоніровані панелі |
| `colorOutline` / `outlineVariant` | роздільники, обводки полів |
| `colorError` | деструктивні дії |

MD3 у темній темі не інвертує світлу: поверхні йдуть по тональній шкалі
(surface → surfaceContainerLowest…Highest), тому картка відрізняється від
фона тоном, а не яскравістю.

**Форми (геометрія)**

| Елемент | Радіус |
|---|---|
| повноекранні панелі, нижні листи | 28 dp |
| картки, діалоги | 12–16 dp |
| кнопки, поля | 12 dp (пігулка — лише для дій типу FAB) |
| іконки застосунків | коло; силует задає `config_icon_mask` у Launcher3 |

**Типографіка** — масштаб Roboto Flex: display/headline/title/body/label з
навантаженням 400/500, міжрядковий крок за MD3.

**Підняття (elevation)** — тіні в MD3 не «сірі», а tonal: на поверхні
використовується `surfaceContainer` замість підняття з тінню, тінь лишається
для FAB і меню.

## Динамічна палітра (Material You)

Android 12+ уже вміє генерувати палітру від шпалер, і саме це робить стоковий
інтерфейс «живим». Наш оверлей із цим не бореться:

- за замовчуванням динамічна палітра увімкнена, тому framework-оверлей
  **не** активний (його умова — `persist.tessera.dynamic_color=0`);
- якщо динамічну палітру вимкнуто — перший запуск, AOD, безпечний режим або
  свідомий вибір користувача — застосовується базова палітра Tessera
  (див. [colors.xml](../overlay/themes/TesseraFrameworkTheme/res/values/colors.xml));
- `theme_customization_overlay_packages` дозволяє перемикати акцент у
  *Налаштування → Шпалери й стиль*.

Тобто: типово система динамічна, а наші значення — це fallback і каркас, а не
жорстке обмеження.

## Перевірка на зібраному образі

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
python3 tools/derive_palette.py --seed "#3B5BDB" --out res/values/colors.xml
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
