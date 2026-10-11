#!/usr/bin/env bash
# Installs the Android SDK pieces the build needs. Safe to run repeatedly and
# never fails the caller: a session must start even when the network is down.

CMDLINE_TOOLS_ZIP="commandlinetools-linux-15859902_latest.zip"
SDK_PACKAGES=("platform-tools" "platforms;android-37.0" "build-tools;37.0.0" "ndk;30.0.16248370")

main() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
  local sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"

  if [ ! -x "$sdkmanager" ]; then
    echo "setup-android: installing command-line tools into $sdk"
    local tmp
    tmp="$(mktemp -d)" || return 0
    curl -fsSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" || {
      echo "setup-android: dl.google.com is unreachable, see docs/NEEDS_HUMAN.md"
      rm -rf "$tmp"
      return 0
    }
    mkdir -p "$sdk/cmdline-tools"
    unzip -q "$tmp/tools.zip" -d "$tmp" || { rm -rf "$tmp"; return 0; }
    rm -rf "$sdk/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$sdk/cmdline-tools/latest"
    rm -rf "$tmp"
  fi

  local missing=()
  for pkg in "${SDK_PACKAGES[@]}"; do
    [ -d "$sdk/${pkg//;//}" ] || missing+=("$pkg")
  done
  if [ ${#missing[@]} -gt 0 ]; then
    echo "setup-android: installing ${missing[*]}"
    yes | "$sdkmanager" --sdk_root="$sdk" --licenses >/dev/null 2>&1
    "$sdkmanager" --sdk_root="$sdk" "${missing[@]}" >/dev/null || echo "setup-android: sdkmanager failed"
  fi

  local project_dir
  project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  if [ ! -f "$project_dir/local.properties" ] || ! grep -q '^sdk.dir=' "$project_dir/local.properties"; then
    echo "sdk.dir=$sdk" >> "$project_dir/local.properties"
  fi

  if [ "$CLAUDE_CODE_REMOTE" = "true" ]; then
    mkdir -p "$HOME/.gradle/init.d"
    cp "$project_dir/scripts/gradle/central-mirror.init.gradle.kts" "$HOME/.gradle/init.d/"
  fi

  if [ -n "$CLAUDE_ENV_FILE" ]; then
    echo "export ANDROID_HOME=$sdk" >> "$CLAUDE_ENV_FILE"
  fi
  echo "setup-android: ready ($sdk)"
}

main "$@"
exit 0
