// SPDX-License-Identifier: GPL-3.0-only
#include <gtest/gtest.h>
#include <algorithm>
#include <vector>
#include <unistd.h>
#include "dictionary/structure/pt_common/patricia_trie_reading_utils.h"
#include "dictionary/structure/pt_common/pt_node_params.h"
#include "dictionary/structure/pt_common/shortcut/shortcut_list_reading_utils.h"
#include "dictionary/structure/v2/bigram/bigram_list_policy.h"
#include "dictionary/structure/v2/shortcut/shortcut_list_policy.h"
#include "dictionary/structure/v2/ver2_patricia_trie_node_reader.h"
#include "dictionary/structure/v2/ver2_pt_node_array_reader.h"
#include "dictionary/structure/v2/patricia_trie_policy.h"
#include "dictionary/structure/v4/content/shortcut_dict_content.h"
#include "dictionary/structure/backward/v402/content/shortcut_dict_content.h"
#include "dictionary/structure/v4/ver4_patricia_trie_node_reader.h"
#include "dictionary/structure/v4/ver4_pt_node_array_reader.h"
#include "dictionary/utils/buffer_with_extendable_buffer.h"
#include "dictionary/utils/mmapped_buffer.h"

namespace latinime {
namespace {
using Reading = PatriciaTrieReadingUtils;

TEST(ShortcutBoundsTest, RejectsTruncatedAndInvalidSizesWithoutSkipping) {
    for (const std::vector<uint8_t> bytes : {std::vector<uint8_t>{}, {0}, {0, 0}, {0, 1}, {0, 5, 0, 'a'}}) {
        int pos = 0;
        EXPECT_FALSE(ShortcutListReadingUtils::skipShortcuts(
                ReadOnlyByteArrayView(bytes.data(), bytes.size()), &pos));
        EXPECT_EQ(0, pos);
    }
    const uint8_t bytes[] = {0, 5, 0, 'a', 0x1F};
    int pos = 0;
    EXPECT_TRUE(ShortcutListReadingUtils::skipShortcuts(ReadOnlyByteArrayView(bytes, 5), &pos));
    EXPECT_EQ(5, pos);
    pos = -1;
    EXPECT_FALSE(ShortcutListReadingUtils::skipShortcuts(ReadOnlyByteArrayView(bytes, 5), &pos));
}

TEST(ShortcutBoundsTest, UnterminatedOrTruncatedTargetStopsIteration) {
    for (const std::vector<uint8_t> bytes : {std::vector<uint8_t>{0x80}, {0x80, 'a'}, {0x80, 0x01, 0x02}}) {
        ShortcutListPolicy policy(ReadOnlyByteArrayView(bytes.data(), bytes.size()));
        int pos = 0, count = 123;
        int word[MAX_WORD_LENGTH];
        bool hasNext = true, whitelist = true;
        policy.getNextShortcut(MAX_WORD_LENGTH, word, &count, &whitelist, &hasNext, &pos);
        EXPECT_EQ(0, count);
        EXPECT_FALSE(hasNext);
        EXPECT_FALSE(whitelist);
        EXPECT_EQ(NOT_A_DICT_POS, pos);
    }
}

template<class Content> class RawShortcutContent : public Content {
 public:
    void append(const std::vector<uint8_t> &bytes) {
        int pos = 0;
        for (uint8_t byte : bytes) ASSERT_TRUE(this->getWritableContentBuffer()->writeUintAndAdvancePosition(byte, 1, &pos));
    }
};

template<class Content> void checkMalformedDynamicShortcut(const std::vector<uint8_t> &bytes) {
    RawShortcutContent<Content> content;
    content.append(bytes);
    int word[MAX_WORD_LENGTH], count = 123, probability = 123, pos = 0;
    bool hasNext = true;
    content.getShortcutEntryAndAdvancePosition(MAX_WORD_LENGTH, word, &count, &probability, &hasNext, &pos);
    EXPECT_EQ(0, count);
    EXPECT_FALSE(hasNext);
    EXPECT_EQ(0, pos);
    EXPECT_FALSE(content.copyShortcutList(0, bytes.size()));
    EXPECT_EQ(NOT_A_DICT_POS, content.findShortcutEntryAndGetPos(0, word, 1));
}

TEST(ShortcutBoundsTest, DynamicAndBackwardReadersRejectMalformedContinuations) {
    for (const std::vector<uint8_t> bytes : {std::vector<uint8_t>{0x80}, {0x80, 'a'}, {0x80, 1, 2}}) {
        checkMalformedDynamicShortcut<ShortcutDictContent>(bytes);
        checkMalformedDynamicShortcut<backward::v402::ShortcutDictContent>(bytes);
    }
}

TEST(ShortcutBoundsTest, ValidContinuationAtEndCannotLoopOrCopyAnEmptyEntry) {
    std::vector<uint8_t> bytes = {0x80, 'a', 0x1F};
    ReadWriteByteArrayView buffers[] = {ReadWriteByteArrayView(), ReadWriteByteArrayView(),
            ReadWriteByteArrayView(bytes.data(), bytes.size())};
    ShortcutDictContent content(buffers);
    EXPECT_FALSE(content.copyShortcutList(0, bytes.size()));
    const int missing[] = {'b'};
    EXPECT_EQ(NOT_A_DICT_POS, content.findShortcutEntryAndGetPos(0, missing, 1));
}

TEST(PtNodeParamsTest, InvalidLengthsNeverCopyOrBecomeValid) {
    const int points[MAX_WORD_LENGTH + 1] = {};
    for (int count : {-1, 0, MAX_WORD_LENGTH + 1, 256, 257}) {
        PtNodeParams v2(1, 0, count, points, 0, NOT_A_DICT_POS, NOT_A_DICT_POS, NOT_A_DICT_POS, 2);
        PtNodeParams v4(1, 0, 0, count, points, 0, 7, 0, 0, NOT_A_DICT_POS, 2);
        EXPECT_FALSE(v2.isValid()) << count;
        EXPECT_FALSE(v4.isValid()) << count;
        EXPECT_EQ(0, v2.getCodePointCount());
        EXPECT_EQ(0, v4.getCodePointCount());
    }
    PtNodeParams nullPoints(1, 0, 1, nullptr, 0, 0, 0, 0, 2);
    PtNodeParams empty(0, NOT_A_DICT_POS, CodePointArrayView(nullptr, 0), 0);
    EXPECT_FALSE(nullPoints.isValid());
    EXPECT_FALSE(empty.isValid());
}

TEST(PtNodeParamsTest, ExactCapacityCopiesAndFormatsHaveDistinctReferences) {
    int points[MAX_WORD_LENGTH];
    std::fill(points, points + MAX_WORD_LENGTH, 'a');
    PtNodeParams v2(1, 0x10, MAX_WORD_LENGTH, points, 50, NOT_A_DICT_POS, 7, 9, 2);
    ASSERT_TRUE(v2.isValid());
    EXPECT_EQ('a', v2.getCodePoints()[MAX_WORD_LENGTH - 1]);
    EXPECT_EQ(7, v2.getV2ShortcutPos());
    EXPECT_EQ(9, v2.getV2BigramsPos());
    PtNodeParams v4(1, 0x10, 0, 1, points, 4, 77, 50, 8, NOT_A_DICT_POS, 12);
    EXPECT_EQ(77, v4.getTerminalId());
    EXPECT_EQ(NOT_A_DICT_POS, v4.getV2ShortcutPos());
    EXPECT_EQ(NOT_A_DICT_POS, v4.getV2BigramsPos());
    PtNodeParams updated(&v4, 0x10, 0, CodePointArrayView(points, 1), 50);
    EXPECT_EQ(77, updated.getTerminalId());
    EXPECT_EQ(NOT_A_DICT_POS, updated.getV2ShortcutPos());
}

TEST(PatriciaBoundsTest, TruncatedArrayAndCodePointFieldsAreRejected) {
    const uint8_t array[] = {0x80};
    int pos = 0;
    EXPECT_EQ(-1, Reading::getPtNodeArraySizeAndAdvancePosition(ReadOnlyByteArrayView(array, 1), &pos));
    EXPECT_EQ(NOT_A_DICT_POS, pos);
    for (const std::vector<uint8_t> bytes : {std::vector<uint8_t>{}, {1}, {1, 2}}) {
        pos = 0;
        int out[2] = {123, 456};
        EXPECT_EQ(-1, Reading::getCharsAndAdvancePosition(
                ReadOnlyByteArrayView(bytes.data(), bytes.size()), 0, 1, CodePointArrayView(), out, &pos));
        EXPECT_EQ(123, out[0]);
        EXPECT_EQ(456, out[1]);
    }
}

TEST(PatriciaBoundsTest, EveryTruncatedFullNodeIsInvalidIncludingShortcutsAndBigrams) {
    // flags: multiple chars, terminal, three-byte children, shortcuts and bigrams.
    // Child points back inside the body for this isolated record fixture.
    const std::vector<uint8_t> record = {0xFC, 'a', 'b', 0x1F, 50, 0, 0, 0,
            0, 5, 0, 'x', 0x1F, 0x10, 1};
    for (size_t size = 0; size <= record.size(); ++size) {
        const ReadOnlyByteArrayView view(record.data(), size);
        ShortcutListPolicy shortcuts(view);
        BigramListPolicy bigrams(view);
        Ver2PatriciaTrieNodeReader reader(view, &bigrams, &shortcuts, CodePointArrayView());
        const auto node = reader.fetchPtNodeParamsInBufferFromPtNodePos(0);
        if (size == record.size()) {
            ASSERT_TRUE(node.isValid());
            EXPECT_EQ(2, node.getCodePointCount());
            EXPECT_EQ(static_cast<int>(size), node.getSiblingNodePos());
        } else EXPECT_FALSE(node.isValid()) << size;
    }
}

TEST(PatriciaBoundsTest, ZeroLengthAndOverlongCharacterSequencesAreInvalid) {
    for (const std::vector<uint8_t> bytes : {std::vector<uint8_t>{0, 0x1F}, {0x20, 0x1F},
            std::vector<uint8_t>(MAX_WORD_LENGTH + 3, 'a')}) {
        const ReadOnlyByteArrayView view(bytes.data(), bytes.size());
        Ver2PatriciaTrieNodeReader reader(view, nullptr, nullptr, CodePointArrayView());
        EXPECT_FALSE(reader.fetchPtNodeParamsInBufferFromPtNodePos(0).isValid());
    }
}

class V2Fixture {
 public:
    explicit V2Fixture(const std::vector<uint8_t> &body) {
        std::vector<uint8_t> data = {0x9B, 0xC1, 0x3A, 0xFE, 0, 202, 0, 0, 0, 0, 0, 12};
        data.insert(data.end(), body.begin(), body.end());
        char path[] = "/tmp/kazuar-v2-XXXXXX";
        int fd = mkstemp(path);
        EXPECT_GE(fd, 0);
        EXPECT_EQ(static_cast<ssize_t>(data.size()), write(fd, data.data(), data.size()));
        close(fd);
        buffer = MmappedBuffer::openBuffer(path, false);
        unlink(path);
    }
    MmappedBuffer::MmappedBufferPtr buffer;
};

TEST(PatriciaBoundsTest, MultiLevelWordsRespectRemainingOutputCapacity) {
    // root: "ab" -> child array; child terminal: "cd".
    V2Fixture fixture({1, 0x60, 'a', 'b', 0x1F, 1, 1, 0x30, 'c', 'd', 0x1F, 50});
    ASSERT_TRUE(fixture.buffer);
    PatriciaTriePolicy policy(std::move(fixture.buffer));
    int out[] = {111, 111, 111, 111, 999};
    EXPECT_EQ(0, policy.getCodePointsAndReturnCodePointCount(7, 3, out));
    EXPECT_EQ(999, out[4]);
    EXPECT_EQ(111, out[0]);
    EXPECT_EQ(4, policy.getCodePointsAndReturnCodePointCount(7, 4, out));
    EXPECT_EQ('a', out[0]); EXPECT_EQ('b', out[1]);
    EXPECT_EQ('c', out[2]); EXPECT_EQ('d', out[3]); EXPECT_EQ(999, out[4]);
    EXPECT_EQ(0, policy.getCodePointsAndReturnCodePointCount(7, 0, out));
    EXPECT_EQ(0, policy.getCodePointsAndReturnCodePointCount(7, 4, nullptr));
}

TEST(PatriciaBoundsTest, BreadthFirstSiblingArraysRestoreTheCorrectPrefix) {
    // Root has "a" and terminal "b"; their child arrays contain "c" and "d".
    V2Fixture fixture({2, 0x40, 'a', 5, 0x50, 'b', 60, 5,
            1, 0x10, 'c', 70, 1, 0x10, 'd', 80});
    ASSERT_TRUE(fixture.buffer);
    PatriciaTriePolicy policy(std::move(fixture.buffer));
    int out[3] = {123, 456, 999};
    ASSERT_EQ(1, policy.getCodePointsAndReturnCodePointCount(4, 2, out));
    EXPECT_EQ('b', out[0]);
    ASSERT_EQ(2, policy.getCodePointsAndReturnCodePointCount(9, 2, out));
    EXPECT_EQ('a', out[0]); EXPECT_EQ('c', out[1]);
    ASSERT_EQ(2, policy.getCodePointsAndReturnCodePointCount(13, 2, out));
    EXPECT_EQ('b', out[0]); EXPECT_EQ('d', out[1]); EXPECT_EQ(999, out[2]);
    EXPECT_EQ(0, policy.getCodePointsAndReturnCodePointCount(13, 1, out));
    EXPECT_FALSE(policy.isCorrupted());
}

TEST(PatriciaBoundsTest, DynamicReaderRejectsTruncatedNodeFields) {
    // non-moved, terminal, two chars, parent(3), terminal id(4), children(3).
    std::vector<uint8_t> bytes = {0xF0, 0, 0, 0, 'a', 'b', 0x1F, 0, 0, 0, 1, 0, 0, 0};
    for (size_t size = 0; size <= bytes.size(); ++size) {
        BufferWithExtendableBuffer buffer(ReadWriteByteArrayView(bytes.data(), size), 1024);
        Ver4PatriciaTrieNodeReader reader(&buffer);
        const auto node = reader.fetchPtNodeParamsInBufferFromPtNodePos(0);
        EXPECT_EQ(size == bytes.size(), node.isValid()) << size;
    }
}
} // namespace
} // namespace latinime
