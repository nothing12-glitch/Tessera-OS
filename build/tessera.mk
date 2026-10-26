# Конфіг продукту TesseraOS для x86_64-віртуальної машини.
# Маніфест лінкує цей файл у device/tessera/x86_64/tessera_x86_64.mk, а
# сусідній AndroidProducts.mk оголошує таргент для lunch. Ручні symlink не
# потрібні:
#
#   source build/envsetup.sh && lunch tessera_x86_64-userdebug && m -j$(nproc)

# --- 1. База ---------------------------------------------------------------
# Повний конфіг емулятора x86_64: framework, SystemUI, застосунки AOSP,
# ядро ranchu, fstab, розділи.
#
# Шлях і вміст перевірені деревом LineageOS 22.2 (2026-10-24):
# device/generic/goldfish/64bitonly/product/sdk_phone64_x86_64.mk бере
# board/emu64x/details.mk + product/phone.mk і ставить PRODUCT_DEVICE := emu64x.
# Саме тому наш продукт не пише власного BoardConfig.mk: каталог платформи
# визначається за іменем PRODUCT_DEVICE (device/generic/goldfish/board/emu64x),
# і власний BoardConfig довівся б до двох конкурентних файлів.
$(call inherit-product, device/generic/goldfish/64bitonly/product/sdk_phone64_x86_64.mk)

# Линії LineageOS (OTA-інфра, Privacy Guard, Theme engine, Backup) ми тут НЕ
# підключаємо — і це не забутовка, а свідоме обмеження першого проходу.
# Перевірено raw-файлом vendor/lineage/config/common.mk гілки lineage-22.2:
# він викликає enforce-product-packages-exist-internal з модулями, яких в
# емуляторному образу просто немає (`rild`, recovery-скрипти, Calendar), тому
# такий успадкування ламає збірку на кроці перевірки пакетів. LineageOS і сам
# збирає емулятор stock-таргетом (wiki.lineageos.org/emulator), а свій конфіг
# бере лише для справжніх телефонів.
#
# Тим не менш база в нас справді линіївна: дерево беремо з маніфеста
# LineageOS (їхні build/make, frameworks/base, soong, vendor/lineage у
# sync), тож патчі й збірка бази — їхні. Додати їхній продукт-шар — задача
# другого проходу, з логом реальної помилки під рукою: див. docs/BUILD.md,
# розділ «Експеримент: шар vendor/lineage».

# Вимоги до шляхів артефактів (mainline enforcement) база пом'якшує лише для
# власної назви продукту: sdk_phone64_x86_64.mk:24-25 перевіряє
# `ifeq (sdk_phone64_x86_64,$(TARGET_PRODUCT))`. Для tessera_x86_64 умова не
# спрацює, і prebuilt-APK у system_ext могли б бути відхилені саме через неї,
# а не через справжню проблему. Тому пом'якшуємо явно — тим самим значенням,
# яке бере база для себе.
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed

# --- 2. Оверлеї інтерфейсу -------------------------------------------------
# Static RRO пакети з overlay/themes/. Збираються через Android.bp blueprint
# поряд з кожним пакетом; isStatic="true" в маніфесті означає, що окрема
# активація через `cmd overlay enable` не потрібна.
#
# Семантика: інтерфейс = stock AOSP + Material You, геометрія — як на Pixel.
#  - framework  — fallback-палітра, активна лише за dynamic_color=0;
#  - iconshape  — коло adaptive-іконок, але ОПТИВНО: підміняє форму, лише коли
#                 persist.tessera.icon_shape=circle. За замовчуванням рядка тут
#                 немає, тож форма лишається стокова й її обирає користувач
#                 (шлях у Налаштуваннях — під питанням: чи є такий перемикач у
#                 базі взагалі, див. технічну перевірку реализуємості). Не примус.
#  - settings   — один підтверджений токен, решту дає Theme.DeviceDefault.
#
# Перемикання теми — вручну: авто-варіантів за годинником/розрядом тут свідомо
# не закладаємо (рішення 2026-10-26).
#
# Імена всіх трьох перевірені деревом бази: build/scripts/check-overlay-names.sh.
PRODUCT_PACKAGES += \
    os.tessera.theme.framework \
    os.tessera.theme.iconshape \
    os.tessera.theme.settings

# Стартова сітка лаунчера 4x6 (4 стовпці × 6 рядків, док на 5 комірок — 4 іконки
# + тека) лежить окремим шаром: це не RRO, а build-time overlay (змінні досі
# живі: build/make/core/product.mk:120-121). Причина
# з'ясувалась на збірці — aapt2 линкує RRO проти власного пакету, тому файл
# з атрибутами власного namespace (launcher:numColumns, launcher:canBeDefault)
# не збирається як RRO взагалі:
#   error: attribute deviceCategory (aka ...:deviceCategory) not found
# Під час збірки застосунку в дереві ті атрибути належать пакету Trebuchet,
# і підміна файлу працює. Не перевірено на зібраному образі — див. docs/UI.md §9
# і docs/PIXEL-PARITY.md.
#
# Після 2026-10-25 це єдине, що ми міняємо в лаунчері: сітку 4x6 читає
# системний Trebuchet (vendor/lineage), а не власний застосунок. Саме це й
# потрібно за новим рішенням — інтерфейс «як на Pixel» роблять тема і стартова
# сітка, а не наш APK.
DEVICE_PACKAGE_OVERLAYS += vendor/tessera/os/overlay-packages

# --- 2b. Чому наших застосунків тут рівно два ------------------------------
# 2026-10-25, після перевірки на живому пристрої. Рішення користувача:
# «Прибрати наш лаунчер зовсім» і «Прибрати всі наші» застосунки. Тому в образі:
#  - домашній екран — Trebuchet (дає база; разом із сіткою 4x6 вище);
#  - «Телефон», «Повідомлення», «Камера», «Годинник», «Файли», калькулятор —
#    ті самі, що в LineageOS/AOSP.
#
# Виняток перший — майстер першого запуску. Того ж дня користувач захотів екран
# першого запуску, якого в базі просто немає: «коли ти встановив систему, то
# вискакує на весь екран вітаємо у Tessera OS» — і уточнив, що це має бути
# системне вітання, а не застосунок з іконкою. Тому модуль привілейований
# (`priv-app` + дозвільний список), фільтра `LAUNCHER` у маніфесті немає, а
# «пройдено» пишеться у системні `device_provisioned` / `user_setup_complete`.
#
# Чого цей рядок НЕ означає — і саме тут легше всього збрехати:
#  - він досі НЕ перекриває майстра бази: `overrides` у Android.bp немає, бо
#    не звірено, яким механізмом LineageOS 22.2 заводить свій мастер —
#    категорією `SETUP_WIZARD` чи рядком `config_deviceProvisioningActivity`.
#    Дерево бази й досі завантажується (`repo sync` триває);
#  - дозвіл `WRITE_SECURE_SETTINGS` перевірено на емуляторі, але виданий він
#    там через `adb install -g`, а не через `priv-app` + allowlist. На образі
#    `user` записати `/system_ext` неможливо взагалі.
# Обидва пункти з текстом помилок — в apps/welcome/README.md.
PRODUCT_PACKAGES += TesseraWelcome

# Дозвільний список привілею. Окремий модуль, а не «частина APK»: образ тримає
# ro.control_privapp_permissions=enforce (§5), тому без цього файла в
# system_ext/etc/permissions/ система падає ще до лаунчера. Забути рядок тут —
# найшвидший спосіб зібрати образ, який не завантажується.
PRODUCT_PACKAGES += privapp-permissions-tessera-welcome

# Виняток другий — «Світловий край» (Tessera Halo). Користувач попросив додати
# в образ цікавий концепт із новин; це Pixel Glow з Android 17, де телефон
# світить на сповіщення діодами під задньою панеллю. У базі такого замінника
# немає взагалі: LineageOS показує пункт «Індикатор» лише коли залізо має діод,
# а на x86_64-емуляторі діода нема за означенням. Тобто це не «наше замість
# стокового», а рівно той випадок, коли образа просить функцію, якої в Lineage
# фізично не існує.
#
# Що тут чесне:
#  - модуль `presigned`, платформного ключа немає, тому він не може ані
#    увімкнути себе сам, ані світити при вимкненому екрані;
#  - два дозволи (поверх інших вікон і доступ до сповіщень) видає людина,
#    без них застосунок мовчить — це його стан «з коробки» в образі;
#  - край малюється на склі, а не на задній панелі; діод він не замінює.
# Перевірено наживо на Android 15 (усі три візерунки, обидва дозволи, зникнення
# вікна після анімації) — деталі й знімки в apps/halo/README.md.
PRODUCT_PACKAGES += TesseraHalo

# Раніше тут стояло `PRODUCT_PACKAGES += TesseraLauncher` і ще шість модулів.
# Прибрано повністю, а не закоментовано: модулі з apps/*/Android.bp Soong зібрав
# би й без рядка тут, але в образ вони б не потрапили — і це той самий клас
# мовчазної неактивності, від якого захищає check-app-integration.sh.
#
# Повернення будь-якого застосунку = два кроки (рядок у build/scripts/apps.list
# + модуль тут). Обидва кроки перевіряються статично, тому «в образі є, а в CI
# ні» знову неможливе.
#
# Джерела в apps/ лишаються в дереві: код не видалений, він просто не є
# частиною продукту. Історія git і вже опубліковані пакунки v15.22.2.2 і
# v15.22.2.3 містять їх і далі.

# --- 3. Google-сервіси: чому їх немає в цьому списку -----------------------
# Обіцянка «GApps ставляться» не реалізується рядками PRODUCT_PACKAGES, і от
# чому: у маніфесті бази (перевірено 2026-10-24 — default.xml і
# snippets/lineage.xml) немає проєктів ні microG, ні GmsCore, ні Aurora.
# Модулі з неіснуючою назвою — це або помилка «module not found», або мовчки
# порожній рядок у списку. Перше гірше, друге бреше.
#
# Чесна модель: сервіси ставлять КОРИСТУВАЧІ, як і в LineageOS.
#  - microG — окремим APK з F-Droid/Aurora, який завантажують самі; або
#    local_manifests від lineage.microg.org, доданий на білд-боксі;
#  - власницькі GApps — ZIP від його проєкту після першого запуску.
# Покроково — docs/GAPPS.md. Наш образ не містить жодної бінарної залежності
# від Google, тож політика репозиторію (policy.yml) тут не порушується.

# --- 3b. Фільтрів списку бази тут більше немає -----------------------------
# Раніше тут стояли два рядки `PRODUCT_PACKAGES := $(filter-out …)`, які
# прибирали стокові `Dialer` і `messaging`: без конкурента роль ROLE_DIALER і
# ROLE_SMS міг взяти наш застосунок. Нашого застосунку в продукті більше немає
# (§2b), тому й прибирати те, що лишилось, — не заради чого: телефонія, SMS і
# MMS (модуль MmsService — build/make/target/product/handheld_system.mk:60)
# лишаються в образі саме в тому вигляді, як їх дає база.
#
# Знання, навіщо фільтр колись був саме `filter-out`, а не змінна-список:
# `PRODUCT_PACKAGES_REMOVE` у LineageOS 22.2 НЕ ІСНУЄ — нулів у
# build/make/core/product.mk, core/Makefile, core/config.mk і у
# vendor/lineage/config/{common,common_mobile}.mk. Такий рядок мовчки не робив
# нічого (той самий «зелений білд ≠ результат»). Працюючий механізм — фільтр
# уже зібраного списку, бо inherit-product вище розкрив PRODUCT_PACKAGES
# повністю. Якщо колись повертатимемо свій «Телефон» — починати звідси.

# --- 4. Ідентичність -------------------------------------------------------
PRODUCT_NAME := tessera_x86_64
# emu64x — не помилка замість x86_64: Soong шукає BoardConfig.mk за іменем
# каталогу, що збігається з PRODUCT_DEVICE, а єдиний такий каталог у базі —
# device/generic/goldfish/board/emu64x. Свій BoardConfig ми тому не пишемо
# (див. §1): другий файл з такою ж назвою каталогу конфліктував би з базовим.
PRODUCT_DEVICE := emu64x
PRODUCT_MANUFACTURER := TesseraOS Project
PRODUCT_BRAND := tessera
PRODUCT_MODEL := TesseraOS (x86_64 VM)

# Версія проєкту: <android>.<lineage>.<tessera patch>. Теги GitHub — v<версія>.
# 15.22.2.2 — перший пакунок із сьомим застосунком («Камера»): реліз 15.22.2.1
# вийшов о 17:24, камера злилася о 19:46 того ж дня, тож у чинному пакунку її немає.
# 15.22.2.3 — перший пакунок БЕЗ власних застосунків (§2b): всередині лише тема
# MD3. Чинний опублікований v15.22.2.2 з сімома APK лишається як є.
TESSERA_VERSION := 15.22.2.3
TESSERA_CODENAME := vega

# Ідентичність образу. Комерційні властивості (brand/manufacturer) мають
# відповідати Apache-2.0 §4 і не імітувати чужі торгові марки.
#
# PRODUCT_PROPERTY_OVERRIDES у 22.2 ще працює, але позначений як застарілий
# (build/make/core/product.mk:89-93 — «in favor of PRODUCT_SYSTEM_PROPERTIES»),
# тому беремо сучасну назву.
PRODUCT_SYSTEM_PROPERTIES += \
    ro.tessera.version=$(TESSERA_VERSION) \
    ro.tessera.codename=$(TESSERA_CODENAME) \
    ro.tessera.base=LineageOS-22.2 \
    ro.tessera.theme=material3 \
    ro.tessera.theme.seed=\#3350C8 \
    ro.tessera.support.url=https://github.com/nothing12-glitch/Tessera-OS/issues \
    persist.tessera.dynamic_color=1 \
    persist.tessera.telemetry=0

# ro.product.first_api_level тут не пишемо: значення бере base-конфіг
# емулятора (35 для Android 15), а ручний перезапис властивості, яку генерує
# система, — клас помилок «зелений білд, але PackageManager думає, що API 33».

# --- 5. Безпека і підпис ---------------------------------------------------
# enforce тримає privapp-permissions чистими, щоб пакет, поставлений
# користувачем, не потребав правок у sepolicy.
PRODUCT_SYSTEM_PROPERTIES += ro.control_privapp_permissions=enforce

# ADB без підтвердження ключа — лише для тестового образу. У user-збірці такі
# рядки були б відчиненими дверима, тому умову робимо прямо тут (так само
# робить база: vendor/lineage/config/common.mk:27-42).
ifneq ($(TARGET_BUILD_VARIANT),user)
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    ro.adb.secure=0 \
    persist.sys.usb.config=adb
endif

# --- 6. Розміри образів ----------------------------------------------------
# Тут спеціально немає рядків. Розміри й dynamic partitions задає базовий
# board-конфіг (device/generic/goldfish/board/emu64x/BoardConfig.mk разом з
# sdk_phone64_x86_64.mk: BOARD_EMULATOR_DYNAMIC_PARTITIONS_SIZE = 1.8 ГБ), і
# наша ранішня спроба вимкнути super.img (`PRODUCT_USE_DYNAMIC_PARTITIONS :=
# false`) суперечила fstab емулятора — образ зібрався б іншим, ніж той, на
# який очікують правила run-avd.sh. З нашого в образі тепер лише три
# RRO-оверлеї (§2) і файл сітки 4x6 — одиниці мегабайт, тож місця з базового
# розміру вистачає з запасом.
