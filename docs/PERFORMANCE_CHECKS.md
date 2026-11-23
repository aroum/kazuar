# Performance measurements

Run **Performance measurements** in GitHub Actions and choose 1–30 iterations.
The workflow also runs on the development branch `fix/performance-profiling`.
The default is ten iterations on a disposable Android 16/API 36 x86_64 emulator
with 4 GiB RAM, disabled system animations and full AOT compilation.

The opt-in `-PperformanceBenchmarks=true` enables a non-debuggable, profileable
build and the separate Macrobenchmark module. The measured package is
`io.github.aroum.kazuar.inputmethod.latin.benchmark`. Its editor activity exists
only in that variant. The harness requires the disposable-emulator flag and
selects and checks the IME before measuring. The profiling variant disables the
system broadcast receiver because Macrobenchmark force-stops the IME between
measurements, causing Android to select a fallback keyboard. The workflow installs and selects
Kazuar in the disposable emulator, pins its declared en_US subtype and requires a
loaded main dictionary, enabled suggestions/personalization, and a responsive
InputConnection before measuring. A benchmark-only IME subclass and shell-only
status provider expose these checks. The editor runs in a separate process, as a
normal client application does.

## Scenarios

- `coldStartup` opens settings after a process-cold start and measures startup
  and frames. Filesystem caches remain warm.
- `keyboardShow` shows a previously prepared keyboard in the benchmark editor.
  Dictionary loading and initial input-view creation occur during setup.
- `keyboardTyping` taps the actual English keyboard to enter `test test test`
  and checks the resulting text.
- `keyboardDeletion` deletes `testing` from `one testing` through the actual
  Delete key and checks that `one ` remains.

Input scenarios invoke ACTION_CLICK on the keyboard accessibility nodes.
Kazuar translates these actions into touch events in its keyboard view.
They require the default English keyboard on the fresh English emulator.
UI Automator operations and waits contribute to scenario durations.
This suite measures fixed workloads and does not replace correction regression
tests or cover all languages, gesture typing and clipboard workloads.

## Reports

The run summary contains measurements. The `performance-api36-<commit>`
artifact contains raw benchmark JSON, per-iteration Perfetto traces, reports,
logs and live memory/frame snapshots taken during scenario setup. On failure it also includes a screenshot
and window hierarchy. Retention is 30 days.

Open traces in https://ui.perfetto.dev/ or Android Studio.
`Kazuar.keyInput` covers synchronous event processing and keyboard-state updates.
Asynchronous suggestions can continue after this section.
`Kazuar.createInputView` measures view construction when it occurs. A cached
keyboard can be shown without another view construction.

Missing scenarios, invalid metrics, missing traces and failed workloads fail the
job. Emulator timings contain host noise. There are no absolute timing gates or
automatic historical comparisons. Compare repeated runs with the same workload,
API and compilation settings. Setup snapshots do not measure peak memory or establish the presence of leaks.
They are collected before each keyboard measurement while the IME is alive,
then pulled from shared emulator storage after the harness uninstalls its APKs.

Reference https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
