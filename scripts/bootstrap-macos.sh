#!/usr/bin/env bash
set -euo pipefail

cat <<'MSG'
Recommended Mac host tools:

  xcode-select --install
  brew install git git-lfs cmake meson ninja pkg-config ccache python jq zstd openjdk@17

Android packages (after installing Android command-line tools):

  sdkmanager --sdk_root="$HOME/Library/Android/sdk" \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;35.0.0" \
    "ndk;27.2.12479018"

This script prints commands only. It does not modify the machine automatically.
MSG
