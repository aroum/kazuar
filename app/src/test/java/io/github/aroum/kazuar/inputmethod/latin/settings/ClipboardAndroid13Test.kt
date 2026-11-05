// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import io.github.aroum.kazuar.inputmethod.latin.ClipboardHistoryManager
import io.github.aroum.kazuar.inputmethod.latin.LatinIME
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClipboardAndroid13Test {
    @Test fun disabledHistoryDoesNotCaptureInitialClipboard() {
        val service = Robolectric.buildService(LatinIME::class.java).get()
        DeviceProtectedUtils.getSharedPreferences(service).edit()
                .putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false).commit()
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("test", "private"))
        val history = ClipboardHistoryManager(service)
        try {
            history.onCreate()
            assertEquals(0, history.getHistorySize())
        } finally { history.onDestroy() }
    }

    @Test fun sensitiveCaptureRequiresExplicitOptIn() {
        val service = Robolectric.buildService(LatinIME::class.java).get()
        val prefs = DeviceProtectedUtils.getSharedPreferences(service)
        prefs.edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true)
                .putBoolean(Settings.PREF_SAVE_SENSITIVE_CLIPS, false).commit()
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("secret", "password")
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        clipboard.setPrimaryClip(clip)
        val history = ClipboardHistoryManager(service)
        try {
            history.onCreate()
            assertEquals(0, history.getHistorySize())
            prefs.edit().putBoolean(Settings.PREF_SAVE_SENSITIVE_CLIPS, true).commit()
            history.onPrimaryClipChanged()
            assertEquals("password", history.getHistoryEntry(0)?.content.toString())
        } finally { history.onDestroy() }
    }

    @Test fun distinctClipsAtSameTimeHaveDistinctStableIdsAndMissingIdsAreSafe() {
        val service = Robolectric.buildService(LatinIME::class.java).get()
        DeviceProtectedUtils.getSharedPreferences(service).edit()
                .putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("first", "first"))
        val history = ClipboardHistoryManager(service)
        try {
            history.onCreate()
            clipboard.setPrimaryClip(ClipData.newPlainText("second", "second"))
            history.onPrimaryClipChanged()
            assertEquals(2, history.getHistorySize())
            assertNotEquals(history.getHistoryEntry(0)?.timeStamp, history.getHistoryEntry(1)?.timeStamp)
            assertNull(history.getHistoryEntryContent(Long.MIN_VALUE))
            history.toggleClipPinned(Long.MIN_VALUE)
            val id = history.getHistoryEntry(0)!!.timeStamp
            history.toggleClipPinned(id)
            assertEquals(id, history.getHistoryEntryContent(id)?.timeStamp)
        } finally { history.onDestroy() }
    }
}
