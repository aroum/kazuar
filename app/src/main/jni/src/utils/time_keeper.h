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

#ifndef LATINIME_TIME_KEEPER_H
#define LATINIME_TIME_KEEPER_H

#include "defines.h"
#include <atomic>
#include <cstdint>
#include <mutex>

namespace latinime {

class TimeKeeper {
 public:
    static void setCurrentTime();

    static void startTestModeWithForceCurrentTime(const int64_t currentTime);

    static void stopTestMode();

    static int64_t peekCurrentTime() { return sCurrentTime.load(std::memory_order_relaxed); }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(TimeKeeper);

    static std::atomic<int64_t> sCurrentTime;
    static std::mutex sWriteMutex;
    static bool sSetForTesting;
};
} // namespace latinime
#endif /* LATINIME_TIME_KEEPER_H */
