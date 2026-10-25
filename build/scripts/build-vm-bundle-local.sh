#!/usr/bin/env bash
# Збирає установчий пакунок для VM на локальному білд-боксі (WSL) — той самий,
# який віддає .github/workflows/release.yml, тільки без GitHub.
#
#   bash build/scripts/build-vm-bundle-local.sh
#
# Навіщо окремий скрипт, якщо три кроки можна набрати вручну: порядок тут
# принциповий. Збірка без aapt2 з SDK дає пакунок «теми начебто просили, але
# їх немає» — саме так народився реліз v15.22.2.0, у якому оформлення не було.
# Тому спочатку APK, потім оверлеї, і лише тоді пакунок, а в кінці гарант, що
# в dist/ не потрапив образ чи ключ.
#
# Перед першим запуском потрібні Gradle і Android SDK:
#   bash build/scripts/install-app-toolchain.sh
#
# Пакунок ЛИШАЄТЬСЯ локально: оприлюднення — окрема дія і окремий дозвіл
# (git push віхи + release.yml), цей скрипт нікуди нічого не пушить.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

die() { echo "vm-bundle-local: $*" >&2; exit 1; }

command -v gradle >/dev/null 2>&1 ||
  die "немає gradle у PATH. Спочатку: bash build/scripts/install-app-toolchain.sh, а далі — новий запуск оболонки (змінні з ~/.profile)"

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
[[ -d "$SDK" ]] || die "немає Android SDK за $SDK — див. install-app-toolchain.sh"
BT="$(ls -1d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
[[ -n "$BT" && -x "${BT%/}/aapt2" ]] ||
  die "у $SDK немає build-tools/*/aapt2: оверлеїв без нього не буде, а пакунок без теми ми вже випускали"

# build-themes.sh шукає aapt2/zipalign/apksigner у PATH; у SDK вони лежать у
# build-tools/<версія>/ і в PATH їх нема. В workflow робиться так само.
export AAPT2="${BT%/}/aapt2" ZIPALIGN="${BT%/}/zipalign" APKSIGNER="${BT%/}/apksigner"

echo "=== 1. APK усіх застосунків з реєстру (Gradle) + перевірка маніфестів ==="
bash "$ROOT/build/scripts/prepare-app.sh" all gradle

echo
echo "=== 2. RRO-оверлеї теми MD3 ==="
bash "$ROOT/build/scripts/build-themes.sh"

echo
echo "=== 3. пакунок ==="
bash "$ROOT/build/scripts/make-vm-bundle.sh" --themes

echo
echo "=== 4. гарант: у dist немає ні образів, ні ключів ==="
if find "$ROOT/dist" -type f \( -name '*.img' -o -name '*.pk8' -o -name '*.pem' \
     -o -name '*.keystore' -o -name '*.jks' \) | grep -q .; then
  die "у dist/ знайдено образ або ключ — таке не можна віддавати"
fi
ARCHIVE="$(ls -1t "$ROOT"/dist/*.tar.xz | head -1)"
echo "ok: $(basename "$ARCHIVE")"
( cd "$ROOT/dist" && sha256sum "$(basename "$ARCHIVE")" | tee "$(basename "$ARCHIVE").verified" )

echo
echo "Далі, якщо пакунок справді хочемо показати світу:"
echo "  1) підняти TESSERA_VERSION у build/tessera.mk і закомітити"
echo "  2) поставити віху v<версія> і зробити git push origin <віха>"
echo "     — release.yml тоді сам збере і оприлюднить пакунок"
