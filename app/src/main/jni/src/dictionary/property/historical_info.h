/*
 * Copyright (C) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef LATINIME_HISTORICAL_INFO_H
#define LATINIME_HISTORICAL_INFO_H

#include "defines.h"
#include <cstdint>
#include <limits>

namespace latinime {

class HistoricalInfo {
 public:
    // Invalid historical info.
    HistoricalInfo()
            : mTimestamp(NOT_A_TIMESTAMP), mLevel(0), mCount(0) {}

    HistoricalInfo(const int64_t timestamp, const int level, const int count)
            : mTimestamp(timestamp), mLevel(level), mCount(count) {}

    bool isValid() const {
        return mTimestamp >= 0 && mTimestamp <= UINT32_MAX
                && mTimestamp != static_cast<int64_t>(static_cast<uint32_t>(NOT_A_TIMESTAMP));
    }

    // Existing dictionary/JNI fields carry unsigned 32-bit seconds, with a reserved sentinel.
    static int64_t decodeTimestamp(uint32_t value) {
        return value == static_cast<uint32_t>(NOT_A_TIMESTAMP) ? NOT_A_TIMESTAMP : static_cast<int64_t>(value);
    }
    uint32_t encodeTimestamp() const {
        return isValid() ? static_cast<uint32_t>(mTimestamp) : static_cast<uint32_t>(NOT_A_TIMESTAMP);
    }
    int32_t getLegacyTimestamp() const {
        const uint32_t value = encodeTimestamp();
        return value <= INT32_MAX ? static_cast<int32_t>(value)
                : static_cast<int32_t>(static_cast<int64_t>(value) - (int64_t{1} << 32));
    }

    int64_t getTimestamp() const {
        return mTimestamp;
    }

    // TODO: Remove
    int getLevel() const {
        return mLevel;
    }

    int getCount() const {
        return mCount;
    }

 private:
    // Default copy constructor is used for using in std::vector.
    DISALLOW_ASSIGNMENT_OPERATOR(HistoricalInfo);

    const int64_t mTimestamp;
    const int mLevel;
    const int mCount;
};
} // namespace latinime
#endif /* LATINIME_HISTORICAL_INFO_H */
