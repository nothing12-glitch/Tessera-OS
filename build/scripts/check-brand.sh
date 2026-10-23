#!/usr/bin/env bash
# Перевірка айдентики: шрифти, шпалери, знак і те, як сайт усе це підключує.
#
# Кожна перевірка тут — про щось, що ламається тихо. Заміна woff2 у Google
# Fonts, нова версія сімейства, інший радіус плиток у SVG, порожній квадратик
# замість «ї» — усе це не зупиняє збірку, тому зупиняємо тут.
#
# Вихід: 0 — усе гаразд, 1 — щось не так, 2 — не вистачає файлів, які треба
# спершу дістати (fetch-brand-fonts.sh).
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO"

ok=0; bad=0; skip=0
pass() { echo "  ok   $1"; ok=$((ok + 1)); }
fail() { echo "  FAIL $1"; bad=$((bad + 1)); }
skipmsg() { echo "  пропуск $1"; skip=$((skip + 1)); }
sha() { sha256sum "$1" | cut -d" " -f1; }

# <шлях>|<очікувана сума>
FONTS='
brand/fonts/manrope-latin.woff2|a30ddcd349703aff7464c34bef3fffdff405ee50c113440d7c8693c02d210972
brand/fonts/manrope-cyrillic.woff2|c268b459a9329e59fecf39a17618efd44c71735532048d60b12aab76a8c14914
brand/fonts/unbounded-latin.woff2|22f9b928aa3928a896340422b54d690fbb5875c30eae4c106f029c61f2289fd6
brand/fonts/unbounded-cyrillic.woff2|e30bf6a6063b6a47232bbd657e24c7cf4fdaf76fc2c50d92d9ee63e2ad2087cf
site/fonts/manrope-latin.woff2|a30ddcd349703aff7464c34bef3fffdff405ee50c113440d7c8693c02d210972
site/fonts/manrope-cyrillic.woff2|c268b459a9329e59fecf39a17618efd44c71735532048d60b12aab76a8c14914
site/fonts/unbounded-latin.woff2|22f9b928aa3928a896340422b54d690fbb5875c30eae4c106f029c61f2289fd6
site/fonts/unbounded-cyrillic.woff2|e30bf6a6063b6a47232bbd657e24c7cf4fdaf76fc2c50d92d9ee63e2ad2087cf
'

WALLPAPERS='
brand/wallpapers/tessera-nightfall.jpg|736b1e7cfc9ee73206e7259e28e2193863f9cbd33d1dbb40e0c91855fa6c0bb8
brand/wallpapers/tessera-ember.jpg|b8981bcd9c72c11bc18b5bf1603144650de1aa0e0ac86f5aaf17b5b62ac065cb
brand/wallpapers/tessera-verdigris.jpg|f7b0552a158f3ffd3c54f83f8281cf17a99f193e16e25c7af5919beb4688a26e
'

echo "шрифти (суми офіційних підмножин)"
while IFS='|' read -r file want; do
  [[ -n "${file// }" ]] || continue
  if [[ ! -f "$file" ]]; then fail "$file — файла немає в репо"; continue; fi
  [[ "$(sha "$file")" == "$want" ]] && pass "$file" || fail "$file — сума не збіглась (підмножина інша, ніж записана в brand/TYPE.md)"
done <<< "$FONTS"

echo "шпалери"
while IFS='|' read -r file want; do
  [[ -n "${file// }" ]] || continue
  if [[ ! -f "$file" ]]; then fail "$file — файла немає"; continue; fi
  [[ "$(sha "$file")" == "$want" ]] && pass "$file" || fail "$file — вміст змінився"
done <<< "$WALLPAPERS"

echo "ліцензії"
for lic in brand/fonts/manrope-OFL.txt brand/fonts/unbounded-OFL.txt; do
  [[ -s "$lic" ]] || { fail "$lic — немає тексту ліцензії"; continue; }
  grep -qi 'SIL Open Font License' "$lic" && pass "$lic = OFL 1.1" || fail "$lic — це не OFL"
done

echo "покриття українських літер"
ttf_present=0
for ttf in brand/fonts/Manrope-var.ttf brand/fonts/Unbounded-var.ttf; do
  [[ -f "$ttf" ]] || continue
  ttf_present=1
  if out="$(node build/scripts/brand-font-check.js "$ttf" 2>&1)"; then
    pass "$(basename "$ttf"): Ґ ґ є і ї на місці"
  else
    fail "$(basename "$ttf"): $(echo "$out" | grep '^НЕМАЄ' | tr '\n' ' ')"
  fi
done
if (( ! ttf_present )); then
  skipmsg "TTF немає — дістаньте build/scripts/fetch-brand-fonts.sh, потім перезапустіть"
fi

echo "знак: SVG і VectorDrawable мають збігатися"
svg="brand/logo/tessera-mark.svg"
vd="brand/android/tessera_mark.xml"
if [[ -f "$svg" && -f "$vd" ]]; then
  # Куті повороту плитки — найчутливіше місце: один інший координат, і знак
  # стає іншим знаком. Порівнюємо саме їх, витягнувши лише цифри.
  diamond_svg="$(grep -o 'M70 55 L85 70 L70 85 L55 70 Z' "$svg" | tr -dc '0-9 ')"
  diamond_vd="$(grep -o 'M70,55 L85,70 L70,85 L55,70 Z' "$vd" | tr -dc '0-9, ' | tr ',' ' ')"
  [[ -n "$diamond_svg" && "$diamond_svg" == "$diamond_vd" ]] \
    && pass "повернута плитка — однакова геометрія" \
    || fail "повернута плитка різниться: svg='$diamond_svg' vector='$diamond_vd'"
  for c in 3350C8 8FB0FF C8D8FF E8B23A; do
    s_ok=0; v_ok=0
    grep -qi "#$c" "$svg" && s_ok=1
    grep -qi "#$c" "$vd" && v_ok=1
    (( s_ok && v_ok )) && pass "марковий колор #$c в обох форматах" || fail "#$c: svg=$s_ok vector=$v_ok"
  done
  for f in brand/logo/tessera-mark-mono.svg brand/logo/tessera-lockup.svg brand/logo/tessera-favicon.svg; do
    [[ -s "$f" ]] && pass "$(basename "$f")" || fail "$(basename "$f") — порожній або немає"
  done
else
  fail "немає $svg або $vd"
fi

echo "сайт лишається самохостованим"
site="site/index.html"
if [[ -s "$site" ]]; then
  grep -q 'fonts.gstatic.com' "$site" && fail "сайт тягне шрифт з CDN" || pass "жодного зовнішнього шрифтового CDN"
  grep -q 'src="fonts/' "$site" || grep -q "url('fonts/" "$site" || grep -q 'url("fonts/' "$site" \
    && pass "шрифт підключений локально" || fail "у сайті немає локального @font-face на site/fonts/"
  grep -q 'wallpapers/' "$site" && pass "шпалери підключені локально" || fail "у сайті немає посилань на site/wallpapers/"
else
  fail "$site порожній"
fi

echo
echo "разом: ok=$ok  пропусків=$skip  проблем=$bad"
if (( bad )); then exit 1; fi
if (( skip )); then
  echo "увага: частина перевірок не запустилась (див. «пропуск» вище)."
  exit 2
fi
echo "Усі перевірки айдентики пройдені."
