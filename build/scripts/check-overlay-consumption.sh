#!/usr/bin/env bash
# Перевіряє, чи кожне ім'я, яке ми підміняємо, хтось у базі взагалі ЧИТАЄ.
#
# Навіщо. check-overlay-names.sh доводить, що ім'я існує. Але 27.10 дерево
# показало іншу поломку того ж класу: `opaque_scrim` і `dual_shade_scrim_alpha`
# оголошені в SystemUI, і в усьому frameworks/base їх не читає жоден рядок коду.
# Оверлей на таке ім'я збирається, проходить обидва попередні гаранті й не змінює
# на екрані нічого. Цей скрипт — друга половина того ж питання.
#
# Потрібне локальне дерево бази (той самий ~/tessera, що й для збірки образу):
#   TESSERA_TREE=~/tessera build/scripts/check-overlay-consumption.sh
#   TESSERA_TREE=~/tessera build/scripts/check-overlay-consumption.sh --selftest
#
# Вихід 0 — кожне ім'я має хоч один зчитувач. Вихід 1 — є мертві підміни або
# немає дерева (обидва варіанти означають «не перевірено», тому не 0).
# У CI не запускається: GitHub-раннер дерева бази не має.
set -uo pipefail

TREE="${TESSERA_TREE:-$HOME/tessera}"
SELF="${BASH_SOURCE[0]:-$0}"
if [[ -f "$SELF" ]]; then
  ROOT="$(cd "$(dirname "$SELF")/../.." && pwd)"
else
  ROOT="$PWD"
fi

if [[ ! -d "$TREE/frameworks/base" ]]; then
  echo "[consumption] дерева бази немає за $TREE/frameworks/base"
  echo "[consumption] перевірку НЕ виконано. Вкажіть TESSERA_TREE=/шлях/до/ ~/tessera"
  exit 1
fi

# Де шукати зчитувачів для кожного цільового пакету. Показуємо і сам
# пакет (його власні res/src), і весь frameworks/base: ресурси `android`
# читаються звідусіль, включно з приватними посиланнями @*android:….
roots_for() {
  case "$1" in
    android)
      echo "$TREE/frameworks/base"
      echo "$TREE/packages/apps" ;;
    com.android.settings)
      echo "$TREE/packages/apps/Settings"
      echo "$TREE/frameworks/base/packages/SettingsLib" ;;
    com.android.systemui)
      echo "$TREE/frameworks/base/packages/SystemUI" ;;
    com.android.launcher3)
      echo "$TREE/packages/apps/Trebuchet" ;;
  esac
}

# Правило grep 3.11, виміряного на цьому дереві 27.10 (матриця на чотирьох
# файлах a.bp/a.mk/a.txt/a.xml):
#   · кілька --include в одному виклику = ОБ'ЄДНАННЯ, працює;
#   · --include=… --exclude=…  = працює;
#   · --exclude=… ПЕРЕД --include=… = grep мовчки ЗʼїДАЄ --include і грає по
#     всіх файлах. Саме так прохід 27.10 раптом нарахував «≥18» там, де перед
#     тим було 12: xml-пошук почав читати .java й .kt.
# Тому єдиний глоб у виклику — --include, а зайві шари (оголошення у values/,
# lint-базейлін) зрізаємо пост-фільтром, не --exclude.
DIR_EXCLUDES=(--exclude-dir=.repo --exclude-dir=out --exclude-dir=prebuilts
              --exclude-dir=tmp --exclude-dir=.git)

# Lint-базейліни (packages/apps/Settings/color-check-baseline.xml) — це звіт:
# у ньому чужі рядки процитовані як errorLine1. Такий «зчитувач» не означає,
# що ресурс хтось читає, — лише що lint колись дивився на той рядок.
NOISE='/res/values[a-zA-Z-]*/|baseline'

# Скільки файлів максимум витягуємо з одного пошуку. Коли вперся у стелю,
# число нижче вже не точна кількість — тоді друкуємо «≥».
HITS=3

# Імена з їхнім типом беремо з НАШИХ оверлеїв: <color name="x"> → color.
own_names() {
  local theme="$1"
  find "$theme/res" -path '*/values*' -name '*.xml' \
    -not -name 'local_*' -not -name 'public.xml' \
    -exec grep -hoP '<(color|dimen|integer|bool|string|item)[^>]*\bname="\K[^"]+' {} + 2>/dev/null |
    grep -vE '^(Theme\.|.*shapeAppearance)' | sed 's/^android://' | sort -u
}

own_type_of() {
  local theme="$1" n="$2"
  grep -rhoP "<\K(color|dimen|integer|bool|string)(?=[^>]*\bname=\"$n\")" \
    "$theme/res" 2>/dev/null | head -1
}

# Файли-зчитувачі для імені $2 типу $3 у цільовому пакету $1. Результат —
# у глобальному READERS (масив), стеля позначає SAT=1. Специально без
# командної підстановки: `x=$(readers_for …)` запускає функцію в підоболонці,
# і SAT у матері губиться — перший прогін 27.10 друкував «12 файл(ів)» без «≥»
# саме тому.
READERS=(); SAT=0
readers_for() {
  local target="$1" n="$2" t="$3" r raw h acc=""
  READERS=(); SAT=0
  local -a rs; mapfile -t rs < <(roots_for "$target")
  local code_re="R\\.(attr|color|dimen|integer|bool|string)\\.$n\b|R\\.styleable\\.[A-Za-z0-9_]+_${n}\\b"
  for r in "${rs[@]}"; do
    [[ -d "$r" ]] || continue
    # 1) код: R.<type>.<name>, включно з android./com.android.internal.
    #    префіксами та згенерованими styleable-індексами. .java і .kt — одним
    #    викликом: кілька --include grep 3.11 обʼєднує справді (виміряно).
    raw=$(grep -rl "${DIR_EXCLUDES[@]}" --include=*.java --include=*.kt \
              -E "$code_re" "$r" 2>/dev/null | head -"$HITS")
    [[ -n "$raw" ]] && acc+="$raw"$'\n'
    [[ $(printf '%s\n' "$raw" | grep -c .) == "$HITS" ]] && SAT=1
    # 2) xml-посилання: @<type>/<name>, @android:<type>/…, @*android:…, ?attr/…,
    #    і <item name="<name>"> в чужому стилі (те саме застосовування атрибута).
    #    Стелю рахуємо ДО пост-фільтра: інакше «2 файл(ів)» після відрізання
    #    базейліна виглядало б точною кількістю, хоча за стелею їх могло бути 50.
    raw=$(grep -rl "${DIR_EXCLUDES[@]}" --include=*.xml \
              -e "@\*$t/$n\b" -e "@[a-z0-9.]*:$t/$n\b" -e "@$t/$n\b" \
              -e "?attr/$n\b" -E -e "<item name=\"$n\"" "$r" 2>/dev/null |
          head -"$HITS")
    [[ $(printf '%s\n' "$raw" | grep -c .) == "$HITS" ]] && SAT=1
    h=$(printf '%s\n' "$raw" | grep -vE "$NOISE" | head -"$HITS")
    [[ -n "$h" ]] && acc+="$h"$'\n'
  done
  mapfile -t READERS < <(printf '%s' "$acc" | sort -u | grep . || true)
  return 0
}

# Само-тест. Гарант, який завжди каже «ok», нічого не вартий: він може
# мовчки зчитувати порожньо. Тому перевіряємо обидва напрямки на іменах,
# виміряних руками на цьому ж дереві 27.10:
#   мертві — opaque_scrim (config.xml:1006) і dual_shade_scrim_alpha (:1008):
#           оголошені в SystemUI, а в усьому frameworks/base зчитувачів нуль;
#   живий — config_useRoundIcon: його читає PackageParser.java.
# Якщо гарант не бачить перших або другого — він не валідний, і краще дізнатись
# про це тут, ніж довіряти його «ok» у решті перевірок.
if [[ "${1:-}" == "--selftest" ]]; then
  fail=0
  while read -r tgt nm ty; do
    readers_for "$tgt" "$nm" "$ty"
    if [[ ${#READERS[@]} -gt 0 ]]; then
      echo "[selftest] ПАМОЛКА: $nm нібито має зчитувачів (${READERS[0]}) — пошук занадто широкий"
      fail=1
    else
      echo "[selftest] ok: $nm мертве, і гарант це бачить"
    fi
  done <<'EOF'
com.android.systemui opaque_scrim color
com.android.systemui dual_shade_scrim_alpha dimen
EOF
  readers_for android config_useRoundIcon bool
  if [[ ${#READERS[@]} -eq 0 ]]; then
    echo "[selftest] ПАМОЛКА: config_useRoundIcon нібито не читає ніхто — пошук занадто вузький"
    fail=1
  else
    echo "[selftest] ok: config_useRoundIcon живе (${READERS[0]})"
  fi
  exit $fail
fi

bad=0; checked=0
for theme in "$ROOT"/overlay/themes/*/; do
  tag="$(basename "${theme%/}")"
  target="$(grep -oP 'android:targetPackage="\K[^"]+' "$theme/AndroidManifest.xml" || true)"
  [[ -z "$target" ]] && { echo "[$tag] НЕМАЄ targetPackage — пропущено"; continue; }
  mapfile -t roots < <(roots_for "$target")
  [[ ${#roots[@]} -eq 0 ]] && { echo "[$tag] невідома ціль $target — додай мапу"; bad=1; continue; }

  for n in $(own_names "$theme"); do
    checked=$((checked + 1))
    t="$(own_type_of "$theme" "$n")"
    [[ -z "$t" ]] && t="item"
    # Атрибути теми (`<item name="…">` усередині `<style>`) у нас теж виходять
    # type=item, а читаються вони зовсім іншими способами: R.attr.<n>,
    # R.styleable.Window_<n> і `<item name="<n>">` у чиємусь стилі. Тому
    # шукаємо одразу всі форми — інакше windowLightStatusBar (читається в
    # PhoneWindow.java:2672) виглядав би «мертвим».
    readers_for "$target" "$n" "$t"
    count=${#READERS[@]}
    if [[ "$count" == "0" ]]; then
      echo "[$tag → $target] МЕРТВА ПІДМІНА: $n ($t) — ім'я існує, але в $target його ніхто не читає"
      bad=1
    else
      echo "[$tag → $target] ok: $n ($t) читають $([[ $SAT -eq 1 ]] && echo '≥')$count файл(ів), напр. ${READERS[0]#"$TREE"/}"
    fi
  done
done

echo "---"
echo "[consumption] перевірено імен: $checked, дерево: $TREE"
if [[ $bad -eq 1 ]]; then
  echo "[consumption] є підміни без зчитувача: такий оверлей збереться й нічого не змінить."
  echo "[consumption] або знайди справжнє ім'я, яке читають, або прибери рядок."
  exit 1
fi
echo "[consumption] кожне підмінене ім'я хтось у базі читає."
