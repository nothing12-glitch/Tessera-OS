#!/usr/bin/env bash
# Провіжинінг Ubuntu всередині WSL2 під збірку TesseraOS. Запускати від root:
#
#   powershell -File build\scripts\setup-wsl.ps1        # із Windows, він кличе це
#   sudo bash build/scripts/wsl-provision.sh            # або всередині Ubuntu
#
# Навіщо окремий скрипт, якщо є bootstrap.sh: bootstrap ставить пакунки, а тут
# речі, які є тільки у WSL — користувач у Linux-теці (на /mnt/c файли належать
# root, і git потім скаржиться на «dubious ownership»), /etc/wsl.conf і PATH без
# Windows-течок. Без останнього Soong бачить C:\... у PATH і збирає повільніше,
# а буває що й інакше: на /mnt/c ФС не розрізняє регістр літер.
set -euo pipefail

BUILD_USER="${TESSERA_USER:-tessera}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

[[ "$(id -u)" == "0" ]] || { echo "wsl-provision: потрібен root (або -u root)." >&2; exit 1; }
[[ -e /proc/sys/fs/binfmt_misc/WSLInterop || -n "${WSL_DISTRO_NAME:-}" ]] ||
  echo "увага: це не схоже на WSL — скрипт все одно спробує, але /etc/wsl.conf тут ні на що не вплине." >&2

export DEBIAN_FRONTEND=noninteractive

echo "=== 1. пакунки для збірки (bootstrap.sh) ==="
bash "$SCRIPT_DIR/bootstrap.sh"

echo
echo "=== 2. користувач $BUILD_USER ==="
if id -u "$BUILD_USER" >/dev/null 2>&1; then
  echo "існує — не чіпаємо."
else
  useradd -m -G sudo -s /bin/bash "$BUILD_USER"
  # Пароль не ставимо: вхід іде через 'wsl -d Ubuntu-24.04 -u tessera' із
  # Windows (саме так дистрибутив називають setup-wsl.ps1 і
  # install-wsl-offline.ps1),
  # а sudo в Ubuntu на WSL за замовчуванням і так без пароля.
  echo "створено $BUILD_USER (група sudo)."
fi

echo
echo "=== 3. /etc/wsl.conf ==="
if [[ -f /etc/wsl.conf ]] && grep -q 'appendWindowsPath' /etc/wsl.conf; then
  echo "appendWindowsPath уже є — файл не чіпаємо:"
  sed 's/^/    /' /etc/wsl.conf
else
  # metadata+uid дають /mnt/c власні права, інакше git на Windows-файлах
  # пише «dubious ownership». uid беремо реального користувача, а не 1000 «на око».
  UID_N=$(id -u "$BUILD_USER")
  cat >/etc/wsl.conf <<EOF
[user]
default=$BUILD_USER

[automount]
enabled = true
options = metadata,uid=$UID_N,gid=$UID_N,umask=022

[interop]
enabled = true
appendWindowsPath = false
EOF
  echo "написано: default=$BUILD_USER (uid $UID_N), appendWindowsPath=false"
fi

echo
echo "=== 4. копія репозиторію в Linux-теці ==="
# Клон у ~/tessera-os, а не робота з /mnt/c: там FS без прав і без регістру, і
# git на таких файлах пише «dubious ownership».
if [[ -d "/home/$BUILD_USER/tessera-os/.git" ]]; then
  sudo -u "$BUILD_USER" git -C "/home/$BUILD_USER/tessera-os" fetch --depth=1 origin main &&
    sudo -u "$BUILD_USER" git -C "/home/$BUILD_USER/tessera-os" reset --hard origin/main
else
  sudo -u "$BUILD_USER" git clone --depth=1 https://github.com/nothing12-glitch/Tessera-OS \
    "/home/$BUILD_USER/tessera-os"
fi

echo
echo "=== 5. PATH і repo для $BUILD_USER ==="
# .profile, а не .bashrc: типовий .bashrc Ubuntu виходить у перших рядках, якщо
# сесія не інтерактивна — а саме так запускаємо все з Windows
# (`wsl -u tessera -- bash -lc ...`). Кладемо туди, де спрацює завжди.
PROFILE="/home/$BUILD_USER/.profile"
if ! grep -q 'TESSERA WSL' "$PROFILE" 2>/dev/null; then
  cat >>"$PROFILE" <<'EOF'
# TESSERA WSL — дерево збірки тримаємо в ext4, не на /mnt/c
export PATH="$HOME/bin:$PATH"
export TESSERA_TREE="$HOME/tessera"
EOF
  echo "дописано PATH і TESSERA_TREE у ~/.profile"
else
  echo "~/.profile уже налаштовано."
fi
# у машин, провіжинених попередньою версією скрипту, ці ж рядки стояли в
# .bashrc — прибираємо дублікат, щоб PATH не подвоювався.
if grep -q 'TESSERA WSL' "/home/$BUILD_USER/.bashrc" 2>/dev/null; then
  sudo -u "$BUILD_USER" sed -i '/# TESSERA WSL/,/TESSERA_TREE=/d' "/home/$BUILD_USER/.bashrc"
  echo "дублікат з ~/.bashrc прибрано"
fi
if [[ ! -x "/home/$BUILD_USER/bin/repo" ]]; then
  sudo -u "$BUILD_USER" mkdir -p "/home/$BUILD_USER/bin"
  sudo -u "$BUILD_USER" curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo \
    -o "/home/$BUILD_USER/bin/repo"
  sudo -u "$BUILD_USER" chmod +x "/home/$BUILD_USER/bin/repo"
  echo "repo покладено у ~/bin користувача $BUILD_USER"
fi

echo
echo "Готово. Наступний крок (з Windows):"
echo "  wsl --shutdown"
echo "  wsl -d ${WSL_DISTRO_NAME:-Ubuntu-24.04} -u $BUILD_USER"
echo "і всередині:"
echo "  cd ~/tessera-os && REPO_DEPTH=1 bash build/scripts/new-tree.sh lineage-22.2"
echo "  bash build/scripts/build-emulator.sh tessera_x86_64-userdebug"
