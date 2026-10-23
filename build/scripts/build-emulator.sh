#!/usr/bin/env bash
# Повна збірка образу TesseraOS для x86_64-віртуальної машини.
# Тільки Linux, 250+ ГБ, 16+ ГБ RAM. Перший прохід — 2-6 годин.
set -euo pipefail

export OOT_DIR="${OOT_DIR:-$PWD}"
export USE_CCACHE=1
export CCACHE_DIR="${CCACHE_DIR:-$HOME/.ccache}"
export CCACHE_COMPRESS=1
export CCACHE_SIZE="${CCACHE_SIZE:-80G}"
export WITH_DEXPREOPT=true
# Відтворюваність: один і той самий timestamp для всіх файлів образу.
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-$(git -C "$(dirname "${BASH_SOURCE[0]}")/../.." log -1 --format=%cI 2>/dev/null | xargs -I{} date -u -d {} +%s 2>/dev/null || date -u +%s)}"

TARGET="${1:-tessera_x86_64-userdebug}"

# Наші застосунки (реєстр — build/scripts/apps.list) входять у образ як
# android_app_import, тому APK мають існувати ще до аналізу Soong: інакше
# помилка «module TesseraFiles: apk not found» з'явиться посеред години збірки.
# Пропустити — TESSERA_SKIP_APPS=1; інший режим — TESSERA_APPS_ARGS, напр.
# "prebuilt /шлях" для одного застосунку або "artifact <id>" для всіх.
if [[ "${TESSERA_SKIP_APPS:-0}" != "1" ]]; then
  REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  # shellcheck disable=SC2086  # TESSERA_APPS_ARGS — це режим + аргумент
  bash "$REPO_DIR/build/scripts/prepare-app.sh" all ${TESSERA_APPS_ARGS:-}
fi

source build/envsetup.sh
lunch "$TARGET"

# Кількість паралельних завдань обмежуємо пам'яттю: орієнтовно 4 ГБ на ядро.
CORES=$(nproc)
MEM_JOBS=$(free -g | awk '/^Mem:/{print int($2/4)}')
JOBS=$(( CORES < MEM_JOBS ? CORES : MEM_JOBS ))
JOBS=$(( JOBS < 1 ? 1 : JOBS ))

echo ">>> m -j$JOBS (target: $TARGET)"
m -j"$JOBS" 2>&1 | tee "${LOG_DIR:-/tmp}/tessera-build-$(date -u +%Y%m%dT%H%M%SZ).log"

PRODUCT="${TARGET%%-*}"
OUT="out/target/product/${PRODUCT#tessera_}"
echo ">>> Образи у $OUT:"
ls -1 "$OUT"/*.img 2>/dev/null || {
  echo "Не знайдено .img — переві назву таргета (docs/BUILD.md, крок 3)." >&2
  exit 1
}
( cd "$OUT" && sha256sum *.img > tessera-images-sha256.txt && cat tessera-images-sha256.txt )
