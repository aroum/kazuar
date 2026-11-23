package io.github.aroum.kazuar.benchmarks

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
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
        require(it in 1..30)
    }

    @Before fun requireDisposableDevice() {
        check(args.getString("ephemeralDevice") == "true")
        check(Build.HARDWARE in listOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
        selectKeyboard()
    }

    private fun selectKeyboard() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        // Wait until the system has processed the target installation broadcast.
        val deadline = SystemClock.uptimeMillis() + 30_000
        var methods: String
        do {
            methods = device.executeShellCommand("ime list -a -s")
            if (IME in methods.lineSequence().map { it.trim() }.toList()) break
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        check(IME in methods) { "Profiling IME was not registered: $methods" }
        device.executeShellCommand("ime enable $IME")
        // Pin the declared en_US subtype, rather than relying on implicit selection.
        device.executeShellCommand("settings put secure enabled_input_methods '$IME;$ENGLISH_SUBTYPE'")
        device.executeShellCommand("ime set $IME")
        device.executeShellCommand("settings put secure selected_input_method_subtype $ENGLISH_SUBTYPE")
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

    private fun pressKey(description: String) {
        key(description)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.packageName?.toString() == PACKAGE
                && node.contentDescription?.toString().equals(description, ignoreCase = true)) {
                return node
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                find(child)?.let { return it }
            }
            return null
        }
        val node = automation.windows.mapNotNull { it.root }.firstNotNullOfOrNull { find(it) }
            ?: error("Accessibility key missing: $description")
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            "Keyboard rejected click: $description"
        }
    }

    private fun awaitText(expected: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (editor().text == expected) return
            SystemClock.sleep(25)
        }
        error("Editor text mismatch, expected '$expected', got '${editor().text}'")
    }

    private fun status(): String =
        device.executeShellCommand("content query --uri content://$PACKAGE.performance")

    private fun ready(status: String) =
        listOf("locale=en_US", "main_dictionary=1", "slow_connection=0",
            "suggestions=1", "learning=1").all { it in status }

    private fun awaitReady() {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var state: String
        do {
            state = status()
            if (ready(state)) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        error("IME is not ready for measurement: $state")
    }

    private fun checkReady() {
        val state = status()
        check(ready(state)) { "Measured IME path is incomplete: $state" }
    }

    private var snapshotIndex = 0
    private fun snapshot(scenario: String) {
        // Capture a live, prepared process outside the measured block. Download survives uninstall.
        val base = "/sdcard/Download/kazuar-performance/$scenario-${snapshotIndex++}"
        device.executeShellCommand("mkdir -p /sdcard/Download/kazuar-performance")
        device.executeShellCommand("sh -c 'dumpsys meminfo $PACKAGE > $base-memory.txt'")
        device.executeShellCommand("sh -c 'dumpsys gfxinfo $PACKAGE > $base-frames.txt'")
        device.executeShellCommand("sh -c 'content query --uri content://$PACKAGE.performance > $base-status.txt'")
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
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        iterations = iterations,
        setupBlock = {
            selectKeyboard()
            startActivityAndWait(editorIntent())
            editor().click()
            key("a")
            awaitReady()
            snapshot("show")
            device.pressBack()
            editor()
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
            awaitReady()
            snapshot("typing")
        }
    ) {
        for (letter in "test test test") {
            pressKey(if (letter == ' ') "Space" else letter.toString())
        }
        awaitText("test test test")
        checkReady()
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
            awaitReady()
            snapshot("deletion")
        }
    ) {
        repeat(7) { pressKey("Delete") }
        awaitText("one ")
        checkReady()
    }

    companion object {
        private const val NAMESPACE = "io.github.aroum.kazuar.inputmethod.latin"
        private const val PACKAGE = "$NAMESPACE.benchmark"
        private const val IME = "$PACKAGE/$NAMESPACE.ProfilingIme"
        // Explicit subtypeId from res/xml/method.xml (0xc9194f98).
        private const val ENGLISH_SUBTYPE = -921088104
    }
}
