package io.github.aroum.kazuar.benchmarks

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class PerformanceBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val args get() = InstrumentationRegistry.getArguments()
    private val iterations get() = (args.getString("performanceIterations") ?: "3").toInt().also {
        require(it in 1..10)
    }

    @Before fun requireDisposableDevice() {
        check(args.getString("ephemeralDevice") == "true")
        check(Build.HARDWARE in listOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
        selectKeyboard()
    }

    private fun selectKeyboard() {
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        // Gradle installs the target again before instrumentation, resetting IME selection.
        device.executeShellCommand("ime enable $IME")
        device.executeShellCommand("ime set $IME")
        check(device.executeShellCommand("settings get secure default_input_method").trim() == IME)
        device.executeShellCommand("am wait-for-broadcast-idle")
    }

    private fun editorIntent(text: String = "") = Intent().apply {
        component = ComponentName(PACKAGE, "$NAMESPACE.performance.BenchmarkEditorActivity")
        putExtra("text", text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private fun editor(): UiObject2 =
        device.wait(Until.findObject(By.desc("Benchmark editor")), 10_000)
            ?: error("Benchmark editor did not appear")

    private fun key(description: String): UiObject2 =
        device.wait(Until.findObject(By.pkg(PACKAGE).desc(
            Pattern.compile(Pattern.quote(description), Pattern.CASE_INSENSITIVE))), 10_000)
            ?: error("Keyboard key missing: $description")

    private fun awaitText(expected: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (editor().text == expected) return
            SystemClock.sleep(25)
        }
        error("Editor text mismatch, expected '$expected', got '${editor().text}'")
    }

    @Test fun coldStartup() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = iterations,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait(Intent().apply {
            component = ComponentName(PACKAGE, "$NAMESPACE.settings.SettingsActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
    }

    @Test fun keyboardShow() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric(), TraceSectionMetric("Kazuar.createInputView")),
        compilationMode = CompilationMode.Full(),
        iterations = iterations,
        setupBlock = {
            selectKeyboard()
            startActivityAndWait(editorIntent())
            device.waitForIdle()
        }
    ) {
        editor().click()
        key("a")
    }

    @Test fun keyboardTyping() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric(), TraceSectionMetric("Kazuar.keyInput")),
        compilationMode = CompilationMode.Full(),
        iterations = iterations,
        setupBlock = {
            selectKeyboard()
            startActivityAndWait(editorIntent())
            editor().click()
            key("a")
        }
    ) {
        for (letter in "test test test") {
            key(if (letter == ' ') "Space" else letter.toString()).click()
        }
        awaitText("test test test")
    }

    @Test fun keyboardDeletion() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric(), TraceSectionMetric("Kazuar.keyInput")),
        compilationMode = CompilationMode.Full(),
        iterations = iterations,
        setupBlock = {
            selectKeyboard()
            startActivityAndWait(editorIntent("one testing"))
            editor().click()
            key("Delete")
        }
    ) {
        repeat(7) { key("Delete").click() }
        awaitText("one ")
    }

    companion object {
        private const val NAMESPACE = "io.github.aroum.kazuar.inputmethod.latin"
        private const val PACKAGE = "$NAMESPACE.benchmark"
        private const val IME = "$PACKAGE/$NAMESPACE.LatinIME"
    }
}
