#!/usr/bin/env bash
set -u

failures=0
warnings=0

check_cmd() {
  local cmd="$1"
  if command -v "$cmd" >/dev/null 2>&1; then
    printf 'OK   %-18s %s\n' "$cmd" "$(command -v "$cmd")"
  else
    printf 'MISS %-18s\n' "$cmd"
    failures=$((failures + 1))
  fi
}

printf 'Host: %s %s\n' "$(uname -s)" "$(uname -m)"
[[ "$(uname -s)" == "Darwin" ]] || { echo 'WARN This script is designed for macOS'; warnings=$((warnings + 1)); }

if xcode-select -p >/dev/null 2>&1; then
  echo "OK   Xcode CLI          $(xcode-select -p)"
else
  echo "MISS Xcode CLI"
  failures=$((failures + 1))
fi

for cmd in git python3 java adb cmake meson ninja pkg-config; do
  check_cmd "$cmd"
done

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
NDK_VERSION="27.2.12479018"
NDK_ROOT="${ANDROID_NDK_ROOT:-$ANDROID_HOME/ndk/$NDK_VERSION}"
CLANG="$NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++"

printf 'INFO ANDROID_HOME       %s\n' "$ANDROID_HOME"
printf 'INFO ANDROID_NDK_ROOT   %s\n' "$NDK_ROOT"

if [[ -x "$CLANG" ]]; then
  echo "OK   NDK clang++        $CLANG"
else
  echo "MISS NDK clang++        $CLANG"
  failures=$((failures + 1))
fi

if command -v adb >/dev/null 2>&1; then
  echo '--- adb devices ---'
  adb devices -l || true
fi

for var in PKG_CONFIG_PATH CMAKE_PREFIX_PATH DYLD_LIBRARY_PATH; do
  value="${!var:-}"
  if [[ "$value" == *'/opt/homebrew'* || "$value" == *'/usr/local/Cellar'* ]]; then
    echo "WARN $var may contaminate Android target lookup: $value"
    warnings=$((warnings + 1))
  fi
done

printf 'Summary: failures=%d warnings=%d\n' "$failures" "$warnings"
[[ "$failures" -eq 0 ]]
