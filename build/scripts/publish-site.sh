#!/usr/bin/env bash
# Публікує site/ на GitHub Pages.
#
# Pages із гілки вміє обслуговувати лише корінь або /docs — довільної папки
# на кшталт /site в інтерфейсі та API немає. Тому site/ копіюється в корінь
# ізольованої гілки gh-pages: robocopy/rsync не потрібні, а робоче дерево
# основної гілки лишається недоторканим.
#
#   publish-site.sh            # синхронізувати й запушити
#   publish-site.sh --check    # лише показати, чи є чого пушити
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BRANCH=gh-pages
WT="$ROOT/.pages-worktree"
CHECK=0
[ "${1:-}" = "--check" ] && CHECK=1

command -v git >/dev/null || { echo "[publish-site] git не знайдено"; exit 1; }
[ -d "$ROOT/site" ] || { echo "[publish-site] немає site/"; exit 1; }

if ! git -C "$ROOT" ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
  echo "[publish-site] гілки $BRANCH на віддаленні ще немає — створюємо першим пушем"
fi

git -C "$ROOT" fetch --quiet origin "$BRANCH" 2>/dev/null || true

if [ -d "$WT" ]; then
  git -C "$ROOT" worktree remove --force "$WT" 2>/dev/null || rm -rf "$WT"
fi

# Основа — origin/gh-pages, а не локальна гілка: пуш із detached-robot
# («HEAD:gh-pages») локального рефу не рухає, тому другий деплой поспіль
# діставав перший коміт і віддалення відхиляло його як non-fast-forward.
if git -C "$ROOT" rev-parse --verify --quiet "refs/remotes/origin/$BRANCH" >/dev/null; then
  git -C "$ROOT" worktree add --quiet --detach "$WT" "origin/$BRANCH" || {
    echo "[publish-site] не вдалося підмонтувати origin/$BRANCH"; exit 1; }
elif git -C "$ROOT" rev-parse --verify --quiet "$BRANCH" >/dev/null; then
  git -C "$ROOT" worktree add --quiet --detach "$WT" "$BRANCH" || {
    echo "[publish-site] не вдалося підмонтувати $BRANCH"; exit 1; }
else
  git -C "$ROOT" worktree add --quiet --orphan "$WT" "$BRANCH" || {
    echo "[publish-site] не вдалося створити $BRANCH"; exit 1; }
fi

# Вміст гілки = вміст site/. Крапки (.nojekyll) теж видаляємо, иначе залишаються
# файли від попередніх версій сайту.
find "$WT" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -R "$ROOT/site/." "$WT/"

# autocrlf на Windows дає гору попереджень про CRLF; справжню помилку `add`
# все одно викидаємо разом із виводом.
if ! add_out="$(git -C "$WT" add -A 2>&1)"; then
  echo "$add_out"
  exit 1
fi
if git -C "$WT" diff --cached --quiet; then
  echo "[publish-site] сайт уже синхронізований, пушати нічого"
  git -C "$ROOT" worktree remove --force "$WT"
  exit 0
fi

echo "[publish-site] змін:"
git -C "$WT" diff --cached --stat | sed 's/^/  /'
[ "$CHECK" = 1 ] && { git -C "$ROOT" worktree remove --force "$WT"; exit 0; }

src="$(git -C "$ROOT" rev-parse --short HEAD)"
git -C "$WT" -c user.name="TesseraOS bot" -c user.email="tessera-bot@users.noreply.github.com" \
  commit --quiet -m "deploy: сайт TesseraOS з $src"
git -C "$WT" push --quiet origin "HEAD:$BRANCH" || {
  echo "[publish-site] пуш не вдалося"; exit 1; }
git -C "$ROOT" worktree remove --force "$WT"

echo "[publish-site] запушено до $BRANCH — Pages перебудує сайт за хвилину-дві"
