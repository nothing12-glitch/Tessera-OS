#!/usr/bin/env bash
# tessera-logs.sh — експорт журналу для супроводу помилки (bug report).
#
# Ідея: зібрати за один запуск те, що ми просимо додати в issue, і водночас
# не зібрати зайвого. Тому:
#   * logcat лише в режимі дампу (-d): стріму тут немає, скрипт завершується сам;
#   * dumpsys вибірково, а не «усе підряд» — повний dumpsys важить десятки МБ
#     і майже весь є шумом;
#   * dmesg тільки якщо сесія справді має root. На production-образі
#     (ro.build.type=user) adb root заборонено, і ми чесно пишемо
#     «потрібен root» замість видимості, що все зібралося.
#
#   tessera-logs.sh                       # дамп у ./tessera-logs-<timestamp>/
#   tessera-logs.sh --serial emulator-5554
#   tessera-logs.sh --out /tmp/zip-ready
#   tessera-logs.sh --logcat-lines 4000
#   tessera-logs.sh --help
set -euo pipefail

SCRIPT_VERSION="1.0.0"
TAG="[logs]"
SERIAL="${TESSERA_ADB_SERIAL:-}"
OUT_DIR="."
LOGCAT_LINES=""
ADB="${ADB:-adb}"

# Пакети, чиї dumpsys нам справді потрібні: власні модулі рівно два.
OUR_PACKAGES="os.tessera.welcome os.tessera.halo"

usage() {
  cat <<'EOF'
Використання: tessera-logs.sh [--out <каталог>] [--serial <SN>]
                             [--logcat-lines N] [--help]

  --out <каталог>     куди покласти tessera-logs-<timestamp>/ (типово ./)
  --serial <SN>       конкретний adb-пристрій (або TESSERA_ADB_SERIAL)
  --logcat-lines N    обмежити дамп останніми N рядками (без N — увесь буфер)

Дамп лише читає зі стану пристрою; НІЧОГО не змінює й нікуди не надсилає.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)           OUT_DIR="${2:-}"; [[ -n "$OUT_DIR" ]] || { echo "$TAG --out порожній" >&2; exit 2; }; shift 2 ;;
    --serial)        SERIAL="${2:-}"; [[ -n "$SERIAL" ]] || { echo "$TAG --serial порожній" >&2; exit 2; }; shift 2 ;;
    --logcat-lines)
      if [[ ! "${2:-}" =~ ^[0-9]+$ ]]; then
        echo "$TAG --logcat-lines має бути числом (отримано: '${2:-}')" >&2
        exit 2
      fi
      LOGCAT_LINES="$2"; shift 2 ;;
    -h|--help)       usage; exit 0 ;;
    *) echo "$TAG невідомий аргумент: $1" >&2; usage; exit 2 ;;
  esac
done

say() { echo "$TAG $*"; }
warn() { echo "$TAG УВАГА: $*" >&2; }

adb_cmd() {
  if [[ -n "$SERIAL" ]]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi
}
run_shell() {
  local out
  out="$(adb_cmd shell "$@" 2>/dev/null || true)"
  printf '%s' "$out" | tr -d '\r'
}

if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "$TAG adb не знайдено в PATH — журналу збирати нізвідки" >&2
  exit 1
fi
if ! adb_cmd get-state >/dev/null 2>&1; then
  echo "$TAG adb-пристрій не знайдено — експорт журналу неможливий" >&2
  echo "$TAG перевірте: $ADB devices -l (unauthorized — прийміть діалог дозволу)" >&2
  exit 1
fi

TS="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="$OUT_DIR/tessera-logs-$TS"
mkdir -p "$OUT_DIR"
if [[ -e "$DEST" ]]; then echo "$TAG каталог $DEST уже існує" >&2; exit 1; fi
mkdir -p "$DEST"
say "пишемо в $DEST (версія скрипту $SCRIPT_VERSION)"

SERIAL_SEEN="$(adb_cmd get-serialno 2>/dev/null | tr -d '\r' || true)"
STATE="$(adb_cmd get-state 2>/dev/null | tr -d '\r' || true)"

# ---------- чи є root -------------------------------------------------------
UID_VAL="$(run_shell id -u)"
IS_ROOT=0
if [[ "$UID_VAL" == "0" ]]; then IS_ROOT=1; fi
ROOT_LABEL="ні (uid=${UID_VAL:-невідомо})"
if [[ "$IS_ROOT" == 1 ]]; then ROOT_LABEL="так (uid=0)"; fi

# ---------- 01 оточення -----------------------------------------------------
{
  echo "host:  $(uname -s 2>/dev/null || printf 'невідомо') $(uname -m 2>/dev/null || true)"
  echo "adb:   $("$ADB" version 2>/dev/null | head -1 || printf 'невідомо')"
  echo "serial: ${SERIAL_SEEN:-невідомо}"
  echo "state:  ${STATE:-невідомо}"
  echo "uid:    ${UID_VAL:-невідомо} (root: $ROOT_LABEL)"
  echo "date UTC: $TS"
  for p in ro.build.type ro.debuggable ro.build.version.release ro.product.model ro.control_privapp_permissions; do
    printf '%s: %s\n' "$p" "$(run_shell getprop "$p")"
  done
} > "$DEST/01-environment.txt"

# ---------- 02 logcat (тільки -d, без стрімінгу) ---------------------------
LC_ARGS="-d --brief"
if [[ -n "$LOGCAT_LINES" ]]; then LC_ARGS="$LC_ARGS -t $LOGCAT_LINES"; fi
run_shell logcat $LC_ARGS > "$DEST/02-logcat-brief.txt"
say "02-logcat-brief.txt — $(wc -l < "$DEST/02-logcat-brief.txt") рядків (--brief, дамп без стрімінгу)"

# окремий буфер крашів: найкорисніша частина для issue
run_shell logcat -d -b crash --brief > "$DEST/03-logcat-crash.txt"
if [[ ! -s "$DEST/03-logcat-crash.txt" ]]; then
  printf '(буфер crash порожній або недоступний на цьому образі)\n' > "$DEST/03-logcat-crash.txt"
fi
run_shell logcat -g > "$DEST/04-logcat-buffers.txt"

# ---------- 05 dumpsys наших пакетів ---------------------------------------
idx=5
for pkg in $OUR_PACKAGES; do
  out_file="$DEST/0${idx}-dumpsys-package-$pkg.txt"
  run_shell dumpsys package "$pkg" > "$out_file"
  if grep -qi 'not found' <<<"$(head -3 "$out_file")"; then
    say "0${idx}: пакет $pkg НЕ знайдений у цій збірці (ідемо далі)"
  else
    say "0${idx}-dumpsys-package-$pkg.txt — $(wc -l < "$out_file") рядків"
  fi
  idx=$((idx + 1))
done

# ---------- 07 dumpsys window ----------------------------------------------
run_shell dumpsys window > "$DEST/07-dumpsys-window.txt"
say "07-dumpsys-window.txt — $(wc -l < "$DEST/07-dumpsys-window.txt") рядків"

# ---------- 08 dumpsys device_policy: тільки якщо сервіс доступний ---------
# Назву служби навмисно не гадаємо: у різних збірках вона зустрічається і як
# «devicepolicy», і як «device_policy». Шукаємо точний запис у переліку
# служб (dumpsys -l); якщо немає — чесно пишемо, що зірвалося.
SERVICES="$(run_shell dumpsys -l | tr -d ' \t')"
DP_NAME=""
for cand in device_policy devicepolicy; do
  if printf '%s\n' "$SERVICES" | grep -qx "$cand"; then DP_NAME="$cand"; break; fi
done
if [[ -n "$DP_NAME" ]]; then
  run_shell dumpsys "$DP_NAME" > "$DEST/08-dumpsys-devicepolicy.txt"
  say "08-dumpsys-devicepolicy.txt — сервіс $DP_NAME ($(wc -l < "$DEST/08-dumpsys-devicepolicy.txt") рядків)"
else
  {
    echo 'Сервісу device_policy/devicepolicy у переліку служб (dumpsys -l) немає.'
    if [[ "$IS_ROOT" == 0 ]]; then
      echo 'Без root перелік служб може бути обрізаний — потрібен root (userdebug-образ).'
    else
      echo 'root є, отже служби справді немає в цій збірці (політики пристрою неактивні).'
    fi
  } > "$DEST/08-dumpsys-devicepolicy.txt"
  say "08: dumpsys devicepolicy недоступний — записали причину"
fi

# ---------- 09 dmesg: тільки за root ---------------------------------------
if [[ "$IS_ROOT" == 1 ]]; then
  run_shell dmesg > "$DEST/09-dmesg.txt"
  say "09-dmesg.txt — $(wc -l < "$DEST/09-dmesg.txt") рядків (uid=0)"
else
  {
    echo 'dmesg НЕ зібрано: потрібен root.'
    echo "Поточна adb-сесія uid=${UID_VAL:-невідомо}. На production-образі"
    echo '(ro.build.type=user) adb root заборонено політикою, тому це очікувано.'
    echo 'Якщо потрібен саме dmesg — зніміть його на userdebug-збірці:'
    echo '  adb root && adb shell dmesg > dmesg.txt'
    echo 'або скористайтеся adb bugreport (файл 12) — ядро туди теж потрапляє.'
  } > "$DEST/09-dmesg.txt"
  say "09-dmesg.txt — записано «потрібен root» (чесно, імітації немає)"
fi

# ---------- 10 додатково: getprop + перелік пакетів ------------------------
run_shell getprop > "$DEST/10-getprop.txt"
run_shell pm list packages > "$DEST/11-packages.txt"

# ---------- 12 bugreport: лише інструкція, бо це довго ---------------------
cat > "$DEST/12-bugreport-HOWTO.txt" <<'EOF'
Якщо 02..11 недостатньо — зробіть повний знімок:
  adb bugreport bugreport.zip
Це триває хвилини й показує діалог дозволу на пристрої. Файл містить
ЖУРНАЛИ ВСІЄЇ СИСТЕМИ, включно з вмістом повідомлень і номерами телефонів:
перед публікацією в публічному issue його не можна викладати як є.
EOF

# ---------- MANIFEST --------------------------------------------------------
PRIV_NOTE() {
  cat <<'EOF'

ПОПЕРЕДЖЕННЯ ПРО ПЕРСОНАЛЬНІ ДАНІ
  logcat і dumpsys на Android можуть містити: номери телефонів, адреси
  e-mail, текст сповіщень і повідомлень, імена Wi-Fi-мереж, токени й
  куки, список встановлених застосунків, геодані. Цей каталог НЕ є
  «знеособленим» дампом.
  ПЕРЕД публікацією у відкритий issue:
    1) відкрийте MANIFEST.txt і перелікуйте файли, які збираєтеся віддати;
    2) перегляньте кожен із них у редакторі (grep -i -n 'tel\|email\|token')
       і видаліть чутливі рядки;
    3) 07-dumpsys-window.txt і 02-logcat-brief.txt — найчастіші джерела
       тексту повідомлень: починайте з них;
    4) не викладайте 10-getprop.txt, якщо в ньому є serialno/MEID;
    5) найбезпечніше — надсилати супроводу архів із паролем, а не публічне
       посилання.
EOF
}

MAN="$DEST/MANIFEST.txt"
{
  echo "tessera-logs.sh версії $SCRIPT_VERSION"
  echo "UTC часу дампу:   $TS"
  echo "SERIAL пристрою:  ${SERIAL_SEEN:-невідомо}"
  echo "стан adb:         ${STATE:-невідомо}"
  echo "root у сесії:     $ROOT_LABEL"
  echo "каталог:          $DEST"
  echo
  echo "Зібрано (розмір у байтах / шлях):"
  find "$DEST" -type f ! -name MANIFEST.txt -printf '%s\t%p\n' 2>/dev/null | sort -k2 ||
    find "$DEST" -type f ! -name MANIFEST.txt | sort | while IFS= read -r f; do
      printf '%s\t%s\n' "$(wc -c < "$f")" "${f#"$DEST"/}"
    done
  echo
  echo "Не зібрано / потребує більше прав:"
  echo "  * dmesg — див. 09-dmesg.txt (потрібен root, на user-образі недоступно)"
  echo "  * SELinux-деніали (logcat -b events / audit) — потребують root"
  echo "  * повний дамп усіх служб (dumpsys без аргументу) — не робимо свідомо:"
  echo "    десятки мегабайтів шуму замість корисного виводу"
  PRIV_NOTE
} > "$MAN"

say "готово: $DEST"
warn "прочитайте MANIFEST.txt (попередження про персональні дані), перш ділитися"
warn "у публічному issue"
exit 0
