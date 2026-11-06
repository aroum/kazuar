// SPDX-License-Identifier: GPL-3.0-only
#include <gtest/gtest.h>
#include <map>
#include <string>
#include <unistd.h>
#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"
#include "dictionary/property/unigram_property.h"
#include "dictionary/property/ngram_property.h"
#include "dictionary/property/word_property.h"
#include "dictionary/utils/file_utils.h"
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

class TemporaryDictionary {
 public:
    TemporaryDictionary() {
        char pattern[] = "/tmp/kazuar-gc-XXXXXX";
        char *created = mkdtemp(pattern);
        if (created) { root = created; path = root + "/dictionary"; }
    }
    ~TemporaryDictionary() {
        if (!root.empty()) {
            FileUtils::removeDirAndFiles(path.c_str());
            FileUtils::removeDirAndFiles((path + ".tmp").c_str());
            rmdir(root.c_str());
        }
    }
    std::string root, path;
};

TEST(DynamicGcTest, SplitMovedAndRemovedWordsSurviveGcAndReopenInBothFormats) {
    for (int version : {402, 403}) {
        SCOPED_TRACE(version);
        TemporaryDictionary temporary;
        ASSERT_FALSE(temporary.path.empty());
        DictionaryHeaderStructurePolicy::AttributeMap attributes;
        auto policy = DictionaryStructureWithBufferPolicyFactory::newPolicyForOnMemoryDict(
                version, {'e', 'n'}, &attributes);
        ASSERT_TRUE(policy);
        const UnigramProperty property(false, false, false, 200, HistoricalInfo(),
                {UnigramProperty::ShortcutProperty({'X'}, 10)});
        const std::vector<std::string> inserted = {"abcd", "abef", "ab", "xyz", "xy", "mnop", "mn", "m"};
        const std::vector<std::string> removed = {"abef", "xyz", "mnop"};
        for (const auto &word : inserted) {
            const std::vector<int> points(word.begin(), word.end());
            ASSERT_TRUE(policy->addUnigramEntry(CodePointArrayView(points.data(), points.size()), &property));
        }
        for (const auto &word : removed) {
            const std::vector<int> points(word.begin(), word.end());
            ASSERT_TRUE(policy->removeUnigramEntry(CodePointArrayView(points.data(), points.size())));
        }
        const int contextWord[] = {'a', 'b'};
        const NgramProperty bigram(NgramContext(contextWord, 2, false), {'a', 'b', 'c', 'd'},
                180, HistoricalInfo());
        ASSERT_TRUE(policy->addNgramEntry(&bigram));
        ASSERT_TRUE(policy->flushWithGC(temporary.path.c_str()));
        ASSERT_FALSE(policy->isCorrupted());
        policy.reset();
        auto reopened = DictionaryStructureWithBufferPolicyFactory::newPolicyForExistingDictFile(
                temporary.path.c_str(), 0, 0, true);
        ASSERT_TRUE(reopened);
        for (const auto &word : inserted) {
            const std::vector<int> points(word.begin(), word.end());
            const int id = reopened->getWordId(CodePointArrayView(points.data(), points.size()), false);
            if (std::find(removed.begin(), removed.end(), word) != removed.end()) {
                EXPECT_EQ(NOT_A_WORD_ID, id) << word;
            } else {
                ASSERT_NE(NOT_A_WORD_ID, id) << word;
                int restored[MAX_WORD_LENGTH];
                const int count = reopened->getCodePointsAndReturnCodePointCount(id, MAX_WORD_LENGTH, restored);
                ASSERT_EQ(static_cast<int>(points.size()), count) << word;
                EXPECT_EQ(points, std::vector<int>(restored, restored + count));
                EXPECT_EQ(200, reopened->getProbabilityOfWord(WordIdArrayView(), id)) << word;
                const auto wordProperty = reopened->getWordProperty(CodePointArrayView(points.data(), points.size()));
                const auto &shortcuts = wordProperty.getUnigramProperty().getShortcuts();
                ASSERT_EQ(1u, shortcuts.size()) << word;
                EXPECT_EQ(std::vector<int>{'X'}, *shortcuts.front().getTargetCodePoints());
            }
        }
        const int previousId = reopened->getWordId(CodePointArrayView(contextWord, 2), false);
        const int targetPoints[] = {'a', 'b', 'c', 'd'};
        const int targetId = reopened->getWordId(CodePointArrayView(targetPoints, 4), false);
        EXPECT_NE(NOT_A_PROBABILITY, reopened->getProbabilityOfWord(WordIdArrayView(&previousId, 1), targetId));
        EXPECT_FALSE(reopened->isCorrupted());
    }
}
} // namespace
} // namespace latinime
