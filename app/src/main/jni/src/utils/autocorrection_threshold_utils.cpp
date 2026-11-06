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

#include "utils/autocorrection_threshold_utils.h"

#include <algorithm>
#include <cmath>

#include "defines.h"
#include "suggest/policyimpl/utils/edit_distance.h"
#include "suggest/policyimpl/utils/damerau_levenshtein_edit_distance_policy.h"

namespace latinime {

/* static */ int AutocorrectionThresholdUtils::editDistance(const int *before,
        const int beforeLength, const int *after, const int afterLength) {
    if (beforeLength < 0 || afterLength < 0 || beforeLength > 4096 || afterLength > 4096
            || (beforeLength && !before) || (afterLength && !after)) return -1;
    const DamerauLevenshteinEditDistancePolicy daemaruLevenshtein(
            before, beforeLength, after, afterLength);
    return static_cast<int>(EditDistance::getEditDistance(&daemaruLevenshtein));
}

/* static */ float AutocorrectionThresholdUtils::calcNormalizedScore(const int *before,
        const int beforeLength, const int *after, const int afterLength, const int score) {
    if (!before || !after || beforeLength <= 0 || afterLength <= 0
            || beforeLength > 4096 || afterLength > 4096) {
        return 0.0f;
    }
    const int distance = editDistance(before, beforeLength, after, afterLength);
    int spaceCount = 0;
    for (int i = 0; i < afterLength; ++i) {
        if (after[i] == KEYCODE_SPACE) {
            ++spaceCount;
        }
    }

    if (spaceCount == afterLength) {
        return 0.0f;
    }

    if (score <= 0 || distance < 0 || distance >= afterLength) {
        // normalizedScore must be 0.0f (the minimum value) if the score is less than or equal to 0,
        // or if the edit distance is larger than or equal to afterLength.
        return 0.0f;
    }
    // add a weight based on edit distance.
    const float weight = 1.0f - static_cast<float>(distance) / static_cast<float>(afterLength);

    return (static_cast<float>(score) / SUGGEST_INTERFACE_OUTPUT_SCALE) * weight;
}

} // namespace latinime
