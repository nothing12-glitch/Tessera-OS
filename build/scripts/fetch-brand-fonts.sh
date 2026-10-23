#!/usr/bin/env bash
# Кладає брендові TTF-шрифти в brand/fonts/. У git їх немає: woff2-підмножини
# для сайту ми тримаємо в репо (вони маленькі й мають зафіксовану суму), а
# повні варіативні TTF — це вже робочі файли для збірки застосунків і образу.
#
# Використання:
#   build/scripts/fetch-brand-fonts.sh          # завантажити й перевірити
#   TESSERA_FONT_DIR=/tmp/шрифти …              # кидати не в brand/fonts
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="${TESSERA_FONT_DIR:-$REPO/brand/fonts}"

# <файл>|<sha256>|<URL>
PINS='
Manrope-var.ttf|3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6|https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf
Unbounded-var.ttf|323b511be380c8d474ef030686b71aedde501f8d9cd46da558b7c40454372c3f|https://raw.githubusercontent.com/google/fonts/main/ofl/unbounded/Unbounded%5Bwght%5D.ttf
'

sha_of() { sha256sum "$1" | cut -d" " -f1; }

mkdir -p "$OUT"
changed=0

while IFS='|' read -r file want url; do
  [[ -n "${file// }" ]] || continue
  target="$OUT/$file"
  if [[ -f "$target" ]] && [[ "$(sha_of "$target")" == "$want" ]]; then
    echo "= $file — вже на місці, сума збігається"
    continue
  fi
  echo "↓ $file"
  tmp="$target.part"
  curl -fsSL --retry 3 -m 120 "$url" -o "$tmp"
  got="$(sha_of "$tmp")"
  if [[ "$got" != "$want" ]]; then
    rm -f "$tmp"
    echo "ПОМИЛКА: $file має іншу суму." >&2
    echo "  очікували $want" >&2
    echo "  отримали  $got" >&2
    echo "Якщо шрифт навмисно оновили — оновіть PINS тут і brand/TYPE.md." >&2
    exit 1
  fi
  mv "$tmp" "$target"
  echo "✓ $file  $(wc -c < "$target") байт"
  changed=$((changed + 1))
done <<< "$PINS"

# Ліцензії лишаються в репо: без них шрифт у продукті роздавати не можна.
for lic in manrope-OFL.txt unbounded-OFL.txt; do
  [[ -s "$OUT/$lic" ]] || { echo "ПОМИЛКА: немає ліцензії $lic" >&2; exit 1; }
  grep -qi 'SIL Open Font License' "$OUT/$lic" || {
    echo "ПОМИЛКА: $lic не схожа на OFL" >&2; exit 1; }
done

echo
if (( changed )); then
  echo "Готово: свіжих файлів — $changed. далі: build/scripts/check-brand.sh"
else
  echo "Готово: нічого завантажувати не треба було."
fi
