#!/usr/bin/env bash
# Підготовка білд-боксу Zirka OS (Linux: Debian 12 / Ubuntu 22.04+).
# Не поспішай: спочатку прочитай вимоги в docs/BUILD.md.
set -euo pipefail

APPS=()
require() { command -v "$1" >/dev/null 2>&1 || APPS+=("$2"); }

require git git
require repo repo
require ccache ccache
require unzip unzip
require zip zip
require python3 python3
require curl curl
require java openjdk-21-jdk

if [[ "${#APPS[@]}" -gt 0 ]]; then
  echo "Потрібно встановити: ${APPS[*]}"
  if [[ "$(id -u)" == "0" ]]; then
    apt-get update && apt-get install -y "${APPS[@]}"
  else
    echo "Спробуй: sudo apt-get update && sudo apt-get install -y ${APPS[*]}"
  fi
fi

# 1. Місце на диску — найчастіша причина провалу збірки AOSP.
FREE_GB=$(df -BG --output=avail . | tail -1 | tr -dc '0-9')
if [[ "$FREE_GB" -lt 250 ]]; then
  echo "Увага: вільно ${FREE_GB} ГБ. Для повної збірки треба 250+ ГБ." >&2
fi

# 2. KVM — потрібен для швидкого емулятора (docs/EMULATOR.md).
if [[ -e /dev/kvm ]]; then
  echo "KVM: доступний."
else
  echo "KVM: відсутній. Емулятор буде повільним (x86_64-образ)." >&2
fi

# 3. Android SDK для збірки оверлеїв і test-запусків.
if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME не задано — build-themes.sh не зможе знайти android.jar."
  echo "Див. docs/EMULATOR.md, розділ 'Android SDK'."
fi

# 4. repo у ~/bin
mkdir -p "$HOME/bin"
if [[ ! -x "$HOME/bin/repo" ]]; then
  curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo -o "$HOME/bin/repo"
  chmod +x "$HOME/bin/repo"
  echo "repo встановлено в ~/bin. Додай ~/bin у PATH."
fi

echo
echo "Далі:"
echo "  repo init -u https://github.com/nothing12-glitch/zirka-os -b main -m manifest/zirka.xml"
echo "  repo sync -c -j\$(nproc)"
echo "  build/scripts/build-emulator.sh"
