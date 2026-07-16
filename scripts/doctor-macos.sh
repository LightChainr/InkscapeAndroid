#!/usr/bin/env bash
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
failures=0
warnings=0

ok() { printf 'OK   %-24s %s\n' "$1" "${2:-}"; }
warn() { printf 'WARN %-24s %s\n' "$1" "${2:-}"; warnings=$((warnings + 1)); }
miss() { printf 'MISS %-24s %s\n' "$1" "${2:-}"; failures=$((failures + 1)); }

check_cmd() {
  local cmd="$1"
  if command -v "$cmd" >/dev/null 2>&1; then
    ok "$cmd" "$(command -v "$cmd")"
  else
    miss "$cmd"
  fi
}

printf 'Host: %s %s\n' "$(uname -s)" "$(uname -m)"
[[ "$(uname -s)" == "Darwin" ]] || warn "host OS" "script is designed for macOS"

if xcode-select -p >/dev/null 2>&1; then
  ok "Xcode CLI" "$(xcode-select -p)"
else
  miss "Xcode CLI" "run xcode-select --install"
fi

for cmd in git python3 java adb sdkmanager cmake meson ninja pkg-config ccache jq zstd; do
  check_cmd "$cmd"
done

if command -v java >/dev/null 2>&1; then
  java_version="$(java -version 2>&1 | head -n 1)"
  if [[ "$java_version" == *'"17.'* || "$java_version" == *'"17"'* ]]; then
    ok "JDK major" "$java_version"
  else
    miss "JDK major" "expected JDK 17, got $java_version"
  fi
fi

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
NDK_VERSION="$(python3 - "$ROOT/sources.lock.toml" <<'PY'
import sys, tomllib
with open(sys.argv[1], 'rb') as handle:
    print(tomllib.load(handle)['android']['ndk'])
PY
)"
NDK_ROOT="${ANDROID_NDK_ROOT:-$ANDROID_HOME/ndk/$NDK_VERSION}"
NDK_HOST_TAG="darwin-x86_64"
CLANG="$NDK_ROOT/toolchains/llvm/prebuilt/$NDK_HOST_TAG/bin/clang++"

printf 'INFO %-24s %s\n' "ANDROID_HOME" "$ANDROID_HOME"
printf 'INFO %-24s %s\n' "ANDROID_NDK_ROOT" "$NDK_ROOT"

[[ -d "$ANDROID_HOME" ]] && ok "Android SDK root" "$ANDROID_HOME" || miss "Android SDK root" "$ANDROID_HOME"
[[ -x "$ANDROID_HOME/platform-tools/adb" ]] && ok "platform-tools" || miss "platform-tools"
[[ -f "$ANDROID_HOME/platforms/android-36/android.jar" ]] && ok "platform android-36" || miss "platform android-36"
[[ -x "$ANDROID_HOME/build-tools/36.0.0/aapt2" ]] && ok "build-tools 36.0.0" || miss "build-tools 36.0.0"
[[ -x "$ANDROID_HOME/build-tools/35.0.0/aapt2" ]] && ok "build-tools 35.0.0" || warn "build-tools 35.0.0" "needed only by current GTK/Pixiewood candidate"
[[ -x "$CLANG" ]] && ok "NDK clang++" "$CLANG" || miss "NDK clang++" "$CLANG"

if command -v gradle >/dev/null 2>&1; then
  ok "gradle" "$(gradle --version 2>/dev/null | awk '/Gradle / {print $2; exit}')"
else
  warn "gradle" "not on PATH; Android Studio or CI can still build the probe"
fi

if command -v adb >/dev/null 2>&1; then
  device_count="$(adb devices 2>/dev/null | awk 'NR > 1 && $2 == "device" {count++} END {print count+0}')"
  if [[ "$device_count" -gt 0 ]]; then
    ok "ADB authorized devices" "$device_count"
    adb devices -l || true
  else
    warn "ADB authorized devices" "none connected"
  fi
fi

for var in PKG_CONFIG_PATH CMAKE_PREFIX_PATH DYLD_LIBRARY_PATH; do
  value="${!var:-}"
  if [[ "$value" == *'/opt/homebrew'* || "$value" == *'/usr/local/Cellar'* || "$value" == *'/System/Library/Frameworks'* ]]; then
    warn "$var" "may contaminate Android target lookup: $value"
  fi
done

if command -v df >/dev/null 2>&1; then
  free_kb="$(df -Pk "$ROOT" | awk 'NR == 2 {print $4}')"
  if [[ "$free_kb" =~ ^[0-9]+$ ]]; then
    free_gb=$((free_kb / 1024 / 1024))
    if [[ "$free_gb" -ge 30 ]]; then
      ok "free disk" "${free_gb} GiB"
    else
      warn "free disk" "${free_gb} GiB; native sysroot work should reserve at least 30 GiB"
    fi
  fi
fi

if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  ok "repository" "$(git -C "$ROOT" rev-parse --show-toplevel)"
else
  warn "repository" "script is not running from a Git checkout"
fi

printf 'Summary: failures=%d warnings=%d\n' "$failures" "$warnings"
[[ "$failures" -eq 0 ]]
