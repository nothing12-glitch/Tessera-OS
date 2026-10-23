# Конфіг продукту TesseraOS для x86_64-віртуальної машини.
# Маніфест лінкує цей файл у device/tessera/x86_64/tessera_x86_64.mk, а
# сусідній AndroidProducts.mk оголошує таргент для lunch. Ручні symlink не
# потрібні:
#
#   source build/envsetup.sh && lunch tessera_x86_64-userdebug && m -j$(nproc)

# --- 1. База ---------------------------------------------------------------
# Повний конфіг AOSP для x86_64: framework, SystemUI, STOCK_AOSP застосунки.
$(call inherit-product, build/make/target/product/full_x86_64.mk)

# Линії LineageOS (OTA-інфра, Privacy Guard, Theme engine, Backup).
# inherit-product-if-exists робить конфіг безпечним і для чисто-AOSP гілки.
$(call inherit-product-if-exists, vendor/lineage/config/lineage_microg.mk)
$(call inherit-product-if-exists, build/make/target/product/lineage_base.mk)

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

# Наші заміни стандартних дрібних застосунків. Усі три — той самий механізм, що
# і лаунчер: apps/<застосунок>/Android.bp з android_app_import + APK, покладений
# build/scripts/prepare-app.sh.
#
# `overrides` у жодного з них свідомо немає, і це не забутовка, а рішення:
#  - калькулятор у базі може й не входити в x86_64-перелік пакетів
#    (vendor/lineage/config/common_mobile.mk), тому перекривати нічого;
#  - файловий застосунок у базі — DocumentsUI, який тримає системні ролі
#    ROOT_DOCUMENTS і DEFAULT_MIME_TYPE_HANDLERS: без них зламається відкриття
#    файлів іншими застосунками. Наш Files цих ролей не реалізує.
#  - годинник у базі — packages/apps/DeskClock; він тримає роль будильника
#    через AlarmManager, і перекриття його власницьким не-системним APK без
#    перевіреного списку модулів — зайвий ризик.
# Наслідок: у образі ці застосунки можуть бути подвоєні зі стоковими.
# Це видимий дубль без наслідків для системи; точні імена модулів бази
# перевіримо на зібраному образі — тоді й додамо overrides.
PRODUCT_PACKAGES += \
    TesseraCalculator \
    TesseraClock \
    TesseraFiles

# --- 3. Сервіси, замість власницьких бінарів -------------------------------
# microG — вільна реалізація push/location/oauth. Профіль GApps користувача
# описано в docs/GAPPS.md; сюда власницькі бінари Google не входять.
PRODUCT_PACKAGES += \
    microG \
    GmsCore \
    FakeStore \
    AuroraStore

# Прибираємо з образу те, що не має сенсу в VM (телефонія, MMS).
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
PRODUCT_PACKAGES := $(filter-out MmsService Dialer messaging,$(PRODUCT_PACKAGES))

# --- 4. Ідентичність -------------------------------------------------------
PRODUCT_NAME := tessera_x86_64
PRODUCT_DEVICE := x86_64
PRODUCT_MANUFACTURER := TesseraOS Project
PRODUCT_BRAND := tessera
PRODUCT_MODEL := TesseraOS (x86_64 VM)

# Версія проєкту: <android>.<lineage>.<tessera>. Теги GitHub — v<версія>.
TESSERA_VERSION := 15.22.2.0
TESSERA_CODENAME := vega

PRODUCT_PROPERTY_OVERRIDES += \
    ro.tessera.version=$(TESSERA_VERSION) \
    ro.tessera.codename=$(TESSERA_CODENAME) \
    ro.tessera.base=LineageOS-22.2 \
    ro.tessera.theme=material3 \
    persist.tessera.dynamic_color=1 \
    ro.product.first_api_level=35

# --- 5. Розміри розділів для VM -------------------------------------------
# image-розміри задаються з BoardConfig; тут лише logical-партиції.
# Звір ці значення з розділами в device/generic/x86_64/BoardConfig.mk.
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.tessera.partition.scheme=non-a-b

# Dynamic partitions: у VM простіше без super.img.
PRODUCT_USE_DYNAMIC_PARTITIONS := false
PRODUCT_BUILD_SUPER_PARTITION := false

# --- 6. Безпека і підпис ---------------------------------------------------
# enforce тримає privapp-permissions чистими, щоб GApps-пакет, поставлений
# користувачем, не потребав правок у sepolicy.
PRODUCT_PROPERTY_OVERRIDES += \
    ro.control_privapp_permissions=enforce

# userdebug-образ для тестів; для release these два рядки має прибрати CI.
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    ro.adb.secure=0 \
    persist.sys.usb.config=adb

# --- 7. Відтворюваність ----------------------------------------------------
# Timestamp беремо з CI (SOURCE_DATE_EPOCH), а не з date, щоб два білд-бокси
# давали однаковий build.prop. Див. docs/BUILD.md#відтворюваність.
ifdef SOURCE_DATE_EPOCH
PRODUCT_BUILD_PROP_OVERRIDES += \
    BUILD_DATETIME=$(shell date -u -d @$(SOURCE_DATE_EPOCH) +%Y-%m-%dT%H:%M:%SZ) \
    BUILD_DATE_UTC=$(shell date -u -d @$(SOURCE_DATE_EPOCH) +%Y-%m-%dT%H:%M:%SZ)
endif
