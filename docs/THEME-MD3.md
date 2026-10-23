# Material Design 3 у Zirka OS

## Механіка: RRO замість правки AOSP

Runtime Resource Overlay — пакет `.apk`, що не містить коду, лише ресурси й
маніфест з `android:targetPackage`. Система при запуску підміняє значення
ресурсів framework. Для нашої теми важливі три властивості:

- `android:isStatic="true"` — оверлей активний від першого завантаження, без
  `cmd overlay enable`;
- `android:priority` — вищий пріоритет перемагає при конфлікті;
- оверлей не чіпає код, тому `repo sync` і апстрім-патчі його не ламають.

Zirka OS постачає два static-оверлеї:

| Пакет | Ціль | Що змінює |
|---|---|---|
| `os.zirka.theme.framework` | `android` | палітра framework, форми, кути, типографіка, іконки |
| `os.zirka.theme.settings` | `com.android.settings` | картки, відступи, стиль списків Settings |

## Токени, які ми визначаємо

Material Design 3 оперує ролями, а не сирими кольорами. У framework-оверлеї
ми задаємо:

**Кольорові ролі (світла й темна тема)**

| Роль | Призначення |
|---|---|
| `colorPrimary` / `onPrimary` / `primaryContainer` | головна дія, топ-бар |
| `colorSecondaryContainer` | чіпи, перемикачі фону |
| `colorSurface` + `surfaceVariant` | фон екрана і тоніровані панелі |
| `colorOutline` / `outlineVariant` | роздільники, обводки полів |
| `colorError` | деструктивні дії |

MD3 у темній темі не інвертує світлу: поверхні йдуть по тональній шкалі
(surface → surfaceContainerLowest…Highest), тому картка відрізняється від
фона тоном, а не яскравістю.

**Форми (геометрія)**

| Елемент | Radius |
|---|---|
| повноекранні панелі, нижні sheets | 28 dp |
| картки, діалоги | 12–16 dp |
| кнопки, поля | 12 dp (full-pill — лише для FAB-подібних дій) |
| іконки застосунків | 22–28 dp  со сталою силуетною формою |

**Типографіка** — scale Roboto Flex: display/headline/title/body/label з
напругою 400/500, міжрядок за MD3.

**Підняття (elevation)** — тіні в MD3 не «сірі», а tonal: на поверхні використовується
`surfaceContainer` замість підняття з тінню, тінь лишається для FAB і меню.

## Динамічна палітра (Material You)

Android 12+ уже вміє генерувати палітру від шпалер. Наш оверлей не бореться
з цим механізмом, а задає **seed** і поведінку:

- `config_dynamic_color_default_enabled` — увімкнено;
- якщо користувач не обрав шпалери-джерело, застосовується базова палітра
  Zirka (див. `colors.xml`);
- `theme_customization_overlay_packages` дозволяє перемикати акцент у
  *Settings → Wallpaper & style*.

Тобто: по замовчуванню система динамічна, а наші значення — це fallback і
кістяк, не жорсткий скін.

## Як додати свою палітру

```bash
cd overlay/themes/ZirkaFrameworkTheme
python3 tools/derive_palette.py --seed "#3B5BDB" --out res/values/colors.xml
```

Скрипт (у наступному milestone) робить tonal palette за MD3: tone 0–100 для
кожної ролі, і пише обидва варіанти — `values/` і `values-night/`.

Поки що правіть значення вручну, дотримуючись правил контрасту:
`on*`-колір на своїй поверхні ≥ 4.5:1, іконки ≥ 3:1.

## Перевірка на зібраному образі

```bash
adb shell cmd overlay list | grep zirka
adb shell cmd overlay enable os.zirka.theme.framework
adb shell settings put secure theme_customization_accent_color 0xFF3B5BDB
adb shell uiautomator dump /sdcard/ui.xml   # знімок структури для тестів
```

## Чому не «клон Pixel»

Зовнішність Pixel — це торгові марки й авторські ресурси Google:
Launcher-айкони, анімації розблокування, обои, звуки, Product Sans. Ми
повторюємо **стандарт оформлення** (Material Design 3, відкриті специфікації
 Material), а не чужий бренд. Наслідок подвійно корисний: нема юридичного
ризику й немає вигляду «ще одного клону Pixel».
