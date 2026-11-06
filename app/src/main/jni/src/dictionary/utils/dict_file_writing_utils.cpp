/*
 * Copyright (C) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "dictionary/utils/dict_file_writing_utils.h"

#include <cstdio>
#include <climits>
#include <cstring>
#include <vector>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "dictionary/header/header_policy.h"
#include "dictionary/structure/backward/v402/ver4_dict_buffers.h"
#include "dictionary/structure/pt_common/dynamic_pt_writing_utils.h"
#include "dictionary/structure/v4/ver4_dict_buffers.h"
#include "dictionary/utils/buffer_with_extendable_buffer.h"
#include "dictionary/utils/entry_counters.h"
#include "dictionary/utils/file_utils.h"
#include "dictionary/utils/format_utils.h"
#include "utils/time_keeper.h"

namespace latinime {

const char *const DictFileWritingUtils::TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE = ".tmp";
// Enough size to describe buffer size.
const int DictFileWritingUtils::SIZE_OF_BUFFER_SIZE_FIELD = 4;

/* static */ bool DictFileWritingUtils::createEmptyDictFile(const char *const filePath,
        const int dictVersion, const std::vector<int> localeAsCodePointVector,
        const DictionaryHeaderStructurePolicy::AttributeMap *const attributeMap) {
    TimeKeeper::setCurrentTime();
    const FormatUtils::FORMAT_VERSION formatVersion = FormatUtils::getFormatVersion(dictVersion);
    switch (formatVersion) {
        case FormatUtils::VERSION_402:
            return createEmptyV4DictFile<backward::v402::Ver4DictConstants,
                    backward::v402::Ver4DictBuffers,
                    backward::v402::Ver4DictBuffers::Ver4DictBuffersPtr>(
                            filePath, localeAsCodePointVector, attributeMap, formatVersion);
        case FormatUtils::VERSION_4_ONLY_FOR_TESTING:
        case FormatUtils::VERSION_403:
            return createEmptyV4DictFile<Ver4DictConstants, Ver4DictBuffers,
                    Ver4DictBuffers::Ver4DictBuffersPtr>(
                            filePath, localeAsCodePointVector, attributeMap, formatVersion);
        default:
            AKLOGE("Cannot create dictionary %s because format version %d is not supported.",
                    filePath, dictVersion);
            return false;
    }
}

template<class DictConstants, class DictBuffers, class DictBuffersPtr>
/* static */ bool DictFileWritingUtils::createEmptyV4DictFile(const char *const dirPath,
        const std::vector<int> localeAsCodePointVector,
        const DictionaryHeaderStructurePolicy::AttributeMap *const attributeMap,
        const FormatUtils::FORMAT_VERSION formatVersion) {
    HeaderPolicy headerPolicy(formatVersion, localeAsCodePointVector, attributeMap);
    DictBuffersPtr dictBuffers = DictBuffers::createVer4DictBuffers(&headerPolicy,
            DictConstants::MAX_DICT_EXTENDED_REGION_SIZE);
    headerPolicy.fillInAndWriteHeaderToBuffer(true /* updatesLastDecayedTime */,
            EntryCounts(), 0 /* extendedRegionSize */, dictBuffers->getWritableHeaderBuffer());
    if (!DynamicPtWritingUtils::writeEmptyDictionary(
            dictBuffers->getWritableTrieBuffer(), 0 /* rootPos */)) {
        AKLOGE("Empty ver4 dictionary structure cannot be created on memory.");
        return false;
    }
    return dictBuffers->flush(dirPath);
}

/* static */ bool DictFileWritingUtils::flushBufferToFileWithSuffix(const char *const basePath,
        const char *const suffix, const BufferWithExtendableBuffer *const buffer) {
    if (!basePath || !suffix || strnlen(basePath, PATH_MAX) >= PATH_MAX
            || strnlen(suffix, PATH_MAX) >= PATH_MAX) return false;
    const std::string path = std::string(basePath) + suffix;
    if (path.size() >= PATH_MAX) return false;
    return flushBufferToFile(path.c_str(), buffer);
}

/* static */ bool DictFileWritingUtils::writeBufferToFileTail(FILE *const file,
        const BufferWithExtendableBuffer *const buffer) {
    uint8_t bufferSize[SIZE_OF_BUFFER_SIZE_FIELD];
    int writingPos = 0;
    ByteArrayUtils::writeUintAndAdvancePosition(bufferSize, buffer->getTailPosition(),
            SIZE_OF_BUFFER_SIZE_FIELD, &writingPos);
    if (fwrite(bufferSize, SIZE_OF_BUFFER_SIZE_FIELD, 1 /* count */, file) < 1) {
        return false;
    }
    return writeBufferToFile(file, buffer);
}

/* static */ bool DictFileWritingUtils::flushBufferToFile(const char *const filePath,
        const BufferWithExtendableBuffer *const buffer) {
    std::lock_guard<std::recursive_mutex> lock(fileMutex());
    if (!buffer || !filePath || strnlen(filePath, PATH_MAX) >= PATH_MAX) return false;
    const std::string pattern = std::string(filePath) + ".tmp.XXXXXX";
    if (pattern.size() >= PATH_MAX) return false;
    std::vector<char> staging(pattern.begin(), pattern.end());
    staging.push_back(0);
    const int fd = mkstemp(staging.data());
    if (fd < 0) return false;
    fcntl(fd, F_SETFD, FD_CLOEXEC);
    FILE *file = fdopen(fd, "wb");
    if (!file) { close(fd); unlink(staging.data()); return false; }
    bool success = writeBufferToFile(file, buffer) && fflush(file) == 0 && fsync(fd) == 0;
    if (fclose(file) != 0) success = false;
    // link publishes a complete inode atomically, preserving the existing no-overwrite contract.
    if (success) success = link(staging.data(), filePath) == 0;
    unlink(staging.data());
    return success;
}

// Returns whether the writing was succeeded or not.
/* static */ bool DictFileWritingUtils::writeBufferToFile(FILE *const file,
        const BufferWithExtendableBuffer *const buffer) {
    const int originalBufSize = buffer->getOriginalBufferSize();
    if (originalBufSize > 0 && fwrite(buffer->getBuffer(false /* usesAdditionalBuffer */),
            originalBufSize, 1, file) < 1) {
        return false;
    }
    const int additionalBufSize = buffer->getUsedAdditionalBufferSize();
    if (additionalBufSize > 0 && fwrite(buffer->getBuffer(true /* usesAdditionalBuffer */),
            additionalBufSize, 1, file) < 1) {
        return false;
    }
    return true;
}

std::recursive_mutex &DictFileWritingUtils::fileMutex() {
    static std::recursive_mutex mutex;
    return mutex;
}

DictFileWritingUtils::StagedDirectory::StagedDirectory(const char *base) : mPath() {
    if (!base || strnlen(base, PATH_MAX) >= PATH_MAX) return;
    const std::string pattern = std::string(base) + ".tmp.XXXXXX";
    if (pattern.size() >= PATH_MAX) return;
    std::vector<char> name(pattern.begin(), pattern.end());
    name.push_back(0);
    if (mkdtemp(name.data())) mPath = name.data();
}

DictFileWritingUtils::StagedDirectory::~StagedDirectory() {
    if (!mPath.empty()) FileUtils::removeDirAndFiles(mPath.c_str());
}

bool DictFileWritingUtils::StagedDirectory::publishTo(const char *target) {
    std::lock_guard<std::recursive_mutex> lock(fileMutex());
    if (mPath.empty() || !target || strnlen(target, PATH_MAX) >= PATH_MAX) return false;
    struct stat status;
    std::string backup;
    if (lstat(target, &status) == 0) {
        if (!S_ISDIR(status.st_mode)) return false;
        const std::string pattern = std::string(target) + ".old.XXXXXX";
        if (pattern.size() >= PATH_MAX) return false;
        std::vector<char> name(pattern.begin(), pattern.end());
        name.push_back(0);
        if (!mkdtemp(name.data()) || rmdir(name.data()) != 0) return false;
        backup = name.data();
        if (rename(target, backup.c_str()) != 0) return false;
    } else if (errno != ENOENT) return false;
    if (rename(mPath.c_str(), target) != 0) {
        // Keep the old dictionary recoverable even if rollback itself fails.
        if (!backup.empty()) rename(backup.c_str(), target);
        return false;
    }
    mPath.clear();
    if (!backup.empty()) FileUtils::removeDirAndFiles(backup.c_str());
    return true;
}

} // namespace latinime
