// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import android.content.Intent
import androidx.preference.Preference
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsAndroid13Test {
    @Test fun settingsOpenAndNavigateOnAndroid13() {
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.supportFragmentManager.executePendingTransactions()
            val fragment = activity.supportFragmentManager.fragments.single() as SettingsFragment
            val backup = Preference(activity).apply { this.fragment = BackupSettingsFragment::class.java.name }
            assertTrue(activity.onPreferenceStartFragment(fragment, backup))
            activity.supportFragmentManager.executePendingTransactions()
            val screen = activity.supportFragmentManager.fragments.single() as BackupSettingsFragment
            assertNotNull(screen.findPreference<Preference>("pref_backup_all_import"))
        }
    }

    @Test fun sensitiveClipboardToggleIsNextToHistoryAndDefaultsOff() {
        val intent = Intent().putExtra(PreferenceHostActivity.EXTRA_SHOW_FRAGMENT, LayoutSettingsFragment::class.java.name)
        Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            activity.supportFragmentManager.executePendingTransactions()
            val screen = activity.supportFragmentManager.fragments.single() as LayoutSettingsFragment
            val history = screen.findPreference<Preference>(Settings.PREF_ENABLE_CLIPBOARD_HISTORY)!!
            val sensitive = screen.findPreference<Preference>(Settings.PREF_SAVE_SENSITIVE_CLIPS)!!
            assertEquals(history.parent, sensitive.parent)
            assertEquals(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, sensitive.dependency)
            assertFalse(DeviceProtectedUtils.getSharedPreferences(activity)
                    .getBoolean(Settings.PREF_SAVE_SENSITIVE_CLIPS, false))
        }
    }

    @Test fun untrustedFragmentNameIsNotInstantiated() {
        val intent = Intent().putExtra(PreferenceHostActivity.EXTRA_SHOW_FRAGMENT, "org.example.UntrustedFragment")
        Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().use { controller ->
            assertTrue(controller.get().isFinishing)
        }
    }
}
