#!/usr/bin/env bash
# Перевіряє маніфест готового APK за тим самим правилом, що й реєстр apps.list.
#
# Правило живе тут, а не подвоєне в build-apps.yml і prepare-app.sh, бо подвоєння
# вже ламало CI: 2026-10-25 з'явився рядок із роллю SETUP_WIZARD, workflow
# продовжував вимагати LAUNCHER у всіх APK, і job «apk (welcome, …)» упав саме на
# ньому — хоча prepare-app.sh того дня вже перевіряв протилежне.
#
#   check-apk-manifest.sh <apk> <package> <роль>
#
# <роль> — п'ятий стовпчик реєстру: "-" для звичайного застосунку, інакше —
# назва категорії без префікса (HOME, SETUP_WIZARD, …).
#
# AAPT2 можна передати зовнішньою змінною; інакше шукаємо в PATH і в дереві бази.
set -euo pipefail

APK="${1:?першим аргументом — шлях до APK}"
PKG="${2:?другим аргументом — package застосунку}"
ROLE="${3:?-}"

die() { echo "check-apk-manifest: $*" >&2; exit 1; }

[[ -f "$APK" ]] || die "немає файлу $APK"

if [[ -z "${AAPT2:-}" ]]; then
  AAPT2="$(command -v aapt2 2>/dev/null || true)"
fi
[[ -n "$AAPT2" && -x "$AAPT2" ]] ||
  die "немає aapt2 (AAPT2=<шлях>) — без нього маніфест APK нечитабельний"

# Вивід зберігаємо у змінні, а не ганяємо конвеєром до grep -q: при
# `set -o pipefail` grep, що знайшов збіг і закрив stdin, вбиває aapt2 SIGPIPE —
# і конвеєр повертає 141 там, де перевірка вдалася.
badge="$("$AAPT2" dump badging "$APK")"
# badging не показує категорії фільтрів — їх читаємо з відтвореного бінарного
# маніфеста.
manifest="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$APK")"

[[ "$badge" == *"package: name='$PKG'"* ]] ||
  die "$APK: APK не наш — інший package (очікували $PKG)"

# Які категорії вимагати — залежно від ролі рядка.
#
# Звичайний застосунок без LAUNCHER не існує: його ніхто не відкриє.
# Майстер першого запуску — навпаки: LAUNCHER у ньому є помилкою, бо іконка
# «Ласкаво просимо» в шухляді суперечить самій ідеї OOBE (екран, який
# бачать один раз). Тому для SETUP_WIZARD перевіряємо відсутність.
if [[ "$ROLE" == "SETUP_WIZARD" ]]; then
  [[ "$manifest" != *'"android.intent.category.LAUNCHER"'* ]] ||
    die "$APK: майстер першого запуску має LAUNCHER-фільтр — в образі знову застосунок з іконкою"
  [[ "$manifest" == *"\"android.intent.category.$ROLE\""* ]] ||
    die "$APK: у APK немає категорії $ROLE — система не вважатиме його майстром"
  echo "ok: $PKG (майстер: +$ROLE, без LAUNCHER)"
else
  [[ "$manifest" == *'"android.intent.category.LAUNCHER"'* ]] ||
    die "$APK: у APK немає LAUNCHER-фільтра — застосунок не з'явиться на екрані"
  if [[ "$ROLE" != "-" ]]; then
    # Додаткова категорія потрібна не для «іконки»: без неї система не вважає
    # застосунок, напр., домашнім екраном.
    [[ "$manifest" == *"\"android.intent.category.$ROLE\""* ]] ||
      die "$APK: у APK немає категорії $ROLE"
  fi
  echo "ok: $PKG (+LAUNCHER$([[ "$ROLE" != "-" ]] && echo ", +$ROLE"))"
fi
