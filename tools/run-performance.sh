#!/usr/bin/env bash
set -euo pipefail
iterations="${PERFORMANCE_ITERATIONS:-3}"
[[ "$iterations" =~ ^([1-9]|10)$ ]] || { echo 'Iterations must be 1–10' >&2; exit 1; }
[[ "${EPHEMERAL_DEVICE:-}" == true ]] || { echo 'Disposable emulator required' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Emulator required' >&2; exit 1; }
package=io.github.aroum.kazuar.inputmethod.latin.benchmark
mkdir -p performance-artifacts
collect() {
  local result=$?
  trap - EXIT
  adb logcat -d > performance-artifacts/logcat.txt 2>&1 || true
  adb shell dumpsys meminfo "$package" > performance-artifacts/memory.txt 2>&1 || true
  adb shell dumpsys gfxinfo "$package" > performance-artifacts/frames.txt 2>&1 || true
  if [[ "$result" != 0 ]]; then
    adb exec-out screencap -p > performance-artifacts/failure.png 2>/dev/null || true
    adb shell uiautomator dump /sdcard/performance-window.xml >/dev/null 2>&1 || true
    adb pull /sdcard/performance-window.xml performance-artifacts/window.xml >/dev/null 2>&1 || true
  fi
  exit "$result"
}
trap collect EXIT
./gradlew :app:installBenchmark -PperformanceBenchmarks=true --max-workers=2 --stacktrace
adb shell ime enable "$package/io.github.aroum.kazuar.inputmethod.latin.LatinIME"
adb shell ime set "$package/io.github.aroum.kazuar.inputmethod.latin.LatinIME"
adb shell am wait-for-broadcast-idle
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest -PperformanceBenchmarks=true   -Pandroid.testInstrumentationRunnerArguments.ephemeralDevice=true   -Pandroid.testInstrumentationRunnerArguments.performanceIterations="$iterations"   -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR   --max-workers=2 --stacktrace 2>&1 | tee performance-artifacts/gradle.log
