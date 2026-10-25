#!/usr/bin/env bash
# Збирає установчий пакунок TesseraOS для віртуальної машини в dist/.
#
# Навіщо пакунок, якщо образ ROM збирається build-emulator.sh: .img важить
# десятки гігабайт, збирається лише на Linux і в git не потрапляє (політика
# репо). Пакунок APK — те, чим можна скористатися сьогодні на будь-якому
# образі Android 13+ у VM; саме його публікує .github/workflows/release.yml.
#
#   build/scripts/make-vm-bundle.sh [--from <каталог з APK>] [--themes]
#
# Без --from APK бере з локальних збірок Gradle (apps/*/app/build/outputs).
# --from корисний у CI: туди кладуть APK артефакти запуску build-apps.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REGISTRY="$REPO/build/scripts/apps.list"
MK="$REPO/build/tessera.mk"
FROM=""
WITH_THEMES=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --from) FROM="$2"; shift 2 ;;
    --themes) WITH_THEMES=1; shift ;;
    *) echo "Невідома опція: $1" >&2; exit 2 ;;
  esac
done

[[ -f "$REGISTRY" ]] || { echo "немає реєстру $REGISTRY" >&2; exit 1; }

VERSION="$(sed -n 's/^TESSERA_VERSION := \([0-9.]*\).*/\1/p' "$MK" | head -1)"
[[ -n "$VERSION" ]] || { echo "не прочитав TESSERA_VERSION з $MK" >&2; exit 1; }
CODENAME="$(sed -n 's/^TESSERA_CODENAME := \([a-z_]*\).*/\1/p' "$MK" | head -1)"

NAME="tessera-vm-$VERSION${CODENAME:+-$CODENAME}"
OUT="$REPO/dist/$NAME"
# Каталога не зрізаємо через rm -rf: на Windows пустий dist/ часто тримає
# сторонній процес (антивірус, індексатор) і «Device or resource busy» зупиняє
# збірку. Порожимо його вміст — цього досить, старих файлів у пакунку не лишається.
mkdir -p "$OUT"
find "$OUT" -mindepth 1 -delete
mkdir -p "$OUT/apks"

echo ">>> Пакунок $NAME"

found=0
themes=0
while IFS='|' read -r dir module pkg _artifact _extra; do
  [[ -z "$dir" || "$dir" =~ ^[[:space:]]*# ]] && continue
  src=""
  for guess in \
      "${FROM:+$FROM/$module.apk}" \
      "${FROM:+$FROM/$dir/$module.apk}" \
      "${FROM:+$FROM/${dir}-debug/}app-debug.apk" \
      "${FROM:+$FROM}/*/app-debug.apk" \
      "$REPO/apps/$dir/app/build/outputs/apk/debug/app-debug.apk"; do
    [[ -n "$guess" ]] || continue
    # glob у змінній не розкривається сам — розкриваємо через ls і беремо перший
    [[ "$guess" == *'*'* ]] && guess="$(ls -d $guess 2>/dev/null | head -1 || true)"
    if [[ -n "$guess" && -f "$guess" ]]; then src="$guess"; break; fi
  done
  [[ -n "$src" ]] || { echo "  увага: $dir — APK не знайдено (немає --from і локальної збірки Gradle?)"; continue; }
  cp "$src" "$OUT/apks/$module.apk"
  echo "  $module.apk ← $pkg"
  found=$((found + 1))
done < "$REGISTRY"

# Порожній реєстр від 2026-10-25 — легальний (власних застосунків у продукті
# немає), тому перевірку перенесено нижче, після теми: пакунок має містити
# щось, а не обов'язково APK застосунків.

# --- тема ---------------------------------------------------------------
# Оверлеї — другий шар вигляду. Без них пакунок дає хіба стокову палітру, тому
# їх кладемо, лише коли build-themes.sh уже відпрацював. Від 2026-10-25 тема —
# узагалі єдине, що пакунок містить.
if [[ "$WITH_THEMES" == "1" ]]; then
  mkdir -p "$OUT/overlays"
  # packages.list — щоб інсталятор увімкнув оверлей за package з маніфеста:
  # «cmd overlay enable» не знає модулів, він знає лише імена пакетів, а
  # ім'я файла APK (TesseraIconShapeTheme) — інше.
  : > "$OUT/overlays/packages.list"
  for apk in "$REPO"/overlay/out/*.apk; do
    [[ -f "$apk" ]] || continue
    name="$(basename "$apk" .apk)"
    pkg="$(sed -n 's/^[[:space:]]*package="\([^"]*\)".*/\1/p' \
             "$REPO/overlay/themes/$name/AndroidManifest.xml" 2>/dev/null | head -1)"
    cp "$apk" "$OUT/overlays/"
    themes=$((themes + 1))
    if [[ -n "$pkg" ]]; then
      printf '%s|%s\n' "$name" "$pkg" >> "$OUT/overlays/packages.list"
      echo "  overlays/$name.apk ← $pkg"
    else
      echo "  overlays/$name.apk (package не прочитано — інсталятор лишить його вимкненим)"
    fi
  done
  if ! ls "$OUT/overlays"/*.apk >/dev/null 2>&1; then
    rm -f "$OUT/overlays/packages.list"
    rmdir "$OUT/overlays" 2>/dev/null
    echo "  увага: overlay/out порожній — спочатку build/scripts/build-themes.sh"
  fi
fi

# Перевірка «у пакунку є щось» — після теми: пакунок із самою темою є чимось,
# а порожній архів користувач назвав би «зламаним» без жодного рядка помилки.
[[ $((found + themes)) -gt 0 ]] || {
  echo "у пакунку немає ні застосунків, ні теми — додай --themes або APK (--from/Gradle)" >&2
  exit 1
}

# --- скрипти та інструкція ---------------------------------------------
# apps.list кладемо поруч: install-on-vm.sh І install-on-vm.ps1 читають той
# самий реєстр, що й CI, і в розпакованому пакунку не змушені шукати каталог
# репозиторію. Раніше перелік модулів жив ще й усередині .ps1 — дубль, який
# ловив розхід «застосунок у реєстрі є, а на Windows його ніхто не ставить».
# Тепер джерело одне: порожній реєстр = інсталятор ставить лише тему.
cp "$REGISTRY" "$OUT/apps.list"
cp "$REPO/build/scripts/install-on-vm.sh" "$OUT/install-on-vm.sh"
cp "$REPO/build/scripts/install-on-vm.ps1" "$OUT/install-on-vm.ps1"
chmod +x "$OUT/install-on-vm.sh"

# Що саме в пакунку — окремою змінною: з порожнім реєстром попередній текст
# брехав би («7 застосунків» у архіві, де лежить лише тема).
if [[ "$found" -gt 0 ]]; then
  CONTENTS="$found наших застосунків, ролі HOME/ROLE_DIALER/ROLE_SMS$([[ $themes -gt 0 ]] && echo " і тема MD3 ($themes RRO-оверлеї)") поверх наявного образу Android 13+."
else
  CONTENTS="Тема MD3 ($themes RRO-оверлеїв) поверх наявного образу Android 13+.
Власних застосунків у продукті від 2026-10-25 немає: домашній екран, «Телефон»,
SMS, «Камера», «Файли», годинник і калькулятор дає система. Код застосунків
лишився в репозиторії (apps/) і в попередньому пакунку v15.22.2.2 — до реєстру
build/scripts/apps.list повертається рядком + модулем у build/tessera.mk."
fi

# Другий блок «що перевірити» має сенс лише коли ставити нічого, окрім теми.
if [[ "$found" -gt 0 ]]; then
  VERIFY_EXTRA="
  adb shell settings get secure default_home
  adb shell dumpsys package role_manager | grep -A2 ROLE_

Дозволи «Телефона» (CALL_PHONE) і «Повідомлень» (READ_SMS) система може
попросити в діалозі при першому запуску — це очікувано."
else
  VERIFY_EXTRA=""
fi

cat > "$OUT/README.txt" <<EOF
TesseraOS $VERSION — установчий пакунок для віртуальної машини

Що це. $CONTENTS
Це не самостійний ROM: повний образ (.img) збирається на Linux-білдбоксі
скриптом build/scripts/build-emulator.sh і важить десятки гігабайт.

Установлення (гостьова система вже запущена й видима для adb):

  Linux / macOS / Git Bash:  ./install-on-vm.sh
  Windows (PowerShell):      .\\install-on-vm.ps1

Після цього:
  adb reboot        # static-оверлеї перечитуються лише перезавантаженням

Що перевірити далі:
  adb shell cmd overlay list | grep tessera$VERIFY_EXTRA
EOF

( cd "$OUT" && find . -type f ! -name sha256sum.txt -print0 | sort -z | xargs -0 sha256sum > sha256sum.txt )

# --- архів -------------------------------------------------------------
# tar.xz замість .zip: policy.yml забороняє бінарники в гілці, а такий
# архів Windows 10+ розпаковує без додаткових програм.
ARCHIVE="$REPO/dist/$NAME.tar.xz"
tar -C "$REPO/dist" -cJf "$ARCHIVE" "$NAME"
( cd "$REPO/dist" && sha256sum "$(basename "$ARCHIVE")" > "$(basename "$ARCHIVE").sha256" )

echo
echo "готово: ${ARCHIVE#"$REPO"/}"
# Кількість оверлеїв — фактична, а не «ми просили --themes»: саме тут ховалася
# збірка без теми, коли build-themes.sh падав ще до цього рядка.
echo "  застосунків: $found, RRO-оверлеїв теми: $themes"
echo "У git таке не лежить: dist/ закриває .gitignore, а policy.yml стежить за бінарниками."
