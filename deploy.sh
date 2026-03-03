#!/usr/bin/env bash
set -euo pipefail

APP_ID="com.morse.master"
LAUNCH_COMPONENT="${APP_ID}/.MainActivity"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

usage() {
  cat <<'EOF_USAGE'
Usage: ./deploy.sh [--serial <device-serial>] [--skip-build]

Builds the debug APK, installs it on a connected device, and launches the app.

Options:
  --serial <device-serial>  Target a specific connected device/emulator
  --skip-build              Skip Gradle build step and use existing APK
  -h, --help                Show this help
EOF_USAGE
}

ANDROID_HOME_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB_BIN="${ADB:-$ANDROID_HOME_DIR/platform-tools/adb}"
if [[ ! -x "$ADB_BIN" ]]; then
  if command -v adb >/dev/null 2>&1; then
    ADB_BIN="$(command -v adb)"
  else
    echo "Error: adb not found. Set ANDROID_HOME or ADB." >&2
    exit 1
  fi
fi

DEVICE_SERIAL=""
SKIP_BUILD=0

while (($# > 0)); do
  case "$1" in
    --serial)
      if (($# < 2)); then
        echo "Error: --serial requires a value." >&2
        exit 1
      fi
      DEVICE_SERIAL="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Error: unknown argument '$1'" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  # Ensure gradle uses bundled JDK; unset JAVA_HOME to avoid mismatches.
  unset JAVA_HOME

  # Use a stable user-wide gradle cache by default so wrapper/distributions
  # are reused across Android projects.
  : "${GRADLE_USER_HOME:=$HOME/.gradle}"
  export GRADLE_USER_HOME
  mkdir -p "$GRADLE_USER_HOME"
  echo "Using GRADLE_USER_HOME=$GRADLE_USER_HOME"

  ./gradlew --gradle-user-home "$GRADLE_USER_HOME" assembleDebug
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "Error: APK not found at '$APK_PATH'. Run a build first." >&2
  exit 1
fi

if [[ -z "$DEVICE_SERIAL" ]]; then
  DEVICES=()
  while IFS= read -r serial; do
    [[ -n "$serial" ]] && DEVICES+=("$serial")
  done < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ "${#DEVICES[@]}" -eq 0 ]]; then
    echo "Error: no connected devices found." >&2
    exit 1
  fi

  if [[ "${#DEVICES[@]}" -eq 1 ]]; then
    DEVICE_SERIAL="${DEVICES[0]}"
  else
    BEST_SERIAL=""
    BEST_SDK=-1
    echo "Multiple devices detected. Selecting highest Android SDK:"
    for serial in "${DEVICES[@]}"; do
      sdk_raw="$("$ADB_BIN" -s "$serial" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
      model_raw="$("$ADB_BIN" -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"

      sdk=-1
      if [[ "$sdk_raw" =~ ^[0-9]+$ ]]; then
        sdk="$sdk_raw"
      fi
      model="${model_raw:-unknown}"

      printf "  - %s (model: %s, sdk: %s)\n" "$serial" "$model" "${sdk_raw:-unknown}"

      if (( sdk > BEST_SDK )); then
        BEST_SDK="$sdk"
        BEST_SERIAL="$serial"
      fi
    done

    if [[ -z "$BEST_SERIAL" || "$BEST_SDK" -lt 0 ]]; then
      echo "Error: failed to auto-select a compatible device. Use --serial <device-serial>." >&2
      exit 1
    fi

    DEVICE_SERIAL="$BEST_SERIAL"
    echo "Auto-selected '$DEVICE_SERIAL' (sdk $BEST_SDK). Use --serial to override."
  fi
fi

echo "Deploying '$APK_PATH' to device '$DEVICE_SERIAL'..."
"$ADB_BIN" -s "$DEVICE_SERIAL" install -r "$APK_PATH"

echo "Launching '$LAUNCH_COMPONENT'..."
"$ADB_BIN" -s "$DEVICE_SERIAL" shell am start -n "$LAUNCH_COMPONENT"

echo "Done."
