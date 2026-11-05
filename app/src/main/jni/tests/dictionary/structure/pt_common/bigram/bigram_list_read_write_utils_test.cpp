// SPDX-License-Identifier: GPL-3.0-only
#include "dictionary/structure/pt_common/bigram/bigram_list_read_write_utils.h"
#include <gtest/gtest.h>
#include <vector>

namespace latinime {
namespace {
TEST(BigramListReadWriteUtilsTest, EveryTruncatedAddressFailsWithoutPublishingOutputs) {
    for (int addressSize = 1; addressSize <= 3; ++addressSize) {
        for (int available = 0; available < addressSize; ++available) {
            // Allocate exactly the available bytes so ASan detects even a single-byte overread.
            std::vector<uint8_t> bytes(1 + available, 1);
            bytes[0] = (addressSize << 4) | 0x80;
            int pos = 0;
            uint8_t flags = 7;
            int target = 123;
            EXPECT_FALSE(BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(
                    ReadOnlyByteArrayView(bytes.data(), bytes.size()), &flags, &target, &pos));
            EXPECT_EQ(0, pos);
            EXPECT_EQ(7, flags);
            EXPECT_EQ(123, target);
        }
    }
}

TEST(BigramListReadWriteUtilsTest, EmptyAndInvalidPositionsFail) {
    const uint8_t data[] = {0x10};
    for (int initial : {-1, 1, 100}) {
        int pos = initial;
        EXPECT_FALSE(BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(
                ReadOnlyByteArrayView(data, sizeof(data)), nullptr, nullptr, &pos));
        EXPECT_EQ(initial, pos);
    }
    int pos = 0;
    EXPECT_FALSE(BigramListReadWriteUtils::skipExistingBigrams(ReadOnlyByteArrayView(), &pos));
}

TEST(BigramListReadWriteUtilsTest, CompleteAddressesAndSignsAdvanceExactly) {
    for (int size = 0; size <= 3; ++size) {
        for (int sign : {0, 0x40}) {
            std::vector<uint8_t> bytes(2 + size, 0);
            bytes[1] = (size << 4) | sign | 5;
            if (size) bytes.back() = 1;
            int pos = 1;
            int target = 0;
            uint8_t flags = 0;
            ASSERT_TRUE(BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(
                    ReadOnlyByteArrayView(bytes.data(), bytes.size()), &flags, &target, &pos));
            EXPECT_EQ(2 + size, pos);
            EXPECT_EQ(2 + (size ? (sign ? -1 : 1) : 0), target);
            EXPECT_EQ(bytes[1], flags);
        }
    }
}

TEST(BigramListReadWriteUtilsTest, ContinuationWithTruncatedLastEntryFails) {
    const uint8_t data[] = {0x90, 1, 0x30, 1};
    int pos = 0;
    EXPECT_FALSE(BigramListReadWriteUtils::skipExistingBigrams(
            ReadOnlyByteArrayView(data, sizeof(data)), &pos));
    EXPECT_EQ(2, pos);
}
}  // namespace
}  // namespace latinime
