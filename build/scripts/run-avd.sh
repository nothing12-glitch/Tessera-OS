#!/usr/bin/env bash
# Запуск зібраного x86_64-образу TesseraOS у Android Emulator (AVD).
# Вимоги: Android SDK (cmdline-tools, platform-tools, emulator), KVM.
set -euo pipefail

# Каталог образу — за PRODUCT_DEVICE, тобто emu64x (платформа емулятора), а не
# за назвою продукту. get_build_var PRODUCT_OUT у build-emulator.sh друкує те
# саме значення.
IMG_DIR="out/target/product/emu64x"
AVD_NAME="Tessera_X86_64"
API="${API:-35}"
ABI="x86_64"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image) IMG_DIR="$2"; shift 2 ;;
    --avd) AVD_NAME="$2"; shift 2 ;;
    --api) API="$2"; shift 2 ;;
    *) echo "Невідома опція: $1" >&2; exit 2 ;;
  esac
done

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
EMULATOR="$SDK/emulator/emulator"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

[[ -x "$EMULATOR" ]] || { echo "Не знайдено $EMULATOR. Встанови пакунок 'emulator'." >&2; exit 1; }
[[ -d "$IMG_DIR" ]] || { echo "Немає каталогу з образами: $IMG_DIR (спочатку build-emulator.sh)" >&2; exit 1; }

# 1. Системний образ нашої ROM у сховище SDK. Формат каталогу збігається з
#    офіційними образами: system-images;<platform>;<tag>;<abi>.
#
#    Окрім .img сюди потрібен ще kernel-ranchu: базовий конфіг платформи копіює
#    ядро саме з такою назвою в каталог образу (device/generic/goldfish/board/
#    emu64x/details.mk:22-23), без нього емулятор не має що завантажувати.
#    Альтернатива, яку варто спробувати першою на білд-боксі: `m emu_img_zip`
#    — архів із готовим layout від бази; тоді копіювати вручну не потрібно.
TAG="tessera"
DEST="$SDK/system-images/android-$API/$TAG/$ABI"
mkdir -p "$DEST"
cp -v "$IMG_DIR"/*.img "$DEST/" 2>/dev/null || {
  echo "У $IMG_DIR немає .img-файлів." >&2; exit 1; }
[[ -f "$IMG_DIR/kernel-ranchu" ]] && cp -v "$IMG_DIR/kernel-ranchu" "$DEST/" ||
  echo "увага: немає kernel-ranchu у $IMG_DIR — у AVD не буде ядра" >&2
( cd "$DEST" && sha256sum *.img kernel-ranchu 2>/dev/null > sha256sum.txt )

# 2. AVD на цей образ. avdmanager шукає пакети в метаданих SDK; якщо відмовляє,
#    створи AVD вручну: ~/.android/avd/<name>.avd.ini з target=android-$API та
#    розпакуй образи в ~/.android/avd/<name>.avd/.
PKG_ID="system-images;android-$API;$TAG;$ABI"
if ! "$AVDMANAGER" list avd 2>/dev/null | grep -q "Name: $AVD_NAME"; then
  "$AVDMANAGER" create avd --name "$AVD_NAME" --package "$PKG_ID" \
    --device "pixel_7" --force <<< "no" || {
    echo "Не вдалося створити AVD для $PKG_ID. Див. коментар вище і docs/EMULATOR.md." >&2
    exit 1
  }
fi

# 3. Запуск. -gpu host дає апаратне прискорення, -writable-system — правки теми.
exec "$EMULATOR" -avd "$AVD_NAME" \
  -writable-system -gpu host -no-snapshot -memory 4096 \
  -shell -skin 1080x2400 "$@"
