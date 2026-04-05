#!/usr/bin/env bash
set -euo pipefail

DEVICE_SERIAL=""

usage() {
  cat <<EOF
Usage:
  $(basename "$0") start <session_dir> [--serial <device>]
  $(basename "$0") stop <session_dir>
EOF
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
    printf '[android-logcat] No adb device available\n' >&2
    exit 1
  fi

  if [[ "${count}" != "1" ]]; then
    printf '[android-logcat] Multiple adb devices found, use --serial\n' >&2
    exit 1
  fi

  printf '%s\n' "${devices}"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    printf '[android-logcat] Missing command: %s\n' "$1" >&2
    exit 1
  }
}

if [[ $# -eq 1 && ( "$1" == "--help" || "$1" == "-h" ) ]]; then
  usage
  exit 0
fi

[[ $# -ge 2 ]] || {
  usage
  exit 1
}

ACTION="$1"
SESSION_DIR="$2"
shift 2

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || {
        printf '[android-logcat] --serial requires a value\n' >&2
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
      printf '[android-logcat] Unknown argument: %s\n' "$1" >&2
      exit 1
      ;;
  esac
done

require_cmd adb

LOG_DIR="${SESSION_DIR}/logs"
mkdir -p "${LOG_DIR}"

PID_FILE="${LOG_DIR}/logcat_live.pid"
META_FILE="${LOG_DIR}/logcat_live.meta"

case "${ACTION}" in
  start)
    SERIAL="$(detect_device)"
    STAMP="$(date +"%Y%m%d_%H%M%S")"
    OUT_FILE="${LOG_DIR}/logcat_live_${STAMP}.txt"

    adb -s "${SERIAL}" logcat -c || true
    adb -s "${SERIAL}" logcat > "${OUT_FILE}" 2>&1 &
    LOGCAT_PID=$!

    printf '%s\n' "${LOGCAT_PID}" > "${PID_FILE}"
    {
      printf 'serial=%s\n' "${SERIAL}"
      printf 'output=%s\n' "${OUT_FILE}"
      printf 'started_at=%s\n' "$(date -Iseconds)"
    } > "${META_FILE}"

    printf '[android-logcat] started pid=%s output=%s\n' "${LOGCAT_PID}" "${OUT_FILE}"
    ;;
  stop)
    [[ -f "${PID_FILE}" ]] || {
      printf '[android-logcat] No active session at %s\n' "${PID_FILE}" >&2
      exit 1
    }
    LOGCAT_PID="$(cat "${PID_FILE}")"
    kill "${LOGCAT_PID}" 2>/dev/null || true
    rm -f "${PID_FILE}"
    printf '[android-logcat] stopped pid=%s\n' "${LOGCAT_PID}"
    ;;
  *)
    usage
    exit 1
    ;;
esac
