# Performance measurements

Run **Performance measurements** in GitHub Actions and choose 1–10 iterations.
The workflow also runs on the development branch `fix/performance-profiling`.
The default is three iterations on a disposable Android 16/API 36 x86_64 emulator
with 4 GiB RAM, disabled system animations and full AOT compilation.

The opt-in `-PperformanceBenchmarks=true` enables a non-debuggable, profileable
build and the separate Macrobenchmark module. The measured package is
`io.github.aroum.kazuar.inputmethod.latin.benchmark`. Its editor activity exists
only in that variant. The harness requires the disposable-emulator flag and
checks the selected IME before measuring. The workflow installs and selects
Kazuar in the disposable emulator.

## Scenarios

- `coldStartup` opens settings after a process-cold start and measures startup
  and frames. Filesystem caches remain warm.
- `keyboardShow` opens the keyboard in the benchmark editor.
- `keyboardTyping` taps the actual English keyboard to enter `test test test`
  and checks the resulting text.
- `keyboardDeletion` deletes `testing` from `one testing` through the actual
  Delete key and checks that `one ` remains.

Input scenarios use accessibility key descriptions and real touch input.
They require the default English keyboard on the fresh English emulator.
UI Automator operations and waits contribute to scenario durations.
This suite measures fixed workloads and does not replace correction regression
tests or cover all languages, gesture typing and clipboard workloads.

## Reports

The run summary contains measurements. The `performance-api36-<commit>`
artifact contains raw benchmark JSON, per-iteration Perfetto traces, reports,
logs and final memory/frame snapshots. On failure it also includes a screenshot
and window hierarchy. Retention is 30 days.

Open traces in https://ui.perfetto.dev/ or Android Studio.
`Kazuar.keyInput` covers synchronous event processing and keyboard-state updates.
Asynchronous suggestions can continue after this section.
`Kazuar.createInputView` measures view construction when it occurs. A cached
keyboard can be shown without another view construction.

Missing scenarios, invalid metrics, missing traces and failed workloads fail the
job. Emulator timings contain host noise. There are no absolute timing gates or
automatic historical comparisons. Compare repeated runs with the same workload,
API and compilation settings. Final memory snapshots do not measure peak memory
or establish the presence of leaks.

Reference https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
