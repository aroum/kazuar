// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.diagnostics

import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import android.os.Looper
import android.app.AlertDialog
import io.github.aroum.kazuar.inputmethod.latin.R
import io.github.aroum.kazuar.inputmethod.latin.settings.SettingsActivity
import io.github.aroum.kazuar.inputmethod.latin.settings.DebugSettingsFragment
import io.github.aroum.kazuar.inputmethod.latin.settings.PreferenceHostActivity
import androidx.preference.Preference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class CrashReporterTest {
    @Test fun handlerStoresTheCauseAndAlwaysDelegates() {
        val context: android.app.Application = RuntimeEnvironment.getApplication()
        val error = IllegalStateException("dialog failed", IllegalArgumentException("cause details"))
        val delegated = AtomicBoolean()
        val handler = CrashReporter.Handler(context) { thread, failure ->
            assertSame(Thread.currentThread(), thread)
            assertSame(error, failure)
            delegated.set(true)
        }
        handler.uncaughtException(Thread.currentThread(), error)
        assertTrue(delegated.get())
        val report = CrashReporter.report(context)!!
        assertTrue(report.contains("dialog failed"))
        assertTrue(report.contains("cause details"))
        assertTrue(report.contains("CrashReporterTest"))
        assertTrue(report.contains("Revision:"))
        assertTrue(CrashReporter.file(context).baseFile.canonicalPath
                .startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
        assertTrue(CrashReporter.takeNotice(context))
        assertFalse(CrashReporter.takeNotice(context))
    }

    @Test fun aLargeOrCyclicCauseIsBounded() {
        val context: android.app.Application = RuntimeEnvironment.getApplication()
        val error = IllegalArgumentException("x".repeat(100000))
        val report = CrashReporter.format(context, Thread.currentThread(), error)
        assertTrue(report.length <= CrashReporter.MAX_CHARS)
        val first = IllegalStateException("first")
        val second = IllegalStateException("second")
        first.initCause(second); second.initCause(first)
        val cycle = CrashReporter.format(context, Thread.currentThread(), first)
        assertTrue(cycle.contains("first")); assertTrue(cycle.contains("second"))
        assertTrue(cycle.length <= CrashReporter.MAX_CHARS)
        assertTrue(CrashReporter.save(context, System.currentTimeMillis(), 1, "x".repeat(100000)))
        assertTrue(CrashReporter.file(context).baseFile.length() <= 65536)
        assertTrue(CrashReporter.report(context)!!.length <= CrashReporter.MAX_CHARS)
    }

    @Test fun aReportWriteFailureStillDelegatesTheOriginalException() {
        val context: android.app.Application = RuntimeEnvironment.getApplication()
        val target = File(context.cacheDir, "not-a-directory").apply { writeText("blocked") }
        val blocked = object : android.content.ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = target
        }
        val failure = IllegalStateException("original failure")
        var delegated = false
        try {
            assertFalse(CrashReporter.save(blocked, 1, 1, "cannot write"))
            CrashReporter.Handler(blocked) { _, error ->
                assertSame(failure, error); delegated = true
            }.uncaughtException(Thread.currentThread(), failure)
            assertTrue(delegated)
        } finally { target.delete() }
    }

    @Test fun reportCanBeViewedAndCopiedWithoutAnExternalIntent() {
        Robolectric.buildActivity(SettingsActivity::class.java, android.content.Intent().putExtra(
                PreferenceHostActivity.EXTRA_SHOW_FRAGMENT, DebugSettingsFragment::class.java.name)).setup().use { controller ->
            val activity = controller.get()
            activity.supportFragmentManager.executePendingTransactions()
            val screen = activity.supportFragmentManager.fragments.filterIsInstance<DebugSettingsFragment>().single()
            val clipboard = activity.getSystemService(ClipboardManager::class.java)
            clipboard.clearPrimaryClip()
            val startedBefore = shadowOf(activity).nextStartedActivity
            assertNull(startedBefore)
            assertTrue(CrashReporter.save(activity, System.currentTimeMillis(), 1, "test stack trace"))
            screen.findPreference<Preference>("pref_crash_report")!!.performClick()
            activity.supportFragmentManager.executePendingTransactions()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue(dialog.isShowing)
            assertNull(clipboard.primaryClip) // Opening a report does not copy it.
            assertEquals(activity.getString(R.string.crash_report_copy), dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("test stack trace", clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertTrue(clipboard.primaryClipDescription!!.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }
}
