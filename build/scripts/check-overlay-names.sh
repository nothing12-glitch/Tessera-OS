#!/usr/bin/env bash
# Перевірка імен ресурсів у наших RRO проти справжньої бази.
#
# Навіщо. aapt2 link --auto-add-overlay НЕ скаржиться на невідоме ім'я
# ресурсу: оверлей збирається, підписується, виглядає здоровим — і мовчки
# не змінює нічого. Єдиний спосіб це виявити — звірити кожне ім'я з
# деревом бази. Саме так ми знайшли 8 мертвих імен у лаунчері, 5 у SystemUI
# і всю палітру framework.
#
#   build/scripts/check-overlay-names.sh              # проти lineage-22.2
#   BASE_BRANCH=lineage-23.0 build/scripts/check-overlay-names.sh
#
# Вихід 0 — усі імена знайдені. Вихід 1 — є мертві; скрипт друкує список.
set -uo pipefail

BASE_BRANCH="${BASE_BRANCH:-lineage-22.2}"
FB=https://raw.githubusercontent.com/LineageOS/android_frameworks_base/$BASE_BRANCH
SETTINGS=https://raw.githubusercontent.com/LineageOS/android_packages_apps_Settings/$BASE_BRANCH
LAUNCHER=https://raw.githubusercontent.com/LineageOS/android_packages_apps_Trebuchet/$BASE_BRANCH
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CACHE="$(mktemp -d)"
trap 'rm -rf "$CACHE"' EXIT

# Кешуємо файли бази: один запит на файл, скільки б тем його не питали.
fetch() {
  local url="$1" key
  key="$(printf '%s' "$url" | md5sum | cut -c1-16)"
  [[ -f "$CACHE/$key" ]] || curl -sfL "$url" -o "$CACHE/$key" || : > "$CACHE/$key"
  printf '%s\n' "$CACHE/$key"
}

# Файли, які треба перевірити для кожного targetPackage.
files_for() {
  case "$1" in
    android)
      for f in core/res/res/values/config.xml core/res/res/values/colors.xml \
               core/res/res/values/colors_device_defaults.xml \
               core/res/res/values/attrs.xml core/res/res/values/styles.xml \
               core/res/res/values/public-final.xml; do echo "$FB/$f"; done ;;
    com.android.settings)
      for f in res/values/colors.xml res/values/dimens.xml res/values/config.xml; do
        echo "$SETTINGS/$f"; done ;;
    com.android.systemui)
      for f in packages/SystemUI/res/values/config.xml \
               packages/SystemUI/res/values/dimens.xml \
               packages/SystemUI/res/values/colors.xml; do echo "$FB/$f"; done ;;
    com.android.launcher3)
      for f in res/values/config.xml res/values/dimens.xml res/values/attrs.xml; do
        echo "$LAUNCHER/$f"; done ;;
    *) return 1 ;;
  esac
}

missing=0 checked=0
for theme in "$ROOT"/overlay/themes/*/; do
  name="$(basename "${theme%/}")"
  target="$(grep -oP 'android:targetPackage="\K[^"]+' "$theme/AndroidManifest.xml" || true)"
  if [[ -z "$target" ]]; then
    echo "[$name] НЕМАЄ targetPackage — пропущено"; continue
  fi
  if ! files_for "$target" > /dev/null 2>&1; then
    echo "[$name] невідомий targetPackage=$target — додай мапу файлів у скрипт"
    missing=1; continue
  fi

  # style-ресурси (Theme.DeviceDefault.*) — це не «підміна значення», а
  # перевизначення стилю; їх не шукаємо в colors/config.
  # Файли local_* — навмисно наші власні ресурси (дизайн-токени, папірці
  # палітри для styles.xml). Їх у базі немає і бути не може, тому далі
  # grep ідуть лише файли, які претендують на роль override.
  # res/xml/* тут цілеспрямовано немає: такі ресурси перевіряються нижче
  # окремо — як файли, а не як імена (launcher:name="..." там не our name=).
  names=$(
    find "$theme/res" -path '*/values*' -name '*.xml' \
      -not -name 'local_*' -not -name 'public.xml' \
      -exec grep -hoP '<(color|dimen|integer|bool|string|item)[^>]*\bname="\K[^"]+' {} + 2>/dev/null |
    grep -vE '^(Theme\.|.*shapeAppearance)' |
    sed 's/^android://' | sort -u
  )

  dead=""
  priv=""
  for n in $names; do
    checked=$((checked + 1))
    found=0
    for url in $(files_for "$target"); do
      f="$(fetch "$url")"
      grep -q "name=\"$n\"" "$f" && { found=1; break; }
    done
    if [[ $found -eq 0 ]]; then
      dead="$dead $n"
      continue
    fi
    # Окремий сигнал: у пакету `android` ресурс може існувати, але бути
    # private. Його не резолвне збірка поза деревом (наш CI), тож такий
    # override живить лише повний AOSP-білд.
    if [[ "$target" == android ]]; then
      pub="$(fetch "$FB/core/res/res/values/public-final.xml")"
      grep -q "type=\"attr\" name=\"$n\"\|type=\"color\" name=\"$n\"\|type=\"dimen\" name=\"$n\"\|type=\"bool\" name=\"$n\"\|type=\"string\" name=\"$n\"" "$pub" \
        || priv="$priv $n"
    fi
  done

  # Ресурси поза values/ (xml/, layout/, drawable/) підміняються ЦІЛИМИ
  # файлами. Тут немає імен для grep — треба лише, щоб такий самий файл
  # існував у цільовому пакеті з тим самим шляхом.
  dead_xml=""
  xml_n=0
  for rel in $(cd "$theme/res" 2>/dev/null && find . -mindepth 2 -name '*.xml' \
                 -not -path './values*' | sed 's|^\./||' | sort); do
    xml_n=$((xml_n + 1))
    case "$target" in
      android)               url="$FB/core/res/res/$rel" ;;
      com.android.settings)  url="$SETTINGS/res/$rel" ;;
      com.android.launcher3) url="$LAUNCHER/res/$rel" ;;
      com.android.systemui)  url="$FB/$rel" ;;
    esac
    code=$(curl -s -o /dev/null -w '%{http_code}' "$url")
    [[ "$code" == "200" ]] || dead_xml="$dead_xml $rel (HTTP $code)"
    checked=$((checked + 1))
  done

  if [[ -n "$dead_xml" ]]; then
    echo "[$name → $target] НЕМАЄ ФАЙЛІВ У БАЗІ:$dead_xml"
    echo "     останній перевірений шлях: $url"
    missing=1
  elif [[ -n "$dead" ]]; then
    echo "[$name → $target] МЕРТВІ ІМЕНА:$dead"
    missing=1
  elif [[ -n "$priv" ]]; then
    echo "[$name → $target] ok, приватні (підміняються лише в дереві AOSP):$priv"
  else
    n_names=$(printf '%s\n' $names | grep -c . || true)
    echo "[$name → $target] ok ($n_names імен + $xml_n файлів поза values/)" \
         "підтверджено базою"
  fi
done

echo "---"
echo "Перевірено ресурсів: $checked, гілка бази: $BASE_BRANCH"

# Build-time оверлеї (DEVICE_PACKAGE_OVERLAYS) — це не імена, а цілі файли,
# які підміняють файли застосунку під час його збірки. Для них єдиний
# змістовний тест: такий самий шлях має існувати в базі, і наша правка має
# бути на місці.
# Кладка: overlay-packages/<шлях у цільовому репо>/… Наприклад
#   overlay-packages/packages/apps/Trebuchet/res/xml/device_profiles.xml
# віддає підміну для
#   android_packages_apps_Trebuchet/res/xml/device_profiles.xml
BUILD_OVERLAY_ROOT="$ROOT/overlay-packages"
if [[ -d "$BUILD_OVERLAY_ROOT" ]]; then
  while IFS= read -r file; do
    rel="${file#"$BUILD_OVERLAY_ROOT"/}"
    base=""; path_in_base=""
    case "$rel" in
      packages/apps/Trebuchet/*) base="$LAUNCHER"; path_in_base="${rel#packages/apps/Trebuchet/}" ;;
      packages/apps/Settings/*)  base="$SETTINGS"; path_in_base="${rel#packages/apps/Settings/}" ;;
      frameworks/base/*)         base="$FB";       path_in_base="${rel#frameworks/base/}" ;;
      *) echo "[build-overlay] $rel — невідома цільова теку, додай мапу"
         missing=1; continue ;;
    esac
    code=$(curl -s -o /dev/null -w '%{http_code}' "$base/$path_in_base")
    if [[ "$code" == "200" ]]; then
      echo "[build-overlay] $rel → є в базі"
    else
      echo "[build-overlay] $rel → Немає в базі (HTTP $code)"
      missing=1
    fi
    # інваріант нашої правки сітки: рівно один дефолтний профіль
    if [[ "$rel" == *device_profiles.xml ]]; then
      n=$(grep -cP '^\s*launcher:canBeDefault="true"' "$file")
      if [[ "$n" == "1" ]]; then
        echo "[build-overlay] canBeDefault=true: рівно 1 профіль"
      else
        echo "[build-overlay] canBeDefault=true: $n профілів — має бути 1"
        missing=1
      fi
    fi
  done < <(find "$BUILD_OVERLAY_ROOT" -type f | sort)
fi

if [[ $missing -eq 1 ]]; then
  echo "Є імена, яких немає в цільовому пакеті. Такий оверлей збереться, але"
  echo "нічого не змінить у системі. Виправ імена або прибери рядки."
  exit 1
fi
echo "Усі імена підтверджені деревом бази."
