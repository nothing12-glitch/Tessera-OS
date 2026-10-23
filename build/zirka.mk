# Конфіг продукту Zirka OS для x86_64-віртуальної машини.
# Маніфест лінкує цей файл у build/make/target/product/zirka_x86_64.mk,
# тому `lunch zirka_x86_64-userdebug` працює без ручних symlink.
#
#   source build/envsetup.sh && lunch zirka_x86_64-userdebug && m -j$(nproc)

# --- 1. База ---------------------------------------------------------------
# Повний конфіг AOSP для x86_64: framework, SystemUI, STOCK_AOSP застосунки.
$(call inherit-product, build/make/target/product/full_x86_64.mk)

# Линії LineageOS (OTA-інфра, Privacy Guard, Theme engine, Backup).
# inherit-product-if-exists робить конфіг безпечним і для чисто-AOSP гілки.
$(call inherit-product-if-exists, vendor/lineage/config/lineage_microg.mk)
$(call inherit-product-if-exists, build/make/target/product/lineage_base.mk)

# --- 2. MD3-оверлеї --------------------------------------------------------
# Static RRO пакети з overlay/themes/. Збираються через Android.mk blueprint
# (Android.bp поряд з кожним пакетом); isStatic="true" в маніфесті
# означає, що окрема активація через `cmd overlay enable` не потрібна.
PRODUCT_PACKAGES += \
    os.zirka.theme.framework \
    os.zirka.theme.settings

# --- 3. Сервіси, замість власницьких бінарів -------------------------------
# microG — вільна реалізація push/location/oauth. Профіль GApps користувача
# описано в docs/GAPPS.md; сюда власницькі бінари Google не входять.
PRODUCT_PACKAGES += \
    microG \
    GmsCore \
    FakeStore \
    AuroraStore

# Прибираємо з образу те, що не має сенсу в VM (телефонія, SMS).
# Продуктова змінна LineageOS; на чистому AOSP ігнорується.
PRODUCT_PACKAGES_REMOVE := Dialer Messaging

# --- 4. Ідентичність -------------------------------------------------------
PRODUCT_NAME := zirka_x86_64
PRODUCT_DEVICE := x86_64
PRODUCT_MANUFACTURER := Zirka OS Project
PRODUCT_BRAND := zirka
PRODUCT_MODEL := Zirka OS (x86_64 VM)

# Версія проєкту: <android>.<lineage>.<zirka>. Теги GitHub — v<версія>.
ZIRKA_VERSION := 15.22.2.0
ZIRKA_CODENAME := vega

PRODUCT_PROPERTY_OVERRIDES += \
    ro.zirka.version=$(ZIRKA_VERSION) \
    ro.zirka.codename=$(ZIRKA_CODENAME) \
    ro.zirka.base=LineageOS-22.2 \
    ro.zirka.theme=material3 \
    persist.zirka.dynamic_color=1 \
    ro.product.first_api_level=35

# --- 5. Розміри розділів для VM -------------------------------------------
# image-розміри задаються з BoardConfig; тут лише logical-партиції.
# Звір ці значення з розділами в device/generic/x86_64/BoardConfig.mk.
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.zirka.partition.scheme=non-a-b

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
