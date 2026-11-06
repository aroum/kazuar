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

#ifndef LATINIME_NORMAL_DISTRIBUTION_H
#define LATINIME_NORMAL_DISTRIBUTION_H

#include <cmath>

#include "defines.h"
#include "suggest/core/layout/geometry_utils.h"

namespace latinime {

// Normal distribution N(u, sigma^2).
class NormalDistribution {
 public:
    NormalDistribution(const float u, const float sigma)
            : mU(u),
              mPreComputedNonExpPart(1.0 / sqrt(2.0 * M_PI
                      * validSigma(sigma) * validSigma(sigma))),
              mPreComputedExponentPart(-0.5 / (validSigma(sigma) * validSigma(sigma))) {}

    float getProbabilityDensity(const float x) const {
        if (!std::isfinite(x) || !std::isfinite(mU)) return 0.0f;
        const double shiftedX = static_cast<double>(x) - mU;
        return mPreComputedNonExpPart
                * exp(mPreComputedExponentPart * shiftedX * shiftedX);
    }

 private:
    static double validSigma(float sigma) {
        return std::isfinite(sigma) && sigma >= 0.001f ? sigma : 0.001f;
    }
    DISALLOW_IMPLICIT_CONSTRUCTORS(NormalDistribution);

    const float mU; // mean value
    const double mPreComputedNonExpPart; // = 1 / sqrt(2 * PI * sigma^2)
    const double mPreComputedExponentPart; // = -1 / (2 * sigma^2)
};
} // namespace latinime
#endif // LATINIME_NORMAL_DISTRIBUTION_H
