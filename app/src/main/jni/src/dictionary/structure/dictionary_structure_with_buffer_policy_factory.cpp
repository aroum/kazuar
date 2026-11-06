/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"

#include <climits>
#include <string>
#include <vector>
#include <mutex>

#include "defines.h"
#include "dictionary/structure/backward/v402/ver4_dict_buffers.h"
#include "dictionary/structure/backward/v402/ver4_dict_constants.h"
#include "dictionary/structure/backward/v402/ver4_patricia_trie_policy.h"
#include "dictionary/structure/pt_common/dynamic_pt_writing_utils.h"
#include "dictionary/structure/v2/patricia_trie_policy.h"
#include "dictionary/structure/v4/ver4_dict_buffers.h"
#include "dictionary/structure/v4/ver4_dict_constants.h"
#include "dictionary/structure/v4/ver4_patricia_trie_policy.h"
#include "dictionary/utils/dict_file_writing_utils.h"
#include "dictionary/utils/file_utils.h"
#include "dictionary/utils/format_utils.h"
#include "dictionary/utils/mmapped_buffer.h"
#include "utils/byte_array_view.h"

namespace latinime {

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory::newPolicyForExistingDictFile(
                const char *const path, const int bufOffset, const int size,
                const bool isUpdatable) {
    std::lock_guard<std::recursive_mutex> lock(DictFileWritingUtils::fileMutex());
    if (!path || strnlen(path, PATH_MAX) == 0 || strnlen(path, PATH_MAX) >= PATH_MAX) return nullptr;
    if (FileUtils::existsDir(path)) {
        // Given path represents a directory.
        return newPolicyForDirectoryDict(path, isUpdatable);
    } else {
        if (isUpdatable) {
            AKLOGE("One file dictionaries don't support updating. path: %s", path);
            ASSERT(false);
            return nullptr;
        }
        return newPolicyForFileDict(path, bufOffset, size);
    }
}

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory:: newPolicyForOnMemoryDict(
                const int formatVersion, const std::vector<int> &locale,
                const DictionaryHeaderStructurePolicy::AttributeMap *const attributeMap) {
    FormatUtils::FORMAT_VERSION dictFormatVersion = FormatUtils::getFormatVersion(formatVersion);
    switch (dictFormatVersion) {
        case FormatUtils::VERSION_402: {
            return newPolicyForOnMemoryV4Dict<backward::v402::Ver4DictConstants,
                    backward::v402::Ver4DictBuffers,
                    backward::v402::Ver4DictBuffers::Ver4DictBuffersPtr,
                    backward::v402::Ver4PatriciaTriePolicy>(
                            dictFormatVersion, locale, attributeMap);
        }
        case FormatUtils::VERSION_4_ONLY_FOR_TESTING:
        case FormatUtils::VERSION_403: {
            return newPolicyForOnMemoryV4Dict<Ver4DictConstants, Ver4DictBuffers,
                    Ver4DictBuffers::Ver4DictBuffersPtr, Ver4PatriciaTriePolicy>(
                            dictFormatVersion, locale, attributeMap);
        }
        default:
            AKLOGE("DICT: dictionary format %d is not supported for on memory dictionary",
                    formatVersion);
            break;
    }
    return nullptr;
}

template<class DictConstants, class DictBuffers, class DictBuffersPtr, class StructurePolicy>
/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory::newPolicyForOnMemoryV4Dict(
                const FormatUtils::FORMAT_VERSION formatVersion,
                const std::vector<int> &locale,
                const DictionaryHeaderStructurePolicy::AttributeMap *const attributeMap) {
    HeaderPolicy headerPolicy(formatVersion, locale, attributeMap);
    DictBuffersPtr dictBuffers = DictBuffers::createVer4DictBuffers(&headerPolicy,
            DictConstants::MAX_DICT_EXTENDED_REGION_SIZE);
    if (!DynamicPtWritingUtils::writeEmptyDictionary(
            dictBuffers->getWritableTrieBuffer(), 0 /* rootPos */)) {
        AKLOGE("Empty ver4 dictionary structure cannot be created on memory.");
        return nullptr;
    }
    return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(
            new StructurePolicy(std::move(dictBuffers)));
}

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory::newPolicyForDirectoryDict(
                const char *const path, const bool isUpdatable) {
    const int headerFilePathBufSize = PATH_MAX + 1 /* terminator */;
    char headerFilePath[headerFilePathBufSize];
    if (!getHeaderFilePathInDictDir(path, headerFilePathBufSize, headerFilePath)) return nullptr;
    // Allocated buffer in MmapedBuffer::openBuffer() will be freed in the destructor of
    // MmappedBufferPtr if the instance has the responsibility.
    MmappedBuffer::MmappedBufferPtr mmappedBuffer =
            MmappedBuffer::openBuffer(headerFilePath, isUpdatable);
    if (!mmappedBuffer) {
        return nullptr;
    }
    DictionaryHeaderStructurePolicy::AttributeMap checkedAttributes;
    if (!HeaderReadWriteUtils::fetchAllHeaderAttributes(mmappedBuffer->getReadOnlyByteArrayView(),
            &checkedAttributes)) return nullptr;
    const FormatUtils::FORMAT_VERSION formatVersion = FormatUtils::detectFormatVersion(
            mmappedBuffer->getReadOnlyByteArrayView());
    switch (formatVersion) {
        case FormatUtils::VERSION_2:
        case FormatUtils::VERSION_201:
        case FormatUtils::VERSION_202:
            AKLOGE("Given path is a directory but the format is version 2xx. path: %s", path);
            break;
        case FormatUtils::VERSION_402: {
            return newPolicyForV4Dict<backward::v402::Ver4DictConstants,
                    backward::v402::Ver4DictBuffers,
                    backward::v402::Ver4DictBuffers::Ver4DictBuffersPtr,
                    backward::v402::Ver4PatriciaTriePolicy>(
                            headerFilePath, formatVersion, std::move(mmappedBuffer));
        }
        case FormatUtils::VERSION_4_ONLY_FOR_TESTING:
        case FormatUtils::VERSION_403: {
            return newPolicyForV4Dict<Ver4DictConstants, Ver4DictBuffers,
                    Ver4DictBuffers::Ver4DictBuffersPtr, Ver4PatriciaTriePolicy>(
                            headerFilePath, formatVersion, std::move(mmappedBuffer));
        }
        default:
            AKLOGE("DICT: dictionary format is unknown, bad magic number. path: %s", path);
            break;
    }
    ASSERT(false);
    return nullptr;
}

template<class DictConstants, class DictBuffers, class DictBuffersPtr, class StructurePolicy>
/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory::newPolicyForV4Dict(
                const char *const headerFilePath, const FormatUtils::FORMAT_VERSION formatVersion,
                MmappedBuffer::MmappedBufferPtr &&mmappedBuffer) {
    const int dictDirPathBufSize = strlen(headerFilePath) + 1;
    std::vector<char> pathBuffer(dictDirPathBufSize);
    char *const dictPath = pathBuffer.data();
    if (!FileUtils::getFilePathWithoutSuffix(headerFilePath,
            DictConstants::HEADER_FILE_EXTENSION, dictDirPathBufSize, dictPath)) {
        AKLOGE("Dictionary file name is not valid as a ver4 dictionary. header path: %s",
                headerFilePath);
        ASSERT(false);
        return nullptr;
    }
    DictBuffersPtr dictBuffers =
            DictBuffers::openVer4DictBuffers(dictPath, std::move(mmappedBuffer), formatVersion);
    if (!dictBuffers || !dictBuffers->isValid()) {
        AKLOGE("DICT: The dictionary doesn't satisfy ver4 format requirements. path: %s",
                dictPath);
        ASSERT(false);
        return nullptr;
    }
    return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(
            new StructurePolicy(std::move(dictBuffers)));
}

/* static */ DictionaryStructureWithBufferPolicy::StructurePolicyPtr
        DictionaryStructureWithBufferPolicyFactory::newPolicyForFileDict(
                const char *const path, const int bufOffset, const int size) {
    // Allocated buffer in MmapedBuffer::openBuffer() will be freed in the destructor of
    // MmappedBufferPtr if the instance has the responsibility.
    MmappedBuffer::MmappedBufferPtr mmappedBuffer(
            MmappedBuffer::openBuffer(path, bufOffset, size, false /* isUpdatable */));
    if (!mmappedBuffer) {
        return nullptr;
    }
    DictionaryHeaderStructurePolicy::AttributeMap checkedAttributes;
    if (!HeaderReadWriteUtils::fetchAllHeaderAttributes(mmappedBuffer->getReadOnlyByteArrayView(),
            &checkedAttributes)) return nullptr;
    switch (FormatUtils::detectFormatVersion(mmappedBuffer->getReadOnlyByteArrayView())) {
        case FormatUtils::VERSION_2:
        case FormatUtils::VERSION_201:
            AKLOGE("Dictionary versions 2 and 201 are incompatible with this version");
            break;
        case FormatUtils::VERSION_202:
            return DictionaryStructureWithBufferPolicy::StructurePolicyPtr(
                    new PatriciaTriePolicy(std::move(mmappedBuffer)));
        case FormatUtils::VERSION_4_ONLY_FOR_TESTING:
        case FormatUtils::VERSION_402:
        case FormatUtils::VERSION_403:
            AKLOGE("Given path is a file but the format is version 4. path: %s", path);
            break;
        default:
            AKLOGE("DICT: dictionary format is unknown, bad magic number. path: %s", path);
            break;
    }
    ASSERT(false);
    return nullptr;
}

/* static */ bool DictionaryStructureWithBufferPolicyFactory::getHeaderFilePathInDictDir(
        const char *const dirPath, const int capacity, char *const out) {
    if (!dirPath || !out || capacity <= 0 || strnlen(dirPath, PATH_MAX) >= PATH_MAX) return false;
    std::string path(dirPath);
    while (path.size() > 1 && path.back() == '/') path.pop_back();
    const auto slash = path.find_last_of('/');
    const std::string name = path.substr(slash == std::string::npos ? 0 : slash + 1);
    if (name.empty() || name == "." || name == "..") return false;
    const int written = snprintf(out, capacity, "%s/%s%s", path.c_str(), name.c_str(),
            Ver4DictConstants::HEADER_FILE_EXTENSION);
    return written >= 0 && written < capacity;
}

} // namespace latinime
