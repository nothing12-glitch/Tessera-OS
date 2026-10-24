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
#  - iconshape  — круглі adaptive-іконки (config_useRoundIcon + config_icon_mask);
#  - settings   — один підтверджений токен, решту дає Theme.DeviceDefault.
#
# Імена всіх трьох перевірені деревом бази: build/scripts/check-overlay-names.sh.
PRODUCT_PACKAGES += \
    os.tessera.theme.framework \
    os.tessera.theme.iconshape \
    os.tessera.theme.settings

# Стартова сітка лаунчера 5x6 лежить окремим шаром: це не RRO, а build-time
# overlay (змінні досі живі: build/make/core/product.mk:120-121). Причина
# з'ясувалась на збірці — aapt2 линкує RRO проти власного пакету, тому файл
# з атрибутами власного namespace (launcher:numColumns, launcher:canBeDefault)
# не збирається як RRO взагалі:
#   error: attribute deviceCategory (aka ...:deviceCategory) not found
# Під час збірки застосунку в дереві ті атрибути належать пакету Trebuchet,
# і підміна файлу працює. Не перевірено на зібраному образі — див. docs/UI.md §9
# і docs/PIXEL-PARITY.md.
DEVICE_PACKAGE_OVERLAYS += vendor/tessera/os/overlay-packages

# Наш стартовий застосунок. Модуль оголошений у apps/launcher/Android.bp як
# android_app_import (APK збирає Gradle, у git його немає — готує
# build/scripts/prepare-app.sh до m; список застосунків — apps.list там само).
#
# Сток-лаунчер НЕ перекримо: у базі це TrebuchetQuickStep
# (vendor/lineage/config/common_mobile.mk:31-38), і він дає ще й перегляд
# недавніх (frameworks/base/core/res/res/values/config.xml:3622
# config_recentsComponentName). Наш лаунчер Recents ще не робить, тому в
# образі поки два HOME-застосунки, а вибір робить користувач один раз:
#   adb shell cmd package set-home-activity os.tessera.launcher/.LauncherActivity
PRODUCT_PACKAGES += TesseraLauncher

# Наші заміни стандартних дрібних застосунків. Усі п'ять — той самий механізм,
# що й лаунчер: apps/<застосунок>/Android.bp з android_app_import + APK,
# покладений build/scripts/prepare-app.sh.
#
# «Телефон» і «Повідомлення» стоять окремо: вони не дублюють, а замінюють.
# Стокові Dialer і messaging прибрано з переліку пакетів фільтром нижче, тому в
# образі лишається один набір — і `overrides` для цього не потрібні (див.
# пояснення до фільтра).
#
# У решти трьох `overrides` свідомо немає, і це не забутовка, а рішення:
#  - калькулятор у базі може й не входити в x86_64-перелік пакетів
#    (vendor/lineage/config/common_mobile.mk), тому перекривати нічого;
#  - файловий застосунок у базі — DocumentsUI, який тримає системні ролі
#    ROOT_DOCUMENTS і DEFAULT_MIME_TYPE_HANDLERS: без них зламається відкриття
#    файлів іншими застосунками. Наш Files цих ролей не реалізує.
#  - годинник у базі — packages/apps/DeskClock; він тримає роль будильника
#    через AlarmManager, і перекриття його власницьким не-системним APK без
#    перевіреного списку модулів — зайвий ризик.
# Наслідок: у образі ці три (калькулятор, файли, годинник) можуть бути подвоєні
# зі стоковими.
# Це видимий дубль без наслідків для системи; точні імена модулів бази
# перевіримо на зібраному образі — тоді й додамо overrides.
PRODUCT_PACKAGES += \
    TesseraCalculator \
    TesseraCamera \
    TesseraClock \
    TesseraDialer \
    TesseraFiles \
    TesseraMessaging

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

# Прибираємо з образу зайві модулі бази. Причина в них різна, тому й рядки
# розділені.
#
# `Dialer` — прибираємо, бо його замінює наш «Телефон» (TesseraDialer вище). Це
# не «телефонії в VM не буває»: дзвінки — одна з цілей проєкту. Прибираємо саме
# стоковий набір, щоб в образі не було двох «Телефонів» з однаковим виглядом, і
# щоб роль ROLE_DIALER міг взяти наш застосунок без конкурента.
#
# `messaging` — прибираємо, бо його замінює наш застосунок (TesseraMessaging).
#
# `MmsService` навпаки — повертаємо в образ. Рішення свідоме й ціна його відома:
# MMS ми не реалізуємо (компонент WAP_PUSH у «Повідомленнях» порожній — див.
# apps/messaging/.../receive/WapPushReceiver.kt), тому поки роль ROLE_SMS зайнято
# нами, мультимедійні повідомлення не довантажуються взагалі. Без MmsService
# пристрій не мав би їх навіть тоді, коли користувач не захоче ставити наш
# застосунок основним SMS — а це гірше, ніж одна непрохана мода у списку пакетів.
#
# Раніше тут стояло `PRODUCT_PACKAGES_REMOVE := Dialer Messaging`. Перевірено
# деревом бази: такої змінної в LineageOS 22.2 НЕМАЄ — нулів у
# build/make/core/product.mk, core/Makefile, core/config.mk і у
# vendor/lineage/config/{common,common_mobile}.mk. Рядок мовчки нічого не
# прибирав (те саме «зелений білд ≠ результат», від якого захищає
# check-overlay-names.sh). Працюючий механізм — фільтр уже зібраного списку,
# бо inherit-product вище розкрив PRODUCT_PACKAGES повністю.
#
# Імена модулів підтверджені: MmsService — build/make/target/product/
# handheld_system.mk:60; Dialer — packages/apps/Dialer/Android.bp:11;
# messaging — packages/apps/Messaging/Android.bp:55 (малими літерами!).
# Останні два доходять лише якщо база додає їх через vendor/lineage/config;
# на x86_64-VM вони можуть не бути в списку взагалі — фільтр тоді порожній,
# але й не бреше.
PRODUCT_PACKAGES := $(filter-out messaging,$(PRODUCT_PACKAGES))
PRODUCT_PACKAGES := $(filter-out Dialer,$(PRODUCT_PACKAGES))

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

# Версія проєкту: <android>.<lineage>.<tessera>. Теги GitHub — v<версія>.
TESSERA_VERSION := 15.22.2.1
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
# який очікують правила run-avd.sh. Наші сім застосунків + три RRO важать
# ~30 МБ, тож місця вистачає з базового розміру.
