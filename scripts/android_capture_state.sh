#!/usr/bin/env bash
set -euo pipefail

APP_ID="com.scf.nyxguard"
DEVICE_SERIAL=""

usage() {
  cat <<EOF
Usage: $(basename "$0") <session_dir> <label> [--serial <device>]
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '[android-capture] Missing command: %s\n' "$1" >&2
    exit 1
  }
}

detect_device() {
  if [[ -n "${DEVICE_SERIAL}" ]]; then
    printf '%s\n' "${DEVICE_SERIAL}"
    return 0
  fi

  local devices
  devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
  local count
  count="$(printf '%s\n' "${devices}" | sed '/^$/d' | wc -l | tr -d ' ')"

  if [[ "${count}" == "0" ]]; then
    printf '[android-capture] No adb device available\n' >&2
    exit 1
  fi

  if [[ "${count}" != "1" ]]; then
    printf '[android-capture] Multiple adb devices found, use --serial\n' >&2
    exit 1
  fi

  printf '%s\n' "${devices}"
}

sanitize() {
  printf '%s' "$1" | tr '[:space:]/:' '_' | tr -cd '[:alnum:]_-'
}

if [[ $# -eq 1 && ( "$1" == "--help" || "$1" == "-h" ) ]]; then
  usage
  exit 0
fi

if [[ $# -lt 2 ]]; then
  usage
  exit 1
fi

SESSION_DIR="$1"
LABEL="$2"
shift 2

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || {
        printf '[android-capture] --serial requires a value\n' >&2
        exit 1
      }
      DEVICE_SERIAL="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf '[android-capture] Unknown argument: %s\n' "$1" >&2
      exit 1
      ;;
  esac
done

require_cmd adb

SERIAL="$(detect_device)"
STAMP="$(date +"%Y%m%d_%H%M%S")"
SAFE_LABEL="$(sanitize "${LABEL}")"

SCREEN_DIR="${SESSION_DIR}/screenshots"
UI_DIR="${SESSION_DIR}/ui"
LOG_DIR="${SESSION_DIR}/logs"

mkdir -p "${SCREEN_DIR}" "${UI_DIR}" "${LOG_DIR}"

SCREENSHOT_PATH="${SCREEN_DIR}/${STAMP}_${SAFE_LABEL}.png"
UI_PATH="${UI_DIR}/${STAMP}_${SAFE_LABEL}.xml"
LOGCAT_PATH="${LOG_DIR}/${STAMP}_${SAFE_LABEL}_logcat.txt"
CRASH_PATH="${LOG_DIR}/${STAMP}_${SAFE_LABEL}_crash.txt"
WINDOW_PATH="${LOG_DIR}/${STAMP}_${SAFE_LABEL}_window.txt"

adb -s "${SERIAL}" exec-out screencap -p > "${SCREENSHOT_PATH}"
adb -s "${SERIAL}" exec-out uiautomator dump /dev/tty > "${UI_PATH}" 2>/dev/null || true
adb -s "${SERIAL}" logcat -d > "${LOGCAT_PATH}" || true
adb -s "${SERIAL}" logcat -b crash -d > "${CRASH_PATH}" || true
adb -s "${SERIAL}" shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' > "${WINDOW_PATH}" || true

printf '[android-capture] package=%s serial=%s saved=%s\n' "${APP_ID}" "${SERIAL}" "${SCREENSHOT_PATH}"
