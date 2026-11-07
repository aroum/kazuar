// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import android.content.Intent
import androidx.preference.ListPreference
import androidx.preference.Preference
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import android.os.Looper
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
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
            val screen = activity.supportFragmentManager.fragments.filterIsInstance<LayoutSettingsFragment>().single()
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
    @Test fun russianLayoutDialogOpensAndPersistsSelection() {
        chooseLayout("pref_keyboard_layout_ru")
    }

    @Test fun englishLayoutDialogAlsoUpdatesFallbackLayout() {
        chooseLayout("pref_keyboard_layout_en")
    }

    private fun chooseLayout(key: String) {
        val intent = Intent().putExtra(PreferenceHostActivity.EXTRA_SHOW_FRAGMENT,
                LayoutSettingsFragment::class.java.name)
        Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            activity.supportFragmentManager.executePendingTransactions()
            val screen = activity.supportFragmentManager.fragments.filterIsInstance<LayoutSettingsFragment>().single()
            val preference = screen.findPreference<ListPreference>(key)!!
            preference.performClick()
            activity.supportFragmentManager.executePendingTransactions()
            val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
            assertTrue(dialog.isShowing)
            val index = preference.findIndexOfValue("v2")
            assertTrue(index >= 0)
            dialog.listView.performItemClick(null, index, index.toLong())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("v2", preference.value)
            assertEquals("v2", DeviceProtectedUtils.getSharedPreferences(activity).getString(key, null))
            if (key == "pref_keyboard_layout_en") {
                assertEquals("v2", DeviceProtectedUtils.getSharedPreferences(activity)
                        .getString("pref_keyboard_layout_zz", null))
            }
            assertFalse(dialog.isShowing)
        }
    }

    @Test fun layoutDialogSurvivesActivityRecreation() {
        val intent = Intent().putExtra(PreferenceHostActivity.EXTRA_SHOW_FRAGMENT,
                LayoutSettingsFragment::class.java.name)
        Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().use { controller ->
            controller.get().supportFragmentManager.executePendingTransactions()
            val screen = controller.get().supportFragmentManager.fragments
                    .filterIsInstance<LayoutSettingsFragment>().single()
            screen.findPreference<ListPreference>("pref_keyboard_layout_ru")!!.performClick()
            controller.get().supportFragmentManager.executePendingTransactions()
            controller.recreate()
            controller.get().supportFragmentManager.executePendingTransactions()
            val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
            assertTrue(dialog.isShowing)
            val restored = controller.get().supportFragmentManager.fragments
                    .filterIsInstance<LayoutSettingsFragment>().single()
            val preference = restored.findPreference<ListPreference>("pref_keyboard_layout_ru")!!
            val index = preference.findIndexOfValue("v2")
            dialog.listView.performItemClick(null, index, index.toLong())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("v2", preference.value)
        }
    }

}
