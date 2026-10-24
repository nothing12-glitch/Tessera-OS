#!/usr/bin/env bash
# Створює дерево збірки TesseraOS поверх маніфеста LineageOS 22.2.
#
# Навіщо окремий скрипт: наш шар — ДОДАТОК до чужого маніфеста, а не замінник.
# Перелічувати сотні проєктів бази власним default.xml — шлях, де поломка
# неминуча. Ми мали саме так: до цієї правки manifest/tessera.xml перелічував
# сім проєктів бази, тоді як у повному дереві їх понад тисячу, і два з семи
# посилалися на репозиторії, яких на GitHub немає (перевірено HTTP-кодом).
# Рідний механізм repo для додатків — тека .repo/local_manifests/, і скрипт
# користується саме нею.
#
#    build/scripts/new-tree.sh [гілка-бази]      # за замовчуванням lineage-22.2
#
# Тільки Linux. Спершу прочитай вимоги (docs/BUILD.md): sync і збірка
# займають 400+ ГБ і кілька годин.
set -euo pipefail

BRANCH="${1:-lineage-22.2}"
TREE="${TESSERA_TREE:-$HOME/tessera}"
BASE_MANIFEST="https://github.com/LineageOS/android.git"
OUR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

die() { echo "new-tree: $*" >&2; exit 1; }

command -v repo >/dev/null 2>&1 || [[ -x "$HOME/bin/repo" ]] ||
  die "немає 'repo'. Поклади його в ~/bin — див. build/scripts/bootstrap.sh."

# --git-lfs потрібен маніфесту бази; без git-lfs 'repo init' падає з
# «Git LFS objects not found», що легко сплутати з мережною помилкою.
command -v git-lfs >/dev/null 2>&1 ||
  die "немає git-lfs: sudo apt install git-lfs."

mkdir -p "$TREE"
cd "$TREE"

FREE_GB=$(df -BG --output=avail . | tail -1 | tr -dc '0-9')
# Оцінка місця: повна історія git — це ~250 ГБ дерева плюс 150-250 ГБ out/.
# З --depth=1 (.git без історії) дерево стає приблизно вдвічі меншим, тому
# поріг різний. Обидва числа — з docs/BUILD.md, перевірені на реальному
# маніфесті lineage-22.2, а не на пам'яті.
NEED_GB=400
[[ -n "${REPO_DEPTH:-}" ]] && NEED_GB=300
if [[ "${FREE_GB:-0}" -lt "$NEED_GB" ]]; then
  echo "увага: вільно ${FREE_GB} ГБ, потрібно ${NEED_GB} ГБ." >&2
  if [[ -z "${REPO_DEPTH:-}" ]]; then
    echo "      на машині з меншим диском спробуй REPO_DEPTH=1 $0 $BRANCH" >&2
    echo "      — без повної історії git дерево вдвічі менше." >&2
  else
    echo "      і з REPO_DEPTH=1 sync може оборватися на півдорозі;" >&2
    echo "      повтори цей скрипт після звільнення місця — він продовжує." >&2
  fi
fi

if [[ ! -d .repo ]]; then
  echo ">>> repo init -u $BASE_MANIFEST -b $BRANCH"
  # --depth=1 — свідомий компроміс: історія потрібна лише розробці, а місце
  # потрібне зараз. Якщо REPO_DEPTH не задано, тягнемо повну історію.
  DEPTH_OPT=()
  [[ -n "${REPO_DEPTH:-}" ]] && DEPTH_OPT=(--depth="$REPO_DEPTH")
  repo init -u "$BASE_MANIFEST" -b "$BRANCH" "${DEPTH_OPT[@]}" --git-lfs --no-clone-bundle
fi

[[ -f .repo/manifests/default.xml ]] ||
  die "немає .repo/manifests/default.xml — 'repo init' не завершився"

# Наш шар. Копіюємо, а не лінкуємо: repo читає local_manifests як звичайні
# xml-файли і не любить symlink-ів у цій теці.
mkdir -p .repo/local_manifests
cp "$OUR_ROOT/manifest/tessera.xml" .repo/local_manifests/tessera.xml
echo ">>> шар TesseraOS: .repo/local_manifests/tessera.xml"

# Синхронізація. -c (лише поточна гілка) і --fail-fast: обірваний sync на
# сімсотому проєкті лишає дерево неробочим, тому краще дізнатися відразу.
# Повторний запуск цього скрипта продовжує з місця зупинки.
echo ">>> repo sync — це години роботи і сотні ГБ"
# -j тут обмежуємо не пам'яттю, а мережею: 16 — стеля, що витримує GitHub без
# «fetch failed» на середині. На повільному каналі зменши: REPO_JOBS=4.
JOBS="${REPO_JOBS:-$(nproc)}"
[[ "$JOBS" -gt 16 ]] && JOBS=16
repo sync -c -j"$JOBS" --fail-fast

# linkfile створює каталог платформи. Перевіряємо його тут, бо без цих файлів
# lunch не бачить таргета, а помилка «No rule to make target» прилетіла б
# через годину.
[[ -f device/tessera/x86_64/tessera_x86_64.mk ]] ||
  die "не з'явився device/tessera/x86_64/tessera_x86_64.mk — перевір .repo/local_manifests/tessera.xml"
[[ -f device/tessera/x86_64/AndroidProducts.mk ]] ||
  die "не з'явився device/tessera/x86_64/AndroidProducts.mk"

echo
echo "Готово. Далі:"
echo "  cd $TREE"
echo "  $OUR_ROOT/build/scripts/check-build-refs.sh --tree ."
echo "  $OUR_ROOT/build/scripts/build-emulator.sh tessera_x86_64-userdebug"
