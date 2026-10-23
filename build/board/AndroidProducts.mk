# Файл відкриття lunch-таргетів для платформи x86_64 VM.
# Маніфест лінкує його в device/tessera/x86_64/AndroidProducts.mk.
#
# AOSP шукає таргети не за іменем файлу, а саме тут: envsetup.sh збирає
# усі device/**/AndroidProducts.mk і бере з них PRODUCT_MAKEFILES.
# Без цього файлу `lunch tessera_x86_64-userdebug` не бачить продукт,
# навіть якщо .mk конфігу існує.

PRODUCT_LIST := \
    tessera_x86_64

# $(LOCAL_DIR) = device/tessera/x86_64
PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/tessera_x86_64.mk

COMMON_LUNCH_CHOICES := \
    tessera_x86_64-userdebug \
    tessera_x86_64-eng
