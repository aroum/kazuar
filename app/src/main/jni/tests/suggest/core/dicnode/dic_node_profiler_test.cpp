// SPDX-License-Identifier: GPL-3.0-only
#include <gtest/gtest.h>
#include "suggest/core/dicnode/dic_node_profiler.h"
#include "utils/profiler.h"
namespace latinime {
TEST(DicNodeProfilerTest, AdditionalProximityMacroAndTerminalInsertionState) {
    int points[] = {'a'}; char chars[8] = {};
    INTS_TO_CHARS(points, 1, chars, sizeof(chars));
    (void)points; (void)chars;
    DicNodeProfiler source, copy;
    PROF_ADDITIONAL_PROXIMITY(source);
    PROF_TERMINAL_INSERTION(source);
    PROF_NODE_COPY(&source, copy);
#if DEBUG_DICT
    EXPECT_EQ(1, copy.mProfAdditionalProximity);
    EXPECT_EQ(1, copy.mProfTerminalInsertion);
#endif
    PROF_NODE_RESET(copy);
#if DEBUG_DICT
    EXPECT_EQ(0, copy.mProfTerminalInsertion);
    EXPECT_EQ(0, copy.mProfAdditionalProximity);
#endif
}
#ifdef FLAG_DO_PROFILE
TEST(ProfilerTest, ZeroTotalDurationHasFinitePercentage) {
    EXPECT_FLOAT_EQ(0, Profiler::percentage(0,0));
    EXPECT_FLOAT_EQ(0, Profiler::percentage(1,0));
    EXPECT_FLOAT_EQ(50, Profiler::percentage(1,2));
}
#endif
} // namespace latinime
