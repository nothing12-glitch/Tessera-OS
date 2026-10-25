#!/usr/bin/env bash
# Ставить у WSL (або на будь-якому Linux-білдбоксі) те, чим збираються наші APK:
# Android SDK і Gradle.
#
#   bash build/scripts/install-app-toolchain.sh      # від звичайного користувача
#
# Навіщо окремий скрипт, якщо CI (`.github/workflows/build-apps.yml`) має все це
# на раннері з коробки: у WSL раннера немає, а збирати APK локально треба — лише
# так пакунок для VM можна перевірити ще до оприлюднення. Версії тут навмисно ті
# самі, що в workflow: розійтися з ними означало б «в CI зібралось, удома — ні».
#
# Нічого з цього не потрапляє в git: SDK і Gradle живуть у $HOME, у репо — лише
# цей скрипт.
set -euo pipefail

GRADLE_VERSION="${GRADLE_VERSION:-8.14.3}"
SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
# Той самий пакунок cmdline-tools, що й у workflow: sdkmanager звідти бере
# platforms;android-35 і build-tools;35.0.0.
CMDLINE_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

die() { echo "app-toolchain: $*" >&2; exit 1; }

command -v java >/dev/null 2>&1 || die "немає java (sdkmanager потребує JDK 17): див. bootstrap.sh"
command -v curl >/dev/null 2>&1 || die "немає curl"
command -v unzip >/dev/null 2>&1 || die "немає unzip: sudo apt install unzip"

# Одна тека для розпакування і один trap: другий trap у тій самій оболонці
# перезаписав би перший, і перший архів лишився б сміттям назавжди.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# А архіви — навпаки, у кеш, бо вони не сміття, а здобуте: 137 МБ Gradle на
# каналі з ~0,1-1 МБ/с — це 20-40 хв, і обрив (у нас це curl 56 на 80 %) не
# повинен коштувати початку з нуля. Кеш лишається між запусками.
CACHE_DIR="${TESSERA_TOOLCHAIN_CACHE:-$HOME/.cache/tessera-toolchain}"
mkdir -p "$CACHE_DIR"

# --no-progress-meter: у переспрямованому виводі смуга прогресу curl — це сотні
# рядків на одну завантажку, і такий лог не читається.
# -C - лише коли файл уже початий: для відсутнього curl і так качає з 0, а
# для завершеного сервер відповідає 416, і це було б падіння на рівному місці.
curl_get() { # $1 — ім'я в кеші, $2 — URL; друкує шлях до файлу
  local dest="$CACHE_DIR/$1" rc=0
  if [[ -s "$dest" ]]; then
    curl -fL --retry 5 --retry-connrefused --retry-delay 15 --no-progress-meter \
         -C - -o "$dest" "$2" || rc=$?
    # 33 = «діапазон неможливий»: файл уже цілий. Це не поломка, а завершене
    # раніше качання, тож лишаємо його як є. Усе інше — справжня помилка,
    # і тоді пробуємо з нуля (наприклад, кеш обірвався на битому шматку).
    if (( rc != 0 && rc != 33 )); then
      curl -fL --retry 5 --retry-connrefused --retry-delay 15 --no-progress-meter \
           -o "$dest" "$2" || return 1
    fi
  else
    curl -fL --retry 5 --retry-connrefused --retry-delay 15 --no-progress-meter \
         -o "$dest" "$2" || return 1
  fi
  echo "$dest"
}

echo "=== 1. Android SDK -> $SDK_DIR ==="
mkdir -p "$SDK_DIR/cmdline-tools"
if [[ -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "cmdline-tools уже є — не качаємо вдруге."
else
  echo ">>> cmdline-tools з dl.google.com"
  cmdline="$(curl_get cmdline.zip "$CMDLINE_ZIP_URL")" ||
    die "не довантажив cmdline-tools — повтори скрипт, кеш у $CACHE_DIR"
  echo "    архів: $(du -h "$cmdline" | cut -f1)"
  # Офіційної суми для цього архіву у зручному вигляді немає, тому перевіряємо
  # зміст: пошкоджений чи чужий zip не пройде «є bin/sdkmanager».
  unzip -q "$cmdline" -d "$TMP/cmdline"
  [[ -f "$TMP/cmdline/cmdline-tools/bin/sdkmanager" ]] ||
    die "у архіві немає bin/sdkmanager — архів не той"
  mv "$TMP/cmdline/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  echo "cmdline-tools розпаковано."
fi

echo ">>> ліцензії + platforms;android-35 + build-tools;35.0.0 + platform-tools"
# Так звано «прийняти ліцензії» ламає скрипт, якщо про це забути: sdkmanager
# закриває stdin після останньої згоди, `yes` на запис у трубу, яку ніхто не
# читає, отримує SIGPIPE (код 141), а pipeline з `set -o pipefail` робить це
# фатальним — і весь інсталятор помирає на порожньому місці. Тому || true.
yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
# Другий виклик ліцензій уже не запитує (згоду видно в ~/.android/licenses),
# тому ним судимо за кодом виходу звичайного виклику.
"$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-35" "build-tools;35.0.0" "platform-tools" >/dev/null ||
  die "sdkmanager не поставив пакунки SDK — див. вище (часто це обірване з'єднання з dl.google.com; повтори скрипт)"
[[ -d "$SDK_DIR/platforms/android-35" ]] || die "platforms/android-35 не з'явився"

echo
echo "=== 2. Gradle $GRADLE_VERSION ==="
if [[ -x "$HOME/gradle-dist/gradle-$GRADLE_VERSION/bin/gradle" ]]; then
  echo "Gradle $GRADLE_VERSION уже лежить у ~/gradle-dist."
else
  # services.gradle.org віддає 307 на GitHub Releases — а це саме той канал, де
  # в нас ~1 МБ/с, тому п'ять спроб із довантаженням тут не примха, а необхідність.
  echo ">>> gradle-$GRADLE_VERSION-bin.zip"
  gradle_zip="$(curl_get "gradle-$GRADLE_VERSION-bin.zip" \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip")" ||
    die "не довантажив Gradle — повтори скрипт, кеш у $CACHE_DIR"
  # Сума від gradle.org — голий hex без імені файлу, тож ім'я додаємо вручну.
  # Перевіряємо саме в тій теці, де лежить архів: sha256sum --check шукає файл
  # за ім'ям з рядка відносно поточної теки, і поза нею щойно завантажений
  # архів «не існує».
  expected="$(curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip.sha256")"
  ( cd "$CACHE_DIR" && printf '%s  %s\n' "$expected" "gradle-$GRADLE_VERSION-bin.zip" |
        sha256sum --check --strict ) ||
    die "sha256 архіву Gradle не збігається з офіційною — не розпаковуємо. Частіше за все це обірваний і доданий криво файл: видали $CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip і повтори"
  mkdir -p "$HOME/gradle-dist"
  unzip -q "$gradle_zip" -d "$HOME/gradle-dist"
  echo "Gradle $GRADLE_VERSION у ~/gradle-dist."
fi

echo
echo "=== 3. змінні середовища ==="
# ~/.profile, а не ~/.bashrc: Ubuntu обриває .bashrc для неінтерактивних оболонок,
# а збірки з Windows запускаються саме так (`wsl -u tessera -- bash -lc`).
PROFILE="$HOME/.profile"
if ! grep -q 'TESSERA TOOLCHAIN' "$PROFILE" 2>/dev/null; then
  cat >>"$PROFILE" <<EOF
# TESSERA TOOLCHAIN — Android SDK і Gradle для збірки наших APK
export ANDROID_HOME="$SDK_DIR"
export PATH="\$HOME/gradle-dist/gradle-$GRADLE_VERSION/bin:\$SDK_DIR/platform-tools:\$PATH"
EOF
  echo "дописано ANDROID_HOME і PATH у ~/.profile"
else
  echo "~/.profile уже налаштовано."
fi

echo
echo "Перевірка:"
"$HOME/gradle-dist/gradle-$GRADLE_VERSION/bin/gradle" --version | sed -n '1,4p'
ls -1 "$SDK_DIR/build-tools" | sed 's/^/  build-tools: /'
echo
echo "Далі: bash build/scripts/build-vm-bundle-local.sh"
