// SPDX-License-Identifier: GPL-3.0-only
#include <gtest/gtest.h>
#include <climits>
#include <cfloat>
#include <cmath>
#include <limits>
#include <set>
#include <unistd.h>
#include "dictionary/utils/probability_utils.h"
#include "dictionary/utils/byte_array_utils.h"
#include "dictionary/utils/mmapped_buffer.h"
#include "dictionary/utils/sparse_table.h"
#include "suggest/core/dicnode/dic_node_priority_queue.h"
#include "suggest/core/layout/normal_distribution_2d.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/layout/proximity_info_state.h"
#include "suggest/core/layout/proximity_info_state_utils.h"
#include "suggest/core/result/suggested_word.h"
#include "suggest/core/session/dic_traverse_session.h"

namespace latinime {
namespace {
TEST(SuggestionSafetyTest, ProbabilityEncodingHandlesNonFiniteAndOutOfRangeInputs) {
    EXPECT_EQ(0, ProbabilityUtils::encodeRawProbability(-1));
    EXPECT_EQ(0, ProbabilityUtils::encodeRawProbability(std::nanf("")));
    EXPECT_EQ(MAX_PROBABILITY, ProbabilityUtils::encodeRawProbability(INFINITY));
    EXPECT_EQ(MAX_PROBABILITY, ProbabilityUtils::encodeRawProbability(FLT_MAX));
    EXPECT_EQ(0, ProbabilityUtils::encodeRawProbability(0));
}
TEST(SuggestionSafetyTest, Signed24BitMagnitudeUsesDisjointFields) {
    for (int high : {0, 1, 127, 128, 129, 255}) {
        for (int low : {0, 1, 0x7fff, 0xffff}) {
            uint8_t bytes[] = {static_cast<uint8_t>(high), static_cast<uint8_t>(low >> 8), static_cast<uint8_t>(low)};
            int pos = 0;
            const int magnitude = ((high & 127) << 16) | low;
            EXPECT_EQ(high & 128 ? -magnitude : magnitude,
                    ByteArrayUtils::readSint24AndAdvancePosition(bytes, &pos));
            EXPECT_EQ(3, pos);
        }
    }
}
TEST(SuggestionSafetyTest, SparseTableRejectsInvalidIdsAndIncompleteBlocks) {
    BufferWithExtendableBuffer indices(1024), values(1024);
    SparseTable table(&indices, &values, 16, 4);
    for (int id : {-1, INT_MIN, INT_MAX}) {
        EXPECT_FALSE(table.contains(id)); EXPECT_EQ(static_cast<uint32_t>(NOT_A_DICT_POS), table.get(id));
        EXPECT_FALSE(table.set(id, 42));
    }
    ASSERT_TRUE(table.set(5, 42)); EXPECT_EQ(42u, table.get(5));
    EXPECT_EQ(static_cast<uint32_t>(NOT_A_DICT_POS), table.get(6));
    ASSERT_TRUE(indices.writeUint(INT_MAX, 4, 0));
    EXPECT_FALSE(table.contains(5));
    SparseTable invalid(&indices, &values, 0, 0);
    EXPECT_FALSE(invalid.set(1, 1)); EXPECT_FALSE(invalid.contains(1));
}
TEST(SuggestionSafetyTest, MmapRejectsRangesBeyondTheOpenedFile) {
    char path[] = "/tmp/kazuar-mmap-XXXXXX";
    const int fd = mkstemp(path); ASSERT_GE(fd, 0);
    const char bytes[] = "abcdef"; ASSERT_EQ(6, write(fd, bytes, 6)); close(fd);
    EXPECT_FALSE(MmappedBuffer::openBuffer(path, -1, 2, false));
    EXPECT_FALSE(MmappedBuffer::openBuffer(path, 0, -1, false));
    EXPECT_FALSE(MmappedBuffer::openBuffer(path, 5, 2, false));
    EXPECT_FALSE(MmappedBuffer::openBuffer(path, INT_MAX, INT_MAX, false));
    auto mapped = MmappedBuffer::openBuffer(path, 1, 5, true); ASSERT_TRUE(mapped);
    EXPECT_EQ('b', mapped->getReadOnlyByteArrayView()[0]);
    mapped->getReadWriteByteArrayView()[0] = 'x';
    mapped.reset(); mapped = MmappedBuffer::openBuffer(path, false);
    EXPECT_EQ('b', mapped->getReadOnlyByteArrayView()[1]); // MAP_PRIVATE
    unlink(path);
}
TEST(SuggestionSafetyTest, OutputStateDoesNotWriteSpaceBeyondFullWord) {
    DicNodeStateOutput state; state.init();
    int word[MAX_WORD_LENGTH]; std::fill(word, word + MAX_WORD_LENGTH, 'a');
    state.addMergedNodeCodePoints(MAX_WORD_LENGTH, word);
    DicNodeStateOutput next; next.init(&state);
    next.addMergedNodeCodePoints(1, word);
    EXPECT_EQ('a', next.getOutputCodePointAt(MAX_WORD_LENGTH - 1));
    EXPECT_EQ(NOT_A_CODE_POINT, next.getCurrentWordCodePointAt(0));
    for (int index : {-1, MAX_WORD_LENGTH, INT_MAX}) EXPECT_EQ(NOT_A_CODE_POINT, next.getOutputCodePointAt(index));
}
TEST(SuggestionSafetyTest, InputStateInitializesAndRejectsInvalidPointers) {
    DicNodeStateInput input;
    EXPECT_EQ(0, input.getInputIndex(0));
    for (int id : {-1, MAX_POINTER_COUNT_G, INT_MAX}) {
        input.forwardInputIndex(id, 1); input.setPrevCodePoint(id, 'x');
        input.updateInputIndexG(id, 5, 'a', 0, 0);
        EXPECT_EQ(NOT_A_CODE_POINT, input.getPrevCodePoint(id));
    }
    input.forwardInputIndex(0, INT_MAX); input.forwardInputIndex(0, 1);
    EXPECT_EQ(INT_MAX, input.getInputIndex(0));
    DicTraverseSession session; EXPECT_FALSE(session.getProximityInfoState(-1)->isUsed());
}
TEST(SuggestionSafetyTest, RootContextUsesOnlyInitializedPreviousWordIds) {
    DicNode root, first, next; root.initAsRoot(0, WordIdArrayView());
    const int word[] = {'a'}; first.initAsChild(&root, NOT_A_DICT_POS, 42, CodePointArrayView(word, 1));
    next.initAsRootWithPreviousWord(&first, 0);
    ASSERT_EQ(1u, next.getPrevWordIds().size()); EXPECT_EQ(42, next.getPrevWordIds()[0]);
    DicNode invalid; invalid.initAsChild(&root, 0, 42, CodePointArrayView());
    EXPECT_FALSE(invalid.hasChildren()); EXPECT_FALSE(invalid.isTerminalDicNode());
}
TEST(SuggestionSafetyTest, PoolGrowthPreservesLiveNodesAndRejectsDoubleReturns) {
    DicNodePool pool(1); DicNode *node = pool.getInstance(); ASSERT_TRUE(node);
    node->initAsRoot(123, WordIdArrayView()); pool.ensureCapacity(100);
    EXPECT_TRUE(node->hasChildren());
    pool.placeBackInstance(nullptr); pool.placeBackInstance(node); pool.placeBackInstance(node);
    std::set<DicNode *> addresses;
    while (DicNode *value = pool.getInstance()) EXPECT_TRUE(addresses.insert(value).second);
    EXPECT_EQ(100u, addresses.size());
}
TEST(SuggestionSafetyTest, QueueGrowthKeepsCandidatesAndZeroCapacityIsSafe) {
    DicNodePriorityQueue queue(1); DicNode root; root.initAsRoot(0, WordIdArrayView());
    queue.copyPush(&root); queue.setMaxSize(8);
    for (int i = 0; i < 7; ++i) queue.copyPush(&root);
    EXPECT_EQ(8, queue.getSize()); queue.setMaxSize(2); EXPECT_EQ(2, queue.getSize());
    queue.setMaxSize(0); queue.copyPush(&root); EXPECT_EQ(0, queue.getSize());
}
TEST(SuggestionSafetyTest, NormalDistributionAndRoundingAreFiniteAndEvaluateOnce) {
    for (float sigma : {0.0f, -1.0f, INFINITY, std::nanf("")}) {
        NormalDistribution2D distribution(0, sigma, 0, sigma, 0);
        EXPECT_TRUE(std::isfinite(distribution.getProbabilityDensity(0, 0)));
    }
    float value = 1; ROUND_FLOAT_10000(value++); EXPECT_EQ(2, value);
}
TEST(SuggestionSafetyTest, GridBoundsCheckBothAxesBeforeLinearization) {
    EXPECT_EQ(0, ProximityInfoUtils::getStartIndexFromCoordinates(0, 0, 10, 10, 3, 2));
    for (auto xy : {std::pair<int,int>(-1, 15), {30, 0}, {0, 20}, {INT_MAX, INT_MAX}}) {
        EXPECT_EQ(NOT_AN_INDEX, ProximityInfoUtils::getStartIndexFromCoordinates(xy.first, xy.second, 10, 10, 3, 2));
    }
    EXPECT_EQ(NOT_AN_INDEX, ProximityInfoUtils::getStartIndexFromCoordinates(0, 0, 0, 0, 0, 0));
}
TEST(SuggestionSafetyTest, InvalidKeyboardConfigurationIsFullyInitialized) {
    ProximityInfo info(nullptr, 0, 0, 0, 0, 0, 0, nullptr, -1, nullptr, nullptr, nullptr,
            nullptr, nullptr, nullptr, nullptr, nullptr);
    EXPECT_GT(info.getCellWidth(), 0); EXPECT_GT(info.getMostCommonKeyWidth(), 0);
    EXPECT_EQ(0, info.getKeyCount()); EXPECT_FALSE(info.hasSpaceProximity(INT_MAX, INT_MAX));
    EXPECT_FALSE(info.hasSweetSpotData(-1)); EXPECT_EQ(0, info.getSweetSpotRadiiAt(INT_MAX));
    ProximityInfoState state; int codes[MAX_WORD_LENGTH + 1] = {};
    state.initInputParams(0, 1, &info, codes, MAX_WORD_LENGTH + 1, nullptr, nullptr, nullptr, nullptr, false, nullptr);
    EXPECT_EQ(0, state.size()); EXPECT_EQ(NOT_A_CODE_POINT, state.getPrimaryCodePointAt(INT_MAX));
}
TEST(SuggestionSafetyTest, EmptyAndStationaryGestureSpeedIsFinite) {
    std::vector<int> points, times, lengths, indices; std::vector<float> rates, directions;
    EXPECT_FLOAT_EQ(1, ProximityInfoStateUtils::refreshSpeedRates(0, nullptr, nullptr, nullptr,
            0, 0, &points, &points, &times, &lengths, &indices, &rates, &directions));
    EXPECT_TRUE(directions.empty());
    points = {0}; times = {0}; lengths = {0}; indices = {0};
    EXPECT_FLOAT_EQ(1, ProximityInfoStateUtils::refreshSpeedRates(1, points.data(), points.data(), times.data(),
            0, 1, &points, &points, &times, &lengths, &indices, &rates, &directions));
    EXPECT_FLOAT_EQ(1, rates[0]);
    std::vector<int> percentiles;
    ProximityInfoStateUtils::refreshBeelineSpeedRates(10, std::nanf(""), 1, points.data(), points.data(),
            nullptr, 1, &points, &points, &indices, &percentiles);
    EXPECT_EQ(MAX_PERCENTILE, percentiles[0]);
}
TEST(SuggestionSafetyTest, EmptySuggestedWordsDoNotThrowOrReadInvalidRanges) {
    SuggestedWord empty(nullptr, 0, 0, 0, 0, 0);
    EXPECT_EQ(nullptr, empty.getCodePoint()); EXPECT_EQ(0, empty.getCodePointCount());
    int point = 'a'; SuggestedWord invalid(&point, -1, 0, 0, 0, 0);
    EXPECT_EQ(0, invalid.getCodePointCount());
}
} // namespace
} // namespace latinime
