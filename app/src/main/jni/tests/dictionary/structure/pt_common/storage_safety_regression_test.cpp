// SPDX-License-Identifier: GPL-3.0-only
#include <gtest/gtest.h>
#include <array>
#include <climits>
#include <vector>
#include "dictionary/header/header_policy.h"
#include "dictionary/structure/v4/content/language_model_dict_content.h"
#include "dictionary/structure/v4/content/probability_entry.h"
#include "dictionary/structure/v4/content/terminal_position_lookup_table.h"
#include "dictionary/structure/backward/v402/content/terminal_position_lookup_table.h"
#include "dictionary/structure/backward/v402/content/probability_dict_content.h"
#include "dictionary/structure/backward/v402/content/probability_entry.h"
#include "dictionary/structure/v4/content/dynamic_language_model_probability_utils.h"
#include "dictionary/utils/binary_dictionary_bigrams_iterator.h"
#include "dictionary/utils/binary_dictionary_shortcut_iterator.h"
#include "dictionary/utils/bloom_filter.h"
#include "dictionary/utils/buffer_with_extendable_buffer.h"
#include "dictionary/utils/trie_map.h"

namespace latinime {
namespace {
TEST(StorageSafetyTest, SegmentedIntegersReadAcrossBoundaryAndRejectInvalidRanges) {
    uint8_t original[] = {1, 2};
    BufferWithExtendableBuffer buffer(ReadWriteByteArrayView(original, 2), 16);
    uint32_t result = 123;
    EXPECT_FALSE(buffer.tryReadUint(3, 0, &result)); EXPECT_EQ(123u, result);
    EXPECT_TRUE(buffer.writeUint(0x0304, 2, 2));
    EXPECT_TRUE(buffer.tryReadUint(4, 0, &result)); EXPECT_EQ(0x01020304u, result);
    for (int pos : {-1, NOT_A_DICT_POS, 4, INT_MAX}) {
        int cursor = pos;
        EXPECT_EQ(0u, buffer.readUintAndAdvancePosition(1, &cursor));
        EXPECT_EQ(NOT_A_DICT_POS, cursor);
    }
    EXPECT_FALSE(buffer.tryReadUint(0, 0, &result));
    EXPECT_FALSE(buffer.tryReadUint(5, 0, &result));
}
TEST(StorageSafetyTest, SegmentedStringsRequireTerminatorAndRespectCapacity) {
    uint8_t original[] = {0x01};
    BufferWithExtendableBuffer buffer(ReadWriteByteArrayView(original, 1), 16);
    ASSERT_TRUE(buffer.writeUint(0xF6001F, 3, 1));
    int word[2] = {0, 999}, count = 0, pos = 0;
    buffer.readCodePointsAndAdvancePosition(1, word, &count, &pos);
    EXPECT_EQ(1, count); EXPECT_EQ(0x1F600, word[0]); EXPECT_EQ(999, word[1]); EXPECT_EQ(4, pos);
    pos = 0;
    buffer.readCodePointsAndAdvancePosition(0, nullptr, &count, &pos);
    EXPECT_EQ(0, count); EXPECT_EQ(NOT_A_DICT_POS, pos);
    BufferWithExtendableBuffer incomplete(16);
    ASSERT_TRUE(incomplete.writeUint('a', 1, 0));
    pos = 0; incomplete.readCodePointsAndAdvancePosition(1, word, &count, &pos);
    EXPECT_EQ(0, count); EXPECT_EQ(NOT_A_DICT_POS, pos);
}
TEST(StorageSafetyTest, WritesCannotOverflowOrPartiallyExtendOnCapacityFailure) {
    BufferWithExtendableBuffer buffer(16);
    EXPECT_FALSE(buffer.writeUint(1, 4, INT_MAX - 1));
    EXPECT_FALSE(buffer.extend(INT_MAX)); EXPECT_EQ(0, buffer.getTailPosition());
    int cursor = 0;
    EXPECT_FALSE(buffer.writeCodePointsAndAdvancePosition(nullptr, 1, true, &cursor));
    BufferWithExtendableBuffer invalidLimit(-1);
    EXPECT_FALSE(invalidLimit.extend(1));
}
TEST(StorageSafetyTest, BlockCopyHandlesDifferentSegmentBoundariesAndSelfCopy) {
    uint8_t original[] = {1, 2};
    BufferWithExtendableBuffer source(ReadWriteByteArrayView(original, 2), 16);
    ASSERT_TRUE(source.writeUint(0x0304, 2, 2));
    uint8_t targetOriginal[] = {0};
    BufferWithExtendableBuffer destination(ReadWriteByteArrayView(targetOriginal, 1), 16);
    ASSERT_TRUE(destination.copy(&source));
    EXPECT_EQ(0x01020304u, destination.readUint(4, 0)); EXPECT_EQ(1, targetOriginal[0]);
    EXPECT_TRUE(destination.copy(&destination)); EXPECT_EQ(4, destination.getTailPosition());
    BufferWithExtendableBuffer tooSmall(1);
    EXPECT_FALSE(tooSmall.copy(&source)); EXPECT_EQ(0, tooSmall.getTailPosition());
}
template<class Table> void checkTerminalLimits() {
    Table table;
    EXPECT_FALSE(table.setTerminalPtNodePosition(INT_MAX, 100));
    EXPECT_FALSE(table.setTerminalPtNodePosition(1000000, 100));
    EXPECT_EQ(0, table.getNextTerminalId());
    EXPECT_TRUE(table.setTerminalPtNodePosition(10, 100));
    EXPECT_FALSE(table.setTerminalPtNodePosition(11, INT_MAX));
    EXPECT_EQ(11, table.getNextTerminalId());
    EXPECT_EQ(100, table.getTerminalPtNodePosition(10));
}
TEST(StorageSafetyTest, TerminalExpansionIsBoundedInBothFormats) {
    checkTerminalLimits<TerminalPositionLookupTable>();
    checkTerminalLimits<backward::v402::TerminalPositionLookupTable>();
}
TEST(StorageSafetyTest, NegativeBloomPositionsNeverContaminateRealBuckets) {
    BloomFilter filter;
    filter.setInFilter(NOT_A_DICT_POS); filter.setInFilter(-1);
    EXPECT_FALSE(filter.isInFilter(-1)); EXPECT_FALSE(filter.isInFilter(NOT_A_DICT_POS));
    for (int i = 0; i < 1021; ++i) EXPECT_FALSE(filter.isInFilter(i));
    filter.setInFilter(0); EXPECT_TRUE(filter.isInFilter(0));
}
TEST(StorageSafetyTest, EmptyAndMovedFromIteratorsAreSafeToAdvance) {
    BinaryDictionaryBigramsIterator empty;
    empty.next(); EXPECT_FALSE(empty.hasNext());
    BinaryDictionaryBigramsIterator invalid(nullptr, 1);
    invalid.next(); EXPECT_FALSE(invalid.hasNext());
    BinaryDictionaryBigramsIterator moved(std::move(invalid));
    invalid.next(); moved.next();
    BinaryDictionaryShortcutIterator shortcut(nullptr, 0);
    BinaryDictionaryShortcutIterator movedShortcut(std::move(shortcut));
    int length = 123; bool whitelist = true;
    movedShortcut.nextShortcutTarget(1, nullptr, &length, &whitelist);
    EXPECT_EQ(0, length); EXPECT_FALSE(whitelist); EXPECT_FALSE(shortcut.hasNextShortcutTarget());
    shortcut.nextShortcutTarget(1, nullptr, &length, &whitelist);
}
TEST(LanguageModelSafetyTest, InputUpdatesAndDirectReplacementCountOnlyDeltas) {
    TimeKeeper::startTestModeWithForceCurrentTime(1000);
    LanguageModelDictContent content(true);
    MutableEntryCounters counts;
    for (int i = 0; i < 5; ++i)
        ASSERT_TRUE(content.updateAllEntriesOnInputWord(WordIdArrayView(), 7, true,
                HistoricalInfo(1000, 0, 1), nullptr, &counts));
    EXPECT_EQ(5, content.getTotalCount());
    const HistoricalInfo h(1000, 0, 3); const ProbabilityEntry replacement(0, &h);
    ASSERT_TRUE(content.setProbabilityEntry(7, &replacement)); EXPECT_EQ(3, content.getTotalCount());
    ASSERT_TRUE(content.setProbabilityEntry(8, &replacement)); EXPECT_EQ(6, content.getTotalCount());
    ASSERT_TRUE(content.removeProbabilityEntry(7)); EXPECT_EQ(3, content.getTotalCount());
    const int context[] = {8};
    ASSERT_TRUE(content.setNgramProbabilityEntry(WordIdArrayView(context, 1), 8, &replacement));
    EXPECT_EQ(3, content.getTotalCount());
    TerminalPositionLookupTable::TerminalIdMap map{{8, 0}};
    LanguageModelDictContent compacted(true);
    ASSERT_TRUE(compacted.runGC(&map, &content)); EXPECT_EQ(3, compacted.getTotalCount());
    TimeKeeper::stopTestMode();
}
TEST(LanguageModelSafetyTest, TruncatingUnigramsHandlesEmptyContextAndRecomputesTotal) {
    LanguageModelDictContent content(true);
    for (int id = 1; id <= 3; ++id) {
        const HistoricalInfo history(id * 10, 0, id);
        const ProbabilityEntry entry(0, &history);
        ASSERT_TRUE(content.setProbabilityEntry(id, &entry));
    }
    MutableEntryCounters result;
    ASSERT_TRUE(content.truncateEntries(EntryCounts(std::array<int,4>{{3,0,0,0}}), EntryCounts(std::array<int,4>{{1,0,0,0}}), nullptr, &result));
    EXPECT_EQ(3, content.getTotalCount());
    EXPECT_FALSE(content.getProbabilityEntry(1).isValid());
    EXPECT_TRUE(content.getProbabilityEntry(3).isValid());
}
TEST(LanguageModelSafetyTest, HalvingRecountsRoundedIndividualCounters) {
    TimeKeeper::startTestModeWithForceCurrentTime(1000);
    LanguageModelDictContent content(true);
    const HistoricalInfo a(1000, 0, 65501), b(1000, 0, 3);
    const ProbabilityEntry first(0, &a), second(0, &b);
    ASSERT_TRUE(content.setProbabilityEntry(1, &first)); ASSERT_TRUE(content.setProbabilityEntry(2, &second));
    MutableEntryCounters result;
    ASSERT_TRUE(content.updateAllProbabilityEntriesForGC(nullptr, &result));
    EXPECT_EQ(32751, content.getTotalCount());
    TimeKeeper::stopTestMode();
}
TEST(LanguageModelSafetyTest, UnsupportedContextsAreRejectedBeforeFixedArrayAccess) {
    LanguageModelDictContent content(false);
    const int context[] = {1, 2, 3, 4};
    const ProbabilityEntry entry(0, 100);
    EXPECT_FALSE(content.setNgramProbabilityEntry(WordIdArrayView(context, 4), 5, &entry));
    EXPECT_EQ(NOT_A_PROBABILITY, content.getWordAttributes(WordIdArrayView(context, 4), 5, true, nullptr).getProbability());
}
TEST(LanguageModelSafetyTest, TooDeepSerializedNgramsStopGcAndExport) {
    TrieMap trie;
    const HistoricalInfo history(100, 0, 1);
    const ProbabilityEntry entry(0, &history);
    int bitmap = trie.getRootBitmapEntryIndex();
    TerminalPositionLookupTable::TerminalIdMap map;
    for (int depth = 0; depth < 5; ++depth) {
        const int id = 100 + depth; map[id] = id;
        ASSERT_TRUE(trie.put(id, entry.encode(true), bitmap));
        if (depth < 4) bitmap = trie.getNextLevelBitmapEntryIndex(id, bitmap);
    }
    std::unique_ptr<FILE, int(*)(FILE*)> file(tmpfile(), fclose);
    ASSERT_TRUE(file); ASSERT_TRUE(trie.save(file.get()));
    ASSERT_EQ(0, fseek(file.get(), 0, SEEK_END));
    const long size = ftell(file.get()); ASSERT_GT(size, 4);
    std::vector<uint8_t> bytes(size); rewind(file.get());
    ASSERT_EQ(bytes.size(), fread(bytes.data(), 1, bytes.size(), file.get()));
    uint8_t counters[8] = {};
    ReadWriteByteArrayView buffers[] = {ReadWriteByteArrayView(bytes.data() + 4, bytes.size() - 4),
            ReadWriteByteArrayView(counters, sizeof(counters))};
    LanguageModelDictContent source(buffers, true), compacted(true);
    EXPECT_FALSE(compacted.runGC(&map, &source));
    EXPECT_TRUE(source.exportAllNgramEntriesRelatedToWord(nullptr, 100).empty());
}
TEST(LanguageModelSafetyTest, FutureTimestampsAreRemovedAndCannotOverflowTimeSubtraction) {
    TimeKeeper::startTestModeWithForceCurrentTime(100);
    const HistoricalInfo future(200, 0, 1);
    EXPECT_EQ(NOT_A_PROBABILITY, DynamicLanguageModelProbabilityUtils::getDecayedProbability(100, future));
    EXPECT_TRUE(DynamicLanguageModelProbabilityUtils::shouldRemoveEntryDuringGC(future));
    LanguageModelDictContent content(true); const ProbabilityEntry entry(0, &future);
    ASSERT_TRUE(content.setProbabilityEntry(1, &entry));
    MutableEntryCounters counts; ASSERT_TRUE(content.updateAllProbabilityEntriesForGC(nullptr, &counts));
    EXPECT_EQ(0, content.getTotalCount());
    TimeKeeper::startTestModeWithForceCurrentTime(INT_MIN);
    EXPECT_TRUE(DynamicLanguageModelProbabilityUtils::shouldRemoveEntryDuringGC(HistoricalInfo(INT_MAX, 0, 1)));
    TimeKeeper::stopTestMode();
}
TEST(LanguageModelSafetyTest, V403ZeroWidthLevelCannotOverwriteTimestampAndCountsSaturate) {
    const HistoricalInfo h(0x12345670, 255, 0xABCD);
    const ProbabilityEntry entry(0xF0, &h);
    const auto decoded = ProbabilityEntry::decode(entry.encode(true), true);
    EXPECT_EQ(0x12345670, decoded.getHistoricalInfo()->getTimestamp());
    EXPECT_EQ(0, decoded.getHistoricalInfo()->getLevel()); EXPECT_EQ(0xABCD, decoded.getHistoricalInfo()->getCount());
    const HistoricalInfo large(100, 0, INT_MAX); const ProbabilityEntry overflow(0, &large);
    EXPECT_EQ(65535, ProbabilityEntry::decode(overflow.encode(true), true).getHistoricalInfo()->getCount());
    backward::v402::ProbabilityDictContent old(true);
    const backward::v402::ProbabilityEntry oldEntry(7, 123, &large);
    ASSERT_TRUE(old.setProbabilityEntry(0, &oldEntry));
    const auto restored = old.getProbabilityEntry(0);
    EXPECT_EQ(7, restored.getFlags()); EXPECT_EQ(123, restored.getProbability());
    EXPECT_EQ(100, restored.getHistoricalInfo()->getTimestamp()); EXPECT_EQ(255, restored.getHistoricalInfo()->getCount());
}
TEST(ByteArraySafetyTest, SourceAndCodePointTableLengthsAreEnforced) {
    const uint8_t bytes[] = {0xFF}; const int table[] = {'a', 'b'};
    int pos = 0;
    EXPECT_EQ(NOT_A_CODE_POINT, ByteArrayUtils::readCodePointAndAdvancePosition(
            ReadOnlyByteArrayView(bytes, 1), CodePointArrayView(table, 2), &pos));
    EXPECT_EQ(NOT_A_DICT_POS, pos);
    const uint8_t truncated[] = {1, 2}; pos = 0;
    EXPECT_EQ(NOT_A_CODE_POINT, ByteArrayUtils::readCodePointAndAdvancePosition(
            ReadOnlyByteArrayView(truncated, 2), CodePointArrayView(), &pos));
    EXPECT_EQ(NOT_A_DICT_POS, pos);
    const uint8_t unterminated[] = {'a'}; int out[2] = {0, 999}; pos = 0;
    EXPECT_EQ(-1, ByteArrayUtils::readStringAndAdvancePosition(ReadOnlyByteArrayView(unterminated, 1),
            1, CodePointArrayView(), out, &pos)); EXPECT_EQ(999, out[1]);
}
TEST(ByteArraySafetyTest, CopiedHeaderOwnsItsCodePointTable) {
    std::unique_ptr<HeaderPolicy> copied;
    {
        DictionaryHeaderStructurePolicy::AttributeMap attributes;
        HeaderReadWriteUtils::setCodePointVectorAttribute(&attributes, "codePointTable", {'q', 'w'});
        HeaderPolicy header(FormatUtils::VERSION_202, {'e','n'}, &attributes);
        copied.reset(new HeaderPolicy(&header));
    }
    ASSERT_EQ(2u, copied->getCodePointTable().size());
    const uint8_t bytes[] = {0x20}; int pos = 0;
    EXPECT_EQ('q', ByteArrayUtils::readCodePointAndAdvancePosition(ReadOnlyByteArrayView(bytes, 1), copied->getCodePointTable(), &pos));
}
} // namespace
} // namespace latinime
