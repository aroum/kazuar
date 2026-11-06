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

#include "utils/time_keeper.h"

#include <ctime>
#if defined(__ANDROID__)
#include <sys/syscall.h>
#include <unistd.h>
#endif

namespace latinime {

std::atomic<int64_t> TimeKeeper::sCurrentTime{0};
std::mutex TimeKeeper::sWriteMutex;
bool TimeKeeper::sSetForTesting;

/* static  */ void TimeKeeper::setCurrentTime() {
    std::lock_guard<std::mutex> lock(sWriteMutex);
    if (sSetForTesting) return;
#if defined(__ANDROID__) && !defined(__LP64__) && defined(__NR_clock_gettime64)
    struct KernelTimespec64 { int64_t seconds; int64_t nanos; } ts;
    if (syscall(__NR_clock_gettime64, CLOCK_REALTIME, &ts) == 0) {
        sCurrentTime.store(ts.seconds, std::memory_order_relaxed);
        return;
    }
#endif
    const time_t now = time(nullptr);
    if (now == static_cast<time_t>(-1)) return;
    // Legacy 32-bit bionic time_t preserves the unsigned epoch through 2106.
    sCurrentTime.store(sizeof(time_t) == 4 ? static_cast<int64_t>(static_cast<uint32_t>(now))
            : static_cast<int64_t>(now), std::memory_order_relaxed);
}

/* static */ void TimeKeeper::startTestModeWithForceCurrentTime(const int64_t currentTime) {
    std::lock_guard<std::mutex> lock(sWriteMutex);
    sCurrentTime = currentTime;
    sSetForTesting = true;
}

/* static */ void TimeKeeper::stopTestMode() {
    std::lock_guard<std::mutex> lock(sWriteMutex);
    sSetForTesting = false;
}

} // namespace latinime
