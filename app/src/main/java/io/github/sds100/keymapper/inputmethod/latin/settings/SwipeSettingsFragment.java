package io.github.sds100.keymapper.inputmethod.latin.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;

import io.github.sds100.keymapper.inputmethod.latin.R;

public final class SwipeSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_swipe);

        setupSwipeThresholdSettings();
        setupActionSummary("pref_swipe_up_action");
        setupActionSummary("pref_swipe_down_action");
        setupActionSummary("pref_swipe_left_action");
        setupActionSummary("pref_swipe_right_action");
    }

    private void setupSwipeThresholdSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference("pref_swipe_threshold");
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
                return prefs.getInt(key, 40);
            }

            @Override
            public int readDefaultValue(final String key) {
                return 40;
            }

            @Override
            public String getValueText(final int value) {
                return value + "%";
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

    private void setupActionSummary(String key) {
        final ListPreference pref = (ListPreference)findPreference(key);
        if (pref == null) return;
        pref.setSummary(pref.getEntry());
        pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String val = (String)newValue;
                int index = pref.findIndexOfValue(val);
                if (index >= 0) {
                    pref.setSummary(pref.getEntries()[index]);
                }
                return true;
            }
        });
    }
}
