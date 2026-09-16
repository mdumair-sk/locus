#!/usr/bin/env bash
# tools/phone.sh - Phone Remote Execution & Device Deploy Orchestrator
set -euo pipefail

PORT=8022
KEY_PATH="${HOME}/.ssh/id_turbotransfer"
FLAVOR="${2:-oss}"

ensure_adb_tunnel() {
    adb forward tcp:${PORT} tcp:8022 2>/dev/null || true
    if ! adb shell "pidof sshd" >/dev/null 2>&1; then
        adb shell am start -n com.termux/.app.TermuxActivity >/dev/null 2>&1 || true
        sleep 1
    fi
}

run_ssh() {
    ensure_adb_tunnel
    ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR \
        -i "${KEY_PATH}" -p "${PORT}" localhost "$@"
}

case "${1:-status}" in
    status)
        echo "=== ADB Device ==="
        adb devices -l
        echo "=== Phone Node (Termux SSH) ==="
        ensure_adb_tunnel
        if run_ssh "uname -a && echo -n 'Cores: ' && nproc" 2>/dev/null; then
            echo "SSH Node: Connected and ready."
        else
            echo "SSH Node: Offline or port 8022 not listening."
        fi
        ;;
    deploy)
        echo "Building ${FLAVOR} flavor..."
        case "${FLAVOR}" in
            full) sh ./gradlew :app:assembleFullDebug ;;
            *) sh ./gradlew :app:assembleOssDebug ;;
        esac
        APK="app/build/outputs/apk/${FLAVOR}/debug/app-${FLAVOR}-debug.apk"
        echo "Installing ${APK} to device..."
        adb install -r "${APK}"
        echo "Launching MainActivity..."
        adb shell am start -n com.locus.app/.MainActivity
        ;;
    test)
        echo "Running on-device connected tests via ADB..."
        sh ./gradlew connectedOssDebugAndroidTest
        ;;
    logcat)
        echo "Streaming Locus logcat..."
        adb logcat -v time -s LocusApplication:V MainActivity:V *:E
        ;;
    ssh)
        shift
        run_ssh "$@"
        ;;
    *)
        echo "Usage: $0 {status|deploy [oss|full]|test|logcat|ssh <command>}"
        exit 1
        ;;
esac
