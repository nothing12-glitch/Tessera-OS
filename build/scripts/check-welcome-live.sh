#!/usr/bin/env bash
# Живий прогін майстра першого запуску на емуляторі — один відтворюваний
# інструмент замість розкиданих разових скриптів.
#
# Навіщо він у git: висновок на кшталт «тема застосовується наживо, а не після
# ребута» лишається чуткою, якщо його можна лише переказати з чужого
# термінального логу. Тут вимірюються ті самі пари «рядок у Secure» проти «стан
# сервісу», і код виходу 0 буває лише тоді, коли кожне виміряне твердження
# справді виконалося.
#
#   build/scripts/check-welcome-live.sh [-s <серіал>] install <шлях/до/apk>
#   build/scripts/check-welcome-live.sh [-s <серіал>] theme
#   build/scripts/check-welcome-live.sh [-s <серіал>] walk [--fresh]
#   build/scripts/check-welcome-live.sh [-s <серіал>] screens <dark|light> [--video]
#
# --fresh має значення лише для `walk`: саме він вимірює відновлення на кроці
# зупинки. `theme` і `screens` чистять стан модуля примусово — їм потрібен
# конкретний крок (4 і 1), а не доказ відновлення, а попередній прохід лишає
# майстра на кроці 10.
#
# Передумови (докладніше — docs/EMULATOR.md, розділ про system_ext/priv-app):
# образ userdebug (на user `adb root` заборонено), емулятор запущено з
# -writable-system, verity вимкнено, модуль уже зібрано (apps/welcome).
#
# Шлях до adb змінюється без правки файла: ADB=/шлях/до/adb ...
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ADB="${ADB:-adb}"
DEV="${DEV:-emulator-5554}"

PKG="os.tessera.welcome"
COMPONENT="$PKG/.WelcomeActivity"
PRIV_DIR="/system_ext/priv-app/TesseraWelcome"
DEV_APK="$PRIV_DIR/TesseraWelcome.apk"
DEV_XML="/system_ext/etc/permissions/privapp-permissions-tessera-welcome.xml"
SRC_XML="$ROOT/apps/welcome/permissions/privapp-permissions-tessera-welcome.xml"
# Знімки — докази, а не сміття, але й не в git: out/ ігнорується.
OUT_DIR="${OUT_DIR:-$ROOT/out/welcome-live}"

# adb.exe — Windows-бінарник: ЛОКАЛЬНИЙ шлях він розуміє лише як C:\..., а шлях
# ПРИСТРОЮ — як /system_ext/... без жодної конвертації. Один формат на обидва не
# працює ніколи (перший прогін споткнувся саме на цьому), тому MSYS_NO_PATHCONV
# глобально, а windows-вигляд підставляємо тільки локальним аргументам.
export MSYS_NO_PATHCONV=1
host_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"
  else printf '%s' "$1"; fi
}

FAILED=0
CHECKS=0

say()  { echo "[$(date -u '+%T')] $*"; }
note() { echo "       виміряно: $*"; }
die()  { echo "[welcome-live] STOP: $*" >&2; exit 1; }

# Твердження. Їхня кількість і визначає код виходу, тому «подивлюся й сам
# зрозумію» тут не варіант: скрипт каже, який саме висновок не тримався.
ok_if() {
  local label="$1"; shift
  CHECKS=$((CHECKS + 1))
  if "$@" >/dev/null 2>&1; then
    echo "  [ ok ] $label"
  else
    echo "  [FAIL] $label"; FAILED=$((FAILED + 1))
  fi
}

report() {
  echo
  if [[ "$CHECKS" == 0 ]]; then
    echo "[welcome-live] STOP: жодного твердження не виміряно — прогін ні чому не слугує." >&2
    exit 1
  fi
  if [[ "$FAILED" != 0 ]]; then
    echo "[welcome-live] Провалено: $FAILED із $CHECKS тверджень." >&2
    exit 1
  fi
  echo "[welcome-live] Виконано $CHECKS із $CHECKS тверджень."
}

adb_sh() { "$ADB" -s "$DEV" shell "$@" 2>&1 | tr -d '\r'; }

file_size() { wc -c <"$1" | tr -d ' '; }

wait_boot() {
  local bc="" i
  for i in $(seq 1 40); do
    bc="$("$ADB" -s "$DEV" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    [[ "$bc" == "1" ]] && { say "sys.boot_completed=1 (спроба $i)"; return 0; }
    sleep 10
  done
  die "пристрій не завантажився за 400 с. Найгірший варіант — enforce-політика priv-app: неповний дозвільний список вбиває завантаження цілком"
}

# ---------- UI: крок і тапи читаємо з дампа, а не з лічильника тапів ---------

dump_ui() {
  # Дамп знімаємо на пристрої й читаємо назад через cat: `pull` потребував би
  # локального каталогу, а той якраз не має бути «машинним» у файлі репо.
  adb_sh 'uiautomator dump /sdcard/wdump.xml >/dev/null 2>&1; cat /sdcard/wdump.xml; rm -f /sdcard/wdump.xml' \
    | sed 's/></><\n</g'
}

# Готовність — це НЕ «пристрій видно в adb devices» і навіть не boot_completed=1.
# 27.10.2026 прогін, запущений за ~30 с після cold boot, отримав `mCurrentFocus=null`
# і «null root node» від uiautomator, хоча система вже повідомила про завершений
# boot. Без цього воріт скрипт вимірює стан завантаження, а не модуль, і видає хибний
# висновок: walk тоді написав би «майстер відновився на кроці невідомо».
wait_ui_ready() {
  local i focus
  wait_boot
  for i in $(seq 1 30); do
    [[ "$(adb_sh 'dumpsys power' | grep -m1 -oE 'mWakefulness=[A-Za-z]+' | sed 's/.*=//')" == "Asleep" ]] \
      && adb_sh 'input keyevent KEYCODE_WAKEUP' >/dev/null
    focus="$(adb_sh 'dumpsys window' | grep -m1 -oE 'mCurrentFocus=Window\{[^}]*\}' || true)"
    if [[ -n "$focus" ]] && dump_ui | grep -q '<hierarchy'; then
      say "UI готовий (спроба $i): ${focus#mCurrentFocus=}"
      return 0
    fi
    sleep 3
  done
  die "за ~90 с після boot система не дала ні вікна у фокусі, ні валідного дампа (uiautomator мовчить). Повтори прогін на живому екрані — інакше міритимеш завантаження, а не модуль"
}

tap_node() {
  local pat="$1" line b x y
  # Рівність атрибута, а не підрядок: 26.10.2026 під час живого прогону «Увійти в
  # Tessera» перший збіг виявився у тексті підказки «Прапорці ще не записували:
  # натисніть „Увійти в Tessera“», тобто тап пішов у неактивну рамку над кнопкою,
  # а прохід лишився на кроці 10 з нульовими прапорцями.
  line="$(dump_ui | grep -m1 -E "(text|content-desc)=\"$pat\"" || true)"
  [[ -n "$line" ]] || { echo "  у поточному дампі немає вузла з рівним ім'ям «$pat»" >&2; return 2; }
  b="$(printf '%s' "$line" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1 | tr '[],' '   ')"
  [[ -n "$b" ]] || { echo "  у вузла «$pat» немає bounds — тасти нічого" >&2; return 2; }
  # shellcheck disable=SC2086
  set -- $b
  x=$(( ($3 + $1) / 2 )); y=$(( ($4 + $2) / 2 ))
  echo "  тап «$pat» bounds=$b → ($x,$y)"
  adb_sh "input tap $x $y" >/dev/null
  sleep 1
}

current_step() {
  # Оракул стану, а не власний лічильник тапів: «тикнув двічі Далі й гадаю, що
  # на кроці 4» — рівно та підстава, через яку перший прогін перевірку теми не
  # зробив.
  dump_ui | grep -oE 'крок [0-9]+ із [0-9]+' | head -1 | grep -oE '[0-9]+' | head -1
}

# Фіксована пауза після старту імітує дефект модуля: 27.10.2026 перший старт після
# `pm clear` на свіжозавантаженій системі двічі поспіль дав порожній дамп, і прогін
# написав би «майстер відновився на кроці невідомо» замість правди «екран ще не
# встиг». Тому опитування. Друкує номер крока, щойно екран показав хоча б один,
# або порожній рядок після ~60 с.
wait_step_marker() {
  local i st=""
  for i in $(seq 1 20); do
    st="$(current_step)"
    [[ -n "$st" ]] && break
    sleep 3
  done
  printf '%s' "$st"
}

screenshot() { # screenshot <ім'я-файла> [пікаталог усередині OUT_DIR]
  mkdir -p "$OUT_DIR/${2:-}" 2>/dev/null || return 0
  adb_sh "screencap -p /sdcard/shot.png" >/dev/null
  "$ADB" -s "$DEV" pull /sdcard/shot.png "$(host_path "$OUT_DIR/$1")" >/dev/null 2>&1 || return 0
  adb_sh 'rm -f /sdcard/shot.png' >/dev/null
  echo "  знімок → $OUT_DIR/$1"
}

# ---------- відео: записує сам пристрій, зупиняємо сигналом ------------------

# screenrecord пише mp4 на пристрої. Зупинка — саме SIGINT (`pkill -2`): вбитий
# іншим сигналом файл лишається без moov-атома й не відтворюється, а за розміром
# відрізнити його неможливо. Тому після сигналу ще sleep — дописати атом.
record_start() { # record_start <шлях-на-пристрої>
  local dev="$1" size
  adb_sh "rm -f $dev" >/dev/null
  adb_sh "screenrecord --time-limit 180 $dev >/dev/null 2>&1 &" >/dev/null
  sleep 3
  # Поле $5 з `ls -l`, а не «цифри наприкінці рядка»: рядок закінчується самим
  # шляхом, тож перший варіант не збігався ніколи й підкоманда вмирала б ще до того,
  # як що-небудь записалося.
  size="$(adb_sh "ls -l $dev" | awk '{print $5}' | tr -dc '0-9')"
  [[ -n "$size" ]] || die "screenrecord не створив $dev — на цьому образі запис екрана недоступний"
  echo "  запис почато: $dev ($size байтів на старті)"
}

record_finish() { # record_finish <шлях-на-пристрої> <пікаталог> <ім'я-файла>
  local dev="$1" sub="$2" name="$3" size host
  adb_sh 'pkill -2 screenrecord' >/dev/null
  sleep 4
  size="$(adb_sh "ls -l $dev" | awk '{print $5}' | tr -dc '0-9')"
  note "на пристроі $dev — ${size:-0} байтів"
  [[ -n "$size" && "$size" -gt 100000 ]] \
    || die "запис відео обірвався: на пристроі ${size:-0} байтів — screenrecord не створив і не дописав файл"
  host="$OUT_DIR/$sub/$name"
  mkdir -p "$OUT_DIR/$sub" 2>/dev/null
  "$ADB" -s "$DEV" pull "$dev" "$(host_path "$host")" >/dev/null 2>&1
  # Перевіряємо обидва боки, бо відмова бувала мовчазною з кожного: pull уміє не
  # спрацювати, а файл на пристрої — виявитися порожнім. Локальний шлях тут
  # posix-вий, бо `head` і `wc` читають його, а adb — навпаки, WINDOWS-вий.
  ok_if "відео стягнено на диск ($host)" [ "$(file_size "$host" 2>/dev/null || echo 0)" -gt 100000 ]
  ok_if "у файлі є mp4-заголовок ftyp"   [ -n "$(head -c 32 "$host" 2>/dev/null | grep -ao ftyp | head -1)" ]
  echo "  відео → $host"
  adb_sh "rm -f $dev" >/dev/null
}

# ---------- тема: рядок у базі проти стану сервісу ---------------------------

uimode_dump() { adb_sh 'dumpsys uimode'; }

cur_uimode() { uimode_dump | grep -m1 -oE 'mCurUiMode=0x[0-9a-f]+' | sed 's/.*=0x//'; }

night_mode() { uimode_dump | grep -m1 -oE 'mNightMode=[0-9]+' | sed 's/.*=//'; }

secure_val() { adb_sh "settings get secure $1" | tr -dc '0-9'; }

global_val() { adb_sh "settings get global $1" | tr -dc '0-9'; }

# Сирий рядок для друку: tr -dc з «1.0» робить «10», тобто показаний стан став би
# іншим, ніж виміряний. Для display — ці функції, для порівнянь — val-варіанти.
raw_secure() { adb_sh "settings get secure $1"; }
raw_global() { adb_sh "settings get global $1"; }

pair() {
  local sec dump
  sec="$(adb_sh 'settings get secure ui_night_mode')"
  # Один dumpsys на обидва поля, а не два: вимір «у ту саму секунду» інакше може
  # зловити два різні кадри сервісу.
  dump="$(uimode_dump)"
  printf 'secure=%s  %s  %s\n' "$sec" \
    "$(printf '%s' "$dump" | grep -m1 -oE 'mNightMode=[0-9]+ \([a-z]+\)')" \
    "$(printf '%s' "$dump" | grep -m1 -oE 'mCurUiMode=0x[0-9a-f]+')"
}

require_device() {
  command -v "$ADB" >/dev/null 2>&1 || die "не знайшов adb («$ADB»): задай ADB=/шлях/до/adb"
  "$ADB" -s "$DEV" get-state >/dev/null 2>&1 || die "пристрою $DEV немає (перевір adb devices). Серіал задається через -s"
  "$ADB" -s "$DEV" wait-for-device
  wait_ui_ready
}

require_installed() {
  local p
  p="$(adb_sh "pm path $PKG")"
  printf '%s' "$p" | grep -q "$PRIV_DIR" ||
    die "модуль не лежить у $PRIV_DIR («${p:-порожньо}»). Спершу: $0 install <шлях/до/apk>"
}

# ---------- install ---------------------------------------------------------

# Оверлей після ребута знову ro, і `adb remount` бреше кодом 0: 26.10.2026 перший
# запуск «успішно» загнав APK в оверлей, який зник при перезавантаженні. Тому
# єдине, чому варто вірити, — рядок mount.
system_ext_is_rw() { adb_sh 'mount' | grep -E 'on /system_ext type overlay' | grep -q '(rw'; }

ensure_rw_system_ext() {
  local out
  "$ADB" -s "$DEV" root >/dev/null 2>&1
  "$ADB" -s "$DEV" wait-for-device; sleep 2
  out="$("$ADB" -s "$DEV" remount 2>&1 | tr -d '\r' | tail -1)"
  say "remount: $out"
  system_ext_is_rw && return 0

  # ДРУГИЙ remount — не «на всяк випадок», а вимога: після ребута оверлей
  # повертається ro, і перший виклик лише готує ґрунт для другого.
  say "перший remount не дав rw — другий (саме він обовʼязковий після ребута)"
  "$ADB" -s "$DEV" root >/dev/null 2>&1; sleep 2
  out="$("$ADB" -s "$DEV" remount 2>&1 | tr -d '\r' | tail -1)"
  say "remount (другий): $out"
  system_ext_is_rw && return 0

  die "/system_ext лишився ro після двох remount — push не має куди лягати. Порядок: emulator -avd <avd> -writable-system, adb root, adb disable-verity, reboot, і щойно ДРУГИЙ adb remount — див. docs/EMULATOR.md"
}

# Пастка на 80 МБ: /mnt/scratch у образі маленький, а `adb push` на повному
# повертає «1 file pushed» і мовчить про ENOSPC — файл зникає за секунду, і далі
# це виглядає як «модуль зламалося», хоч ламалося лише завантаження.
require_space() {
  local size="$1" avail="" fs existing="" need="" old=""
  for fs in /mnt/scratch /system_ext; do
    avail="$(adb_sh "df -k $fs" | awk 'NR==2 {print $4}')"
    [[ "$avail" =~ ^[0-9]+$ ]] || continue
    say "вільно на $fs: $((avail / 1024)) МБ"
    break
  done
  [[ -n "$avail" ]] || {
    say "увага: df не вдався — віримо лише перевірці файлу після push"
    return 0
  }

  # Другий запис поверх існуючого просить місце ДВІЧІ: стара копія в upper-шарі
  # звільняється не раніше, ніж ляже нова (саме так і приземлився ENOSPC).
  existing="$(adb_sh "stat -c '%s' $DEV_APK" | tr -dc '0-9')"
  need="$size"
  old=""
  if [[ -n "$existing" ]]; then
    need=$((size + existing))
    old=", ще $((existing / 1048576)) МБ старої копії в оверлеї"
  fi
  if (( avail * 1024 < need )); then
    die "на $fs вільно $((avail / 1024)) МБ, потрібно $(( (need + 1048575) / 1048576 )) МБ (новий APK $((size / 1048576)) МБ$old). push на повному поверне «1 file pushed», а файл зникне за секунду. Розчищає це лише дві дії поспіль: \`adb shell rm -f /mnt/scratch/overlay/system_ext/upper/priv-app/TesseraWelcome/TesseraWelcome.apk\` і РЕБУТ — виміряно 27.10.2026, що після \`rm\` df показує той самі 10 МБ (f2fs повертає сегменти тільки після перезавантаження), а після ребута — 33 МБ"
  fi
}

# Код виходу adb тут нічого не доводить — див. require_space. Віримо тільки
# прочитаному з пристрою розміру.
verify_pushed() {
  local dev_path="$1" expect="$2" got
  got="$(adb_sh "stat -c '%s' $dev_path" | tr -dc '0-9')"
  [[ "$got" == "$expect" ]] ||
    die "$dev_path не на місці (на пристроі «${got:-немає}» байт, чекали $expect). Це й є мовчазний ENOSPC на 80 МБ розділі /mnt/scratch — див. docs/EMULATOR.md"
  note "$dev_path = $got байт"
}

cmd_install() {
  local apk="${1:-}" size rootout pm path_out
  [[ -n "$apk" ]] || die "install потребує шляху до APK: $0 install <шлях/до/apk>"
  [[ -f "$apk" ]] || die "немає файла $apk (збери apps/welcome — див. apps/welcome/README.md)"
  [[ -f "$SRC_XML" ]] || die "немає дозвільного списку $SRC_XML"
  apk="$(cd "$(dirname "$apk")" && pwd)/$(basename "$apk")"
  size="$(file_size "$apk")"
  require_device

  rootout="$("$ADB" -s "$DEV" root 2>&1 | tr -d '\r')"
  if printf '%s' "$rootout" | grep -qi 'production builds'; then
    die "adb root заборонено — це user-образ, тож /system_ext у нього незаписуваний, а дозвіл доводиться видавати pm grant замість allowlist. Для цього прогону потрібен userdebug ($rootout)"
  fi

  # «Мовчки» — це те, через що година пошуку «модуль зламалося» виявлялася
  # повним диском. Друкуємо повний задум ДО будь-якого запису.
  say "Пишемо на $DEV:"
  printf '  APK   %s (%s байт) → %s\n' "$apk" "$size" "$DEV_APK"
  printf '  XML   %s (%s байт) → %s\n' "$SRC_XML" "$(file_size "$SRC_XML")" "$DEV_XML"
  printf '  і робимо reboot (без нього priv-app-політика не перечитується)\n'

  ensure_rw_system_ext
  require_space "$size"

  adb_sh "mkdir -p $PRIV_DIR" >/dev/null
  "$ADB" -s "$DEV" push "$(host_path "$apk")" "$DEV_APK" 2>&1 | tail -1
  verify_pushed "$DEV_APK" "$size"
  "$ADB" -s "$DEV" push "$(host_path "$SRC_XML")" "$DEV_XML" 2>&1 | tail -1
  verify_pushed "$DEV_XML" "$(file_size "$SRC_XML")"

  # 755 на каталог і 644 на файли — не косметика: PackageManager відмовляється
  # читати приватний apk, недоступний для читання іншими, а restorecon повертає
  # правильний SELinux-контекст після копіювання з /data, де він інший.
  say "права 755/644, root:root + restorecon"
  adb_sh "chmod 755 $PRIV_DIR
          chmod 644 $DEV_APK; chown root:root $DEV_APK
          chmod 644 $DEV_XML; chown root:root $DEV_XML
          restorecon -R $PRIV_DIR $DEV_XML 2>&1 | tail -2
          ls -lZ $PRIV_DIR/"

  say "reboot: саме boot показує, чи enforce-політика вбиває завантаження"
  "$ADB" -s "$DEV" reboot 2>&1 | tail -1
  "$ADB" -s "$DEV" wait-for-device
  wait_boot
  sleep 12

  say "докази (жодного adb install у цьому прогоні)"
  path_out="$(adb_sh "pm path $PKG")"; note "pm path: $path_out"
  pm="$(adb_sh "dumpsys package $PKG")"
  printf '  %s\n' "$(printf '%s' "$pm" | grep -m1 -E 'codePath=|flags=\[' | head -1)"
  ok_if "модуль лежить у priv-app partition"  [ -n "$(printf '%s' "$path_out" | grep "$PRIV_DIR")" ]
  ok_if "codePath = $PRIV_DIR"                [ -n "$(printf '%s' "$pm" | grep -m1 "codePath=$PRIV_DIR")" ]
  ok_if "PackageManager вважає його SYSTEM"   [ -n "$(printf '%s' "$pm" | grep -m1 'flags=\[.*SYSTEM')" ]
  ok_if "WRITE_SECURE_SETTINGS видано з allowlist"  [ -n "$(printf '%s' "$pm" | grep -m1 'WRITE_SECURE_SETTINGS: granted=true')" ]
  ok_if "MODIFY_DAY_NIGHT_MODE видано з allowlist"  [ -n "$(printf '%s' "$pm" | grep -m1 'MODIFY_DAY_NIGHT_MODE: granted=true')" ]
  printf '  рядки дозволів: %s\n' "$(printf '%s' "$pm" | grep -oE '(WRITE_SECURE_SETTINGS|MODIFY_DAY_NIGHT_MODE): granted=[a-z]+' | tr '\n' ' ')"

  report
}

# ---------- theme -----------------------------------------------------------

cmd_theme() {
  local step baseline cur nm i says log
  require_device; require_installed

  say "фон: журнал чистимо, тему ставимо на «ночі» і вирівнюємо базовий стан"
  adb_sh 'logcat -c' >/dev/null
  adb_sh 'cmd uimode night no' >/dev/null
  # Зауваження, а не прикраса: після `cmd uimode night no` сервіс на «no», а
  # рядок у Secure лишається з попереднього сеансу. Базу й сервіс вирівнюємо
  # свідомо, інакше перший вимір був би порівнянням із чужим станом.
  adb_sh 'settings put secure ui_night_mode 1' >/dev/null
  echo "  базовий стан: $(pair)"
  baseline="$(cur_uimode)"; note "базовий mCurUiMode=0x$baseline"
  ok_if "базовий стан світлий (0x11)" [ "$baseline" = "11" ]

  # Підготовка — та сама, що в `walk`: з піднятими прапорцями модуль виходить
  # миттєво (skipWhenAlreadyPassed читає device_provisioned), і тоді «тема не
  # застосовується» насправді означало б «майстра на екрані не було». 27.10.2026
  # саме так і сталось після успішного walk: theme впав, хоча тема й далі працювала.
  #
  # --fresh тут увімкнено примусово, бо підкоманди вимірюють різне: `walk`
  # свідомо перевіряє відновлення на кроці зупинки, тож для нього чистий стан —
  # опція. Темі байдуже, де майстер зупинився, — їй потрібен крок 4. А
  # `screens`-прохід фінальний тап свідомо не робить і лишає майстра на кроці 10,
  # тож без чищення `theme` падав би після КОЖНОГО такого проходу — це був би не
  # вимір, а ритуал повторних запусків.
  FRESH=1
  prepare_at_step_one
  step=1
  for i in 1 2 3 4 5; do
    [[ "$step" == "4" ]] && break
    # Без паузи дамп іноді знімає ще старий крок — тоді лічильник відстає і
    # прогін зупиняється на кроці 3, імітуючи «тема не працює».
    tap_node "Перейти до наступного кроку" >/dev/null || break
    sleep 2
    step="$(current_step)"
    echo "  тап $i → крок ${step:-порожньо}"
  done
  [[ "$step" == "4" ]] || die "до крока 4 (Вигляд) не дійшли — зараз на «${step:-невідомо}». Без цього вимір теми неможливий"

  say "крок 4: «Світла» — підготовка, базовий стан і так світлий"
  tap_node "Світла" || die "чип «Світла» не знайдено — це не крок 4"
  sleep 2; echo "  $(pair)"

  say "крок 4: ТАП «Темна» (тап міг би й проігнорувати вибір, і «не застосовано» тоді означало б просто «не було виклику» — тому «Світла» спершу)"
  tap_node "Темна" || die "чип «Темна» не знайдено на кроці 4"
  echo "  -- відразу --"; echo "  $(pair)"
  cur="$(cur_uimode)"; nm="$(night_mode)"
  # Головне твердження всього розділу: сервіс змінив режим БЕЗ перезавантаження.
  # Сирий запис у Secure давав рядок у базі й mCurUiMode=0x11 — тобто телефон не
  # темнів, і читання бази це ховало.
  ok_if "mCurUiMode змінився на 0x21 у ту ж секунду" [ "$cur" = "21" ]
  ok_if "mNightMode = 2 (yes)"                        [ "$nm" = "2" ]
  ok_if "рядок у Secure теж 2"                        [ "$(secure_val ui_night_mode)" = "2" ]
  sleep 1; echo "  -- +1 с --";  echo "  $(pair)"
  ok_if "через 1 с тема ще 0x21 (не відкотилась)"     [ "$(cur_uimode)" = "21" ]
  sleep 3; echo "  -- +4 с --";  echo "  $(pair)"
  ok_if "через 4 с тема ще 0x21 (застосовано наживо)" [ "$(cur_uimode)" = "21" ]
  screenshot "theme-dark.png"

  say "що каже сама вітальня (її стан, а не наш висновок) — саме на цьому тапі"
  # Твердження читається ДО зворотного тапу: після «Світла» екран уже показує
  # інший стан, і «збіг слів модуля з сервісом» став би порівнянням із чужим кадром.
  says="$(dump_ui | grep -oE '(text|content-desc)="[^"]*(Застосовано|не вдало|не переключено|не застосов|записано|всієї системи)[^"]*"' | sort -u)"
  printf '  %s\n' "${says:-(жодного рядка про застосування в дампі — це вже підозра)}"
  # Той самий оракул, який і показав дефект: винятку немає, а теми немає. Тож
  # збіг слів модуля зі станом сервісу — окреме твердження, а не деталь.
  ok_if "модуль сам каже «Застосовано до всієї системи»" [ -n "$(printf '%s' "$says" | grep -m1 'Застосовано до всієї системи')" ]

  say "чи скаржився сервіс"
  log="$(adb_sh 'logcat -d -v brief' | grep -iE 'UiModeManager|Night mode locked|SecurityException' | head -6)"
  [[ -n "$log" ]] && printf '  %s\n' "$log"
  ok_if "у журналі немає «Night mode locked» / SecurityException" [ -z "$(printf '%s' "$log" | grep -iE 'Night mode locked|SecurityException')" ]

  say "зворот «Світла»: 0x21 -> 0x11 той самий рух у бік світлого, теж без ребута"
  tap_node "Світла" || die "чип «Світла» знову не знайдено"
  sleep 2; echo "  $(pair)"
  ok_if "зворот повертає 0x11 без ребута"              [ "$(cur_uimode)" = "11" ]

  report
}

# ---------- walk ------------------------------------------------------------

flags_state() {
  printf '  device_provisioned=%s  user_setup_complete=%s  font_scale=%s  ui_night_mode=%s\n' \
    "$(raw_global device_provisioned)" "$(raw_secure user_setup_complete)" \
    "$(raw_secure font_scale)" "$(raw_secure ui_night_mode)"
}

# Спільна підготовка `walk` і `screens`: чистий стан модуля, скинуті прапорці,
# старт явним cmp і підтвердження, що майстер справді на кроці 1.
prepare_at_step_one() {
  local start
  if [[ "$FRESH" == "1" ]]; then
    say "--fresh: pm clear $PKG — це прибере збережені вибори модуля й крок, на якому він зупинився"
    adb_sh "pm clear $PKG" | head -2
  fi

  say "стан прапорців ДО проходу"
  flags_state
  # Модуль виходить відразу, якщо система вже налаштована
  # (skipWhenAlreadyPassed читає device_provisioned), тому «скинути» тут свідомо
  # і вголос: без цього прохід був би порожнім.
  if [[ "$(global_val device_provisioned)" != "0" || "$(secure_val user_setup_complete)" != "0" ]]; then
    say "скидаємо прапорці, щоб майстер взагалі показався: пишемо 0 у global.device_provisioned і secure.user_setup_complete"
    adb_sh 'settings put global device_provisioned 0' >/dev/null
    adb_sh 'settings put secure user_setup_complete 0' >/dev/null
    flags_state
  fi

  say "старт майстра (явний cmp)"
  adb_sh "am start -a android.intent.action.MAIN -c android.intent.category.SETUP_WIZARD -n $COMPONENT" | head -1
  start="$(wait_step_marker)"
  echo "  фокус: $(adb_sh 'dumpsys window' | grep -m1 -E 'mCurrentFocus' | sed 's/^ *//')"
  [[ -n "$start" ]] || {
    # Порожній маркер буває з двох причин, і плутати їх — значить написати «тема
    # не працює» про екран, на якому майстра не було. Тому друкуємо те, що
    # розрізняє: яка активність лишилася на екрані і які прапорці.
    echo "  активна саме зараз: $(adb_sh 'dumpsys activity activities' | grep -m1 -E 'topResumedActivity' | sed 's/^ *//')"
    flags_state
    die "за ~60 с після старту на екрані немає маркера «крок N із 10». Рядок «активна саме зараз» каже, котра з двох причин: активність лишилася нашою — екран ще не встиг, повтори прогін; лишилася чужою — модуль вийшов сам, бо система вже налаштована"
  }

  # Модуль свідомо відновлює крок, на якому зупинився, тож «1…10» без --fresh
  # виміряти неможливо — і саме ця перевірка відрізняє «не той стан старту» від
  # «дефект вітальні».
  [[ "$start" == "1" ]] || die "майстер відновився на кроці $start, а не почав з 1 — він памʼятає місце зупинки. Послідовність «1…10» вимірюється лише з чистого стану: повтори з --fresh"
  echo "  старт підтверджено: крок 1"
}

cmd_walk() {
  local seen="" norm n st dump after still
  require_device; require_installed
  prepare_at_step_one

  for n in 1 2 3 4 5 6 7 8 9 10; do
    # Один дамп на крок: з нього й номер, і тексти — два окремих зняття зловили б
    # різні кадри, і «крок 6 із 10» раптово стало б «кроком 5».
    dump="$(dump_ui)"
    st="$(printf '%s' "$dump" | grep -oE 'крок [0-9]+ із [0-9]+' | head -1 | grep -oE '[0-9]+' | head -1)"
    seen+="${st} "
    # Перші рядки повними атрибутами, а не байтовим відрізком: head -c ламає
    # український текст посеред символу.
    echo "  крок ${st:-?}: $(printf '%s' "$dump" | grep -oE 'text="[^"]+"' | head -4 | tr '\n' '|')"
    screenshot "walk-step${st:-na}.png"
    [[ "$st" == "10" ]] && break
    tap_node "Перейти до наступного кроку" >/dev/null || { echo "  далі йти нічим — «Далі» не знайдено"; break; }
    sleep 2
  done
  # Без лапок: word splitting з'їдає зайві прогалини, а пропущений крок лишає
  # діру — і рядок уже не «1 2 … 10».
  norm=$(echo $seen)
  ok_if "смужка прогресу показала всі десять кроків по порядку" [ "$norm" = "1 2 3 4 5 6 7 8 9 10" ]
  note "послідовність кроків з дампа: $norm"

  say "тап «Увійти в Tessera» — саме він пише прапорці"
  tap_node "Увійти в Tessera" || die "фінальної кнопки не знайдено — до «Готово» не дійшли"
  sleep 3
  say "стан прапорців ПІСЛЯ проходу"
  flags_state
  ok_if "device_provisioned = 1"  [ "$(global_val device_provisioned)" = "1" ]
  ok_if "user_setup_complete = 1" [ "$(secure_val user_setup_complete)" = "1" ]
  # Модуль лише викликає finish(): домашній екран під ним тримає система. Тому
  # твердження чесне — нашого вікна більше немає в дампі. Порожній дамп
  # (uiautomator не зловив idle) доказом НЕ вважається: інакше перевірка
  # проходила б рівно тоді, коли нічого не перевірено.
  after="$(dump_ui)"; still=0
  printf '%s' "$after" | grep -q 'Прогрес майстра' && still=1
  printf '%s' "$after" | grep -q '<hierarchy'      || still=1
  ok_if "майстер закрився (дамп живий і без його смужки прогресу)" [ "$still" = "0" ]
  screenshot "walk-after.png"

  report
}

# ---------- screens: кадри всіх кроків в одній темі --------------------------

cmd_screens() {
  local want="${1:-dark}" want_mode expect_hex seen="" norm n st dump got VIDEO_DEV=""
  case "$want" in
    dark)  want_mode=2; expect_hex=21 ;;
    light) want_mode=1; expect_hex=11 ;;
    *) die "screens буває <dark|light>, а не «$want»" ;;
  esac
  require_device; require_installed

  # Тему ставимо сервісом ДО проходу, а не на кроці 4: інакше перші три кадри
  # лишилися б в іншій темі, і набору «десять кадрів темної» не вийшло б.
  say "тема для всього набору: $want (сервіс, а не лише рядок у базі)"
  if [[ "$want" == "dark" ]]; then adb_sh 'cmd uimode night yes' >/dev/null
  else adb_sh 'cmd uimode night no' >/dev/null; fi
  adb_sh "settings put secure ui_night_mode $want_mode" >/dev/null
  sleep 2
  echo "  стан сервісу: $(pair)"
  ok_if "тема $want застосована наживо: mCurUiMode=0x$expect_hex" [ "$(cur_uimode)" = "$expect_hex" ]
  ok_if "mNightMode = $want_mode"                                 [ "$(night_mode)" = "$want_mode" ]

  # Той самий випадок, що й у `theme`: набір «десять кадрів по порядку»
  # вимірюється лише з крока 1, а попередній прохід лишає майстра там, де його
  # зупинили. Тому чищення тут не опція, а частина виміру.
  FRESH=1
  prepare_at_step_one

  # Запис ведеться під весь прохід, а не окремою командою: окремо відео знімало б
  # інший стан (тему, крок, на якому зупинився майстер), і «кадр N» не збігався б
  # з «секундою N» відеорядку. --time-limit 180 на десяти кроках — з тричі запасом.
  if [[ "$VIDEO" == "1" ]]; then
    VIDEO_DEV="/sdcard/oobe-$want.mp4"
    record_start "$VIDEO_DEV"
  fi

  for n in 1 2 3 4 5 6 7 8 9 10; do
    dump="$(dump_ui)"
    st="$(printf '%s' "$dump" | grep -oE 'крок [0-9]+ із [0-9]+' | head -1 | grep -oE '[0-9]+' | head -1)"
    seen+="${st} "
    [[ "$st" == "$n" ]] || say "увага: чекали крок $n, а дамп показав «${st:-порожньо}»"
    screenshot "$want/step${st:-na}.png" "$want"
    [[ "$st" == "10" ]] && break
    tap_node "Перейти до наступного кроку" >/dev/null || { echo "  далі йти нічим — «Далі» не знайдено"; break; }
    sleep 2
  done
  [[ -z "$VIDEO_DEV" ]] || record_finish "$VIDEO_DEV" "$want" "oobe-$want.mp4"
  norm=$(echo $seen)
  ok_if "кадри знято з усіх десяти кроків по порядку" [ "$norm" = "1 2 3 4 5 6 7 8 9 10" ]
  # Файли рахуємо на диску, а не за власними рядками «знімок →»: adb pull уміє
  # мовчки не спрацювати, і тоді «десять кадрів» було б брешнею в документації.
  got="$(ls -1 "$OUT_DIR/$want"/step*.png 2>/dev/null | wc -l | tr -d ' ')"
  ok_if "на диску лежить рівно 10 кадрів ($OUT_DIR/$want)" [ "$got" = "10" ]
  note "послідовність кроків з дампа: $norm; кадрів на диску: $got"

  say "прапорців не чіпали: фінальний тап не робився, щоб майстер лишився перед «Готово»"
  flags_state
  report
}

usage() {
  # Верbatim — весь верхній коментар, доки не почнеться код. Старий «sed -n 2,19p»
  # обрізав шапку на фіксованому рядку й уже встиг загубити пояснення про ADB=.
  awk 'NR>1 { if (!/^#/) exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
  cat <<EOF

Команди:
  install <шлях/до/apk>  priv-app + дозвільний список у /system_ext, права
                         755/644, restorecon, reboot і докази з dumpsys package
  theme                  вимір пари «рядок у Secure» / «стан сервісу» до тапу,
                         відразу після тапу «Темна» і на +1/+4 с, плюс зворот.
                         Стан модуля чиститься завжди: темі потрібен крок 4, а не
                         доказ відновлення
  walk                   десять кроків зі станом прапорців до й після;
                         --fresh — спершу \`pm clear\` модуля, бо без нього він
                         відновлюється на кроці зупинки й «1…10» не вимірюється
  screens <dark|light>   кадри всіх десяти кроків в одній темі у
                         \$OUT_DIR/<тема>/stepN.png; тема ставиться ДО проходу,
                         прапорців не чіпає: фінальний тап не робиться.
                         Стан модуля чиститься завжди (потрібен крок 1).
                         --video — той самий прохід ще й записується
                         screenrecord-ом на пристрої і стягується як
                         <тема>/oobe-<тема>.mp4 (Q97: коротке відео майстра)

Опції: -s <серіал>  (типово $DEV); шлях до adb: ADB=/шлях/до/adb
Змінні: OUT_DIR=<каталог знімків>  (типово <репо>/out/welcome-live)
EOF
}

CMD=""
FRESH=0
VIDEO=0
ARGS=()
# Без break: `walk --fresh` із break-ом тихо втрачав би --fresh, і прогін
# стартував би брудним, а твердження «1…10» провалилося б без пояснення.
while [[ $# -gt 0 ]]; do
  case "$1" in
    -s) DEV="${2:?-s потребує серіал}"; shift 2 ;;
    --fresh) FRESH=1; shift ;;
    --video) VIDEO=1; shift ;;
    -h|--help) usage; exit 0 ;;
    install|theme|walk|screens) CMD="$1"; shift ;;
    *) ARGS+=("$1"); shift ;;
  esac
done
[[ -n "$CMD" ]] || { usage >&2; exit 2; }
[[ ${#ARGS[@]} -le 1 ]] || { echo "Зайві аргументи: ${ARGS[*]}" >&2; exit 2; }
# Прапорець, який ніхто не прочитав, гірший за відмову: `theme --video` інакше
# тихо відпрацював би без відео й з кодом 0.
[[ "$VIDEO" == "0" || "$CMD" == "screens" ]] \
  || { echo "--video має сенс лише з screens (walk і theme не складають кадрів у <тема>/)" >&2; exit 2; }

case "$CMD" in
  install) cmd_install "${ARGS[0]:-}" ;;
  theme)   cmd_theme ;;
  walk)    cmd_walk ;;
  screens) cmd_screens "${ARGS[0]:-dark}" ;;
esac
