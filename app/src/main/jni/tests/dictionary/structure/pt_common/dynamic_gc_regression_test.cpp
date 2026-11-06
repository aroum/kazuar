// SPDX-License-Identifier: GPL-3.0-only
#include <gtest/gtest.h>
#include <map>
#include <type_traits>
#include <vector>
#include "dictionary/structure/pt_common/dynamic_pt_gc_event_listeners.h"
#include "dictionary/structure/pt_common/pt_node_array_reader.h"
#include "dictionary/structure/pt_common/pt_node_writer.h"
#include "dictionary/structure/v4/ver4_patricia_trie_node_writer.h"
#include "dictionary/structure/v4/content/dynamic_language_model_probability_utils.h"
#include "dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {
namespace {
static_assert(std::is_same<decltype(PtNodeParams().representsBeginningOfSentence()), bool>::value,
        "Sentence predicate must return bool");
static_assert(std::is_same<decltype(DynamicLanguageModelProbabilityUtils::shouldRemoveEntryDuringGC(
        HistoricalInfo())), bool>::value, "GC predicate must return bool");
static_assert(std::is_same<decltype(DynamicLanguageModelProbabilityUtils::backoff(1, NgramType::Unigram)),
        int>::value, "Encoded probabilities are integers");

class ChainReader : public PtNodeReader, public PtNodeArrayReader {
 public:
    ChainReader(int depth, bool cycle) : mDepth(depth), mCycle(cycle) {}
    const PtNodeParams fetchPtNodeParamsInBufferFromPtNodePos(int pos) const override {
        const int level = pos / 10;
        const int code = 'a';
        const int child = mCycle ? 0 : (level + 1 < mDepth ? (level + 1) * 10 : NOT_A_DICT_POS);
        return PtNodeParams(pos, 0xD0, NOT_A_DICT_POS, 1, &code, NOT_A_DICT_POS,
                level, 50, NOT_A_DICT_POS, child, pos + 1);
    }
    bool readPtNodeArrayInfoAndReturnIfValid(int pos, int *count, int *first) const override {
        *count = 1; *first = pos + 1; return true;
    }
    bool readForwardLinkAndReturnIfValid(int pos, int *next) const override {
        *next = NOT_A_DICT_POS; return true;
    }
 private:
    int mDepth;
    bool mCycle;
};
class CountingListener : public DynamicPtReadingHelper::TraversingEventListener {
 public:
    int visits = 0, descents = 0;
    bool onAscend() override { return true; }
    bool onDescend(int pos) override { ++descents; return true; }
    bool onReadingPtNodeArrayTail() override { return true; }
    bool onVisitingPtNode(const PtNodeParams *node) override { ++visits; return true; }
};

TEST(DynamicTraversalTest, ExactWordLengthWorksInBothTraversalOrders) {
    ChainReader reader(MAX_WORD_LENGTH, false);
    DynamicPtReadingHelper helper(&reader, &reader);
    for (bool preorder : {false, true}) {
        CountingListener listener;
        helper.initWithPtNodeArrayPos(0);
        EXPECT_TRUE(preorder ? helper.traverseAllPtNodesInPtNodeArrayLevelPreorderDepthFirstManner(&listener)
                : helper.traverseAllPtNodesInPostorderDepthFirstManner(&listener));
        EXPECT_FALSE(helper.isError());
        EXPECT_EQ(MAX_WORD_LENGTH, listener.visits);
    }
}

TEST(DynamicTraversalTest, TooDeepAndCyclicStructuresStopWithStickyError) {
    for (bool cycle : {false, true}) {
        ChainReader reader(MAX_WORD_LENGTH + 10, cycle);
        DynamicPtReadingHelper helper(&reader, &reader);
        for (bool preorder : {false, true}) {
            CountingListener listener;
            helper.initWithPtNodeArrayPos(0);
            EXPECT_FALSE(preorder ? helper.traverseAllPtNodesInPtNodeArrayLevelPreorderDepthFirstManner(&listener)
                    : helper.traverseAllPtNodesInPostorderDepthFirstManner(&listener));
            EXPECT_TRUE(helper.isError());
            EXPECT_LE(listener.descents, MAX_WORD_LENGTH + 1);
        }
    }
}

class RecordingWriter : public PtNodeWriter {
 public:
    std::vector<int> deleted;
    std::map<int, int> children;
    bool markPtNodeAsDeleted(const PtNodeParams *p) override { deleted.push_back(p->getHeadPos()); return true; }
    bool markPtNodeAsMoved(const PtNodeParams *, int, int) override { return true; }
    bool markPtNodeAsWillBecomeNonTerminal(const PtNodeParams *) override { return true; }
    bool updatePtNodeUnigramProperty(const PtNodeParams *, const UnigramProperty *) override { return true; }
    bool updatePtNodeProbabilityAndGetNeedsToKeepPtNodeAfterGC(const PtNodeParams *, bool *keep) override { *keep = true; return true; }
    bool updateChildrenPosition(const PtNodeParams *p, int pos) override { children[p->getHeadPos()] = pos; return true; }
    bool writePtNodeAndAdvancePosition(const PtNodeParams *, int *) override { return true; }
    bool writeNewTerminalPtNodeAndAdvancePosition(const PtNodeParams *, const UnigramProperty *, int *) override { return true; }
    bool addNgramEntry(WordIdArrayView, int, const NgramProperty *, bool *) override { return true; }
    bool removeNgramEntry(WordIdArrayView, int) override { return true; }
    bool updateAllBigramEntriesAndDeleteUselessEntries(const PtNodeParams *, int *) override { return true; }
    bool updateAllPositionFields(const PtNodeParams *, const DictPositionRelocationMap *, int *) override { return true; }
    bool addShortcutTarget(const PtNodeParams *, const int *, int, int) override { return true; }
};

const PtNodeParams node(int pos, bool terminal, int child) {
    const int code = 'a';
    return PtNodeParams(pos, terminal ? 0xD0 : 0xC0, NOT_A_DICT_POS, 1, &code,
            NOT_A_DICT_POS, terminal ? pos : NOT_A_DICT_POS, 50, NOT_A_DICT_POS, child, pos + 1);
}

TEST(DynamicGcTest, PreviousSiblingsLiveChildrenDoNotKeepUselessLeaf) {
    RecordingWriter writer;
    DynamicPtGcEventListeners::TraversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted listener(&writer);
    ASSERT_TRUE(listener.onDescend(0));
    ASSERT_TRUE(listener.onDescend(10));
    const auto child = node(11, true, NOT_A_DICT_POS);
    ASSERT_TRUE(listener.onVisitingPtNode(&child));
    ASSERT_TRUE(listener.onAscend());
    const auto parent = node(1, false, 10);
    ASSERT_TRUE(listener.onVisitingPtNode(&parent));
    const auto uselessSibling = node(2, false, NOT_A_DICT_POS);
    ASSERT_TRUE(listener.onVisitingPtNode(&uselessSibling));
    ASSERT_EQ(1u, writer.deleted.size());
    EXPECT_EQ(2, writer.deleted.front());
    EXPECT_TRUE(writer.children.empty());
}

TEST(DynamicGcTest, RemovedChildArrayIsDetachedFromNonTerminalParent) {
    RecordingWriter writer;
    DynamicPtGcEventListeners::TraversePolicyToUpdateUnigramProbabilityAndMarkUselessPtNodesAsDeleted listener(&writer);
    ASSERT_TRUE(listener.onDescend(0));
    ASSERT_TRUE(listener.onDescend(10));
    ASSERT_TRUE(listener.onAscend());
    const auto parent = node(1, false, 10);
    ASSERT_TRUE(listener.onVisitingPtNode(&parent));
    ASSERT_EQ(1u, writer.children.size());
    EXPECT_EQ(NOT_A_DICT_POS, writer.children.at(1));
    ASSERT_EQ(1u, writer.deleted.size());
    EXPECT_EQ(1, writer.deleted.front());
}

TEST(DynamicGcTest, UnmappedOrDeletedLinksNeverFallbackToOldPositions) {
    PtNodeWriter::DictPositionRelocationMap map;
    int parent = 123, children = 456;
    EXPECT_FALSE(map.relocatePositions(7, 12, &parent, &children));
    EXPECT_EQ(123, parent); EXPECT_EQ(456, children);
    map.mPtNodePositionRelocationMap[7] = NOT_A_DICT_POS;
    map.mPtNodeArrayPositionRelocationMap[12] = 2;
    EXPECT_FALSE(map.relocatePositions(7, 12, &parent, &children));
    map.mPtNodePositionRelocationMap[7] = 1;
    ASSERT_TRUE(map.relocatePositions(7, 12, &parent, &children));
    EXPECT_EQ(1, parent); EXPECT_EQ(2, children);
}

TEST(DynamicGcTest, WriterChecksAllRelocationsBeforeModifyingBuffer) {
    BufferWithExtendableBuffer buffer(1024);
    for (int i = 0; i < 16; ++i) ASSERT_TRUE(buffer.writeUint(0, 1, i));
    Ver4PatriciaTrieNodeWriter writer(&buffer, nullptr, nullptr, nullptr, nullptr);
    const int code = 'a';
    PtNodeParams params(4, 0xC0, 7, 1, &code, NOT_A_DICT_POS,
            NOT_A_DICT_POS, NOT_A_PROBABILITY, 9, 12, 12);
    PtNodeWriter::DictPositionRelocationMap map;
    map.mPtNodePositionRelocationMap[7] = 1;
    EXPECT_FALSE(writer.updateAllPositionFields(&params, &map, nullptr));
    EXPECT_EQ(0u, buffer.readUint(3, 5));
    map.mPtNodeArrayPositionRelocationMap[12] = 20;
    ASSERT_TRUE(writer.updateAllPositionFields(&params, &map, nullptr));
    int pos = 5;
    int offset = DynamicPtReadingUtils::getParentPtNodePosOffsetAndAdvancePosition(buffer.getBuffer(true), &pos);
    EXPECT_EQ(1, DynamicPtReadingUtils::getParentPtNodePos(offset, 4));
    pos = 9;
    EXPECT_EQ(20, DynamicPtReadingUtils::readChildrenPositionAndAdvancePosition(buffer.getBuffer(true), &pos));
}
} // namespace
} // namespace latinime
