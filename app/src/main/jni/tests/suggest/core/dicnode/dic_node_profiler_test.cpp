// SPDX-License-Identifier: GPL-3.0-only
#include <gtest/gtest.h>
#include "suggest/core/dicnode/dic_node_profiler.h"
namespace latinime {
TEST(DicNodeProfilerTest, AdditionalProximityMacroAndTerminalInsertionState) {
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
} // namespace latinime
