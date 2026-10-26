#!/usr/bin/env bash
# tessera-harden.sh — «посилені замовчування» одним прапорцем.
#
# Чесність як первісна вимога. Скрипт оперує ТИМИ ключами settings, назву яких
# знає з контракту android.provider.Settings, і каже окремо, що саме не
# перевірено на нашому образі. Нічого не вигадуємо: зайвий «універсальний»
# ключ, якого немає, — це брехня в dry-run і помилка в --apply.
#
# Рівні довіри кожного пункту:
#   known       — назва ключа з контракту AOSP/Settings; пишеться з --apply.
#                 Статус «перевірено на образі» при цьому НЕ проставлено:
#                 жоден пункт не прогнано на нашій x86_64-збірці.
#   unverified  — назва під сумнівом (могла зникнути/перейменуватися в
#                 Android 15). Типово лише друкується разом із командою
#                 пошуку; пишеться тільки за --include-unverified.
#   doc         — пояснення без команди; ми не знаємо ключа й не робимо вигляду.
#
#   tessera-harden.sh                     # dry-run (типово): лише перелік
#   tessera-harden.sh --probe             + читає фактичні значення з пристрою
#   tessera-harden.sh --apply             # пише відомі ключі
#   tessera-harden.sh --apply --include-unverified
#   tessera-harden.sh --serial emulator-5554
set -euo pipefail

SCRIPT_VERSION="1.0.0"
TAG="[harden]"
SERIAL="${TESSERA_ADB_SERIAL:-}"
APPLY=0
PROBE=0
INCLUDE_UNVERIFIED=0
ADB="${ADB:-adb}"
# Формат пункту:  рівень|namespace|ключ|значення|пояснення
ITEMS=()
# ---------- відомі назви (контракт android.provider.Settings) ---------------
# Увага: «known» тут означає «назву ключа взято з контракту Settings», а НЕ
# «перевірено на нашому x86_64-образі». Другого ми ще не робили — див. TODO.
ITEMS+=("known|global|adb_enabled|0|Вимкнути adb після налагодження: без нього пристрій не віддає shell чужому комп'ютеру. Назва ключа відома; запис перевіряємо повторним читанням.")
ITEMS+=("known|global|development_settings_enabled|0|Сховати панель розробника, коли налагодження завершено.")
ITEMS+=("known|secure|ui_night_mode|1|Темна тема (1 = увімкнено попри системну). Значення беремо з документації OOBE; на образі не звірено.")
ITEMS+=("known|secure|location_mode|0|Вимкнути служби локації цілком (0 = вимкнено).")
ITEMS+=("known|global|auto_time|1|Автоматичний час із мережі — менше розходжень у журналах.")
ITEMS+=("known|global|auto_time_zone|1|Те саме для часового поясу.")
# ---------- під сумнівом ----------------------------------------------------
ITEMS+=("unverified|secure|install_non_market_apps|0|Історичний ключ дозволу на APK з боку. У Android 12+ його замінив per-app дозвіл «небезпечні джерела», тому в Android 15 ключа може взагалі не бути. Знайдіть на пристрої: adb shell settings list secure | grep -i non_market (і global/system).")
ITEMS+=("unverified|secure|share_device_proximity|0|НАЗВА ПІД СУМНІВОМ: такої назви в контракті Settings я впевнено не знаю. Перш застосовувати — знайдіть ключ на пристрої: adb shell settings list secure | grep -i proximity (і global/system).")
ITEMS+=("unverified|global|zen_mode|1|Межі DND: історично DND тримає глобальний ключ zen_mode, але в Android 15 він, схоже, належить системі й запис з shell може ігноруватися. НЕ ЗВІРЕНО; правильний шлях — налаштування → Sound → DND, або знайдіть ключ: adb shell settings list global | grep -i zen")
ITEMS+=("unverified|global|package_verifier_enable|1|Перевірка застосунків перед встановленням. Назва з контракту AOSP, на нашому образі не перевірена.")
ITEMS+=("unverified|secure|lineage_stats_enabled|0|Статистика використання LineageOS (якщо збірка має відповідний постачальник статистики). НЕ ЗВІРЕНО: точний ключ читайте на пристрої — adb shell settings list secure | grep -iE 'stats|telemetry|analytics'.")
# ---------- лише пояснення --------------------------------------------------
ITEMS+=("doc|-|-|-|Телеметрії у LineageOS за замовчуванням немає: проєкт не збирає статистику на власний сервер. Тому «вимкнути телеметрію» тут означає радше не вмикати сторонні збирачі, ніж шукати прапорець.")
ITEMS+=("doc|-|-|-|Автоматичні оновлення й хмарні бекапи — у самих застосунках, не в глобальному settings. Єдиного «вимкнути все» ключа не існує; для кожного застосунку — власний дозвіл/перемикач.")
ITEMS+=("doc|-|-|-|Наш власний код: os.tessera.welcome (системний OOBE, priv-app) і os.tessera.halo (світловий край сповіщень). Жодного мережевого збору в них немає — це можна перечитати в маніфестах і коді, а не вимкнути прапорцем.")
ITEMS+=("doc|-|-|-|Образ тримає ro.control_privapp_permissions=enforce; adb root на production (ro.build.type=user) заборонено. Частина записів нижче без root не вдасться — скрипт перевірить повторним читанням і скаже про це.")

usage() {
  cat <<'EOF'
Використання: tessera-harden.sh [--apply] [--probe] [--include-unverified]
                               [--serial <SN>] [--help]

  (без прапорців)     dry-run: надрукувати, що б скрипт зробив; НИЧОГО не пише
  --probe             прочитати фактичні значення з пристрою (readonly) і показати
  --apply             записати пункти рівня known; без --apply запису не буде
  --include-unverified разом із --apply чіпати ще й пункти «під сумнівом»
  --serial <SN>       конкретний adb-пристрій (або TESSERA_ADB_SERIAL)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply)               APPLY=1; shift ;;
    --probe)               PROBE=1; shift ;;
    --include-unverified)  INCLUDE_UNVERIFIED=1; shift ;;
    --serial)              SERIAL="${2:-}"; [[ -n "$SERIAL" ]] || { echo "$TAG --serial порожній" >&2; exit 2; }; shift 2 ;;
    -h|--help)             usage; exit 0 ;;
    *) echo "$TAG невідомий аргумент: $1" >&2; usage; exit 2 ;;
  esac
done

say() { echo "$TAG $*"; }
warn() { echo "$TAG УВАГА: $*" >&2; }

adb_cmd() {
  if [[ -n "$SERIAL" ]]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi
}
shell_read() {
  local out
  out="$(adb_cmd shell "$@" 2>/dev/null || true)"
  printf '%s' "$out" | tr -d '\r'
}
# value_or_null — «null» і порожній рядок розрізняємо: обидва означають «ключа немає»
read_key() { shell_read settings get "$1" "$2"; }

DEVICE_OK=0
if command -v "$ADB" >/dev/null 2>&1 && adb_cmd get-state >/dev/null 2>&1; then
  DEVICE_OK=1
fi
if [[ "$PROBE" == 1 || "$APPLY" == 1 ]] && [[ "$DEVICE_OK" == 0 ]]; then
  echo "$TAG --probe/--apply потребують adb-пристрою; $ADB devices -l нічого не показало" >&2
  exit 1
fi
if [[ "$DEVICE_OK" == 1 ]]; then
  say "пристрій: $(adb_cmd get-serialno 2>/dev/null | tr -d '\r' || printf 'невідомо')"
  PROP_TYPE="$(shell_read getprop ro.build.type)"
  say "ro.build.type=${PROP_TYPE:-невідомо}  uid=$(shell_read id -u)"
  if [[ "$PROP_TYPE" == "user" ]]; then
    warn "production-образ: частина записів у secure/global буде відхилена системою"
  fi
else
  say "adb-пристрою немає — працюємо в dry-run (список пунктів без значень з пристрою)"
  PROBE=0
fi

MODE="DRY-RUN (нічого не записано)"
if [[ "$APPLY" == 1 ]]; then MODE="APPLY"; fi
echo
say "версія скрипту $SCRIPT_VERSION | режим: $MODE"
if [[ "$APPLY" == 0 ]]; then say "щоб справді записати — додайте --apply"; fi
echo

applied=0
skipped=0
rejected=0
for item in "${ITEMS[@]}"; do
  IFS='|' read -r level ns key val why <<<"$item"
  case "$level" in
    doc)
      printf -- '—   [doc]     %s\n' "$why"
      skipped=$((skipped + 1))
      continue ;;
    known|unverified) : ;;
    *) warn "пункт з невідомим рівнем: $item"; continue ;;
  esac

  label="[$level]"
  actual="-"
  if [[ "$DEVICE_OK" == 1 && "$PROBE" == 1 ]]; then
    actual="$(read_key "$ns" "$key")"
    if [[ -z "$actual" || "$actual" == "null" ]]; then actual="(ключа немає в $ns)"; fi
  fi

  if [[ "$level" == "unverified" && "$INCLUDE_UNVERIFIED" == 0 ]]; then
    printf -- '✗   %-12s %s/%s → %s — НЕ ЗАСТОСОВАНО (назва не звірена)\n' "$label" "$ns" "$key" "$val"
    printf '    пояснення: %s\n' "$why"
    skipped=$((skipped + 1))
    continue
  fi

  if [[ "$APPLY" == 0 ]]; then
    printf -- '•   %-12s dry-run: adb shell settings put %s %s %s\n' "$label" "$ns" "$key" "$val"
    if [[ "$actual" != "-" ]]; then
      printf '         поточне значення на пристрої: %s\n' "$actual"
    fi
    printf '    пояснення: %s\n' "$why"
    skipped=$((skipped + 1))
  else
    before="$(read_key "$ns" "$key")"
    adb_cmd shell settings put "$ns" "$key" "$val" >/dev/null 2>&1 || warn "не вдалося виконати запис $ns/$key"
    after="$(read_key "$ns" "$key")"
    if [[ "$after" == "$val" ]]; then
      printf -- '+   %-12s записано: %s/%s = %s (було: %s)\n' "$label" "$ns" "$key" "$val" "${before:-null}"
      applied=$((applied + 1))
    else
      printf -- '!   %-12s відхилено: просили %s, на пристрої %s\n' "$label" "$val" "${after:-null}"
      printf '    причина, найімовірніше: ключ належить system-процесу, не існує в Android 15\n'
      printf '    або вимагає дозволу, якого shell не має. НЕ ЗВІРЕНО на цьому образі.\n'
      rejected=$((rejected + 1))
    fi
  fi
done

echo
say "підсумок: застосовано $applied, відхилено $rejected, не застосовано $skipped"
if [[ "$APPLY" == 1 ]]; then
  say "перевірте результат руками: adb shell settings list global | sort > after.txt"
  say "Масового повернення «як було» скрипт НЕ робить: значення before не збережені."
  say "Якщо це важливо — спершу зробіть знімок: build/scripts/tessera-backup.sh"
else
  say "нічого не змінено. Для запису відомих пунктів: --apply"
  say "Для пунктів «під сумнівом» спершу --probe і пошук назви на пристрої:"
  say "  adb shell settings list secure | grep -i <частина_назви>"
  say "  adb shell settings list global | grep -i <частина_назви>"
fi

# TODO(не звірено): коли матимемо userdebug-збірку на x86_64, прогнати
#   --probe --apply і переставити рівень known/unverified за фактичним
#   результатом повторного читання. До того всі «перевірено» тут — лише
#   за назвою з контракту Settings, не за нашим образом.
exit 0
