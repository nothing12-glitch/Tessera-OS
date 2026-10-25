#!/usr/bin/env bash
# Перевіряє, чи справді існують файли бази, на які посилається наш шар збірки.
#
# Ідея: «зеленому білду» ми не віримо, поки конфіг може посилатися на файл,
# якого в дереві немає. Саме так і було до 2026-10-24 — build/tessera.mk
# успадковував build/make/target/product/full_x86_64.mk, якого у гілці
# lineage-22.2 не існує (перевірено HTTP-404), а маніфест перелічував два
# репозиторії-пустішки. Жоден чинний CI цього не бачив: образ ніхто не збирав.
#
# Linux-дерево не потрібне: скрипт бере справжній маніфест бази з мережі,
# з'ясовує з нього, у якому репозиторії лежить кожен шлях, і питає сам файл
# HTTP-запитом. У режимі --tree замість мережі обшукує синхронізоване дерево —
# тоді перевіряються й проєкти з AOSP-віддалення.
#
#   check-build-refs.sh                  # мережевий режим (CI, будь-яка ОС)
#   check-build-refs.sh --tree ~/tessera # у синхронізованому дереві
set -uo pipefail

BRANCH="${TESSERA_BASE_BRANCH:-lineage-22.2}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TREE="${2:-}"
[[ "${1:-}" == "--tree" ]] || TREE=""

missing=0
ok()   { echo "[build-refs] ok: $*"; }
fail() { echo "[build-refs] ВАДА: $*"; missing=1; }
skip() { echo "[build-refs] пропущено: $*"; }

# ---------- маніфест бази: текст і пошук проєкта ----------------------------
BASE_XML=""
load_manifest() {
  if [[ -n "$TREE" ]]; then
    local mf="$TREE/.repo/manifests"
    [[ -d "$mf" ]] || { fail "немає $mf — $TREE не є sync-нутим деревом"; return 1; }
    BASE_XML="$(cat "$mf/default.xml" "$mf"/snippets/*.xml 2>/dev/null)"
  else
    BASE_XML=$(curl -sf "https://raw.githubusercontent.com/LineageOS/android/$BRANCH/default.xml" &&
               curl -sf "https://raw.githubusercontent.com/LineageOS/android/$BRANCH/snippets/lineage.xml")
  fi
  [[ -n "$BASE_XML" ]] || { fail "не вдалося прочитати маніфест бази ($BRANCH)"; return 1; }
}

# Довший шлях-проєкт, що є префіксом наданого шляху. Печатє "repo remote proot"
# (repo — ім'я проєкту без організації, як у GitHub-URL).
project_for() {
  local file="$1" dir name remote
  dir="$(dirname "$file")"
  while [[ -n "$dir" && "$dir" != "." ]]; do
    line=$(printf '%s\n' "$BASE_XML" | grep -E "path=\"$dir(\")?[[:space:]]" | head -1)
    if [[ -n "$line" ]]; then
      name=$(printf '%s' "$line" | sed -nE 's/.*name="([^"/]+\/)?([^"]+)".*/\2/p')
      remote=$(printf '%s' "$line" | sed -nE 's/.*remote="([^"]+)".*/\1/p')
      printf '%s %s %s' "${name:-}" "${remote:-github}" "$dir"
      return 0
    fi
    dir="${dir%/*}"
    [[ "$dir" == "/" ]] && break
  done
  return 1
}

# Чи існує файл у базі: 0 — так, 1 — ні, 2 — невідомо (проєкт з AOSP, а дерева немає).
upstream_has() {
  local file="$1" info name remote proot url code
  info=$(project_for "$file") || {
    if [[ -n "$TREE" ]]; then [[ -f "$TREE/$file" ]]; return; fi
    fail "$file — у маніфесті бази ($BRANCH) немає проєкту, до якого належить цей шлях"
    return 1
  }
  read -r name remote proot <<<"$info"
  if [[ "$remote" == "aosp" && -z "$TREE" ]]; then
    skip "$file — проєкт $name з AOSP-віддалення; перевір у режимі --tree"
    return 2
  fi
  if [[ -n "$TREE" ]]; then
    [[ -f "$TREE/$file" ]] && return 0
    fail "$file — у синхронізованому дереві такого файлу немає"
    return 1
  fi
  url="https://raw.githubusercontent.com/LineageOS/$name/$BRANCH/${file#"$proot"/}"
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 "$url")
  [[ "$code" == "200" ]] && return 0
  fail "$file — HTTP $code для ${url#https://raw.githubusercontent.com/}"
  return 1
}

# ---------- наші джерела посилань -------------------------------------------
ours() { printf '%s\n' "${1#vendor/tessera/os/}"; }   # шлях у дереві → шлях у репо

echo "[build-refs] база: LineageOS/android @ $BRANCH"
load_manifest || exit 1

# 1. Успадкування конфігу продукту.
while IFS= read -r target; do
  [[ -z "$target" ]] && continue
  case "$target" in
    vendor/tessera/os/*)
      rel="${target#vendor/tessera/os/}"
      [[ -f "$ROOT/$rel" ]] && ok "успадкування (наше): $target" ||
        fail "успадкування: $target — наш файл, якого немає в репо"
      ;;
    build/*|device/*|vendor/lineage/*|packages/*)
      if upstream_has "$target"; then ok "успадкування: $target"
      elif [[ "$target" == *-if-exists* ]]; then skip "$target — необов'язкове"; fi
      ;;
    *) fail "успадкування: незрозумілий префікс у '$target' — додай його в перевірку" ;;
  esac
done < <(sed -nE 's/.*\$\(call inherit-product(-if-exists)?,[[:space:]]*([^),[:space:]]+)\).*/\2/p' "$ROOT/build/tessera.mk" | sort -u)

# 2. КОЖЕН продукт-пакет має бути оголошений якимось Android.bp — інакше це
#    знову microG/GmsCore-подібна порожня обіцянка (такі рядки ми прибрали).
OUR_MODULES=$(
  find "$ROOT/apps" "$ROOT/overlay/themes" -name Android.bp -print0 2>/dev/null |
    xargs -0 grep -hoE 'name: "[^"]+"' 2>/dev/null | sed -E 's/name: "([^"]+)"/\1/' | sort -u
)
BASE_ONLY=""
while IFS= read -r pkg; do
  [[ -z "$pkg" ]] && continue
  if printf '%s\n' "$OUR_MODULES" | grep -qx -- "$pkg"; then
    ok "модуль оголошений: $pkg"
  else
    BASE_ONLY="$BASE_ONLY $pkg"
  fi
done < <(
  sed -nE '/PRODUCT_PACKAGES[[:space:]]*\+?=/,/[^\\]$/p' "$ROOT/build/tessera.mk" |
    tr -d '\\' | tr ' ' '\n' |
    grep -E '^[A-Za-z][A-Za-z0-9_.-]*$' |
    grep -vE '^(PRODUCT_PACKAGES|filter|out|+=|\$\(PRODUCT_PACKAGES\))$' | sort -u
)

# Модулі, яких у нас немає, має оголосити база. У мережевому режимі імена
# модулів не перевіриш HTTP-запитом (це не шлях до файла), тому вони
# потрапляють у список «не перевірено»; у режимі --tree шукаємо оголошення
# безпосередньо в Android.bp / Android.mk дерева.
if [[ -n "$BASE_ONLY" ]]; then
  if [[ -n "$TREE" ]]; then
    for mod in $BASE_ONLY; do
      if grep -rqE "name: \"$mod\"|name:[[:space:]]*\"$mod\"|LOCAL_MODULE[[:space:]]*:?[[:space:]]*$mod" \
           "$TREE" --include=Android.bp --include=Android.mk 2>/dev/null; then
        ok "модуль бази: $mod"
      else
        fail "PRODUCT_PACKAGES: '$mod' не оголошений у дереві бази — збірка впаде або пакет мовчки зникне"
      fi
    done
  else
    skip "модулі бази: $BASE_ONLY — HTTP-перевірка імен модулів неможлива; прогони --tree <дерево>"
  fi
fi

# 3. Каталог build-time оверлеїв.
overlay_dir=$(sed -nE 's/^DEVICE_PACKAGE_OVERLAYS[[:space:]]*\+=[[:space:]]*([^[:space:]]+).*/\1/p' "$ROOT/build/tessera.mk" | head -1)
if [[ -n "$overlay_dir" ]]; then
  case "$overlay_dir" in
    vendor/tessera/os/*)
      [[ -d "$ROOT/${overlay_dir#vendor/tessera/os/}" ]] && ok "оверлеї: $overlay_dir" ||
        fail "оверлеї: немає каталогу $overlay_dir" ;;
    *) upstream_has "$overlay_dir/manifest-package/Android.bp" >/dev/null &&
         ok "оверлеї: $overlay_dir (у базі)" ;;
  esac
fi

# 4. Кожен linkfile маніфеста посилається на файл, що є в репо.
while IFS= read -r src; do
  [[ -z "$src" ]] && continue
  [[ -f "$ROOT/$src" ]] && ok "linkfile: $src" || fail "linkfile: немає файлу $src"
done < <(sed -nE 's/.*src="([^"]+)".*/\1/p' "$ROOT/manifest/tessera.xml")

# 5. Конфіг платформи, який ми свідомо НЕ пишемо, але на який посилаємось.
upstream_has "device/generic/goldfish/board/emu64x/BoardConfig.mk" >/dev/null &&
  ok "board: device/generic/goldfish/board/emu64x/BoardConfig.mk (базовий)"

# 6. Шляхи всередині РЕПО, які цитують документи й сайт. Той самий клас помилки,
#    що й мертві посилання на файли бази, але всередині нашого дерева: у docs/EMULATOR.md
#    роки стояло «tools/emulator-smoke.sh перевіряє», хоча ані tools/, ані самого
#    скрипту в репо ніколи не було. Читач іде за посиланням і знаходить порожнечу.
OUR_DOCS=$(find "$ROOT/docs" -name '*.md'; ls "$ROOT/README.md" "$ROOT/site/index.html" 2>/dev/null)
# Беремо цілий шляхоподібний токен, а потім відсіюємо за першою ланкою.
# Короткий префікс у самому взірці ловив би «config/common.mk» із
# «vendor/lineage/config/common.mk» — це файл бази, а не наш мертвий шлях.
OUR_REFS=$(printf '%s\n' "$OUR_DOCS" | xargs -r grep -ohE \
  '[a-z][a-z0-9._-]*(/[A-Za-z0-9._-]+)*\.(sh|ps1|md|json|xml|html|js|mk|bp|py|txt)' \
  2>/dev/null |
  grep -E '^(tools|docs|config|build|manifest|overlay|overlay-packages|brand|apps|site)/' |
  grep -vE '^build/(make|soong)/' | sort -u)
if [[ -z "$OUR_REFS" ]]; then
  skip "посилань на наші файли в документах немає"
fi
while IFS= read -r ref; do
  [[ -z "$ref" ]] && continue
  if [[ -e "$ROOT/$ref" ]]; then ok "документ посилається на: $ref"; continue; fi
  # docs/GAPPS.md пише шлях від свого каталогу — «GAPPS.md», а не «docs/GAPPS.md»
  if [[ "$ref" != */* && -e "$ROOT/docs/$ref" ]]; then
    ok "документ посилається на: docs/$ref"
  elif grep -rqF --exclude=check-build-refs.sh -- "$(basename "$ref")" "$ROOT/build/scripts" 2>/dev/null; then
    # Файл створює наш скрипт під час запуску, тому в git його справді немає
    # (саме так: config/gapps/profile.json пише package-gapps.sh). Себе з пошуку
    # виключаємо: цей коментар теж згадує назву, і рядок «генерує» брехав би.
    ok "документ посилається на: $ref — генерує $(grep -rlF --exclude=check-build-refs.sh -- "$(basename "$ref")" "$ROOT/build/scripts" | head -1 | sed "s|$ROOT/||")"
  else
    fail "документ посилається на $ref — такий шлях у репо відсутній"
  fi
done <<< "$OUR_REFS"

echo
if [[ "$missing" == "0" ]]; then
  echo "[build-refs] усі посилання шару збірки ведуть на справжні файли."
else
  echo "[build-refs] є мертві посилання — на білд-боксі збірка провалиться."
fi
exit "$missing"
