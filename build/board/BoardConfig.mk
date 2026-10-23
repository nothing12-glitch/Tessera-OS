# Конфіг платформи x86_64 для віртуальної машини Zirka OS.
# Маніфест лінкує цей файл у device/zirka/x86_64/BoardConfig.mk.
#
# Зверни увагу: значна частина параметрів успадковується від
# device/generic/x86_64. Сюди додаємо лише те, що відрізняє наш образ.

include device/generic/x86_64/BoardConfig.mk

TARGET_ARCH := x86_64
TARGET_ARCH_VARIANT := x86_64
TARGET_CPU_ABI := x86_64
TARGET_2ND_ARCH := x86
TARGET_2ND_ARCH_VARIANT := x86_64
TARGET_2ND_CPU_ABI := x86

# 64-бітний system; без translation layer для ARM-бібліотек
# (libhoudini — власницький, у Zirka OS відсутній). Див. docs/EMULATOR.md.
TARGET_SUPPORTS_64_BIT_APPS := true
TARGET_SUPPORTS_32_BIT_APPS := true

# --- Розділи ---------------------------------------------------------------
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 8589934592
BOARD_VENDORIMAGE_PARTITION_SIZE := 1073741824
BOARD_PRODUCTIMAGE_PARTITION_SIZE := 2147483648
BOARD_USERDATAIMAGE_PARTITION_SIZE := 6442450944
BOARD_FLASH_BLOCK_SIZE := 512

TARGET_USERIMAGES_USE_EXT4 := true
TARGET_USERIMAGES_SPARSE_EXT_DISABLED := false

# Без A/B: в VM OTA-перемикання розділів лише заплутує досвід.
AB_OTA_UPDATER := false
BOARD_DYNAMIC_PARTITION_ENABLE := false

# --- Ядро / boot -----------------------------------------------------------
BOARD_KERNEL_CMDLINE += console=ttyS0 androidboot.hardware=ranchu
BOARD_KERNEL_CMDLINE += androidboot.selinux=enforcing
BOARD_KERNEL_BASE := 0x00000000
BOARD_KERNEL_PAGESIZE := 4096
# BSD-ліцензійне ядро для ranchu береМО з device/generic (перевір у своїй гілці)

# SELinux: власних правил не додаємо;тематичний шар не потребує sepolicy-правок.
include device/generic/x86_64/sepolicy.mk

# --- Графіка ---------------------------------------------------------------
# -gpu host у qemu дає hardware composer;/software fallback лишається.
USE_OPENGL_RENDERER := true
BOARD_GPU_DRIVERS := swiftshader
TARGET_USES_SWSHADER := true

# --- Відтворюваність -------------------------------------------------------
# Вимикаємо випадкові мітки часу в образах.
BUILD_BROKEN_DUP_RULES := true
$(warning "Zirka OS BoardConfig: перевір BOARD_*_PARTITION_SIZE для своєї гілки")
