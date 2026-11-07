// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import io.github.aroum.kazuar.inputmethod.latin.R;

public final class AppOverrideSettingsFragment extends SubScreenFragment {
    public static final String ARG_PACKAGE = "package";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String app = getArguments() == null ? null : getArguments().getString(ARG_PACKAGE);
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);
        if (!AppOverrides.validPackage(app)) { requireActivity().finish(); return; }
        screen.setTitle(app);
        Preference explanation = new Preference(requireContext());
        explanation.setTitle(app);
        explanation.setSummary(R.string.app_override_explanation);
        explanation.setSelectable(false); screen.addPreference(explanation);
        add(app, AppOverrides.ENABLED, R.string.app_override_enabled, false, null);
        String dependency = AppOverrides.key(app, AppOverrides.ENABLED);
        add(app, AppOverrides.LEARNING, R.string.app_override_learning,
                !Settings.readAlwaysIncognitoMode(getSharedPreferences()), dependency);
        add(app, AppOverrides.SUGGESTIONS, R.string.app_override_suggestions,
                getSharedPreferences().getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true), dependency);
        add(app, AppOverrides.CORRECTION, R.string.app_override_correction,
                Settings.readAutoCorrectEnabled(getSharedPreferences(), getResources()), dependency);
        add(app, AppOverrides.PERSONALIZED, R.string.app_override_personalized,
                getSharedPreferences().getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true), dependency);
        add(app, AppOverrides.PREDICTIONS, R.string.app_override_predictions,
                getSharedPreferences().getBoolean(Settings.PREF_BIGRAM_PREDICTIONS,
                        getResources().getBoolean(R.bool.config_default_next_word_prediction)), dependency);
        add(app, AppOverrides.CAPS, R.string.app_override_caps,
                getSharedPreferences().getBoolean(Settings.PREF_AUTO_CAP, true), dependency);
    }
    private void add(String app, String option, int title, boolean defaultValue, String dependency) {
        SwitchPreferenceCompat pref = new SwitchPreferenceCompat(requireContext());
        pref.setKey(AppOverrides.key(app, option)); pref.setTitle(title); pref.setDefaultValue(defaultValue);
        getPreferenceScreen().addPreference(pref);
        if (dependency != null) pref.setDependency(dependency);
    }
}
