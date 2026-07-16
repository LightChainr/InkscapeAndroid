#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cat <<'MSG'
Recommended Mac host tools:

  xcode-select --install
  brew install git git-lfs cmake meson ninja pkg-config ccache python jq zstd openjdk@17

Set the Android SDK and JDK environment:

  export ANDROID_HOME="$HOME/Library/Android/sdk"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

Install the repository package set:
MSG

printf '  sdkmanager --sdk_root="$ANDROID_HOME"'
while IFS= read -r package; do
  [[ -z "$package" || "$package" == \#* ]] && continue
  printf ' \\\n    %q' "$package"
done < "$ROOT/sdk-packages.lock"
printf '\n\n'

cat <<'MSG'
Then accept Android SDK licenses:

  sdkmanager --sdk_root="$ANDROID_HOME" --licenses

This script prints commands only. It does not modify the machine automatically.
MSG
