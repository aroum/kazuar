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

package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.content.SharedPreferences;
import android.content.res.Resources;
import io.github.aroum.kazuar.inputmethod.latin.R;
import io.github.aroum.kazuar.inputmethod.latin.utils.ResourceUtils;
import java.util.Locale;

final class KeyPreviewSettings {
    private final SubScreenFragment screen;
    private KeyPreviewSettings(SubScreenFragment screen) { this.screen = screen; }
    static void setup(SubScreenFragment screen) { new KeyPreviewSettings(screen).setup(); }
    private void setup() {
        final Resources res = screen.getResources();
        setupKeyPreviewAnimationDuration(DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_DURATION,
                res.getInteger(R.integer.config_key_preview_show_up_duration));
        setupKeyPreviewAnimationDuration(DebugSettings.PREF_KEY_PREVIEW_DISMISS_DURATION,
                res.getInteger(R.integer.config_key_preview_dismiss_duration));
        final float defaultKeyPreviewShowUpStartScale = ResourceUtils.getFloatFromFraction(
                res, R.fraction.config_key_preview_show_up_start_scale);
        final float defaultKeyPreviewDismissEndScale = ResourceUtils.getFloatFromFraction(
                res, R.fraction.config_key_preview_dismiss_end_scale);
        setupKeyPreviewAnimationScale(DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_X_SCALE,
                defaultKeyPreviewShowUpStartScale);
        setupKeyPreviewAnimationScale(DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_Y_SCALE,
                defaultKeyPreviewShowUpStartScale);
        setupKeyPreviewAnimationScale(DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_X_SCALE,
                defaultKeyPreviewDismissEndScale);
        setupKeyPreviewAnimationScale(DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_Y_SCALE,
                defaultKeyPreviewDismissEndScale);
    }

    private void setupKeyPreviewAnimationScale(final String prefKey, final float defaultValue) {
        final SharedPreferences prefs = screen.getSharedPreferences();
        final Resources res = screen.getResources();
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)screen.findPreference(prefKey);
        if (pref == null) {
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            private static final float PERCENTAGE_FLOAT = 100.0f;

            private float getValueFromPercentage(final int percentage) {
                return percentage / PERCENTAGE_FLOAT;
            }

            private int getPercentageFromValue(final float floatValue) {
                return (int)(floatValue * PERCENTAGE_FLOAT);
            }

            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putFloat(key, getValueFromPercentage(value)).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return getPercentageFromValue(
                        Settings.readKeyPreviewAnimationScale(prefs, key, defaultValue));
            }

            @Override
            public int readDefaultValue(final String key) {
                return getPercentageFromValue(defaultValue);
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return String.format(Locale.ROOT, "%d%%", value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

    private void setupKeyPreviewAnimationDuration(final String prefKey, final int defaultValue) {
        final SharedPreferences prefs = screen.getSharedPreferences();
        final Resources res = screen.getResources();
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)screen.findPreference(prefKey);
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
                return Settings.readKeyPreviewAnimationDuration(prefs, key, defaultValue);
            }

            @Override
            public int readDefaultValue(final String key) {
                return defaultValue;
            }

            @Override
            public String getValueText(final int value) {
                return res.getString(R.string.abbreviation_unit_milliseconds, value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

}
