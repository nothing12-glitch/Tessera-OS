#!/usr/bin/env bash
# Повна збірка образу TesseraOS для x86_64-віртуальної машини.
#
# Тільки Linux. Орієнтир вимог (wiki.lineageos.org/emulator, гілка 22.2):
# 400+ ГБ на диску і 16–32 ГБ оперативної; перший прохід — години, наступні —
# хвилини, якщо працює ccache. Перед запуском має бути нове дерево:
# build/scripts/new-tree.sh.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export USE_CCACHE=1
export CCACHE_DIR="${CCACHE_DIR:-$HOME/.ccache}"
export CCACHE_COMPRESS=1
# Кеш корисний, але він їсть те саме місце, що й out/. Орієнтир: чверть вільного
# місця на диску, стеля 80G. На ноутбуці з 300 ГБ виходить ~75G, на машині з
# 60 ГБ — 15G, і збірка не вмирає посередині через «No space left on device».
FREE_GB=$(df -BG --output=avail . 2>/dev/null | tail -1 | tr -dc '0-9')
DEFAULT_CCACHE="${FREE_GB:+$(( FREE_GB / 4 ))G}"
export CCACHE_SIZE="${CCACHE_SIZE:-${DEFAULT_CCACHE:-80G}}"
export WITH_DEXPREOPT=true
# Відтворюваність: однакова дата в усіх файлах образу. Значення бере дату
# останнього коміку нашого шару; CI перезаписує її значенням із запуску.
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-$(git -C "$REPO_DIR" log -1 --format=%ct 2>/dev/null || date -u +%s)}"

TARGET="${1:-tessera_x86_64-userdebug}"

# Наші застосунки (реєстр — build/scripts/apps.list) входять у образ як
# android_app_import, тому APK мають існувати ще до аналізу Soong: інакше
# помилка «module TesseraFiles: apk not found» з'явиться посеред години збірки.
# Пропустити — TESSERA_SKIP_APPS=1; інший режим — TESSERA_APPS_ARGS, напр.
# "prebuilt /шлях" для одного застосунку або "artifact <id>" для всіх.
if [[ "${TESSERA_SKIP_APPS:-0}" != "1" ]]; then
  # shellcheck disable=SC2086  # TESSERA_APPS_ARGS — це режим + аргумент
  bash "$REPO_DIR/build/scripts/prepare-app.sh" all ${TESSERA_APPS_ARGS:-}
fi

# Статична страховка перед годинною збіркою: чи всі наші посилання на базу
# ведуть на файли, які справді є в цьому дереві.
bash "$REPO_DIR/build/scripts/check-build-refs.sh" --tree . || exit 1

source build/envsetup.sh
lunch "$TARGET"

# Кількість паралельних завдань обмежуємо пам'яттю: орієнтовно 4 ГБ на ядро.
CORES=$(nproc)
MEM_JOBS=$(free -g | awk '/^Mem:/{print int($2/4)}')
JOBS=$(( CORES < MEM_JOBS ? CORES : MEM_JOBS ))
JOBS=$(( JOBS < 1 ? 1 : JOBS ))

echo ">>> m -j$JOBS (таргет: $TARGET)"
m -j"$JOBS" 2>&1 | tee "${LOG_DIR:-/tmp}/tessera-build-$(date -u +%Y%m%dT%H%M%SZ).log"

# Каталог образу виводимо з конфіга, а не вгадуємо з назви таргета: для
# tessera_x86_64 він out/target/product/emu64x, бо PRODUCT_DEVICE успадковується
# від платформи емулятора (sdk_phone64_x86_64.mk:36).
OUT="$(get_build_var PRODUCT_OUT)"
echo ">>> Образи у $OUT:"
ls -1 "$OUT"/*.img 2>/dev/null || {
  echo "Немає .img у $OUT — див. лог вище (docs/BUILD.md, розділ «Типові помилки»)." >&2
  exit 1
}
( cd "$OUT" && sha256sum *.img > tessera-images-sha256.txt && cat tessera-images-sha256.txt )
echo ">>> Далі: build/scripts/run-avd.sh --image $OUT"
