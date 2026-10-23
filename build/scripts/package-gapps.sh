#!/usr/bin/env bash
# Готує профіль встановлення Google-сервісів для користувача.
# НИКОЛИ не викладу власницькі бінари у git — див. docs/GAPPS.md.
set -euo pipefail

VENDOR="mtg"      # mtg | microg | opengapps (last — legacy)
VARIANT="basic"   # pico | nano | basic | stock | full
API="35"; ARCH="x86_64"; FLAVOR="zirka"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --vendor) VENDOR="$2"; shift 2 ;;
    --variant) VARIANT="$2"; shift 2 ;;
    --android) API="$2"; shift 2 ;;
    --arch) ARCH="$2"; shift 2 ;;
    *) echo "Невідома опція: $1" >&2; exit 2 ;;
  esac
done

OUT="config/gapps"
mkdir -p "$OUT"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

case "$VENDOR" in
  microg)
    URL="https://microg.org/download.html"
    NOTE="Вільна реалізація push/location/oauth. Входить в образ за замовчуванням."
    ;;
  mtg)
    URL="https://github.com/MindTheGApps/releases/raw/$API-$ARCH-$VARIANT.zip"
    NOTE="Пакети MindTheGApps під конкретний ROM-білд; потребує прийняття умов Google."
    ;;
  opengapps)
    URL="https://github.com/opengapps/opengapps/wiki/Package-Comparison"
    NOTE="ЗАСТАРІЛЕ: OpenGApps не оновлювався з 2023, на Android 15 дає bootloop."
    ;;
  *) echo "Невідомий вендор: $VENDOR" >&2; exit 2 ;;
esac

cat > "$OUT/profile.json" <<EOF
{
  "generated": "$STAMP",
  "vendor": "$VENDOR",
  "variant": "$VARIANT",
  "android_api": "$API",
  "arch": "$ARCH",
  "flavor": "$FLAVOR",
  "source": "$URL",
  "note": "$NOTE",
  "install": [
    "0. Зроби резервну копію і переконайся, що imagesigned з тим самим releasekey.",
    "1. Завантаж пакет за source і ПЕРЕВІР sha256 з офіційного оголошення.",
    "2. Проши через recovery (LineageOS Recovery) або adb sideload.",
    "3. Перше завантаження після GApps — 2-5 хвилин, не переривай."
  ],
  "checksum_policy": "білд падає, якщо sha256 не збігається з оголошеним"
}
EOF

echo "Профіль: $OUT/profile.json"
echo
echo "Далі (виконує користувач, не CI):"
if [[ "$VENDOR" == "microg" ]]; then
  echo "  Відкрий $URL і завантаж APK для потрібної архітектури."
  echo "  microG уже в образі, тож цей крок — лише оновлення компонентів."
else
  echo "  curl -fL -o /tmp/gapps.zip '$URL'"
  echo "  sha256sum /tmp/gapps.zip   # звір з офіційним оголошенням вендора"
fi
echo
echo "Важливо: власницькі бінари не комітяться — workflow policy.yml блокує"
echo "будь-який .apk/.so/.jar у дереві. Якщо потрібна повністю вільна стека,"
echo "лиши microG і не встановлюй GApps."
