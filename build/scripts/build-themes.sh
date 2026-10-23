#!/usr/bin/env bash
# Збірка RRO-оверлеїв MD3 поза деревом AOSP: aapt2 compile/link + apksigner.
# Використовується CI на GitHub-hosted раннері й локальною розробницею.
#
#   build/scripts/build-themes.sh
#   build/scripts/build-themes.sh --framework-jar out/soong/framework-res/android.jar
#
# Windows (Git Bash): apksigner постачається як .bat і bash не знаходить його
# без повного шляху, тому APKSIGNER="$SDK/build-tools/36.0.0/apksigner.bat".
#
# Обмеження, яке варто розуміти: res/values/styles.xml посилається на
# приховані ресурси framework (@*android:style/Theme.DeviceDefault*).
# Публічний android.jar з SDK їх не містить, тому без --framework-jar
# скрипт будує «палітровий» варіант оверлею (colors/dimens) і друкує
# попередження. Повний оверлей збирається всередині дерева AOSP:
#   m os.tessera.theme.framework os.tessera.theme.settings \
#     os.tessera.theme.launcher3 os.tessera.theme.systemui
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/overlay/out"
FRAMEWORK_JAR="${TESSERA_ANDROID_JAR:-}"
AAPT2="${AAPT2:-aapt2}"
ZIPALIGN="${ZIPALIGN:-zipalign}"
APKSIGNER="${APKSIGNER:-apksigner}"
KEYSTORE="${TESSERA_KEYSTORE:-$ROOT/build/testkey/tessera-debug.jks}"
KS_PASS="${TESSERA_KEYSTORE_PASSWORD:-tessera}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --framework-jar) FRAMEWORK_JAR="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    *) echo "Невідома опція: $1" >&2; exit 2 ;;
  esac
done

find_android_jar() {
  if [[ -n "$FRAMEWORK_JAR" ]]; then printf '%s\n' "$FRAMEWORK_JAR"; return; fi
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    ls -1d "$ANDROID_HOME"/platforms/android-* 2>/dev/null | sort -V | tail -1 |
      { read -r p && printf '%s\n' "$p/android.jar"; }
  fi
}

ANDROID_JAR_PATH="$(find_android_jar || true)"
if [[ -z "$ANDROID_JAR_PATH" || ! -f "$ANDROID_JAR_PATH" ]]; then
  echo "Помилка: android.jar не знайдено. Експортуй ANDROID_HOME або --framework-jar." >&2
  exit 1
fi

# Підписувальний ключ. Тестовий, генерується на місці; release-ключ у CI
# приходить з secrets і сюди не потрапляє.
if [[ ! -f "$KEYSTORE" ]]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -v -keystore "$KEYSTORE" -alias tessera \
    -keyalg RSA -keysize 2048 -validity 10000 -storepass "$KS_PASS" \
    -keypass "$KS_PASS" -dname "CN=TesseraOS test key, O=TesseraOS Project" >/dev/null
  echo "Згенеровано тестовий ключ: $KEYSTORE"
fi

mkdir -p "$OUT"
: > "$OUT/sha256sum.txt"

build_theme() {
  local dir="$1" name stage full
  name="$(basename "$dir")"; stage="$OUT/.stage/$name"; full="${FRAMEWORK_JAR:-}"
  rm -rf "$stage"; mkdir -p "$stage/res"
  cp -r "$dir/res/." "$stage/res/"

  if [[ -z "$full" ]] && find "$stage/res" -name styles.xml -print -quit | grep -q .; then
    echo "[$name] styles.xml пропущено: private-посилання framework потребують AOSP android.jar"
    find "$stage/res" -name styles.xml -delete
  fi

  "$AAPT2" compile --dir "$stage/res" -o "$stage/compiled.zip"
  "$AAPT2" link -o "$stage/unsigned.apk" \
    --manifest "$dir/AndroidManifest.xml" \
    -I "$ANDROID_JAR_PATH" --auto-add-overlay \
    --min-sdk-version 30 --target-sdk-version 35 \
    "$stage/compiled.zip"
  "$ZIPALIGN" -f 4 "$stage/unsigned.apk" "$stage/aligned.apk"
  "$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
    --out "$OUT/$name.apk" "$stage/aligned.apk"
  ( cd "$OUT" && sha256sum "$name.apk" >> sha256sum.txt )
  echo "[$name] overlay/out/$name.apk"
}

for theme in "$ROOT"/overlay/themes/*/; do build_theme "${theme%/}"; done
echo "Готово. Артефакти в $OUT"
