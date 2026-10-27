#!/usr/bin/env bash
# Гарант того, що документація про дозволи не бреше маніфесту, — і що екранна
# копія не обіцяє того, що вже виміряно як хибне. Друга перевірка додана 27.10
# після того, як макет «Налаштування» на сайті стояв з рядком «Яскравість —
# Адаптивна» того самого дня, коли вимір дав `mAutoBrightnessAvailable= false`.
#
# Чому він з'явився саме 27.10.2026: в `apps/welcome/README.md` три кроки
# (навігація, профіль живлення, політика MAC) стояли в тому самому абзаці, де
# мова, і той абзац твердив, що всім їм «бракує дозволу». Насправді
# `WRITE_SECURE_SETTINGS` уже видано з allowlist (прогін
# `check-welcome-live.sh install` — 5/5), тобто документ описував межу, якої в
# образі не було. Маніфест і дозвільний список читаються машинно; речення в
# прозі — ні. Цей скрипт і є переклад другого першими: витягує твердження двох
# конкретних форм і звіряє їх із двома конкретними файлами.
#
# Чого він НЕ перевіряє — і це не недогляд, а межа:
#   - тверджень про область дії дозволу («`WRITE_SECURE_SETTINGS` не покриває
#     мову») — їх не вивести з тексту, лише з живого виклику; для цього є
#     `check-welcome-live.sh theme|walk`, а його результат пишеться в документи
#     фактом, а не враженням;
#   - того, чий дозвіл є безпечним, — це політика, а не рядки.
#
#   bash build/scripts/check-doc-claims.sh              # прогін по репо
#   bash build/scripts/check-doc-claims.sh --selftest   # перевірка, що падає
#   TESSERA_CLAIM_FILES="а.md б.md" bash build/scripts/check-doc-claims.sh
#       ^ прогін по ownих файлах — саме так ловиться регресія: справжній
#         маніфест + речення, яке йому бреше.
#
# --selftest не підробка: він будує тимчасовий fixture з однією свідомою
# брехнею і вимагає, щоб скрипт впав саме на ній. Перевірка, яка не вміє
# падати, нічого не перевіряє.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

MANIFEST="${TESSERA_MANIFEST:-$ROOT/apps/welcome/app/src/main/AndroidManifest.xml}"
ALLOWLIST="${TESSERA_ALLOWLIST:-$ROOT/apps/welcome/permissions/privapp-permissions-tessera-welcome.xml}"

# Друга гарантія в тому самому скрипті: копія, яка йде на екран користувача, не
# може обіцяти те, що вже виміряно як хибне. Це не перевірка стилю: за кожним
# рядком таблиці стоїть вимір і документ, де він описаний.
COPY_DEFAULT=("$ROOT/site/index.html")
for s in "$ROOT"/apps/welcome/app/src/main/res/values*/strings.xml \
         "$ROOT"/apps/halo/app/src/main/res/values*/strings.xml; do
  [[ -f "$s" ]] && COPY_DEFAULT+=("$s")
done

PROMISES=(
  'Адаптивна|автояскравість на нашому образі не діє: конфіг дисплея не має кривої люкс→яскравість — docs/FEASIBILITY.md §7, виміряно 27.10'
  'Динамічна, з кольору шпалер|тему перемикують вручну, «Авто» свідомо немає — опитування №2 і крок 4 вітальні кажуть саме це'
)

missing=0
fail() { echo "[doc-claims] $*"; missing=1; }
ok()   { echo "[doc-claims] ok: $*"; }
say()  { echo "[doc-claims] $*"; }

for f in "$MANIFEST" "$ALLOWLIST"; do
  [[ -f "$f" ]] || { echo "[doc-claims] немає $f — на чому тоді звіряти?"; exit 1; }
done

# Імена дозволів, які справді є в образі: маніфест оголошує, allowlist видає.
# Шукаємо лише рядок-значення `"android.permission.X"`, бо атрибути у двох
# файлах різні: у маніфесті `android:name=`, у дозвільному списку просто `name=`.
declared() {
  grep -oE '"android\.permission\.[A-Z0-9_]+"' "$1" \
    | sed -E 's/.*android\.permission\.([A-Z0-9_]+)".*/\1/' | sort -u
}
HELD="$(declared "$MANIFEST")"
LISTED="$(declared "$ALLOWLIST")"
has() { printf '%s\n' "$2" | grep -qx "$1"; }

# Два якорі тверджень. Файл перед пошуком склеюється в один рядок: речення в
# прозі переноситься через рядок, і поелементний `grep` його б не побачив.
# Слово «у маніфесті» саме по собі якорем НЕ є: речення «заборона `LAUNCHER` у
# маніфесті» означає протилежне, і перша версія скрипту ловила саме її. Тому
# лише явні звороти, що обіцяють наявність.
ABSENT_RE='`[A-Za-z.]*[A-Z][A-Z0-9_]{4,}`[^`]{0,40}(у модуля|у цього модуля) немає|немає дозволу `[A-Z][A-Z0-9_]{4,}`|`[A-Z][A-Z0-9_]{4,}`, якого в модуля немає'
HELD_RE='`[A-Za-z.]*[A-Z][A-Z0-9_]{4,}`[`, :[:space:]]{0,4}(видано з allowlist|видано з дозвільного|оголошений у маніфесті)|[A-Z][A-Z0-9_]{4,}: granted=true|[A-Z][A-Z0-9_]{4,} видано з allowlist|`[A-Z][A-Z0-9_]{4,}`[^`]{0,20}у маніфест потрапив'

FILES_DEFAULT=("$ROOT/apps/welcome/README.md" "$ROOT/site/index.html")
for d in "$ROOT"/docs/*.md; do FILES_DEFAULT+=("$d"); done

if [[ "${1:-}" == "--selftest" ]]; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  cat > "$TMP/manifest" <<'XML'
<manifest><uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" /></manifest>
XML
  cat > "$TMP/allowlist" <<'XML'
<permissions><privapp-permissions package="os.tessera.welcome">
<permission name="android.permission.WRITE_SECURE_SETTINGS" /></privapp-permissions></permissions>
XML
  mkdir -p "$TMP/docs"
  # Чиста «екранна копія» для герметичності: інакше усі три нижчі прогони
  # перевіряли б ще й справжній репозиторій.
  echo 'Яскравість — вручну. Тема — вручну.' > "$TMP/copy-ok.txt"

  # 1) чесний fixture — має пройти
  echo 'Дозвіл `WRITE_SECURE_SETTINGS` видано з allowlist, а `CHANGE_CONFIGURATION` у модуля немає.' > "$TMP/docs/ok.md"
  TESSERA_MANIFEST="$TMP/manifest" TESSERA_ALLOWLIST="$TMP/allowlist" \
    TESSERA_COPY_FILES="$TMP/copy-ok.txt" \
    TESSERA_CLAIM_FILES="$TMP/docs/ok.md" bash "${BASH_SOURCE[0]}" >/dev/null 2>&1 \
    && ok "чесний fixture → rc=0" \
    || fail "чесний fixture не пройшов — скрипт занадто суворий"

  # 2) брехливий fixture — має впасти саме на брехні
  echo 'Дозвіл `WRITE_SECURE_SETTINGS` у модуля немає.' > "$TMP/docs/lie.md"
  out="$(TESSERA_MANIFEST="$TMP/manifest" TESSERA_ALLOWLIST="$TMP/allowlist" \
    TESSERA_COPY_FILES="$TMP/copy-ok.txt" \
    TESSERA_CLAIM_FILES="$TMP/docs/lie.md" bash "${BASH_SOURCE[0]}" 2>&1)"
  rc=$?
  if (( rc != 0 )) && grep -q "WRITE_SECURE_SETTINGS" <<<"$out"; then
    ok "брехливий fixture → rc=$rc, ім'я дозволу названо в повідомленні"
  else
    fail "брехливий fixture НЕ впав (rc=$rc) — гарант порожній:
$out"
  fi

  # 3) другий бік: твердження «видано з allowlist» без запису в allowlist
  echo '<permissions/>' > "$TMP/empty-allowlist"
  out2="$(TESSERA_MANIFEST="$TMP/manifest" TESSERA_ALLOWLIST="$TMP/empty-allowlist" \
    TESSERA_COPY_FILES="$TMP/copy-ok.txt" \
    TESSERA_CLAIM_FILES="$TMP/docs/ok.md" bash "${BASH_SOURCE[0]}" 2>&1)"
  rc2=$?
  if (( rc2 != 0 )) && grep -q "allowlist" <<<"$out2"; then
    ok "твердження про allowlist без allowlist → rc=$rc2"
  else
    fail "зв'язок «видано з allowlist» ↔ файл allowlist не перевіряється (rc=$rc2):
$out2"
  fi

  # 4) екранна копія з виміряно хибною обіцянкою — має впасти й назвати причину
  echo "<div>Яскравість</div><small>Адаптивна</small>" > "$TMP/copy-lie.txt"
  out3="$(TESSERA_MANIFEST="$TMP/manifest" TESSERA_ALLOWLIST="$TMP/allowlist" \
    TESSERA_COPY_FILES="$TMP/copy-lie.txt" \
    TESSERA_CLAIM_FILES="$TMP/docs/ok.md" bash "${BASH_SOURCE[0]}" 2>&1)"
  rc3=$?
  if (( rc3 != 0 )) && grep -q "автояскравість" <<<"$out3"; then
    ok "копія з «Адаптивна» → rc=$rc3, в повідомленні є чому це хибно"
  else
    fail "гарант екранної копії не впав на свідомій обіцянці (rc=$rc3):
$out3"
  fi

  echo "[doc-claims] --selftest завершено."
  exit "$missing"
fi

# Файли для прогону: список з TESSERA_CLAIM_FILES (потрібно selftest) або репо.
if [[ -n "${TESSERA_CLAIM_FILES:-}" ]]; then
  read -r -a FILES <<<"$TESSERA_CLAIM_FILES"
else
  FILES=()
  for f in "${FILES_DEFAULT[@]}"; do [[ -f "$f" ]] && FILES+=("$f"); done
fi

say "звіряємо з:"
say "  маніфест оголошує: $(printf '%s ' $HELD)"
say "  allowlist видає:   $(printf '%s ' $LISTED)"

total_absent=0
total_held=0
total_mentions=0

for f in "${FILES[@]}"; do
  [[ -f "$f" ]] || continue
  flat="$(tr '\n' ' ' < "$f")"
  rel="${f#"$ROOT"/}"

  absent_names="$(grep -oE "$ABSENT_RE" <<<"$flat" | grep -oE '[A-Z][A-Z0-9_]{4,}' | sort -u || true)"
  held_names="$(grep -oE "$HELD_RE" <<<"$flat" | grep -oE '[A-Z][A-Z0-9_]{4,}' | sort -u || true)"

  while read -r name; do
    [[ -n "$name" ]] || continue
    total_absent=$((total_absent + 1))
    if has "$name" "$HELD"; then
      fail "$rel: твердження «$name у модуля немає» суперечить маніфесту. Рядки згадки:"
      grep -nF "$name" "$f" | head -3 | sed 's/^/            /'
    else
      ok "$rel: $name справді не оголошений — маніфест чистий"
    fi
  done <<<"$absent_names"

  while read -r name; do
    [[ -n "$name" ]] || continue
    total_held=$((total_held + 1))
    if ! has "$name" "$HELD"; then
      fail "$rel: твердження «$name видано» суперечить маніфесту — його там немає"
    elif ! has "$name" "$LISTED"; then
      fail "$rel: «$name видано з allowlist», а в allowlist-файлі запису немає — на enforce-політиці такий пакунок не завантажиться"
    else
      ok "$rel: $name є і в маніфесті, і в allowlist"
    fi
  done <<<"$held_names"

  # Інформаційний лічильник: лише токени, схожі на ім'я дозволу за префіксом,
  # бо `MAC`, `README` чи `SYSTEM` у тексті — не дозвіл, а абревіатура.
  mentions="$(grep -oE '(android\.permission\.)?[A-Z][A-Z0-9_]{4,}' <<<"$flat" \
    | grep -cE '^(WRITE|MODIFY|CHANGE|VIBRATE|QUERY|INTERNET|POST|READ|CAMERA|BLUETOOTH|ACCESS|FOREGROUND|MANAGE)' || true)"
  total_mentions=$((total_mentions + mentions))
done

# --- друга гарантія: екранна копія не обіцяє виміряно хибного ---------------
if [[ -n "${TESSERA_COPY_FILES:-}" ]]; then
  read -r -a COPY <<<"$TESSERA_COPY_FILES"
else
  COPY=()
  for f in "${COPY_DEFAULT[@]}"; do [[ -f "$f" ]] && COPY+=("$f"); done
fi

total_promise=0
for f in "${COPY[@]}"; do
  [[ -f "$f" ]] || continue
  rel="${f#"$ROOT"/}"
  for p in "${PROMISES[@]}"; do
    needle="${p%%|*}"
    reason="${p#*|}"
    if grep -qF -- "$needle" "$f"; then
      total_promise=$((total_promise + 1))
      fail "$rel: копія обіцяє «$needle», а це вже виміряно як хибне — $reason"
      grep -nF -- "$needle" "$f" | head -2 | sed 's/^/            /'
    fi
  done
done
(( total_promise == 0 )) && ok "екранна копія: файлів ${#COPY[@]}, чорний список ${#PROMISES[@]}, збігів 0"

say "покрито якорями: «немає» — $total_absent, «видано» — $total_held"
rest=$((total_mentions - total_absent - total_held))
say "усього згадок дозволів у тексті: $total_mentions; з них поза цими двома якорями (область дії, історія, цитати з логу): $((rest > 0 ? rest : 0))"

if (( missing == 0 )); then
  echo "[doc-claims] Усі перевірки пройдені."
  exit 0
fi
echo "[doc-claims] Є суперечність між документацією і маніфестом — див. вище."
exit 1
