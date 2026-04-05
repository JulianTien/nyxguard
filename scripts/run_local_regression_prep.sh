#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACT_ROOT="${ROOT_DIR}/Docs/QA/artifacts"
SESSION_ID="$(date +"%Y%m%d_%H%M%S")"
SESSION_DIR="${ARTIFACT_ROOT}/${SESSION_ID}"
APP_ID="com.scf.nyxguard"
APK_PATH="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
BACKEND_PYTHON=""

DEVICE_SERIAL=""
SKIP_BACKEND=0
SKIP_BUILD=0
SKIP_INSTALL=0

log() {
  printf '[regression-prep] %s\n' "$*"
}

die() {
  printf '[regression-prep] ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Options:
  --serial <device>       Use a specific adb device serial
  --skip-backend          Skip api_smoke and backend unittest
  --skip-build            Skip ./gradlew assembleDebug
  --skip-install          Skip adb install / clear / grant / launch
  --help                  Show this help
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing command: $1"
}

choose_backend_python() {
  if [[ -x "${ROOT_DIR}/backend/.venv/bin/python" ]]; then
    printf '%s\n' "${ROOT_DIR}/backend/.venv/bin/python"
    return 0
  fi
  printf '%s\n' "python3"
}

detect_api_url() {
  local project_props="${ROOT_DIR}/local.properties"
  local global_props="${HOME}/.gradle/gradle.properties"
  local key_regex='^(nyxGuardApiBaseUrl|nyxGuardProdApiBaseUrl|nyxGuardStagingApiBaseUrl|nyxGuardLocalApiBaseUrl)='

  if [[ -f "${project_props}" ]] && grep -Eq "${key_regex}" "${project_props}"; then
    grep -E "${key_regex}" "${project_props}" | head -n 1
    return 0
  fi

  if [[ -f "${global_props}" ]] && grep -Eq "${key_regex}" "${global_props}"; then
    grep -E "${key_regex}" "${global_props}" | head -n 1
    return 0
  fi

  return 1
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
    return 1
  fi

  if [[ "${count}" != "1" ]]; then
    die "Multiple adb devices found. Re-run with --serial <device>."
  fi

  printf '%s\n' "${devices}"
}

run_and_log() {
  local name="$1"
  shift
  log "Running: $name"
  (
    cd "${ROOT_DIR}"
    "$@"
  ) | tee "${SESSION_DIR}/${name}.log"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || die "--serial requires a value"
      DEVICE_SERIAL="$2"
      shift 2
      ;;
    --skip-backend)
      SKIP_BACKEND=1
      shift
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --skip-install)
      SKIP_INSTALL=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      die "Unknown argument: $1"
      ;;
  esac
done

mkdir -p "${SESSION_DIR}"
mkdir -p "${SESSION_DIR}/logs"

require_cmd adb
require_cmd python3

[[ -x "${ROOT_DIR}/gradlew" ]] || die "Missing executable gradlew at ${ROOT_DIR}/gradlew"
BACKEND_PYTHON="$(choose_backend_python)"

{
  printf 'session_id=%s\n' "${SESSION_ID}"
  printf 'created_at=%s\n' "$(date -Iseconds)"
  printf 'root_dir=%s\n' "${ROOT_DIR}"
  printf 'backend_python=%s\n' "${BACKEND_PYTHON}"
} > "${SESSION_DIR}/session_meta.txt"

if api_url_line="$(detect_api_url)"; then
  log "Detected Android API config: ${api_url_line}"
  printf 'android_api_config=%s\n' "${api_url_line}" >> "${SESSION_DIR}/session_meta.txt"
else
  log "WARNING: no nyxGuardApiBaseUrl property found in local.properties or ~/.gradle/gradle.properties"
fi

if [[ "${SKIP_BACKEND}" -eq 0 ]]; then
  run_and_log "api_smoke" "${BACKEND_PYTHON}" scripts/api_smoke.py --profile local
  log "Running backend unittest"
  (
    cd "${ROOT_DIR}/backend"
    "${BACKEND_PYTHON}" -m unittest tests.test_v2_api
  ) | tee "${SESSION_DIR}/backend_unittest.log"
fi

if [[ "${SKIP_BUILD}" -eq 0 ]]; then
  run_and_log "assemble_debug" ./gradlew assembleDebug
fi

if [[ "${SKIP_INSTALL}" -eq 0 ]]; then
  serial="$(detect_device)" || die "No adb device available"
  printf 'device_serial=%s\n' "${serial}" >> "${SESSION_DIR}/session_meta.txt"

  [[ -f "${APK_PATH}" ]] || die "APK not found at ${APK_PATH}. Build first or remove --skip-build."

  log "Installing APK on ${serial}"
  adb -s "${serial}" install -r "${APK_PATH}" | tee "${SESSION_DIR}/adb_install.log"

  log "Clearing app data"
  adb -s "${serial}" shell pm clear "${APP_ID}" | tee "${SESSION_DIR}/adb_pm_clear.log"

  for permission in \
    android.permission.ACCESS_FINE_LOCATION \
    android.permission.ACCESS_COARSE_LOCATION \
    android.permission.POST_NOTIFICATIONS
  do
    adb -s "${serial}" shell pm grant "${APP_ID}" "${permission}" \
      >> "${SESSION_DIR}/adb_permissions.log" 2>&1 || true
  done

  log "Launching app"
  adb -s "${serial}" shell am start -n "${APP_ID}/com.scf.nyxguard.SafeActivity" \
    | tee "${SESSION_DIR}/adb_launch.log"
fi

cat <<EOF

Prepared regression session:
  ${SESSION_DIR}

Suggested next steps:
  ./scripts/android_logcat_session.sh start ${SESSION_DIR}${DEVICE_SERIAL:+ --serial ${DEVICE_SERIAL}}
  ./scripts/android_capture_state.sh ${SESSION_DIR} cold_start${DEVICE_SERIAL:+ --serial ${DEVICE_SERIAL}}

EOF
