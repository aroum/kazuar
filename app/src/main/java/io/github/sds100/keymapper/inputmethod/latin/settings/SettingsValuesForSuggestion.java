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

public class SettingsValuesForSuggestion {
    public final boolean mBlockPotentiallyOffensive;
    public final boolean mReplaceYoWithYe;
    public final boolean mSplitSpaceNeighborWords;
    public final boolean mEnableMacroReplacements;

    public SettingsValuesForSuggestion(final boolean blockPotentiallyOffensive,
            final boolean replaceYoWithYe, final boolean splitSpaceNeighborWords,
            final boolean enableMacroReplacements) {
        mBlockPotentiallyOffensive = blockPotentiallyOffensive;
        mReplaceYoWithYe = replaceYoWithYe;
        mSplitSpaceNeighborWords = splitSpaceNeighborWords;
        mEnableMacroReplacements = enableMacroReplacements;
    }

    public SettingsValuesForSuggestion(final boolean blockPotentiallyOffensive,
            final boolean replaceYoWithYe, final boolean splitSpaceNeighborWords) {
        this(blockPotentiallyOffensive, replaceYoWithYe, splitSpaceNeighborWords, true);
    }

    public SettingsValuesForSuggestion(final boolean blockPotentiallyOffensive,
            final boolean replaceYoWithYe) {
        this(blockPotentiallyOffensive, replaceYoWithYe, true);
    }

    public SettingsValuesForSuggestion(final boolean blockPotentiallyOffensive) {
        this(blockPotentiallyOffensive, false, true);
    }
}
