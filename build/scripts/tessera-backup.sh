#!/usr/bin/env bash
# tessera-backup.sh — бекап користувацького стану ПЕРЕД прошиванням образу.
#
# Що реально можна зберегти на Android 15 і чого тут не буде:
#   * adb backup досі існує в platform-tools, але з Android 12+ й далі він
#     обмежений: застосунки з allow_backup="false" у архів не потрапляють,
#     частина вендорів вимкнула бекап узагалі, а сам архів треба
#     підтвердити діалогом на екрані пристрою. Тож «повного образу даних»
#     цей скрипт не обіцяє — він збирає списки, прапорці й те, що віддається.
#   * списки пакетів і значення settings — це єдине, що після прошивання
#     відновлюється буквально одним `settings put`, тому цінне;
#   * /sdcard знімаємо вибірково: спочатку перелік (метадані), файли — лише
#     з прапорцями нижче, бо це десятки гігабайтів.
#
#   tessera-backup.sh                       # бекап у ./tessera-backup-<timestamp>/
#   tessera-backup.sh --out /mnt/backup     # кудись писати
#   tessera-backup.sh --serial emulator-5554
#   tessera-backup.sh --sdcard-dirs Download Documents   # ще і тягнути файли
#   tessera-backup.sh --no-adb-backup       # не запускати adb backup
#   tessera-backup.sh --help
set -euo pipefail

SCRIPT_VERSION="1.0.0"
TAG="[backup]"
SERIAL="${TESSERA_ADB_SERIAL:-}"
OUT_DIR="."
SD_DIRS=""
DO_ADB_BACKUP=1
BACKUP_FLAGS=""
ADB="${ADB:-adb}"

# Відомі ключі settings, які мають сенс при відновленні після прошивки.
# Порядок перевірки namespace (secure → global → system) визначається на
# пристрої, а не коментарем вище: який namespace фактично тримає ключ, видно
# лише з `settings get`.
SETTINGS_KEYS="ui_night_mode device_provisioned user_setup_complete enabled_notification_listeners enabled_accessibility_services adb_enabled install_non_market_apps location_mode time_zone default_location"

usage() {
  cat <<'EOF'
Використання: tessera-backup.sh [--out <каталог>] [--serial <SN>]
                              [--sdcard-dirs "Download Documents"]
                              [--no-adb-backup] [--backup-apk] [--help]

  --out <каталог>     куди покласти tessera-backup-<timestamp>/ (типово ./)
  --serial <SN>       конкретний adb-пристрій (або TESSERA_ADB_SERIAL)
  --sdcard-dirs "…"   тягнути файли з /sdcard/<кожен перелічений каталог>
  --no-adb-backup     пропустити `adb backup` (він інтерактивний і обмежений)
  --backup-apk        додати -apk до adb backup (архів з APK, великий і повільний)
  --help              ця довідка

Скрипт НІЧОГО не змінює на пристрої: лише читає й пише у локальний каталог.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)          OUT_DIR="${2:-}"; [[ -n "$OUT_DIR" ]] || { echo "$TAG --out порожній" >&2; exit 2; }; shift 2 ;;
    --serial)       SERIAL="${2:-}"; [[ -n "$SERIAL" ]] || { echo "$TAG --serial порожній" >&2; exit 2; }; shift 2 ;;
    --sdcard-dirs)  SD_DIRS="${2:-}"; shift 2 ;;
    --no-adb-backup) DO_ADB_BACKUP=0; shift ;;
    --backup-apk)   BACKUP_FLAGS="$BACKUP_FLAGS -apk"; shift ;;
    -h|--help)      usage; exit 0 ;;
    *) echo "$TAG невідомий аргумент: $1" >&2; usage; exit 2 ;;
  esac
done

say() { echo "$TAG $*"; }
warn() { echo "$TAG УВАГА: $*" >&2; }
note() { echo "      $*"; }

adb_cmd() {
  if [[ -n "$SERIAL" ]]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi
}
shell_readonly() {
  local out
  out="$(adb_cmd shell "$@" 2>/dev/null || true)"
  printf '%s' "$out" | tr -d '\r'
}

# ---------- вимога: пристрій має бути --------------------------------------
if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "$TAG adb не знайдено в PATH — без пристрою бекап неможливий" >&2
  exit 1
fi
if ! adb_cmd get-state >/dev/null 2>&1; then
  echo "$TAG adb-пристрій не знайдено — бекап розпочати неможливо." >&2
  echo "$TAG що перевірити: $ADB devices -l ; стан unauthorized — прийміть діалог дозволу;" >&2
  echo "$TAG offline — перепідключіть кабель/емулятор. Ми НЕ робимо «порожній бекап»:" >&2
  echo "$TAG каталог з нульовими файлами ілюзією захисту від втрати даних." >&2
  exit 1
fi

TS="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="$OUT_DIR/tessera-backup-$TS"
mkdir -p "$OUT_DIR"
if [[ -e "$DEST" ]]; then
  echo "$TAG каталог $DEST уже існує — не перезаписуємо" >&2
  exit 1
fi
mkdir -p "$DEST/settings"
say "пишемо в $DEST (версія скрипту $SCRIPT_VERSION)"

SERIAL_SEEN="$(adb_cmd get-serialno 2>/dev/null | tr -d '\r' || true)"
STATE="$(adb_cmd get-state 2>/dev/null | tr -d '\r' || true)"
printf 'serial=%s\nstate=%s\nutc=%s\nscript=tessera-backup.sh %s\n' \
  "${SERIAL_SEEN:-невідомо}" "${STATE:-невідомо}" "$TS" "$SCRIPT_VERSION" > "$DEST/00-device.txt"

# ---------- 1. профіль пристрою і властивості -------------------------------
shell_readonly getprop > "$DEST/01-getprop.txt"
say "01-getprop.txt — властивості образу ($(wc -l < "$DEST/01-getprop.txt") рядків)"

# ---------- 2. списки пакетів ----------------------------------------------
shell_readonly pm list packages -3 > "$DEST/02-packages-third-party.txt"
shell_readonly pm list packages -s > "$DEST/03-packages-system.txt"
shell_readonly pm list packages -f > "$DEST/04-packages-with-path.txt"
say "02-packages-third-party.txt — $(grep -c '^package:' "$DEST/02-packages-third-party.txt" || true) застосунків користувача"
note "список -3 — це єдиний чіткий перелік «що ставили руками»; після прошивки"
note "встановіть ці пакети знову вручну (F-Droid/Aurora/власні APK)."

# ---------- 3. settings: відомі ключі + повні дампи ------------------------
for ns in secure global system; do
  shell_readonly settings list "$ns" > "$DEST/settings/list-$ns.txt"
  say "settings/list-$ns.txt — $(grep -c '=' "$DEST/settings/list-$ns.txt" || true) записів"
done

: > "$DEST/settings/known-keys.tsv"
for key in $SETTINGS_KEYS; do
  found_ns=""
  found_val=""
  for ns in secure global system; do
    val="$(shell_readonly settings get "$ns" "$key")"
    if [[ -n "$val" && "$val" != "null" ]]; then found_ns="$ns"; found_val="$val"; break; fi
  done
  if [[ -n "$found_ns" ]]; then
    printf '%s\t%s\t%s\n' "$key" "$found_ns" "$found_val" >> "$DEST/settings/known-keys.tsv"
  else
    printf '%s\t- absent -\t-\n' "$key" >> "$DEST/settings/known-keys.tsv"
  fi
done
say "settings/known-keys.tsv — значення відомих ключів (tsv: ключ, namespace, значення)"
note "відновлення: adb shell settings put <namespace> <ключ> <значення>"

# ---------- 4. /sdcard: перелік метаданих, файли — лише за прапорцем --------
{
  shell_readonly ls -l /sdcard
  echo "--- du -sh /sdcard/* ---"
  shell_readonly "sh -c 'du -sh /sdcard/* 2>/dev/null'"
  echo "--- find (maxdepth 3) ---"
  shell_readonly find /sdcard -maxdepth 3 -type f
} > "$DEST/05-sdcard-listing.txt"
say "05-sdcard-listing.txt — перелік /sdcard без вмісту ($(wc -l < "$DEST/05-sdcard-listing.txt") рядків)"

if [[ -n "$SD_DIRS" ]]; then
  mkdir -p "$DEST/sdcard"
  for d in $SD_DIRS; do
    say "тягнемо /sdcard/$d → $DEST/sdcard/$d"
    if adb_cmd pull "/sdcard/$d" "$DEST/sdcard/$d" > "$DEST/sdcard/$d.pull.log" 2>&1; then
      note "готово ($(du -sh "$DEST/sdcard/$d" 2>/dev/null | cut -f1 || printf 'невідомо'))"
    else
      warn "каталог /sdcard/$d не витягнуто — див. $DEST/sdcard/$d.pull.log"
    fi
  done
else
  note "файли /sdcard НЕ тягнули (типово лише перелік). Для вибіркового знімку:"
  note "  tessera-backup.sh --sdcard-dirs \"Download Documents\""
fi

# ---------- 5. adb backup --------------------------------------------------
if [[ "$DO_ADB_BACKUP" == 1 ]]; then
  AB="$DEST/06-adb-backup.ab"
  warn "adb backup на Android 15 може бути обмежений: застосунки з allow_backup=\"false\""
  note "у архів НЕ потрапляють; частина вендорів вимкнула бекап повністю;"
  note "на екрані пристрою треба прийняти діалог «Дозволити резервне копіювання»"
  note "(з паролем — зашифрований архів, без пароля — БЕЗ шифрування!)."
  note "Якщо діалог не з'явиться за 60 с — архів вийде порожнім; тоді просто"
  note "продовжуйте: списки пакетів і settings уже збережені вище."
  if [ -n "$BACKUP_FLAGS" ]; then
    say "прапорці adb backup:${BACKUP_FLAGS}"
  fi
  if command -v timeout >/dev/null 2>&1; then
    timeout 180 "$(command -v "$ADB")" ${SERIAL:+-s "$SERIAL"} backup -all $BACKUP_FLAGS -f "$AB" >/dev/null 2>&1 ||
      warn "adb backup завершився нечісто (таймаут 180 с або помилка) — це відоме обмеження"
  else
    warn "у хоста немає timeout — запускаємо adb backup без обмеження часу"
    adb_cmd backup -all $BACKUP_FLAGS -f "$AB" >/dev/null 2>&1 ||
      warn "adb backup завершився нечісто — це відоме обмеження Android 15"
  fi
  if [[ -f "$AB" ]]; then
    say "06-adb-backup.ab — $(wc -c < "$AB") байт"
    if [ "$(wc -c < "$AB")" -lt 32 ]; then
      warn "файл .ab майже порожній — пристрій не віддав дані (перегляньте діалог на екрані)"
    fi
  else
    warn "файл .ab не створено — бекап даних застосунків не вийшов; списки й settings лишилися"
  fi
else
  say "adb backup пропущено (--no-adb-backup); списки пакетів і settings збережено"
fi

# ---------- 6. інструкція відновлення --------------------------------------
cat > "$DEST/RESTORE-HOWTO.txt" <<EOF
Відновлення після прошивання образу $TS
========================================

1. Списки застосунків користувача: 02-packages-third-party.txt.
   APK звідси НЕ витягувалися — ставте застосунки самі (F-Droid / Aurora /
   власні джерела), потім повертайте дозволи в налаштуваннях.

2. Прапорці settings (відновлюються точно):
     while IFS=\$'\t' read -r k ns v; do
       [ "\$ns" = "- absent -" ] && continue
       adb shell settings put "\$ns" "\$k" "\$v"
     done < settings/known-keys.tsv

3. Зміст /sdcard: якщо тягнули --sdcard-dirs, розкладіть вручну:
     adb push $DEST/sdcard/Download/. /sdcard/Download/
   Перелік без вмісту — у 05-sdcard-listing.txt (щоб звірити, чи нічого не згублено).

4. Архів 06-adb-backup.ab (якщо створений):
     adb restore $AB
   Той самий пристрій і той самий ключ, що й при копіюванні; на Android 15
   відновлення може спрацювати лише для одиничних застосунків.

5. ЧЕСНО: даних застосунків, які ввімкнули allow_backup="false", в цьому
   бекапі НЕМАЄ. Їх єдиний шлях — внутрішній експорт самого застосунку.
EOF
say "RESTORE-HOWTO.txt — кроки відновлення"

# ---------- 7. MANIFEST + sha256 -------------------------------------------
sha_tool=""
if command -v sha256sum >/dev/null 2>&1; then sha_tool="sha256sum"
elif command -v shasum >/dev/null 2>&1; then sha_tool="shasum -a 256"
elif command -v openssl >/dev/null 2>&1; then sha_tool="openssl dgst -sha256"; fi

SUMS="$DEST/SHA256SUMS.txt"
: > "$SUMS"
if [[ -n "$sha_tool" ]]; then
  while IFS= read -r f; do
    rel="${f#"$DEST"/}"
    # $NF — останнє поле: у sha256sum це хеш, у openssl dgst теж хеш, а не «SHA256(file)=»
    h="$($sha_tool "$f" 2>/dev/null | awk '{print $NF}')"
    if [ -n "$h" ]; then printf '%s  %s\n' "$h" "$rel" >> "$SUMS"; fi
  done < <(find "$DEST" -type f ! -name SHA256SUMS.txt ! -name MANIFEST.txt | sort)
  say "SHA256SUMS.txt — $(grep -c . "$SUMS" || true) файлів"
else
  warn "на хоста немає sha256sum/shasum/openssl — контрольні суми не записані"
fi

MAN="$DEST/MANIFEST.txt"
{
  echo "tessera-backup.sh версії $SCRIPT_VERSION"
  echo "UTC часу знімку:  $TS"
  echo "SERIAL пристрою:  ${SERIAL_SEEN:-невідомо}"
  echo "стан adb:         ${STATE:-невідомо}"
  echo "каталог:          $DEST"
  echo
  echo "УВАГА — ПЕРСОНАЛЬНІ ДАНІ. у цьому каталогі є перелік застосунків,"
  echo "прапорці користувача, а за --sdcard-dirs — фото й документи."
  echo "Не викладайте каталог у відкритий issue без огляду. Копіюйте з нього"
  echo "лише потрібні рядки, архівуйте з паролем або тримайте локально."
  echo
  echo "Файли бекапу (розмір у байтах):"
  find "$DEST" -type f ! -name MANIFEST.txt -printf '%s\t%p\n' 2>/dev/null | sort -k2 ||
    find "$DEST" -type f ! -name MANIFEST.txt | sort | while IFS= read -r f; do
      printf '%s\t%s\n' "$(wc -c < "$f")" "${f#"$DEST"/}"
    done
} > "$MAN"

say "готово: $DEST"
say "MANIFEST.txt — прочитайте перед тим, ділитися бекапом"
exit 0
