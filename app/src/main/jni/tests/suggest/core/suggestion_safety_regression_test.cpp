// SPDX-License-Identifier: GPL-3.0-only
#include <gtest/gtest.h>
#include <climits>
#include <cfloat>
#include <cmath>
#include <limits>
#include <set>
#include <unistd.h>
#include "dictionary/utils/probability_utils.h"
#include "dictionary/utils/forgetting_curve_utils.h"
#include "dictionary/utils/trie_map.h"
#include "dictionary/header/header_policy.h"
#include "dictionary/header/header_read_write_utils.h"
#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"
#include "dictionary/property/unigram_property.h"
#include "dictionary/utils/binary_dictionary_shortcut_iterator.h"
#include "dictionary/interface/dictionary_shortcuts_structure_policy.h"
#include "suggest/core/result/suggestions_output_utils.h"
#include "suggest/core/result/suggestion_results.h"
#include "suggest/core/dictionary/dictionary_utils.h"
#include "dictionary/utils/byte_array_utils.h"
#include "dictionary/utils/mmapped_buffer.h"
#include "dictionary/utils/sparse_table.h"
#include "suggest/core/dicnode/dic_node_priority_queue.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/layout/normal_distribution_2d.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/layout/proximity_info_state.h"
#include "suggest/core/layout/proximity_info_state_utils.h"
#include "suggest/core/result/suggested_word.h"
#include "suggest/core/session/dic_traverse_session.h"

namespace latinime {
class SuggestionSafetyTestPeer {
 public:
    static void cost(DicNode *node, float cost) {
        node->addCost(cost, 0, false, 1, ErrorTypeUtils::NOT_AN_ERROR);
    }
    static void saveFirstWord(DicNode *node) {
        node->saveNormalizedCompoundDistanceAfterFirstWordIfNoneYet();
    }
    static int confidence(const DicNode *node) {
        return SuggestionsOutputUtils::computeFirstWordConfidence(node);
    }
    static void shortcuts(BinaryDictionaryShortcutIterator *it, int score, bool same,
            SuggestionResults *results) {
        SuggestionsOutputUtils::outputShortcuts(it, score, same, results);
    }
};
namespace {
struct JniArrayData {
    std::vector<int> ints;
    std::vector<float> floats;
    explicit JniArrayData(std::vector<int> values) : ints(values) {}
};
class FakeJni {
 public:
    FakeJni() : table(), env() {
        table.GetArrayLength = [](JNIEnv *, jarray array) -> jsize {
            EXPECT_NE(nullptr, array);
            if (!array) return 0;
            const auto *data = reinterpret_cast<JniArrayData *>(array);
            return data->floats.empty() ? data->ints.size() : data->floats.size();
        };
        table.GetIntArrayRegion = [](JNIEnv *, jintArray array, jsize start, jsize count, jint *out) {
            const auto &data = reinterpret_cast<JniArrayData *>(array)->ints;
            ASSERT_GE(start, 0); ASSERT_LE(start + count, data.size());
            std::copy(data.begin() + start, data.begin() + start + count, out);
        };
        table.GetFloatArrayRegion = [](JNIEnv *, jfloatArray array, jsize start, jsize count, jfloat *out) {
            const auto &data = reinterpret_cast<JniArrayData *>(array)->floats;
            ASSERT_GE(start, 0); ASSERT_LE(start + count, data.size());
            std::copy(data.begin() + start, data.begin() + start + count, out);
        };
        table.FindClass = [](JNIEnv *, const char *) -> jclass { return nullptr; };
        table.ExceptionClear = [](JNIEnv *) {};
        env.functions = &table;
    }
    JNINativeInterface_ table;
    JNIEnv env;
};
class TestShortcuts : public DictionaryShortcutsStructurePolicy {
 public:
    explicit TestShortcuts(bool whitelist) : white(whitelist) {}
    int getStartPos(int pos) const override { return pos; }
    void getNextShortcut(int, int *out, int *count, bool *whitelist, bool *next, int *) const override {
        out[0] = 'x'; *count = 1; *whitelist = white; *next = false;
    }
    bool skipAllShortcuts(int *) const override { return true; }
    bool white;
};
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
    EXPECT_EQ('b', mapped->getReadOnlyByteArrayView().data()[0]);
    mapped->getReadWriteByteArrayView().data()[0] = 'x';
    mapped.reset(); mapped = MmappedBuffer::openBuffer(path, false);
    EXPECT_EQ('b', mapped->getReadOnlyByteArrayView().data()[1]); // MAP_PRIVATE
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
    DicTraverseSession session(nullptr, nullptr, false); EXPECT_FALSE(session.getProximityInfoState(-1)->isUsed());
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
    for (float sigma : {0.0f, -1.0f, FLT_MAX, INFINITY, std::nanf("")}) {
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

TEST(SuggestionSafetyTest, ComparatorIsStrictForNearbyDistancesAndNaNs) {
    DicNode root, a, b, c; root.initAsRoot(0, WordIdArrayView());
    const int x[] = {'z'}, y[] = {'m'}, z[] = {'a'};
    a.initAsChild(&root, NOT_A_DICT_POS, 1, CodePointArrayView(x, 1));
    b.initAsChild(&root, NOT_A_DICT_POS, 2, CodePointArrayView(y, 1));
    c.initAsChild(&root, NOT_A_DICT_POS, 3, CodePointArrayView(z, 1));
    SuggestionSafetyTestPeer::cost(&a, 0);
    SuggestionSafetyTestPeer::cost(&b, 0.00000075f);
    SuggestionSafetyTestPeer::cost(&c, 0.0000015f);
    EXPECT_TRUE(a.compare(&b)); EXPECT_TRUE(b.compare(&c)); EXPECT_TRUE(a.compare(&c));
    EXPECT_FALSE(a.compare(&a));
    SuggestionSafetyTestPeer::cost(&c, std::nanf(""));
    EXPECT_TRUE(a.compare(&c)); EXPECT_FALSE(c.compare(&a)); EXPECT_FALSE(c.compare(&c));
}
TEST(SuggestionSafetyTest, ShortcutScoreIsReducedOnceAndWhitelistKeepsMaximum) {
    for (int base : {S_INT_MIN, 0, 12345, S_INT_MAX}) {
        for (bool white : {false, true}) {
            TestShortcuts policy(white); BinaryDictionaryShortcutIterator it(&policy, 0);
            SuggestionResults results(4);
            SuggestionSafetyTestPeer::shortcuts(&it, base, true, &results);
            ASSERT_EQ(1, results.getSuggestionCount()); int scores[4] = {};
            results.getSortedScores(scores);
            EXPECT_EQ(white ? S_INT_MAX : std::max(S_INT_MIN + 1, base) - 1, scores[0]);
        }
    }
}
TEST(SuggestionSafetyTest, NonFiniteFirstWordConfidenceDisablesCommit) {
    DicNode node; node.initAsRoot(0, WordIdArrayView());
    SuggestionSafetyTestPeer::cost(&node, std::nanf(""));
    SuggestionSafetyTestPeer::saveFirstWord(&node);
    EXPECT_EQ(NOT_A_FIRST_WORD_CONFIDENCE, SuggestionSafetyTestPeer::confidence(&node));
}
TEST(SuggestionSafetyTest, ShortJniArraysDoNotTriggerOutOfBoundsRegionReads) {
    FakeJni jni;
    JniArrayData proximity(std::vector<int>(3 * 2 * MAX_PROXIMITY_CHARS_SIZE, KEYCODE_SPACE));
    JniArrayData shortKeys(std::vector<int>{10});
    ProximityInfo info(&jni.env, 300, 200, 3, 2, 10, 10,
            reinterpret_cast<jintArray>(&proximity), 2,
            reinterpret_cast<jintArray>(&shortKeys), reinterpret_cast<jintArray>(&shortKeys),
            reinterpret_cast<jintArray>(&shortKeys), reinterpret_cast<jintArray>(&shortKeys),
            reinterpret_cast<jintArray>(&shortKeys), nullptr, nullptr, nullptr);
    EXPECT_FALSE(info.hasTouchPositionCorrectionData());
    EXPECT_TRUE(info.hasSpaceProximity(299, 199));
    EXPECT_FALSE(info.hasSpaceProximity(300, 199)); EXPECT_FALSE(info.hasSpaceProximity(0, 200));
    ProximityInfo invalid(&jni.env, 300, 200, 3, 2, 10, 10,
            reinterpret_cast<jintArray>(&shortKeys), 2, nullptr, nullptr, nullptr,
            nullptr, nullptr, nullptr, nullptr, nullptr);
    EXPECT_FALSE(invalid.hasSpaceProximity(10, 10));
}
TEST(SuggestionSafetyTest, UnderflowingKeyDensitiesHaveFiniteNormalizedFallback) {
    FakeJni jni; JniArrayData proximity(std::vector<int>(MAX_PROXIMITY_CHARS_SIZE, 'a'));
    JniArrayData coords(std::vector<int>{0, 10}), widths(std::vector<int>{10, 10}), codes(std::vector<int>{'a', 'b'});
    ProximityInfo info(&jni.env, 100, 100, 1, 1, 10, 10, reinterpret_cast<jintArray>(&proximity), 2,
            reinterpret_cast<jintArray>(&coords), reinterpret_cast<jintArray>(&coords),
            reinterpret_cast<jintArray>(&widths), reinterpret_cast<jintArray>(&widths),
            reinterpret_cast<jintArray>(&codes), nullptr, nullptr, nullptr);
    std::vector<int> points{1000000}, length{0}; std::vector<float> speeds{1}, distances{1, 1};
    std::vector<std::unordered_map<int, float>> probabilities;
    ProximityInfoStateUtils::updateAlignPointProbabilities(1, 10, 2, 0, 1, &points, &points,
            &speeds, &length, &distances, &info, &probabilities);
    float sum = 0; for (const auto &entry : probabilities[0]) { EXPECT_TRUE(std::isfinite(entry.second)); sum += expf(-entry.second); }
    EXPECT_NEAR(1.0f, sum, 0.0001f);
}
TEST(SuggestionSafetyTest, ExactMatchesHandleDigraphsAndIntentionalOmissions) {
    for (int version : {402, 403}) {
        DictionaryHeaderStructurePolicy::AttributeMap attrs;
        HeaderReadWriteUtils::setBoolAttribute(&attrs, "REQUIRES_GERMAN_UMLAUT_PROCESSING", true);
        auto policy = DictionaryStructureWithBufferPolicyFactory::newPolicyForOnMemoryDict(version, {'e','n'}, &attrs);
        ASSERT_TRUE(policy);
        const std::vector<int> umlaut{0xE4, 'b'}, apostrophe{'c', '\'', 'd'};
        UnigramProperty property(false, false, false, 200, HistoricalInfo(), {});
        ASSERT_TRUE(policy->addUnigramEntry(CodePointArrayView(umlaut.data(), umlaut.size()), &property));
        ASSERT_TRUE(policy->addUnigramEntry(CodePointArrayView(apostrophe.data(), apostrophe.size()), &property));
        const int full[] = {'a', 'e', 'b'}, missing[] = {'a','b'}, wrong[] = {'a','x','b'}, omitted[] = {'c','d'};
        EXPECT_NE(NOT_A_PROBABILITY, DictionaryUtils::getMaxProbabilityOfExactMatches(policy.get(), CodePointArrayView(full, 3)));
        EXPECT_NE(NOT_A_PROBABILITY, DictionaryUtils::getMaxProbabilityOfExactMatches(policy.get(), CodePointArrayView(missing, 2))); // Accent folding.
        EXPECT_EQ(NOT_A_PROBABILITY, DictionaryUtils::getMaxProbabilityOfExactMatches(policy.get(), CodePointArrayView(wrong, 3)));
        EXPECT_NE(NOT_A_PROBABILITY, DictionaryUtils::getMaxProbabilityOfExactMatches(policy.get(), CodePointArrayView(omitted, 2)));
        FakeJni jni; Dictionary dict(&jni.env, std::move(policy));
        DicTraverseSession session(nullptr, nullptr, false); session.init(&dict, nullptr, nullptr);
        EXPECT_TRUE(session.getPrevWordIds().empty());
    }
}
TEST(SuggestionSafetyTest, ForgettingTablesCoverEveryVisibleLevelWithoutInvalidWeights) {
    TimeKeeper::setCurrentTime();
    for (int table = 0; table < 4; ++table) {
        DictionaryHeaderStructurePolicy::AttributeMap attrs;
        HeaderReadWriteUtils::setIntAttribute(&attrs, "FORGETTING_CURVE_PROBABILITY_VALUES_TABLE_ID", table);
        HeaderPolicy header(FormatUtils::VERSION_403, {'e','n'}, &attrs);
        for (int level = 2; level <= 15; ++level) {
            HistoricalInfo history(TimeKeeper::peekCurrentTime(), level, 0);
            int probability = ForgettingCurveUtils::decodeProbability(&history, &header);
            EXPECT_GE(probability, 1); EXPECT_LE(probability, MAX_PROBABILITY);
        }
    }
}
TEST(SuggestionSafetyTest, CyclicSerializedTrieBitmapHasBoundedDepth) {
    std::vector<uint8_t> bytes(142, 0); // Root at 128: bitmap bit zero, child index one.
    bytes[131] = 1; bytes[134] = 1;
    bytes[138] = 1; bytes[141] = 1; // Entry one points to itself.
    TrieMap map(ReadWriteByteArrayView(bytes.data(), bytes.size()));
    EXPECT_FALSE(map.getRoot(0).mIsValid); EXPECT_FALSE(map.putRoot(0, 123)); EXPECT_FALSE(map.remove(0, map.getRootBitmapEntryIndex()));
}
TEST(SuggestionSafetyTest, NodePropertiesLimitContextAndDepthBeforeCopying) {
    int ids[100]; std::fill(ids, ids + 100, 42);
    DicNodeProperties properties;
    properties.init(0, 'a', 1, UINT16_MAX, UINT16_MAX, WordIdArrayView(ids, 100));
    EXPECT_EQ(MAX_PREV_WORD_COUNT_FOR_N_GRAM, properties.getPrevWordIds().size());
    EXPECT_EQ(MAX_WORD_LENGTH, properties.getDepth());
    DicNodeProperties passing; passing.init(&properties, 'b');
    EXPECT_EQ(MAX_WORD_LENGTH, passing.getDepth());
}
TEST(SuggestionSafetyTest, ChildExpansionStopsAtSharedWorkCapacity) {
    DicNode root; root.initAsRoot(0, WordIdArrayView());
    DicNodeVector children(0, 2); int point = 'a';
    for (int i = 0; i < 100; ++i) {
        children.pushLeavingChild(&root, NOT_A_DICT_POS, i, CodePointArrayView(&point, 1));
    }
    EXPECT_TRUE(children.isFull()); EXPECT_EQ(2, children.getSizeAndLock());
    children.clear(); EXPECT_FALSE(children.isFull());
}
} // namespace
} // namespace latinime
