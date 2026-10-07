/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.sds100.keymapper.inputmethod.latin.settings

import android.os.Build
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.TwoStatePreference
import io.github.sds100.keymapper.inputmethod.keyboard.KeyboardTheme
import io.github.sds100.keymapper.inputmethod.latin.R
import io.github.sds100.keymapper.inputmethod.latin.common.Constants
import io.github.sds100.keymapper.inputmethod.latin.define.ProductionFlags
import java.util.*

/**
 * "Appearance" settings sub screen.
 */
class AppearanceSettingsFragment : SubScreenFragment(), Preference.OnPreferenceChangeListener {

    private var selectedThemeId = 0

    private lateinit var themeFamilyPref: ListPreference
    private lateinit var themeVariantPref: ListPreference
    private lateinit var keyBordersPref: TwoStatePreference
    private var dayNightPref: TwoStatePreference? = null


    private val REQ_LOAD_CUSTOM_FILE = 2001

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_appearance)
        val keyboardTheme = KeyboardTheme.getKeyboardTheme(activity)
        selectedThemeId = keyboardTheme.mThemeId

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            removePreference(Settings.PREF_THEME_DAY_NIGHT)
        }
        setupTheme()
        setupLayoutsAndIcon()

        if (!ProductionFlags.IS_SPLIT_KEYBOARD_SUPPORTED) {
            removePreference(Settings.PREF_ENABLE_SPLIT_KEYBOARD_PORTRAIT)
            removePreference(Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE)
        }
        setupKeyboardHeight(
                Settings.PREF_KEYBOARD_HEIGHT_SCALE, SettingsValues.DEFAULT_SIZE_SCALE)
    }

    override fun onResume() {
        super.onResume()
        updateThemePreferencesState()
    }

    override fun onPreferenceChange(preference: Preference, value: Any?): Boolean {
        (preference as? ListPreference)?.apply {
            summary = entries[entryValues.indexOfFirst { it == value }]
        }
        saveSelectedThemeId()
        return true
    }

    private fun saveSelectedThemeId(
            family: String = themeFamilyPref.value,
            variant: String = themeVariantPref.value,
            keyBorders: Boolean = keyBordersPref.isChecked,
            dayNight: Boolean = dayNightPref?.isChecked ?: false,
            amoledMode: Boolean = false
    ) {
        selectedThemeId = KeyboardTheme.getThemeForParameters(family, variant, keyBorders, dayNight, amoledMode)
        KeyboardTheme.saveKeyboardThemeId(selectedThemeId, sharedPreferences)
    }

    private fun updateThemePreferencesState(skipThemeFamily: Boolean = false, skipThemeVariant: Boolean = false) {
        val themeFamily = KeyboardTheme.getThemeFamily(selectedThemeId)
        val isLegacyFamily = KeyboardTheme.THEME_FAMILY_HOLO == themeFamily
        if (!skipThemeFamily) {
            themeFamilyPref.apply {
                value = themeFamily
                summary = themeFamily
            }
        }
        val variants = KeyboardTheme.THEME_VARIANTS[themeFamily]!!
        val variant = KeyboardTheme.getThemeVariant(selectedThemeId)
        if (!skipThemeVariant) {
            themeVariantPref.apply {
                entries = variants
                entryValues = variants
                value = variant ?: variants[0]
                summary = variant ?: "Auto"
                isEnabled = isLegacyFamily || !KeyboardTheme.getIsDayNight(selectedThemeId)
            }
        }
        keyBordersPref.apply {
            isEnabled = !isLegacyFamily
            isChecked = isLegacyFamily || KeyboardTheme.getHasKeyBorders(selectedThemeId)
        }
        dayNightPref?.apply {
            isEnabled = !isLegacyFamily
            isChecked = !isLegacyFamily && KeyboardTheme.getIsDayNight(selectedThemeId)
        }
    }

    private fun setupTheme() {
        themeFamilyPref = preferenceScreen.findPreference(Settings.PREF_THEME_FAMILY) as ListPreference
        themeFamilyPref.apply {
            entries = KeyboardTheme.THEME_FAMILIES
            entryValues = KeyboardTheme.THEME_FAMILIES
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                saveSelectedThemeId(family = value as String)
                updateThemePreferencesState(skipThemeFamily = true)
                true
            }
        }
        themeVariantPref = preferenceScreen.findPreference(Settings.PREF_THEME_VARIANT) as ListPreference
        themeVariantPref.apply {
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                saveSelectedThemeId(variant = value as String)
                updateThemePreferencesState(skipThemeFamily = true, skipThemeVariant = true)
                true
            }
        }
        keyBordersPref = preferenceScreen.findPreference(Settings.PREF_THEME_KEY_BORDERS) as TwoStatePreference
        keyBordersPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            saveSelectedThemeId(keyBorders = value as Boolean)
            updateThemePreferencesState(skipThemeFamily = true)
            true
        }
        dayNightPref = preferenceScreen.findPreference(Settings.PREF_THEME_DAY_NIGHT) as? TwoStatePreference
        dayNightPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            saveSelectedThemeId(dayNight = value as Boolean)
            updateThemePreferencesState(skipThemeFamily = true)
            true
        }
    }

    private fun setupKeyboardHeight(prefKey: String, defaultValue: Float) {
        val prefs = sharedPreferences
        val pref = findPreference(prefKey) as? SeekBarDialogPreference
        pref?.setInterface(object : SeekBarDialogPreference.ValueProxy {

            private fun getValueFromPercentage(percentage: Int) =  percentage / PERCENTAGE_FLOAT

            private fun getPercentageFromValue(floatValue: Float) = (floatValue * PERCENTAGE_FLOAT).toInt()

            override fun writeValue(value: Int, key: String) = prefs.edit()
                    .putFloat(key, getValueFromPercentage(value)).apply()

            override fun writeDefaultValue(key: String) = prefs.edit().remove(key).apply()

            override fun readValue(key: String) = getPercentageFromValue(
                    Settings.readKeyboardHeight(prefs, defaultValue))

            override fun readDefaultValue(key: String) = getPercentageFromValue(defaultValue)

            override fun getValueText(value: Int) = String.format(Locale.ROOT, "%d%%", value)

            override fun feedbackValue(value: Int) = Unit
        })
    }

    private fun setupLayoutsAndIcon() {
        val iconStylePref = findPreference("pref_icon_style") as? ListPreference
        iconStylePref?.apply {
            value = sharedPreferences.getString("pref_icon_style", "aroum")
            summary = entry
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                val strVal = value as String
                iconStylePref.value = strVal
                iconStylePref.summary = iconStylePref.entries[iconStylePref.entryValues.indexOfFirst { it == strVal }]
                io.github.sds100.keymapper.inputmethod.latin.SystemBroadcastReceiver.toggleAppIcon(activity)
                true
            }
        }

        findPreference("pref_load_custom_file")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, REQ_LOAD_CUSTOM_FILE)
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.data == null) {
            return
        }

        val uri = data.data ?: return
        val context = activity ?: return

        if (requestCode == REQ_LOAD_CUSTOM_FILE) {
            try {
                handleLoadedFile(context, uri)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, getString(R.string.toast_file_load_failed, e.message), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleLoadedFile(context: android.content.Context, uri: android.net.Uri) {
        val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw Exception("Cannot open file stream")

        val trimmed = content.trim().removePrefix("\uFEFF").trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                org.json.JSONObject(trimmed) // validate JSON
                sharedPreferences.edit()
                    .putString("pref_custom_theme_json", trimmed)
                    .putString(Settings.PREF_THEME_FAMILY, KeyboardTheme.THEME_FAMILY_CUSTOM)
                    .apply()

                selectedThemeId = KeyboardTheme.THEME_ID_CUSTOM
                KeyboardTheme.saveKeyboardThemeId(selectedThemeId, sharedPreferences)

                android.widget.Toast.makeText(context, R.string.toast_custom_theme_loaded, android.widget.Toast.LENGTH_SHORT).show()
                updateThemePreferencesState()
            } catch (e: Exception) {
                throw Exception("Invalid theme JSON format: ${e.message}")
            }
        } else if (trimmed.startsWith("<")) {
            CustomLayoutHelper.handleLoadedLayout(context, uri)
        } else {
            throw Exception(getString(R.string.error_theme_must_start_with_json))
        }
    }

    companion object {
        private const val PERCENTAGE_FLOAT = 100.0f
    }
}