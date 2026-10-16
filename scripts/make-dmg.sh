#!/usr/bin/env bash
# Собирает dmg с ярлыком Applications (образ jpackage/Compose кладёт только .app) и, если заданы переменные,
# подписывает его, отправляет на нотаризацию и прикрепляет результат.
#
#   scripts/make-dmg.sh [путь/к/Logcat.app] [версия]
#
# По умолчанию приложение берётся из LogCactus/build/compose/binaries/main-release/app/Logcat.app (его даёт
# `./gradlew packageReleaseDmg -PmacSignIdentity=...` на JDK 17), версия — из LogCactus/build.gradle.kts.
# Результат: LogCactus/build/compose/binaries/main-release/dmg-custom/Logcat-<версия>.dmg
#
# Подпись и нотаризация — только если заданы переменные окружения (в репозитории их нет):
#   SIGN_IDENTITY   — "Developer ID Application: <Имя> (<TEAM>)"
#   NOTARY_PROFILE  — профиль notarytool в связке ключей (xcrun notarytool store-credentials)
# Нужны macOS, python3 и доступ к PyPI (dmgbuild и pillow ставятся в venv в build/dmg-venv).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
bin="$root/LogCactus/build/compose/binaries/main-release"
app="${1:-$bin/app/Logcat.app}"
version="${2:-$(sed -nE 's/^version = "([^"]+)".*/\1/p' "$root/LogCactus/build.gradle.kts" | head -1)}"
[ -d "$app" ] || { echo "Не найдено приложение: $app (сначала ./gradlew packageReleaseDmg)" >&2; exit 1; }
[ -n "$version" ] || { echo "Не удалось определить версию" >&2; exit 1; }

venv="$root/LogCactus/build/dmg-venv"
if [ ! -x "$venv/bin/dmgbuild" ]; then
  python3 -m venv "$venv"
  "$venv/bin/pip" install -q dmgbuild pillow
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
"$venv/bin/python" "$root/scripts/dmg/make_background.py" "$work"

out_dir="$bin/dmg-custom"
mkdir -p "$out_dir"
dmg="$out_dir/Logcat-$version.dmg"
rm -f "$dmg"
"$venv/bin/dmgbuild" -s "$root/scripts/dmg/settings.py" -D "app=$app" -D "background=$work/bg.png" "Logcat" "$dmg"

if [ -n "${SIGN_IDENTITY:-}" ]; then
  codesign --force --sign "$SIGN_IDENTITY" --timestamp "$dmg"
  if [ -n "${NOTARY_PROFILE:-}" ]; then
    xcrun notarytool submit "$dmg" --keychain-profile "$NOTARY_PROFILE" --wait
    xcrun stapler staple "$dmg"
    xcrun stapler validate "$dmg"
  else
    echo "NOTARY_PROFILE не задан: образ подписан, но не нотаризован" >&2
  fi
else
  echo "SIGN_IDENTITY не задан: образ не подписан" >&2
fi

shasum -a 256 "$dmg"
