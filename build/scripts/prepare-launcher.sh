#!/usr/bin/env bash
# Готує APK лаунчера там, де його шукає Soong: apps/launcher/prebuilt/.
#
# У git apk не лежить (політика репо), тому перед `m` цей скрипт має відпрацювати.
# Викликається з build/scripts/build-emulator.sh; вручну — на білд-боксі або ноутбуці.
#
#   prepare-launcher.sh [gradle | prebuilt <шлях> | artifact <id запуску>]
#
# gradle — збирає сам (потрібні Gradle 8.14.3+, JDK 17, Android SDK);
# prebuilt — бере вже зібраний APK;
# artifact — завантажує апек CI-збірки build-launcher (потрібен GITHUB_TOKEN).
set -euo pipefail

MODE="${1:-gradle}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$REPO/apps/launcher"
DEST_DIR="$SRC/prebuilt"
DEST="$DEST_DIR/TesseraLauncher.apk"
OWNER_REPO="nothing12-glitch/Tessera-OS"

die() { echo "prepare-launcher: $*" >&2; exit 1; }

mkdir -p "$DEST_DIR"

case "$MODE" in
  gradle)
    command -v gradle >/dev/null 2>&1 || die \
      "у PATH немає gradle. Постав Gradle 8.14.3+ або викликай «prebuilt <шлях до APK>»."
    ( cd "$SRC" && gradle --console=plain :app:assembleDebug ) || die "Gradle не зібрав APK"
    BUILT="$SRC/app/build/outputs/apk/debug/app-debug.apk"
    ;;
  prebuilt)
    BUILT="${2:?режим prebuilt потребує шлях до APK другим аргументом}"
    ;;
  artifact)
    RUN="${2:?режим artifact потребує id запуску build-launcher другим аргументом}"
    TOKEN="${GITHUB_TOKEN:?режим artifact потребує GITHUB_TOKEN (artifact-и приватні для аноніма)}"
    ID="$(curl -s -H "Authorization: Bearer $TOKEN" \
              "https://api.github.com/repos/$OWNER_REPO/actions/runs/$RUN/artifacts" \
          | python3 -c 'import json,sys
data = json.load(sys.stdin).get("artifacts", [])
match = [a["id"] for a in data if a["name"] == "tessera-launcher-debug"]
print(match[0] if match else "")')"
    [[ -n "$ID" ]] || die "у запуску $RUN немає артефакту tessera-launcher-debug"
    TMP="$(mktemp -d)"
    curl -sL -H "Authorization: Bearer $TOKEN" \
         -o "$TMP/artifact.zip" \
         "https://api.github.com/repos/$OWNER_REPO/actions/artifacts/$ID/zip"
    unzip -o -q "$TMP/artifact.zip" -d "$TMP/x"
    BUILT="$(find "$TMP/x" -name '*.apk' -type f | head -1)"
    [[ -n "$BUILT" ]] || die "в артефакті $ID немає apk"
    ;;
  *)
    die "невідомий режим «$MODE» — є gradle, prebuilt <шлях>, artifact <id>"
    ;;
esac

[[ -f "$BUILT" ]] || die "немає файлу $BUILT"

# Перевіряємо, що в образ піде саме наш лаунчер. aapt2 беремо з дерева бази,
# якщо скрипт запускається всередині нього; поза деревом — спрощена перевірка.
AAPT2=""
for candidate in \
    "$REPO/../../prebuilts/sdk/tools/linux/bin/aapt2" \
    "$(command -v aapt2 2>/dev/null || true)"; do
  if [[ -n "$candidate" && -x "$candidate" ]]; then AAPT2="$candidate"; break; fi
done

if [[ -n "$AAPT2" ]]; then
  "$AAPT2" dump badging "$BUILT" | grep -q "package: name='os.tessera.launcher'" ||
    die "APK не наш: інший package"
  "$AAPT2" dump xmltree --file AndroidManifest.xml "$BUILT" |
    grep -q '"android.intent.category.HOME"' || die "у APK немає HOME-фільтра"
  echo "ok: aapt2 підтвердив os.tessera.launcher + HOME"
else
  echo "увага: немає aapt2 — перевірка лише складових APK" >&2
fi

for entry in AndroidManifest.xml classes.dex resources.arsc; do
  unzip -l "$BUILT" | grep -q "$entry" || die "у APK немає $entry"
done

cp "$BUILT" "$DEST"

# Бережемо політику: файл, який ми щойно створили, має бути в .gitignore.
git -C "$REPO" check-ignore -q "$DEST" ||
  die "$DEST не ігнорується git — бінарник опиниться в репо"

echo "готово: Soong знайде модуль TesseraLauncher у ${DEST#"$REPO"/}"
sha256sum "$DEST"
