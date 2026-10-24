#!/usr/bin/env bash
# Підготовка білд-боксу TesseraOS (Linux: Ubuntu 22.04/24.04, Debian 12).
#
# Список пакунків — офіційний перелік LineageOS для збірки образу
# (wiki.lineageos.org/emulator, розділ «Install the build packages»), а не
# наш смак: тут усе, чого потребує Soong/Make для гілки lineage-22.2.
# Не поспішай: спочатку прочитай вимоги до заліза в docs/BUILD.md.
set -euo pipefail

# Скрипт говорить з APT. На Fedora/openSUSE перелік той самий за змістом, але
# пакувальні назви відрізняються — краще зупинитися, ніж зламати систему.
command -v dpkg >/dev/null 2>&1 || {
  echo "bootstrap: потрібен APT-дистрибутив (Ubuntu 22.04/24.04, Debian 12)." >&2
  echo "           Список пакунків вище — він і є інструкцією для менеджера
           пакетів твоєї системи." >&2
  exit 1
}

PKGS=(bc bison build-essential ccache curl erofs-utils flex g++-multilib
      gcc-multilib git git-lfs gnupg gperf imagemagick kmod lz4 lzop
      protobuf-compiler python3-protobuf lib32readline-dev lib32z1-dev
      libdw-dev libelf-dev libgnutls28-dev libsdl1.2-dev libssl-dev
      libxml2 libxml2-utils pngcrush rsync schedtool squashfs-tools
      xsltproc xxd zip zlib1g-dev python3 fontconfig)

# JDK. Дерево AOSP 15 тримає власний JDK у prebuilts/, тому host-пакюнок —
# скоріше запобіжник для інструментів навколо (api-stubs, javadoc).
JAVA=""
for candidate in openjdk-17-jdk openjdk-21-jdk; do
  if apt-cache show "$candidate" >/dev/null 2>&1; then JAVA="$candidate"; break; fi
done

MISSING=()
for p in "${PKGS[@]}"; do
  dpkg -s "$p" >/dev/null 2>&1 || MISSING+=("$p")
done
if [[ -n "$JAVA" ]] && ! dpkg -s "$JAVA" >/dev/null 2>&1; then
  MISSING+=("$JAVA")
fi

if [[ "${#MISSING[@]}" -gt 0 ]]; then
  echo ">>> Потрібно встановити (${#MISSING[@]} пакунків): ${MISSING[*]}"
  if [[ "$(id -u)" == "0" ]]; then
    apt-get update && apt-get install -y "${MISSING[@]}"
  else
    echo "    sudo apt-get update && sudo apt-get install -y ${MISSING[*]}"
  fi
else
  echo ">>> Усі пакунки на місці."
fi

# --- Передумови саме для TesseraOS -----------------------------------------

# 1. Місце на диску. База заявляє 400+ ГБ для гілок 21+; 250 ГБ — це шлях
#    до «sync обірвався, дерево непридатне».
FREE_GB=$(df -BG --output=avail . | tail -1 | tr -dc '0-9')
if [[ "${FREE_GB:-0}" -lt 400 ]]; then
  echo "увага: вільно ${FREE_GB} ГБ. Треба 400+ (docs/BUILD.md, «Вимоги»)." >&2
fi

# 2. KVM — для запуску зібраного образу в емуляторі (docs/EMULATOR.md).
if [[ -e /dev/kvm ]]; then
  echo ">>> KVM: доступний."
else
  echo ">>> KVM: немає. Збірка це не блокує, але емулятор буде надзвичайно
      повільним; у BIOS ноутбука віртуалізація (AMD-V/SVM) увімкнена?" >&2
fi

# 3. repo у ~/bin
mkdir -p "$HOME/bin"
if [[ ! -x "$HOME/bin/repo" ]]; then
  curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo -o "$HOME/bin/repo"
  chmod +x "$HOME/bin/repo"
  echo ">>> repo встановлено в ~/bin. Додай ~/bin у PATH (echo 'export PATH=\$HOME/bin:\$PATH' >> ~/.bashrc)."
fi

# 4. Android SDK потрібен лише для складання наших APK (Gradle), не для образу.
if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo ">>> ANDROID_HOME не задано — build/scripts/prepare-app.sh не зможе
      зібрати наші застосунки. Опис налаштування: docs/EMULATOR.md, розділ
      «Android SDK». Якщо збираєшся брати APK із CI-артефактів — можна й так."
fi

echo
echo "Далі (все трьома кроками керує один скрипт):"
echo "  build/scripts/new-tree.sh              # repo init бази + наш шар + sync"
echo "  build/scripts/build-emulator.sh        # підготовка APK + збірка образу"
echo "  build/scripts/run-avd.sh               # запуск у Android Emulator"
