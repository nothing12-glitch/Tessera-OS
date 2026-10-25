#!/usr/bin/env bash
# Ставлямо TesseraOS на вже запущену віртуальну машину з Android 13+.
#
# Навіщо це, якщо є справжній образ ROM: образ .img збирається лише на Linux-
# білдбоксі й важить десятки гігабайт (див. docs/VM-INSTALL.md). Цей скрипт дає
# той самий інтерфейс і ті самі застосунки поверх будь-якого наличного образу —
# емулятора, Android-x86 у VirtualBox, готового AVD. Це не заміна ROM, а шлях,
# яким можна скористатися сьогодні.
#
#   build/scripts/install-on-vm.sh [--apks <каталог>] [-s <серійний номер>] [--dry-run]
#
# Каталі готує build/scripts/make-vm-bundle.sh; можна вказати й власний.
#
# Усі системні виклики тут — найкращі спроби. Причини різні: роль може бути
# недоступна без телефонії, `cmd overlay` не знає модуля, якщо оверлей не
# поставився. Скрипт друкує, що саме не вдалося, і зупинятися не перестає: інакше
# перша ж незвична VM зупиняла б установлення на півслові.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
# Реєстр шукаємо поруч зі скриптом: у розпакованому пакунку каталогу репо
# немає, і фіксований шлях до build/scripts/ вбивав би установлення на першому ж
# рядку. Власний реєстр пакунок отримує від make-vm-bundle.sh.
REGISTRY=""
for guess in "$HERE/apps.list" "$REPO/build/scripts/apps.list"; do
  [[ -f "$guess" ]] && { REGISTRY="$guess"; break; }
done
APKS="${APKS:-$HERE/apks}"
SERIAL=""
DRY=0
WARN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apks) APKS="$2"; shift 2 ;;
    -s) SERIAL="$2"; shift 2 ;;
    --dry-run) DRY=1; shift ;;
    *) echo "Невідома опція: $1" >&2; exit 2 ;;
  esac
done

warn() { echo "  увага: $*" >&2; WARN=$((WARN + 1)); }
note() { echo "  $*"; }
run() {
  if [[ "$DRY" == "1" ]]; then echo "  [dry-run] $*"; return 0; fi
  "$@"
}

ADB=""
# Кандидати в порядку: явний ANDROID_HOME, типові шляхи Linux/macOS, потім
# Windows-SDK через cygpath — без цього скрипт у Git Bash не знаходить adb.exe
# і користувач думає, що в нього немає SDK, хоча він стоїть.
to_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -u "$1" 2>/dev/null || printf '%s' "$1"; else printf '%s' "$1"; fi; }
for candidate in \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_HOME:-}/platform-tools/adb.exe" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
    "$HOME/Android/Sdk/platform-tools/adb" \
    "$(to_path "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe")" \
    "$(command -v adb 2>/dev/null || true)"; do
  if [[ -n "$candidate" && -x "$candidate" ]]; then ADB="$candidate"; break; fi
done
[[ -n "$ADB" ]] || { echo "не знайдено adb — додай Android SDK у PATH або ANDROID_HOME" >&2; exit 1; }
[[ -f "$REGISTRY" ]] || { echo "немає реєстру $REGISTRY" >&2; exit 1; }
[[ -d "$APKS" ]] || { echo "немає каталогу з APK: $APKS (спочатку make-vm-bundle.sh)" >&2; exit 1; }

if [[ -n "$SERIAL" ]]; then ADB="$ADB -s $SERIAL"; fi

# --- 1. пристрій ----------------------------------------------------------
echo ">>> Пристрій"
devices="$($ADB devices | sed -n '2,$p' | awk '$2=="device"{print $1}')"
[[ -n "$devices" ]] || { echo "немає підключеного пристрою (adb devices). Запусти VM і дочекайся завантаження." >&2; exit 1; }
api="$($ADB shell 'getprop ro.build.version.sdk' | tr -dc '0-9')"
echo "  серійні: $(echo "$devices" | tr '\n' ' ')"
echo "  API гостьової системи: ${api:-невідомо}"
if [[ -n "$api" && "$api" -lt 33 ]]; then
  echo "  TesseraOS походить з Android 15; на API $api наші застосунки (minSdk 33) не стануть." >&2
  exit 1
fi

# --- 2. застосунки --------------------------------------------------------
# Список і package беремо з реєстру: саме трійка «каталог — модуль — package»
# ловить помилку «APK зібрано під одним іменем, а роль даємо іншому».
echo ">>> Застосунки"
INSTALLED=()
while IFS='|' read -r dir module pkg _artifact _extra; do
  [[ -z "$dir" || "$dir" =~ ^[[:space:]]*# ]] && continue
  apk=""
  for guess in "$APKS/$module.apk" "$APKS/$dir/$module.apk" "$APKS/$dir/app-debug.apk"; do
    [[ -f "$guess" ]] && { apk="$guess"; break; }
  done
  if [[ -z "$apk" ]]; then
    warn "$dir: немає APK серед $APKS/$module.apk — пропустити чи перестворити пакунок?"
    continue
  fi
  if run $ADB install -r -t "$apk" >/dev/null; then
    note "$pkg ← ${apk#"$REPO"/}"
    INSTALLED+=("$pkg")
  else
    warn "$pkg: adb install відмовив (підпис, ABI або вільне місце)"
  fi
done < "$REGISTRY"

# Порожній реєстр — це не поломка, а стан продукту (2026-10-25: власних
# застосунків більше немає, лишається тема). Раніше цей рядок завершував
# установлення помилкою, і пакунок із самою темою не стався б взагалі.
if [[ "${#INSTALLED[@]}" == 0 ]]; then
  note "у реєстрі власних застосунків немає — далі встановлюємо лише тему"
fi

# --- 3. ролі --------------------------------------------------------------
# Без цих трьох застосунки були б просто іконками: HOME без призначення не
# веде, а ROLE_DIALER і ROLE_SMS дають і дозволи, і вхідні події.
echo ">>> Ролі"
for row in \
    "os.tessera.launcher|HOME|cmd package set-home-activity os.tessera.launcher/.LauncherActivity" \
    "os.tessera.dialer|ROLE_DIALER|cmd role add-role-packages-as-dialer os.tessera.dialer" \
    "os.tessera.messaging|ROLE_SMS|cmd role add-role-packages-as-sms os.tessera.messaging"; do
  IFS='|' read -r pkg role shell <<< "$row"
  [[ " ${INSTALLED[*]} " == *" $pkg "* ]] || continue
  if run $ADB shell "$shell" >/dev/null 2>&1; then
    note "$role → $pkg"
  else
    warn "$role для $pkg не призначено. Зроби вручну: adb shell $shell (діалог ролі — надійніший)"
  fi
done

# Дозволи, яких роль не видає на всіх збірках. `pm grant` на неоголошеному
# дозволі повертає помилку, тому кожен рядок друкуємо окремо — мовчки
# пропущений CALL_PHONE коштує більше, ніж одне «увага» в кінці.
echo ">>> Дозволи"
for row in \
    "os.tessera.camera|android.permission.CAMERA" \
    "os.tessera.camera|android.permission.RECORD_AUDIO" \
    "os.tessera.dialer|android.permission.CALL_PHONE" \
    "os.tessera.dialer|android.permission.READ_CALL_LOG" \
    "os.tessera.dialer|android.permission.READ_CONTACTS" \
    "os.tessera.messaging|android.permission.READ_SMS" \
    "os.tessera.messaging|android.permission.READ_CONTACTS"; do
  IFS='|' read -r pkg perm <<< "$row"
  [[ " ${INSTALLED[*]} " == *" $pkg "* ]] || continue
  run $ADB shell "pm grant $pkg $perm" >/dev/null 2>&1 || { warn "$pkg: $perm не видано"; continue; }
  note "$perm → $pkg"
done

# --- 4. тема --------------------------------------------------------------
# Оверлеї — static RRO з overlay/themes/: без них інтерфейс лишається стоковим,
# і це непомітно лише до першого порівняння скріншотів.
echo ">>> Тема"
OVER_DIR="$(cd "$APKS/.." && pwd)/overlays"
overlays="$(ls "$OVER_DIR"/*.apk 2>/dev/null || true)"
if [[ -z "$overlays" ]]; then
  note "оверлеїв у пакунку немає — образ виглядатиме стоково (збірка: build/scripts/build-themes.sh)"
  [[ "${#INSTALLED[@]}" == 0 ]] && \
    warn "ні застосунків, ні теми: пакунок порожній. Перестворіть його: build/scripts/make-vm-bundle.sh --themes"
else
  for apk in $overlays; do
    name="$(basename "$apk" .apk)"
    # cmd overlay enable знає лише package з маніфеста: «TesseraIconShapeTheme»
    # — це ім'я файла, а не пакет, і на ньому команда мовчки нічого не вмикала.
    pkg="$(awk -F'|' -v n="$name" '$1==n{print $2}' "$OVER_DIR/packages.list" 2>/dev/null)"
    run $ADB install -r -t "$apk" >/dev/null 2>&1 || { warn "$name: не поставився"; continue; }
    if [[ -z "$pkg" ]]; then
      warn "$name: package немає в overlays/packages.list — увімкни вручну: adb shell cmd overlay enable <package>"
      continue
    fi
    run $ADB shell "cmd overlay enable --persistent $pkg" >/dev/null 2>&1 \
      || run $ADB shell "cmd overlay enable $pkg" >/dev/null 2>&1 \
      || warn "$pkg: cmd overlay не зміг увімкнути (приватні ресурси framework вимагають платформного ключа)"
    note "$pkg ← $name"
  done
  note "static-оверлеї перечитуються лише після перезавантаження: adb reboot"
fi

# --- 5. що перевірити -----------------------------------------------------
echo
if [[ "$WARN" -gt 0 ]]; then
  echo "Готово з увагами ($WARN). Перевір рядки вище — зазвичай роль не доступна на образі без телефонії."
else
  echo "Готово."
fi
echo "Швидка перевірка з VM:"
echo "  adb shell dumpsys package role_manager | grep -A2 ROLE_"
echo "  adb shell settings get secure default_home"
echo "  adb shell cmd overlay list | grep tessera"
