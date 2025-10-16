#!/usr/bin/env bash
set -o pipefail
TS="$(date +%Y%m%d-%H%M%S)"
LOG="build-logs/debug-$TS.log"
./gradlew --no-daemon clean assembleDebug 2>&1 | tee "$LOG"
STATUS=${PIPESTATUS[0]}
if [[ $STATUS -ne 0 ]]; then
  tail -n 200 "$LOG"
else
  APK="app/build/outputs/apk/debug/app-debug.apk"
  [[ -f "$APK" ]] && echo "APK: $APK"
fi
CI_FILE=".ci-bump"
echo "$TS" > "$CI_FILE"
git add "$CI_FILE" >/dev/null 2>&1 || true
git commit -m "ci: trigger build $TS" >/dev/null 2>&1 || true
git push || true
echo "Log: $LOG"
exit $STATUS
