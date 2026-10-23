#!/usr/bin/env bash
# Статична перевірка інтеграції наших застосунків в образ (реєстр — apps.list).
#
# Ловить клас помилок, який зелений білд не бачить: модуль є, але в образ не
# потрапляє; APK зібрано під одним package, а документація обіцяє інший;
# бінарник випадково закомічений; новий застосунок з'явився в apps/, але його
# ніхто не готує перед `m`.
#
#   check-app-integration.sh            # усі застосунки з реєстру
#   check-app-integration.sh calculator # один, для швидкого локального прогону
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MK="$ROOT/build/tessera.mk"
REGISTRY="$ROOT/build/scripts/apps.list"
PARITY="$ROOT/docs/PIXEL-PARITY.md"

missing=0
fail() { echo "[app-integration] $*"; missing=1; }
ok()   { echo "[app-integration] ok: $*"; }

[[ -f "$REGISTRY" ]] || { echo "[app-integration] немає реєстру $REGISTRY"; exit 1; }

DIRS_IN_REGISTRY=""
ONLY="${1:-}"

while IFS='|' read -r dir module pkg artifact extra; do
  [[ -z "$dir" || "$dir" =~ ^[[:space:]]*# ]] && continue
  DIRS_IN_REGISTRY+=" $dir"
  [[ -n "$ONLY" && "$dir" != "$ONLY" ]] && continue

  GRADLE="$ROOT/apps/$dir/app/build.gradle.kts"
  BP="$ROOT/apps/$dir/Android.bp"
  tag="[$dir]"

  [[ -f "$BP" ]] || { fail "$tag немає $BP — Soong не знатиме про модуль $module"; continue; }

  # 1. Тип модуля і його ім'я
  grep -q "^android_app_import {" "$BP" || fail "$tag у Android.bp немає блоку android_app_import"
  grep -q "name: \"$module\"," "$BP"   || fail "$tag у Android.bp немає модуля name: \"$module\""

  # 2. Модуль має бути в PRODUCT_PACKAGES — інакше Soong його збере, але в
  #    образ він не потрапить (мовчазна неактивність).
  #
  # Шукаємо саме в рядку призначення: спочатку зклеюємо continuation-рядки `\\`,
  # потім відрізаємо коментарі. Без цього коментар «#PRODUCT_PACKAGES += X»
  # теж вважався б оголошенням — ми це словили на негативному тесті.
  MK_FLAT="$(sed -e :a -e '/\\$/N; s/\\\n//; ta' "$MK" | sed 's/#.*$//')"
  ASSIGN="$(printf '%s\n' "$MK_FLAT" | grep -E '^[[:space:]]*PRODUCT_PACKAGES[[:space:]]*[+:]?=' || true)"
  if printf '%s\n' "$ASSIGN" | grep -qw "$module"; then
    ok "$tag $module оголошений у PRODUCT_PACKAGES (build/tessera.mk)"
  else
    fail "$tag $module не в PRODUCT_PACKAGES — образ його не отримає"
  fi

  # 3. presigned і certificate виключають одне одного (build/soong/java/app_import.go)
  PRESIGNED=$(grep -cE '^\s*presigned:\s*true' "$BP")
  CERT=$(grep -cE '^\s*certificate:' "$BP")
  if [[ "$PRESIGNED" == 0 && "$CERT" == 0 ]]; then
    fail "$tag у Android.bp не вказано ні presigned, ні certificate — Soong відмовить"
  elif [[ "$PRESIGNED" != 0 && "$CERT" != 0 ]]; then
    fail "$tag presigned і certificate одночасно — Soong відмовить"
  else
    ok "$tag підпис визначений однозначно (presigned=$PRESIGNED, certificate=$CERT)"
  fi

  # 4. Шлях до apk з Android.bp ігнорується git: сам файл готується скриптом
  APK_PATH=$(grep -oE 'apk: "[^"]+"' "$BP" | sed 's/apk: "//; s/"$//')
  [[ -n "$APK_PATH" ]] || fail "$tag у Android.bp немає поля apk:"
  if [[ -n "$APK_PATH" ]] && git -C "$ROOT" check-ignore -q "apps/$dir/$APK_PATH"; then
    ok "$tag $APK_PATH ігнорується git (бінарників у репо немає)"
  else
    fail "$tag $APK_PATH не в .gitignore — готуй його скриптом build/scripts/prepare-app.sh $dir"
  fi

  # 5. package з Gradle збігається з тим, що обіцяють Android.bp і документація
  if [[ -f "$GRADLE" ]] && grep -qE "applicationId = \"$pkg\"" "$GRADLE"; then
    ok "$tag applicationId = $pkg"
  else
    fail "$tag у $GRADLE applicationId не $pkg — інструкції (adb, сайт, документи) зманять"
  fi

  # 6. Перекриття сток-компонента — свідомий крок, а не випадок: у лаунчера це
  #    ще й Recents, тому без запису в docs/PIXEL-PARITY.md це регресія навігації
  if grep -qE '^\s*overrides:' "$BP"; then
    if grep -q "overrides" "$PARITY"; then
      ok "$tag overrides є, і він задокументований у docs/PIXEL-PARITY.md"
    else
      fail "$tag overrides у Android.bp, але в docs/PIXEL-PARITY.md немає пояснення"
    fi
  else
    ok "$tag overrides немає: сток-компонент лишається в образі поряд"
  fi
done < "$REGISTRY"

# 7. У git немає жодного apk під apps/ — перевіряємо один раз, не на застосунок
TRACKED=$(git -C "$ROOT" ls-files "apps/*.apk" "apps/**/*.apk" 2>/dev/null)
if [[ -n "$TRACKED" ]]; then
  fail "у git закомічені apk: $TRACKED"
else
  ok "під apps/ у git немає apk"
fi

# 8. Кожен Android.bp у apps/ має стояти в реєстрі. Інакше новий застосунок
#    зібʼється вручну, про нього забудуть у CI, і в образ він не потрапить.
for bp in "$ROOT"/apps/*/Android.bp; do
  [[ -e "$bp" ]] || continue
  d="$(basename "$(dirname "$bp")")"
  if ! printf '%s\n' "$DIRS_IN_REGISTRY" | grep -qw "$d"; then
    fail "apps/$d/Android.bp є, але застосунку «$d» немає в build/scripts/apps.list"
  fi
done

# 9. Стовпчиків у реєстрі має бути рівно пʼять: скрипти читають за позицією,
#    тому «ще один коментар через |» зсуне package в ім'я артефакту.
COUNT=$(awk -F"|" '$0 !~ /^[[:space:]]*#/ && $0 !~ /^[[:space:]]*$/ { n=NF } END { print n+0 }' "$REGISTRY")
if [[ "$COUNT" != "5" ]]; then
  fail "$REGISTRY: рядки мають 5 стовпчиків через «|», а фактично $COUNT"
else
  ok "реєстр: 5 стовпчиків у кожному рядку"
fi

if [[ "$missing" != 0 ]]; then
  echo "[app-integration] Провалено. Див. apps/<застосунок>/Android.bp, build/scripts/apps.list і README застосунків." >&2
  exit 1
fi
echo "[app-integration] Усі перевірки пройдені."
