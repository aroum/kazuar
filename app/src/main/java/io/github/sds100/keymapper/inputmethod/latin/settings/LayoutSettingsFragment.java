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

package io.github.sds100.keymapper.inputmethod.latin.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;

import io.github.sds100.keymapper.inputmethod.keyboard.KeyboardLayoutSet;
import io.github.sds100.keymapper.inputmethod.latin.R;
import io.github.sds100.keymapper.inputmethod.latin.define.ProductionFlags;

public final class LayoutSettingsFragment extends SubScreenFragment {

    private static final int REQ_LOAD_CUSTOM_LAYOUT = 2002;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_layouts);

        if (!ProductionFlags.IS_SPLIT_KEYBOARD_SUPPORTED) {
            removePreference(Settings.PREF_ENABLE_SPLIT_KEYBOARD_PORTRAIT);
            removePreference(Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE);
        }

        setupLayoutPickers();
        setupLoadCustomLayout();
        setupHistoryRetentionTimeSettings();
        refreshEnablingsOfClipboardSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLayoutPickersState();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        refreshEnablingsOfClipboardSettings();
    }

    private void refreshEnablingsOfClipboardSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        setPreferenceEnabled(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME,
                Settings.readClipboardHistoryEnabled(prefs));
    }

    private void setupLayoutListPreference(final String key, final String defaultValue) {
        final SharedPreferences prefs = getSharedPreferences();
        final ListPreference pref = (ListPreference)findPreference(key);
        if (pref != null) {
            pref.setValue(prefs.getString(key, defaultValue));
            pref.setSummary(pref.getEntry());
            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object value) {
                    final String strVal = (String)value;
                    pref.setValue(strVal);
                    final int index = pref.findIndexOfValue(strVal);
                    if (index >= 0) {
                        pref.setSummary(pref.getEntries()[index]);
                    }
                    if ("pref_keyboard_layout_en".equals(key)) {
                        prefs.edit().putString("pref_keyboard_layout_zz", strVal).apply();
                    }
                    KeyboardLayoutSet.clearKeyboardCache();
                    return true;
                }
            });
        }
    }

    private void updateLayoutPicker(final String key, final String defaultValue) {
        final SharedPreferences prefs = getSharedPreferences();
        final ListPreference pref = (ListPreference)findPreference(key);
        if (pref != null) {
            pref.setValue(prefs.getString(key, defaultValue));
            pref.setSummary(pref.getEntry());
        }
    }

    private void setupLayoutPickers() {
        setupLayoutListPreference("pref_keyboard_layout_ru", "v3");
        setupLayoutListPreference("pref_keyboard_layout_en", "v3");
        setupLayoutListPreference("pref_keyboard_layout_editing", "default");
        setupLayoutListPreference("pref_keyboard_layout_num", "default");
        setupLayoutListPreference("pref_keyboard_layout_usr0", "empty");
        setupLayoutListPreference("pref_keyboard_layout_usr1", "empty");
        setupLayoutListPreference("pref_keyboard_layout_usr2", "empty");
    }

    private void setupLoadCustomLayout() {
        final Preference loadLayoutPref = findPreference("pref_load_custom_layout");
        if (loadLayoutPref != null) {
            loadLayoutPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, REQ_LOAD_CUSTOM_LAYOUT);
                    return true;
                }
            });
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        if (requestCode == REQ_LOAD_CUSTOM_LAYOUT) {
            final Activity activity = getActivity();
            if (activity == null) return;
            CustomLayoutHelper.handleLoadedLayout(activity, data.getData(), new CustomLayoutHelper.Callback() {
                @Override
                public void onLayoutLoaded(final String target) {
                    updateLayoutPickersState();
                }
            });
        }
    }

    private void updateLayoutPickersState() {
        updateLayoutPicker("pref_keyboard_layout_ru", "v3");
        updateLayoutPicker("pref_keyboard_layout_en", "v3");
        updateLayoutPicker("pref_keyboard_layout_editing", "default");
        updateLayoutPicker("pref_keyboard_layout_num", "default");
        updateLayoutPicker("pref_keyboard_layout_usr0", "empty");
        updateLayoutPicker("pref_keyboard_layout_usr1", "empty");
        updateLayoutPicker("pref_keyboard_layout_usr2", "empty");
    }

    private void setupHistoryRetentionTimeSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME);
        if (pref == null) {
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readClipboardHistoryRetentionTime(prefs, res);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultClipboardHistoryRetentionTime(res);
            }

            @Override
            public String getValueText(final int value) {
                if (value <= 0) {
                    return res.getString(R.string.settings_no_limit);
                }
                return res.getString(R.string.abbreviation_unit_minutes, value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }
}
