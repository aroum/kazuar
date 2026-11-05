/*
 * Copyright (C) 2013 The Android Open Source Project
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

package io.github.aroum.kazuar.inputmethod.latin.utils;

import io.github.aroum.kazuar.inputmethod.latin.settings.ActionsSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.AdvancedSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.AppearanceSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.BackupSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.CorrectionSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.CustomInputStyleSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.DebugSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.DoubleTapSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.FeedbackSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.GestureSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.IncognitoModeRulesFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.LayoutSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.PreferencesSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.SettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.settings.SwipeSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.spellcheck.SpellCheckerSettingsFragment;
import io.github.aroum.kazuar.inputmethod.latin.userdictionary.UserDictionaryAddWordFragment;
import io.github.aroum.kazuar.inputmethod.latin.userdictionary.UserDictionaryList;
import io.github.aroum.kazuar.inputmethod.latin.userdictionary.UserDictionaryLocalePicker;
import io.github.aroum.kazuar.inputmethod.latin.userdictionary.UserDictionarySettings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FragmentUtils {
    private static final Set<String> sLatinImeFragments = new HashSet<>(Arrays.asList(
            PreferencesSettingsFragment.class.getName(),
            AppearanceSettingsFragment.class.getName(),
            LayoutSettingsFragment.class.getName(),
            FeedbackSettingsFragment.class.getName(),
            ActionsSettingsFragment.class.getName(),
            CustomInputStyleSettingsFragment.class.getName(),
            GestureSettingsFragment.class.getName(),
            CorrectionSettingsFragment.class.getName(),
            AdvancedSettingsFragment.class.getName(),
            DebugSettingsFragment.class.getName(),
            IncognitoModeRulesFragment.class.getName(),
            SettingsFragment.class.getName(),
            BackupSettingsFragment.class.getName(),
            DoubleTapSettingsFragment.class.getName(),
            SwipeSettingsFragment.class.getName(),
            SpellCheckerSettingsFragment.class.getName(),
            UserDictionaryAddWordFragment.class.getName(),
            UserDictionaryList.class.getName(),
            UserDictionaryLocalePicker.class.getName(),
            UserDictionarySettings.class.getName()
    ));

    public static boolean isValidFragment(String fragmentName) {
        return sLatinImeFragments.contains(fragmentName);
    }
}
