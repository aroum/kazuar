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

#ifndef LATINIME_EDIT_DISTANCE_H
#define LATINIME_EDIT_DISTANCE_H

#include <algorithm>
#include <vector>
#include <limits>

#include "defines.h"
#include "suggest/policyimpl/utils/edit_distance_policy.h"

namespace latinime {

class EditDistance {
 public:
    // CAVEAT: There may be performance penalty if you need the edit distance as an integer value.
    AK_FORCE_INLINE static float getEditDistance(const EditDistancePolicy *const policy) {
        if (!policy) return std::numeric_limits<float>::infinity();
        const int beforeLength = policy->getString0Length();
        const int afterLength = policy->getString1Length();
        if (beforeLength < 0 || afterLength < 0 || beforeLength > 4096 || afterLength > 4096) {
            return std::numeric_limits<float>::infinity();
        }
        std::vector<float> older(afterLength + 1), previous(afterLength + 1), current(afterLength + 1);
        for (int j = 0; j <= afterLength; ++j) previous[j] = j ? j * policy->getDeletionCost(-1, j - 1) : 0;
        for (int i = 0; i < beforeLength; ++i) {
            current[0] = (i + 1) * policy->getInsertionCost(i, -1);
            for (int j = 0; j < afterLength; ++j) {
                current[j + 1] = std::min(previous[j + 1] + policy->getInsertionCost(i, j),
                        std::min(current[j] + policy->getDeletionCost(i, j),
                                previous[j] + policy->getSubstitutionCost(i, j)));
                if (i > 0 && j > 0 && policy->allowTransposition(i, j)) {
                    current[j + 1] = std::min(current[j + 1], older[j - 1] + policy->getTranspositionCost(i, j));
                }
            }
            older.swap(previous);
            previous.swap(current);
        }
        return previous[afterLength];
    }

    AK_FORCE_INLINE static void dumpEditDistance10ForDebug(const float *const editDistanceTable,
            const int editDistanceTableWidth, const int outputLength) {
        if (DEBUG_DICT) {
            AKLOGI("EditDistanceTable");
            for (int i = 0; i <= 10; ++i) {
                float c[11];
                for (int j = 0; j <= 10; ++j) {
                    if (j < editDistanceTableWidth + 1 && i < outputLength + 1) {
                        c[j] = (editDistanceTable + i * (editDistanceTableWidth + 1))[j];
                    } else {
                        c[j] = -1.0f;
                    }
                }
                AKLOGI("[ %f, %f, %f, %f, %f, %f, %f, %f, %f, %f, %f ]",
                        c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10]);
                (void)c; // To suppress compiler warning
            }
        }
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(EditDistance);
};
} // namespace latinime

#endif  // LATINIME_EDIT_DISTANCE_H
