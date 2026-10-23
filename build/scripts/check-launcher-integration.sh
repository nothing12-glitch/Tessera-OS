#!/usr/bin/env bash
# Статична перевірка інтеграції лаунчера в образ.
#
# Ловить клас помилок, який зелений білд не бачить: модуль є, але в образ не
# потрапляє; APK зібрано під одним package, а документація й `set-home-activity`
# обіцяють інший; бінарник випадково закомічений.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BP="$ROOT/apps/launcher/Android.bp"
MK="$ROOT/build/tessera.mk"
GRADLE="$ROOT/apps/launcher/app/build.gradle.kts"
PARITY="$ROOT/docs/PIXEL-PARITY.md"
MODULE="TesseraLauncher"
PKG="os.tessera.launcher"

missing=0
fail() { echo "[launcher-integration] $*"; missing=1; }
ok()     { echo "[launcher-integration] ok: $*"; }

[[ -f "$BP" ]] || { fail "немає $BP"; exit 1; }

# 1. Тип модуля й його ім'я
grep -q "^android_app_import {" "$BP" || fail "у Android.bp немає блоку android_app_import"
grep -q "name: \"$MODULE\"," "$BP"   || fail "у Android.bp немає модуля name: \"$MODULE\""

# 2. Модуль має бути в PRODUCT_PACKAGES — інакше Soong його збере, але в
#    образ він не потрапить (мовчазна неактивність).
#
# Шукаємо саме в рядку призначення: спочатку зклеюємо continuation-рядки `\\`,
# потім відрізаємо коментарі. Без цього коментар «#PRODUCT_PACKAGES += X»
# теж вважався б оголошенням — ми це словили на негативному тесті.
MK_FLAT="$(sed -e :a -e '/\\$/N; s/\\\n//; ta' "$MK" | sed 's/#.*$//')"
ASSIGN="$(printf '%s\n' "$MK_FLAT" | grep -E '^[[:space:]]*PRODUCT_PACKAGES[[:space:]]*[+:]?=' || true)"
if printf '%s\n' "$ASSIGN" | grep -qw "$MODULE"; then
  ok "$MODULE оголошений у PRODUCT_PACKAGES (build/tessera.mk)"
else
  fail "$MODULE не в PRODUCT_PACKAGES — образ його не отримає"
fi

# 3. presigned і certificate виключають одне одного (build/soong/java/app_import.go)
PRESIGNED=$(grep -cE '^\s*presigned:\s*true' "$BP")
CERT=$(grep -cE '^\s*certificate:' "$BP")
if [[ "$PRESIGNED" == 0 && "$CERT" == 0 ]]; then
  fail "у Android.bp не вказано ні presigned, ні certificate — Soong відмовить"
elif [[ "$PRESIGNED" != 0 && "$CERT" != 0 ]]; then
  fail "presigned і certificate одночасно — Soong відмовить"
else
  ok "підпис визначений однозначно (presigned=$PRESIGNED, certificate=$CERT)"
fi

# 4. Шлях до apk в Android.bp існує як каталог, але сам файл поза git
APK_PATH=$(grep -oE 'apk: "[^"]+"' "$BP" | sed 's/apk: "//; s/"$//')
[[ -n "$APK_PATH" ]] || fail "у Android.bp немає поля apk:"
if [[ -n "$APK_PATH" ]] && git -C "$ROOT" check-ignore -q "apps/launcher/$APK_PATH"; then
  ok "$APK_PATH ігнорується git (бінарників у репо немає)"
else
  fail "$APK_PATH не в .gitignore — готуй його скриптом build/scripts/prepare-launcher.sh"
fi

# 5. package з Gradle збігається з тим, що обіцяють Android.bp/документація
if grep -qE "applicationId = \"$PKG\"" "$GRADLE"; then
  ok "applicationId = $PKG"
else
  fail "у $GRADLE applicationId не $PKG — інструкція set-home-activity зманить"
fi

# 6. Жодних apk під apps/ у git
TRACKED=$(git -C "$ROOT" ls-files "apps/*.apk" "apps/**/*.apk" 2>/dev/null)
if [[ -n "$TRACKED" ]]; then
  fail "у git закомічені apk: $TRACKED"
else
  ok "під apps/ у git немає apk"
fi

# 7. Перекриття сток-лаунчера — свідомий крок, а не випадок: TrebuchetQuickStep
#    дає ще й Recents, тому без запису в docs/PIXEL-PARITY.md це регресія навігації
if grep -qE '^\s*overrides:' "$BP"; then
  if grep -q "overrides" "$PARITY"; then
    ok "overrides є, і він задокументований у docs/PIXEL-PARITY.md"
  else
    fail "overrides у Android.bp, але в docs/PIXEL-PARITY.md немає пояснення про Recents"
  fi
else
  ok "overrides немає: сток-лаунчер лишається джерелом Recents"
fi

if [[ "$missing" != 0 ]]; then
  echo "[launcher-integration] Провалено. Див. apps/launcher/Android.bp і apps/launcher/README.md" >&2
  exit 1
fi
echo "[launcher-integration] Усі перевірки пройдені."
