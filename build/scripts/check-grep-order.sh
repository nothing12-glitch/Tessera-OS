#!/usr/bin/env bash
# Статична перевірка: жоден наш скрипт не ставить --exclude= ПОПЕРЕД --include=
# у тому самому виклику grep.
#
# Навіщо. grep 3.11 (Ubuntu 24.04 у нашому WSL-хості) на таку комбінацію
# мовчки ЗʼїДАЄ --include і починає грати по файлах усіх розширень. Матриця
# 27.10 на каталозі {a.bp, a.mk, a.txt, a.xml}:
#   --include=*.xml --exclude=*.txt  -> a.xml            (правильно)
#   --exclude=*.txt --include=*.xml  -> a.bp a.mk a.xml  (фільтра немає)
# Кілька --include поспіль — навпаки, коректне об'єднання. --exclude-dir на
# це не впливає.
#
# Це вже вкусилося двічі за один вечір: гарант споживання раптом порахував
# «≥18 зчитувачів» там, де їх було 12, бо xml-пошук почав читати .java.
# Перевірка текстові, тому придатна для CI — дерева бази не потребує.
#
#   bash build/scripts/check-grep-order.sh [--selftest]
set -uo pipefail
SELF="${BASH_SOURCE[0]:-$0}"
if [[ -f "$SELF" ]]; then
  ROOT="$(cd "$(dirname "$SELF")/../.." && pwd)"
else
  ROOT="$PWD"
fi

scan() {
  # $@ — файли. awk з'єднує продовження (кінець рядка = \), відкидає рядки-
  # коментарі і бере лише буфери, де grep справді викликається (`grep -…`):
  # без цього перший прогін 27.10 поскаржився на власний текст повідомлення,
  # на коментар у сусідньому скрипті й на власний fixture.
  awk '
    function flush() {
      if (buf !~ /(^|[;&|(])[[:space:]]*grep[[:space:]]+-/) { buf=""; return }
      ei = index(buf, "--include=")
      xi = index(buf, "--exclude=")
      if (ei && xi && xi < ei)
        printf "%s:%d: фільтр за розширенням не діє — гортати треба --include першим\n", f, l
      buf=""
    }
    FNR==1 { f=FILENAME; l=FNR; flush() }
    {
      if ($0 ~ /^[[:space:]]*#/) next
      if (buf=="") l=FNR
      line=$0
      sub(/[[:space:]]*$/, "", line)
      if (line ~ /\\$/) { buf = buf " " substr(line, 1, length(line)-1); next }
      buf = buf " " line
      flush()
    }
    END { flush() }
  ' "$@"
}

bad=0
if [[ "${1:-}" == "--selftest" ]]; then
  TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
  # Файл-приклад з вадою зібраний у лапках так, щоб У ЦЬОМІ скрипті не було
  # безперервного рядка `--exclude=…--include=`: сканер читає й цей файл, і
  # інакше скаржився б на власний fixture.
  printf '%s\n' "grep -rl --exclude"'='"*.md --include"'='"*.xml pat /somewhere" > "$TMP/bad.sh"
  cat > "$TMP/good.sh" <<'EOF'
grep -rl --exclude-dir=out --include=*.xml --include=*.kt pat /somewhere
grep -rl --exclude=*.md pat /somewhere
EOF
  out=$(scan "$TMP/bad.sh")
  [[ -n "$out" ]] && echo "[grep-order] само-тест ok: ваду спіймано → $out" ||
    { echo "[grep-order] само-тест ПАМОЛКА: свідомо хибний виклик не знайдено"; bad=1; }
  out=$(scan "$TMP/good.sh")
  [[ -z "$out" ]] && echo "[grep-order] само-тест ok: коректні форми не позначені" ||
    { echo "[grep-order] само-тест ПАМОЛКА: хибне спрацювання → $out"; bad=1; }
  exit $((bad))
fi

while IFS= read -r file; do
  out=$(scan "$file")
  if [[ -n "$out" ]]; then echo "$out"; bad=1; fi
done < <(find "$ROOT/build/scripts" "$ROOT/config" -maxdepth 2 -type f \
           \( -name '*.sh' -o -name '*.ps1' \) 2>/dev/null | sort)

if [[ $bad -eq 1 ]]; then
  echo "[grep-order] є виклики grep, де фільтр за розширенням не діє. Перестав"
  echo "[grep-order] --include першим або зрізай зайве пост-фільтром (| grep -vE …)."
  exit 1
fi
echo "[grep-order] усі виклики grep у наших скриптах фільтрують розширення чесно."
