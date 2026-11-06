// SPDX-License-Identifier: GPL-3.0-only
#include <gtest/gtest.h>
#include <atomic>
#include <climits>
#include <cstdio>
#include <cstring>
#include <dirent.h>
#include <string>
#include <thread>
#include <unistd.h>
#include <sys/stat.h>
#include <vector>
#include "dictionary/header/header_read_write_utils.h"
#include "dictionary/property/unigram_property.h"
#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"
#include "dictionary/utils/buffer_with_extendable_buffer.h"
#include "dictionary/utils/dict_file_writing_utils.h"
#include "dictionary/utils/file_utils.h"

namespace latinime {
namespace {
class OwnedDirectory {
 public:
    OwnedDirectory() {
        char pattern[] = "/tmp/kazuar-storage-XXXXXX";
        char *result = mkdtemp(pattern);
        if (result) path = result;
    }
    ~OwnedDirectory() { if (!path.empty()) FileUtils::removeDirAndFiles(path.c_str()); }
    std::string path;
};
int countDescriptors() {
    DIR *dir = opendir("/proc/self/fd"); if (!dir) return -1;
    int count = 0; while (readdir(dir)) ++count; closedir(dir); return count;
}
TEST(DictionaryFilesTest, LongPathsAndTruncatedHeadersAreRejected) {
    const std::string huge(PATH_MAX + 1000, 'a');
    EXPECT_FALSE(DictionaryStructureWithBufferPolicyFactory::newPolicyForExistingDictFile(huge.c_str(), 0, 0, false));
    char base[16] = {'x'}; FileUtils::getBasename(huge.c_str(), sizeof(base), base);
    EXPECT_STREQ("", base);
    uint8_t header[] = {0x9B,0xC1,0x3A,0xFE,0,202,0,0,0,0,0,14,'a','b'};
    DictionaryHeaderStructurePolicy::AttributeMap attributes;
    HeaderReadWriteUtils::setIntAttribute(&attributes, "preserve", 1);
    EXPECT_FALSE(HeaderReadWriteUtils::fetchAllHeaderAttributes(ReadOnlyByteArrayView(header, sizeof(header)), &attributes));
    EXPECT_EQ(1, HeaderReadWriteUtils::readIntAttributeValue(&attributes, "preserve", 0));
    EXPECT_FALSE(HeaderReadWriteUtils::fetchAllHeaderAttributes(ReadOnlyByteArrayView(header, 12), &attributes));
}
TEST(DictionaryFilesTest, TreeDeletionDoesNotFollowSymlinksOrLeakDescriptors) {
    OwnedDirectory root, outside;
    ASSERT_FALSE(root.path.empty()); ASSERT_FALSE(outside.path.empty());
    const std::string nested = root.path + "/nested";
    ASSERT_EQ(0, mkdir(nested.c_str(), 0700));
    const std::string kept = outside.path + "/kept";
    FILE *file = fopen(kept.c_str(), "wb"); ASSERT_TRUE(file); fclose(file);
    ASSERT_EQ(0, symlink(outside.path.c_str(), (nested + "/link").c_str()));
    const int before = countDescriptors();
    EXPECT_TRUE(FileUtils::removeDirAndFiles(root.path.c_str()));
    EXPECT_EQ(0, access(kept.c_str(), F_OK));
    for (int i = 0; i < 20; ++i) EXPECT_FALSE(FileUtils::removeDirAndFiles(kept.c_str()));
    EXPECT_EQ(before, countDescriptors());
    EXPECT_TRUE(FileUtils::removeDirAndFiles(root.path.c_str()));
}
TEST(DictionaryFilesTest, ExcessiveTreeDepthReturnsFailureWithoutRecursion) {
    OwnedDirectory root; ASSERT_FALSE(root.path.empty());
    std::vector<std::string> paths{root.path};
    for (int i = 0; i < 70; ++i) {
        paths.push_back(paths.back() + "/x"); ASSERT_EQ(0, mkdir(paths.back().c_str(), 0700));
    }
    const int before = countDescriptors();
    EXPECT_FALSE(FileUtils::removeDirAndFiles(root.path.c_str()));
    EXPECT_EQ(before, countDescriptors());
    // The test deliberately exceeds the production limit; clean its known private paths bottom-up.
    for (auto it = paths.rbegin(); it != paths.rend(); ++it) rmdir(it->c_str());
}
TEST(DictionaryFilesTest, ConcurrentFileCreationPublishesOneCompleteWinner) {
    OwnedDirectory root; ASSERT_FALSE(root.path.empty());
    const std::string path = root.path + "/snapshot";
    std::atomic<int> winners(0), finished(0);
    std::vector<std::thread> writers;
    for (int id = 1; id <= 6; ++id) writers.emplace_back([&, id] {
        BufferWithExtendableBuffer buffer(128 * 1024);
        bool ok = buffer.extend(128 * 1024) && buffer.writeUint(id, 4, 0)
                && buffer.writeUint(id, 4, 128 * 1024 - 4);
        if (ok && DictFileWritingUtils::flushBufferToFileWithSuffix(path.c_str(), ".bin", &buffer)) ++winners;
        ++finished;
    });
    bool partial = false;
    while (finished.load() < 6) {
        FILE *file = fopen((path + ".bin").c_str(), "rb");
        if (!file) { std::this_thread::yield(); continue; }
        fseek(file, 0, SEEK_END); const long size = ftell(file);
        if (size != 128 * 1024) partial = true;
        fclose(file);
    }
    for (auto &thread : writers) thread.join();
    EXPECT_EQ(1, winners.load()); EXPECT_FALSE(partial);
    FILE *file = fopen((path + ".bin").c_str(), "rb"); ASSERT_TRUE(file);
    uint8_t first[4], last[4]; ASSERT_EQ(4u, fread(first, 1, 4, file));
    ASSERT_EQ(0, fseek(file, -4, SEEK_END)); ASSERT_EQ(4u, fread(last, 1, 4, file)); fclose(file);
    EXPECT_EQ(0, memcmp(first, last, 4));
}
TEST(DictionaryFilesTest, ReadersSeeCompleteDictionariesDuringMixedFormatReplacement) {
    OwnedDirectory root; ASSERT_FALSE(root.path.empty());
    const std::string path = root.path + "/dictionary";
    DictionaryHeaderStructurePolicy::AttributeMap attributes;
    auto old = DictionaryStructureWithBufferPolicyFactory::newPolicyForOnMemoryDict(402, {'e','n'}, &attributes);
    auto current = DictionaryStructureWithBufferPolicyFactory::newPolicyForOnMemoryDict(403, {'e','n'}, &attributes);
    ASSERT_TRUE(old); ASSERT_TRUE(current);
    const int a[] = {'a','a'}, b[] = {'b','b'};
    const UnigramProperty property(false, false, false, 100, HistoricalInfo());
    ASSERT_TRUE(old->addUnigramEntry(CodePointArrayView(a, 2), &property));
    ASSERT_TRUE(current->addUnigramEntry(CodePointArrayView(b, 2), &property));
    ASSERT_TRUE(old->flush(path.c_str()));
    std::atomic<bool> failed(false), go(false);
    auto write = [&](DictionaryStructureWithBufferPolicy *policy) {
        while (!go.load()) std::this_thread::yield();
        for (int i = 0; i < 4; ++i) if (!policy->flush(path.c_str())) failed = true;
    };
    std::thread first(write, old.get()), second(write, current.get());
    go = true;
    for (int i = 0; i < 30; ++i) {
        auto reader = DictionaryStructureWithBufferPolicyFactory::newPolicyForExistingDictFile(path.c_str(), 0, 0, false);
        if (!reader) { failed = true; continue; }
        const int version = reader->getHeaderStructurePolicy()->getFormatVersionNumber();
        const int id = reader->getWordId(CodePointArrayView(version == 402 ? a : b, 2), false);
        if (id == NOT_A_WORD_ID || reader->getProbabilityOfWord(WordIdArrayView(), id) != 100) failed = true;
    }
    first.join(); second.join();
    EXPECT_FALSE(failed.load());
}
} // namespace
} // namespace latinime
