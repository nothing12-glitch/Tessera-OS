# Базовий інтерфейс TesseraOS — stock-подібний

Курс: система має виглядати як сучасний стоковий Android — той самий, що на
Pixel, — але складений виключно з відкритих механізмів AOSP і Material 3.
Самобутня монохромна тема не скасована, вона перенесена в
[UI-minimal.md](UI-minimal.md) як альтернативний варіант.

## Принцип

Не «малюємо Pixel», а «вмикаємо те, що AOSP уже вміє, і тримаємо це в
збіженому стані». Майже весь ефект досягається конфігом і оверлеями, без форку
коду.

## 1. Колір: динамічна палітра, не фіксований акцент

Головна причина, чому Pixel виглядає «живо», — Monet: палітра береся зі шпалер
і перефарбовує систему. Тому:

- за замовчуванням `dynamic_color` увімкнено;
- framework-оверлей **не** перевизначає `system_accent1_*` — інакше він
  перебиває Monet і система стає пласкою;
- значення з [colors.xml](../overlay/themes/TesseraFrameworkTheme/res/values/local_palette.xml)
  лишаються **fallback** для випадку без шпалер-джерела (перший запуск,
  AOD, безпечний режим).

Практичний наслідок для коду: `TesseraFrameworkTheme` переведено з режиму
«hard override» у fallback — його маніфест активується лише за
`persist.tessera.dynamic_color=0`. Стокова динамічна палітра при цьому лишається
чинною.

## 2. Геометрія й компоновка

Стан кожного рядка — після тесту імен ресурсів проти дерев LineageOS 22.2
(див. §8). «Вільно» = не потребує нашого коду взагалі.

| Елемент | Значення | Як досягається | Стан |
|---|---|---|---|
| Маска іконок | коло | RRO на `android`: `config_useRoundIcon=true`, `config_icon_mask=<коло>` | ✅ зроблено, імена підтверджені (framework `config.xml:4526`, `:4626`) |
| Сітка домашнього екрана | 5 колонок × 6 рядків | build-time підміна `res/xml/device_profiles.xml` (`overlay-packages/…`): `canBeDefault` істинний лише для профілю `5_by_6` | ✅ зроблено; RRO для цього файла неможливий — див. §8 і [PIXEL-PARITY.md](PIXEL-PARITY.md) |
| Док | 5 іконок | той самий профіль `5_by_6`: `numHotseatIcons="5"` | ✅ як вище |
| Пошук | пігулка внизу | уже так у стоці | ✅ вільно |
| Рядок «дата + погода» | вгорі | віджет/компонент лаунчера | ⚠️ лише власний лаунчер (M2) |
| Картки в Налаштуваннях | радіус 22 dp, групою | уже так у стоці (`Theme.DeviceDefault`) | ✅ вільно |
| Контейнер іконки в списку | коло | уже так у стоці | ✅ вільно |
| Quick Settings | 2 колонки | `<integer name="quick_settings_num_columns">2</integer>` — **у базі вже 2** | ✅ вільно, оверлей не потрібен |
| Панель QS | радіус 28 dp | `<dimen name="qs_corner_radius">28dp</dimen>` — **у базі вже 28dp** | ✅ вільно |
| Повзунок яскравості | угорі, пігулка | стандарт QS | ✅ вільно |
| Годинник на локі | великий, зліва вгорі | вбудований вибір стилю годинника | ✅ вільно |

Головний висновок з таблиці: майже вся «pixel-подібність» — це вже стан
бази. Реально наша робота — три речі: круглі іконки (RRO), палітра fallback
(RRO) і стартова сітка 5×6 (build-time підміна файла профілів). Решта
поведінки, якої немає в стоці (At a Glance, жест дока), потребує власного
лаунчера — див. §6, пункт 5.

Про межі «один в один» і чому копія прошивки Pixel неможлива в публічному
репо — [PIXEL-PARITY.md](PIXEL-PARITY.md).

## 3. Типографіка

Roboto Flex, ваги 400 і 500 — як у стоку. Єдина вимога до нас: не робити
власних шрифтових замін, бо будь-який «Google Sans» чи Product Sans —
власницький ресурс.

| Стиль | Розмір | Вага |
|---|---|---|
| годинник на локі | 56 sp | 400 |
| заголовок екрана | 20 sp | 400 |
| рядок списку | 13.5–14 sp | 400 |
| другорядний текст | 12 sp | 400 |
| підпис іконки | 10 sp | 400 |

## 4. Рух

Стокова поведінка — короткі, без пружинних перельотів переходи. Досягається
оверлеєм інтерполяторів і тривалостей:

- `config_windowAnimationScale` за замовчуванням 1.0;
- стандарт 120–200 ms, `ease-out`;
- без власних «фірмових» анімацій — анімації розблокування й завантаження з
  прошивок Pixel є ресурсами Google.

## 5. Ліцензійна межа

| Можна | Не можна |
|---|---|
| компоновка, геометрія, поведінка, Material 3 токени | APK Pixel Launcher |
| власний лаунчер на базі Launcher3 (Apache-2.0) | шпалери й звуки Pixel |
| динамічна палітра Monet (частина AOSP) | boot animation і анімації Google |
| кругла маска й сітка іконок | набір іконок Google, Material Symbols у їхніх виконаннях як ресурс |
| — | Product Sans / Google Sans, логотип «G», назва Pixel |

Детальніше про бінарні ресурси — [GAPPS.md](GAPPS.md).

## 6. Що з цього випливає для реалізації

Порядок робіт, від найдешевшого до найдорожчого. Стан після тесту §8:

1. ✅ **`TesseraFrameworkTheme` на fallback-семантиці** — RRO з
   `requiredSystemPropertyName` / `Value="0"`, тож увімкнена динамічна палітра
   його не бачить. Палітру скорочено до семи атрибутів, які framework справді
   має; два з них private, тобто живі лише при збірці в дереві.
2. ✅ **`TesseraIconShapeTheme`** — круглі adaptive-іконки через
   `config_useRoundIcon` + `config_icon_mask` у пакеті `android`. Єдиний
   підтверджений «pixel-подібний» гачок, який не є станом бази за замовчуванням.
3. ✅ **`TesseraSettingsTheme`** — один підтверджений токен `icon_accent`;
   решту кольору Settings бере з `Theme.DeviceDefault`, тобто з пункту 1.
4. ✅ **Сітка лаунчера 5×6** — `overlay-packages/packages/apps/Trebuchet/res/xml/device_profiles.xml`
   і `DEVICE_PACKAGE_OVERLAYS += vendor/tessera/os/overlay-packages` у
   [build/tessera.mk](../build/tessera.mk). Це **не** RRO: файл `res/xml`
   використовує атрибути власного namespace лаунчера, а aapt2 линкує RRO проти
   власного пакету теми (помилка збірки — §8). Не перевірено на зібраному
   образі.
5. ⬜ **Власний лаунчер в `apps/launcher`** (Compose + Material 3) — потрібен
   для того, чого не можна досягти оверлеєм: рядок «дата + погода», поведінка
   дока, жести.
6. ⬜ **Екран блокування** — спершу перевірити, чи вистачить стокового вибору
   стилю годинника.

## 7. Відкриті питання

- Чи лишаємо фіксований акцент: так, як fallback за `dynamic_color=0`.
- Сітка 5×6: ухвалено — build-time підміна файла профілів (§6.4). Відкритим
  лишається лише перевірка на образі: чи не з'їдає підміну примусовий RRO бази.
- Чи робимо власний лаунчер зараз, чи спершу збираємо образ із оверлеями.
- Чи є сенс в `TesseraSettingsTheme` взагалі, якщо один токен — можливо,
  простіше прибрати пакет і лишити лише framework-оверлей.

## 8. Результат тесту імен ресурсів (2026-10-23)

Тест: кожне ім'я, яке ми підмінюємо, порівняне з реальним деревом бази
(гілка `lineage-22.2`, файли беруться з GitHub raw; повторюється командою
`build/scripts/check-overlay-names.sh`).

| Що перевіряли | Результат |
|---|---|
| 30 імен палітри framework (`colorPrimary`, `colorOnPrimary`, `colorSurfaceContainer*`, `colorOutline`…) | **мертві**: у `colors_device_defaults.xml` їх немає |
| ті самі назви як **атрибути теми** (`attrs.xml`, `public-final.xml`) | живуть лише `colorPrimary`, `colorSecondary`, `colorError`, `colorBackground`, `colorAccent`, `windowLightStatusBar`; `colorSurface`/`colorSurfaceVariant` — private; решти (on\*, Container, Outline, surfaceContainer-шкала) **у framework не існує** |
| 8 імен лаунчера (`num_columns`, `config_icon_mask`, `cell_height`, `icon_size`, `num_hotseat_icons`, …) | **усі мертві**. Сітка живе в `res/xml/device_profiles.xml` як атрибути `launcher:numColumns`/`numRows`/`numHotseatIcons`; через resource-values нею не керувати |
| маска іконок | `config_icon_mask` і `config_useRoundIcon` існують, але в **framework** (`core/res/res/values/config.xml:4626` і `:4526`), а не в лаунчері |
| 5 імен SystemUI (`qs_columns_portrait`, `quick_settings_tile_corner_radius`, `qs_tile_vertical_padding`, `qs_detail_corner_radius_top`) | **усі мертві**. Реальні: `quick_settings_num_columns` (=2), `quick_settings_max_rows` (=4), `qs_corner_radius` (=28dp), `qs_tile_height` (=80dp) |
| 7 імен Settings (`settingslib_theme_*`, `ic_info_color`, `summary_card_bg_color`, `blue_grey_600`, `memory_info_description_color`) | **усі мертві** у `packages/apps/Settings/res/values/colors.xml` (227 імен). Живий, підтверджений: `icon_accent` |

Головний висновок: чотири оверлеї зібралися б і підписалися без
жодної помилки в CI — aapt2 з `--auto-add-overlay` не знає, що імена
нікому не потрібні. Тест імен — єдиний, що це ловить; відтепер він у репо і
працює з вихідним кодом 0/1.

Ще не перевірено (потребує зібраного APK, а не з сирцевини): `settingslib_*`
ресурси можуть з'являтися після статичного лінкування SettingsLib —
`aapt2 dump resources Settings.apk`. І те, чи приватні framework-ресурси
`config_icon_mask`/`config_useRoundIcon` дійсно перебиваються static RRO у
виправленому образі (це вже M1 на білд-боксі).

## 9. Сітка: RRO не вийшов (2026-10-23, перевірено збіркою)

Наївну спробу зробити сітку RRO-оверлеєм (`os.tessera.theme.launchergrid`,
той самий `res/xml/device_profiles.xml`) відхилила сама збірка:

```
res/xml/device_profiles.xml:46: error: attribute deviceCategory
  (aka os.tessera.theme.launchergrid:deviceCategory) not found.
```

aapt2 линкує оверлей проти пакету самого оверлея, тому атрибути чужого
namespace (`launcher:*`) у його `res/xml` не існують. Шлях «додати власний
`attrs.xml`» не рятує: id атрибутів були б іншими, ніж у цільового
застосунку, — оверлей зібрався б і мовчки нічого не змінив.

Висновок: файли `res/xml/*` цільового застосунку підмінюються лише
build-time оверлеєм (`DEVICE_PACKAGE_OVERLAYS`), не RRO. Тест імен це тепер
враховує окремо: для кожного файла в `overlay-packages/` перевіряє, що такий
шлях є у цільовому репо бази, і що інваріант «рівно один дефолтний профіль»
на місці.

