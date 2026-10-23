#!/usr/bin/env bash
# Готує APK наших застосунків там, де їх шукає Soong: apps/<застосунок>/prebuilt/.
#
# У git apk не лежить (політика репо), тому перед `m` цей скрипт має відпрацювати
# для кожного застосунку з build/scripts/apps.list. Викликається з
# build/scripts/build-emulator.sh; вручну — на білд-боксі або ноутбуці.
#
#   prepare-app.sh [застосунок|all] [gradle | prebuilt <шлях> | artifact <id запуску>]
#
# Приклади:
#   prepare-app.sh                      # усі з apps.list, зібрати Gradle-ом
#   prepare-app.sh calculator           # лише калькулятор
#   prepare-app.sh calculator prebuilt /tmp/app-debug.apk
#   prepare-app.sh all artifact 1234567 # апекси CI-запуску build-apps (потрібен GITHUB_TOKEN)
#
# Режим prebuilt має сенс лише для одного застосунку: шлях до APK спільний для
# всіх не буде. Режим artifact, навпаки, бере з одного запуску скільки потрібно
# модулів — там артефакти названі за п'ятим стовпчиком реєстру.
set -euo pipefail

REGISTRY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/apps.list"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OWNER_REPO="nothing12-glitch/Tessera-OS"

WHICH="${1:-all}"
MODE="${2:-gradle}"
MODE_ARG="${3:-}"

die() { echo "prepare-app: $*" >&2; exit 1; }

[[ -f "$REGISTRY" ]] || die "немає реєстру $REGISTRY"

# Реєстр → масив рядків "каталог модуль package артефакт категорія".
APPS=()
while IFS='|' read -r dir module pkg artifact extra; do
  [[ -z "$dir" || "$dir" =~ ^[[:space:]]*# ]] && continue
  [[ -n "$extra" ]] || extra="-"
  APPS+=("$dir|$module|$pkg|$artifact|$extra")
done < "$REGISTRY"
[[ "${#APPS[@]}" -gt 0 ]] || die "реєстр $REGISTRY порожній"

pick() { # рядок реєстру з даним каталогом, або нічого
  local row
  for row in "${APPS[@]}"; do
    [[ "${row%%|*}" == "$1" ]] && { echo "$row"; return 0; }
  done
  return 1
}

# Режим prebuilt має сенс лише для одного застосунку: спільного шляху до APK
# в кількох проєктів не буває.
if [[ "$MODE" == "prebuilt" && "$WHICH" == "all" ]]; then
  die "режим prebuilt потребує назву застосунку (prepare-app.sh calculator prebuilt <шлях>)"
fi

SELECTED=()
if [[ "$WHICH" == "all" ]]; then
  SELECTED=("${APPS[@]}")
else
  row="$(pick "$WHICH")" || die "у реєстрі $REGISTRY немає застосунку «$WHICH». Є: $(printf '%s ' "${APPS[@]%%|*}")"
  SELECTED=("$row")
fi

# Gradle викликаємо один раз на застосунок: каталоги окремі проєкти, спільного
# settings.gradle.kts у репо немає.
build_with_gradle() { # → шлях до свіжозібраного APK в stdout
  local src="$1"
  command -v gradle >/dev/null 2>&1 || die \
    "у PATH немає gradle. Постав Gradle 8.14.3+ або викликай «prebuilt <шлях до APK>»."
  # Консоль Gradle — в stderr: функцію викликають як $(build_with_gradle …), і
  # інакше вивід збірки став би «шляхом до APK».
  ( cd "$src" && gradle --console=plain :app:assembleDebug ) 1>&2 || die "Gradle не зібрав APK у $src"
  echo "$src/app/build/outputs/apk/debug/app-debug.apk"
}

download_artifact() { # id запуску + ім'я артефакту → шлях до розпакованого apk
  local run="$1" artifact="$2"
  local token="${GITHUB_TOKEN:?режим artifact потребує GITHUB_TOKEN (артефакти приватні для аноніма)}"
  local id tmp built
  id="$(curl -s -H "Authorization: Bearer $token" \
            "https://api.github.com/repos/$OWNER_REPO/actions/runs/$run/artifacts" \
        | python3 -c 'import json,sys
data = json.load(sys.stdin).get("artifacts", [])
name = sys.argv[1]
match = [a["id"] for a in data if a["name"] == name]
print(match[0] if match else "")' "$artifact")"
  [[ -n "$id" ]] || die "у запуску $run немає артефакту $artifact"
  tmp="$(mktemp -d)"
  curl -sL -H "Authorization: Bearer $token" \
       -o "$tmp/artifact.zip" \
       "https://api.github.com/repos/$OWNER_REPO/actions/artifacts/$id/zip"
  unzip -o -q "$tmp/artifact.zip" -d "$tmp/x"
  built="$(find "$tmp/x" -name '*.apk' -type f | head -1)"
  [[ -n "$built" ]] || die "в артефакті $id немає apk"
  echo "$built"
}

# aapt2 беремо з дерева бази, якщо скрипт запускається всередині нього;
# поза деревом — лише перевірка складових архіву.
AAPT2=""
for candidate in \
    "$REPO/../../prebuilts/sdk/tools/linux/bin/aapt2" \
    "$(command -v aapt2 2>/dev/null || true)"; do
  if [[ -n "$candidate" && -x "$candidate" ]]; then AAPT2="$candidate"; break; fi; done

for row in "${SELECTED[@]}"; do
  IFS='|' read -r dir module pkg artifact extra <<< "$row"
  src="$REPO/apps/$dir"
  dest_dir="$src/prebuilt"
  dest="$dest_dir/$module.apk"
  [[ -d "$src" ]] || die "немає каталогу застосунку $src"
  mkdir -p "$dest_dir"

  case "$MODE" in
    gradle)   built="$(build_with_gradle "$src")" ;;
    prebuilt) built="${MODE_ARG:?режим prebuilt потребує шлях до APK третім аргументом}" ;;
    artifact) built="$(download_artifact "${MODE_ARG:?режим artifact потребує id запуску build-apps третім аргументом}" "$artifact")" ;;
    *) die "невідомий режим «$MODE» — є gradle, prebuilt <шлях>, artifact <id>" ;;
  esac

  [[ -f "$built" ]] || die "немає файлу $built"

  # Вивід aapt2 і unzip спочатку зберігаємо у змінні, а не ганяємо конвеєром до
  # grep -q: при `set -o pipefail` grep, що знайшов збіг і закрив stdin, вбиває
  # unzip сигналом SIGPIPE — і конвеєр повертає 141 там, де перевірка вдалася.
  # Знайшли це на Windows: у APK понад сто записів, і unzip не встигає їх видати.
  if [[ -n "$AAPT2" ]]; then
    badge="$("$AAPT2" dump badging "$built")"
    manifest="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$built")"
    [[ "$badge" == *"package: name='$pkg'"* ]] ||
      die "$built: APK не наш — інший package (очікували $pkg)"
    [[ "$manifest" == *'"android.intent.category.LAUNCHER"'* ]] ||
      die "$built: у APK немає LAUNCHER-фільтра — застосунок не з'явиться на екрані"
    if [[ "$extra" != "-" ]]; then
      # Додаткова категорія потрібна не для «іконки»: без неї система не
      # вважає застосунок, напр., домашнім екраном.
      [[ "$manifest" == *"\"android.intent.category.$extra\""* ]] ||
        die "$built: у APK немає категорії $extra"
    fi
    echo "ok: aapt2 підтвердив $pkg (+LAUNCHER$([[ "$extra" != "-" ]] && echo ", +$extra"))"
  else
    echo "увага: немає aapt2 — перевірка лише складових APK" >&2
  fi

  entries="$(unzip -l "$built")"
  for entry in AndroidManifest.xml classes.dex resources.arsc; do
    [[ "$entries" == *"$entry"* ]] || die "у APK $built немає $entry"
  done

  # Копіюємо лише коли вміст справді інший: повторний запуск не чіпає дати
  # файлу, тож Soong даремно не переаналізує модуль.
  cmp -s "$built" "$dest" || cp "$built" "$dest"

  # Бережемо політику: файл, який ми щойно створили, має бути в .gitignore.
  git -C "$REPO" check-ignore -q "$dest" ||
    die "$dest не ігнорується git — бінарник опиниться в репо"

  echo "готово: Soong знайде модуль $module у ${dest#"$REPO"/}"
  sha256sum "$dest"
done
