#!/usr/bin/env bash
# tessera-microg-check.sh — діагностичний помічник: що на пристрої є з Google-
# сервісів і microG, у якому стані образ, і що саме людина має зробити руками.
#
# Чому це підказка, а не інсталятор. У образі TesseraOS немає ні GMS, ні microG,
# і бути не може (див. docs/GAPPS.md): власних модулів рівно два, а в маніфесті
# бази проєктів microG/GmsCore просто немає. microG — це МОЖЛИВІСТЬ користувача
# ПІСЛЯ встановлення образу. Тому скрипт:
#   * лише читає стан системи (readonly adb shell: getprop, pm list, df, id);
#   * нічого не завантажує, не встановлює, не пише в settings;
#   * наприкінці друкує нумерований текстовий план, який виконує людина.
#
#   tessera-microg-check.sh                     # живий пристрій чи емулятор
#   tessera-microg-check.sh --serial emulator-5554
#   tessera-microg-check.sh --offline           # лише план, без adb
#   tessera-microg-check.sh --help
set -euo pipefail

SCRIPT_VERSION="1.0.0"
TAG="[microg-check]"
SERIAL="${TESSERA_ADB_SERIAL:-}"
OFFLINE=0
ADB="${ADB:-adb}"
IMAGE="tessera-vm-<версія>-vega.tar.xz"

# Маркери пакетів. Увага до чесності: microG Services Core використовує ТОЙ САМИЙ
# ідентифікатор com.google.android.gms, що й справжній GMS, тому за одним лише
# іменем пакету «GMS» і «microG» не розрізнити. Нижче ми печатаємо ще й шлях
# встановлення (pm path) — а остаточний висновок робить читач.
GMS_MARKERS='^package:(com\.google\.android\.gms|com\.google\.android\.gsf|com\.android\.vending)'
OTHER_MARKERS='^package:(org\.microg\.|com\.google\.android\.gsf\.|org\.fdroid\.fdroid)'

usage() {
  cat <<'EOF'
Використання: tessera-microg-check.sh [--serial <SN>] [--offline] [--help]

  --serial <SN>  конкретний adb-пристрій (або змінна середовища TESSERA_ADB_SERIAL)
  --offline      не чіпати adb; видрукувати лише нумерований план кроків
  --help         ця довідка

Скрипт readonly: він нічого не встановлює і нічого не завантажує.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)   SERIAL="${2:-}"; [[ -n "$SERIAL" ]] || { echo "$TAG --serial порожній" >&2; exit 2; }; shift 2 ;;
    --offline)  OFFLINE=1; shift ;;
    -h|--help)  usage; exit 0 ;;
    *) echo "$TAG невідомий аргумент: $1" >&2; usage; exit 2 ;;
  esac
done

say() { echo "$TAG $*"; }
note() { echo "      $*"; }

# ---------- readonly-обгортка над adb ---------------------------------------
adb_cmd() {
  if [[ -n "$SERIAL" ]]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi
}
# shell_readonly <команда> — повертає вивод або пустий рядок; нічого не ламує.
shell_readonly() {
  local out
  out="$(adb_cmd shell "$@" 2>/dev/null || true)"
  # Windows-крайки виводу adb прибиремо, інакше порівняння з верхнім регістром брешуть
  printf '%s' "$out" | tr -d '\r'
}

print_plan() {
  cat <<EOF
$TAG Нумерований план — ці кроки виконує ЛЮДИНА, скрипт їх не робить:

  1. Зробіть бекап користувацького стану, якщо на пристрої є цінні дані:
       build/scripts/tessera-backup.sh
  2. Розблокуйте завантажувач (bootloader unlock). Це затирає користувацькі
     дані й є незворотним у бік гарантійного стану:
       fastboot oem unlock            # частина пристроїв
       fastboot flashing unlock       # сучасніші пристрої
     НЕ ЗВІРЕНО для вашого пристрою: точну форму команде дивіться у
     документації виробника. Для x86_64 VM/емулятора завантажувач зазвичай
     не блокується взагалі — крок пропускаєте.
  3. Прошийте образ $IMAGE (x86_64 VM/емулятор). Власних модулів у ньому два:
     системний OOBE os.tessera.welcome (priv-app) і os.tessera.halo.
     Лаунчера та шести системних застосунків у продукті немає — на їхнє місце
     нічого не ставати не повинно.
  4. Перший запуск: OOBE (os.tessera.welcome) проведе через налаштування.
     Образ тримає ro.control_privapp_permissions=enforce, тому привілеї
     перелічені заздалегідь, а adb root на production-образі заборонено.
  5. Google-сервісів у образі немає й не буде. Якщо потрібні push-повідомлення,
     OAuth «Увійти через Google» чи Locator — поставте microG САМІ зі сторінки
     проєкту (microg.org) після встановлення образу:
       а) APK microG Services Core + Framework, або
       б) F-Droid / Aurora Store, і вже з них microG;
       в) для збірки власного образу — local_manifests від lineage.microg.org
          на білд-боксі, тоді модулі справді існують у дереві.
     Цей скрипт навмисно НЕ качає жодного APK: бінарники GApps/microG
     заборонені політикою репозиторію (policy.yml, check-no-binary.yml).
  6. Після встановлення microG — увімкніть signature spoofing
     (Settings → Apps & notifications → microG → enable signing spoofing)
     без цього attestation-залежні застосунки не працюватимуть.
  7. Перевірте результат своїми руками: запустіть застосунок, якому потрібні
     push-повідомлення, і вже тоді оцінюйте. Автоматичної перевірки «чи
     attestation пройдено» тут немає — див. «що цей скрипт НЕ перевіряє».

$TAG Що цей скрипт НЕ перевіряє:
  * чи пройде attestation/SafetyNet чи Play Integrity на конкретному застосунку;
  * чи доступні мережеві ендпоінти Google (тільки стан пакетів на пристрої);
  * цілісність і справжність будь-якого завантаженого APK (ми їх не беремо);
  * SELinux-заборони, креші, стан OTA — для цього tessera-logs.sh;
  * чи образ взагалі прошитий: ми бачимо лише той пристрій, що на дроті;
  * юр. умови ліцензії Google — читайте docs/GAPPS.md.
EOF
}

if [[ "$OFFLINE" == 1 ]]; then
  say "режим --offline: adb не чіпаємо, версія скрипту $SCRIPT_VERSION"
  print_plan
  exit 0
fi

echo
say "версія скрипту $SCRIPT_VERSION"
if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "$TAG adb не знайдено в PATH — встановіть Android SDK platform-tools або запустіть --offline" >&2
  exit 1
fi

# ---------- стан пристрою ---------------------------------------------------
STATE="$(adb_cmd get-state 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$STATE" ]]; then
  echo "$TAG adb-пристрою немає (get-state не відповів). Перевірте: $ADB devices -l" >&2
  echo "$TAG якщо пристрій у списку як unauthorized — прийміть діалог дозволу на екрані," >&2
  echo "$TAG якщо як offline — перепідключіть; або скористайтесь --offline для самого плану." >&2
  exit 1
fi
SERIAL_SEEN="$(adb_cmd get-serialno 2>/dev/null | tr -d '\r' || true)"
say "пристрій: ${SERIAL_SEEN:-невідомо} (стан: $STATE)"
[[ "$STATE" == "device" ]] || say "увага: стан $STATE, частина перевірок буде недоступна"

# ---------- чи є root (чесно, без видимості) --------------------------------
UID_VAL="$(shell_readonly id -u)"
IS_ROOT=0
if [[ "$UID_VAL" == "0" ]]; then
  IS_ROOT=1
  say "поточна adb-сесія має uid=0 (root)"
else
  say "root НЕМАЄ (uid=${UID_VAL:-невідомо}). На production-образі adb root заборонено —"
  note "це очікуваний стан, а не помилка. Без root недоступні: dmesg, частина dumpsys,"
  note "перевірка вмісту /system через ls. Нижче позначено «потрібен root»."
fi

# ---------- властивості образу ---------------------------------------------
PROP_TYPE="$(shell_readonly getprop ro.build.type)"
PROP_DEBUG="$(shell_readonly getprop ro.debuggable)"
PROP_REL="$(shell_readonly getprop ro.build.version.release)"
PROP_MODEL="$(shell_readonly getprop ro.product.model)"
PROP_ENFORCE="$(shell_readonly getprop ro.control_privapp_permissions)"
say "властивості образу:"
note "ro.build.type                = ${PROP_TYPE:-(порожньо)}"
note "ro.debuggable                = ${PROP_DEBUG:-(порожньо)}"
note "ro.build.version.release     = ${PROP_REL:-(порожньо)}"
note "ro.product.model             = ${PROP_MODEL:-(порожньо)}"
note "ro.control_privapp_permissions = ${PROP_ENFORCE:-(порожньо)}"
case "$PROP_TYPE" in
  user)   note "user = production: adb root заблокований, підпис перевіряється" ;;
  userdebug|eng) note "$PROP_TYPE: розробчий образ, є adb root і вільніші dumpsys" ;;
  *)      note "НЕ ЗВІРЕНО: неочікуване значення ro.build.type — звірте вручну" ;;
esac

# ---------- списки пакетів --------------------------------------------------
PKG_LIST="$(shell_readonly pm list packages)"
if [[ -z "$PKG_LIST" ]]; then
  say "НЕ ВДАЛОСЯ отримати список пакетів (pm list packages порожній)"
  note "для userdebug-образу спробуйте: adb wait-for-device, потім повторити запуск"
else
  PKG_COUNT="$(printf '%s\n' "$PKG_LIST" | grep -c '^package:' || true)"
  say "пакетів на пристрої: ${PKG_COUNT}"

  GMS_HITS="$(printf '%s\n' "$PKG_LIST" | grep -E "$GMS_MARKERS" || true)"
  if [[ -n "$GMS_HITS" ]]; then
    say "знайдені маркери Google-сервісів:"
    while IFS= read -r line; do note "${line#package:}"; done <<<"$GMS_HITS"
    note "увага: com.google.android.gms — те саме ім'я й у справжнього GMS, і в microG."
    note "щоб розрізнити, подивіться шлях встановлення:"
    PATH_GMS="$(shell_readonly pm path com.google.android.gms)"
    note "  pm path com.google.android.gms -> ${PATH_GMS:-(немає шляху)}"
    note "  /product/priv-app|/vendor/priv-app — схоже на власницький GMS;"
    note "  /data/app/... — схоже на встановлений користувачем пакет (microG чи GApps-пакет)."
    note "ЦЕ ПІДКАЗКА, НЕ ВИСНОВОК: достовірний знак — підпис, його тут не перевірено."
  else
    say "маркерів GMS (com.google.android.gms / gsf / com.android.vending) НЕ знайдено"
    note "це очікуваний стан образу TesseraOS — див. docs/GAPPS.md"
  fi

  MICROG_HITS="$(printf '%s\n' "$PKG_LIST" | grep -iE 'microg|org\.microg\.' || true)"
  if [[ -n "$MICROG_HITS" ]]; then
    say "явні microG-пакети (префікс org.microg. або ім'я з «microg»):"
    while IFS= read -r line; do note "${line#package:}"; done <<<"$MICROG_HITS"
  else
    say "явких microG-пакетів не знайдено"
  fi

  STORE_HITS="$(printf '%s\n' "$PKG_LIST" | grep -E "$OTHER_MARKERS" || true)"
  if [[ -n "$STORE_HITS" ]]; then
    note "суміжні пакети, через які microG зазвичай ставлять:"
    while IFS= read -r line; do note "  ${line#package:}"; done <<<"$STORE_HITS"
  else
    note "не знайдено F-Droid / Aurora Store — microG доведеться ставити APK-файлом"
    note "зі сторінки проєкту (користувач робить це сам; скрипт не качає) "
  fi

  printf '%s\n' "$PKG_LIST" | grep -qx 'package:os.tessera.welcome' &&
    note "наш OOBE os.tessera.welcome присутній" ||
    note "НЕ ЗНАЙДЕНО os.tessera.welcome — образ не прошито або модуль не доїхав"
  printf '%s\n' "$PKG_LIST" | grep -qx 'package:os.tessera.halo' &&
    note "наш світловий край os.tessera.halo присутній" ||
    note "НЕ ЗНАЙДЕНО os.tessera.halo — перевірте PRODUCT_PACKAGES (build/tessera.mk)"
fi

# ---------- вільне місце ----------------------------------------------------
# df читаємо на пристрої; на Android 15 /system зазвичай dm-redirect і readonly —
# тому це не «місце під APK», а індикатор розділа. Місце під встановлення — /data.
for mp in /system /product /vendor /data; do
  DF_LINE="$(shell_readonly df "$mp" | tail -n +2 | head -1)"
  if [[ -n "$DF_LINE" ]]; then
    note "df $mp: $DF_LINE"
  else
    note "df $mp: НЕ ВДАЛОСЯ прочитати (потрібен root або розділа немає)"
  fi
done

# ---------- attestation: лише пояснення ------------------------------------
say "підпис образу / attestation:"
note "автоматично тут не перевіряється. Імена сервісу й API з часом змінюються:"
note "SafetyNet-attestation Google оголосив застарілим на користь Play Integrity."
note "НЕ ЗВІРЕНО в цьому скрипті: актуальний статус API і чи microG його емулює —"
note "читайте microg.org/README та docs/GAPPS.md, а не цей вивід."

print_plan
exit 0
