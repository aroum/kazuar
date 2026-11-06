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

#include "dictionary/structure/pt_common/shortcut/shortcut_list_reading_utils.h"

#include "dictionary/utils/byte_array_utils.h"
#include "utils/bounded_byte_array_reader.h"

namespace latinime {

// Flag for presence of more attributes
const ShortcutListReadingUtils::ShortcutFlags
        ShortcutListReadingUtils::FLAG_ATTRIBUTE_HAS_NEXT = 0x80;
// Mask for attribute probability, stored on 4 bits inside the flags byte.
const ShortcutListReadingUtils::ShortcutFlags
        ShortcutListReadingUtils::MASK_ATTRIBUTE_PROBABILITY = 0x0F;
const int ShortcutListReadingUtils::SHORTCUT_LIST_SIZE_FIELD_SIZE = 2;
// The numeric value of the shortcut probability that means 'whitelist'.
const int ShortcutListReadingUtils::WHITELIST_SHORTCUT_PROBABILITY = 15;

/* static */ ShortcutListReadingUtils::ShortcutFlags
        ShortcutListReadingUtils::getFlagsAndForwardPointer(const ReadOnlyByteArrayView buffer,
                int *const pos) {
    uint32_t flags = 0;
    BoundedByteArrayReader::readUint(buffer, 1, &flags, pos);
    return flags;
}

/* static */ int ShortcutListReadingUtils::getShortcutListSizeAndForwardPointer(
        const ReadOnlyByteArrayView buffer, int *const pos) {
    int cursor = *pos;
    uint32_t totalSize;
    if (!BoundedByteArrayReader::readUint(buffer, SHORTCUT_LIST_SIZE_FIELD_SIZE, &totalSize, &cursor)
            || totalSize < static_cast<uint32_t>(SHORTCUT_LIST_SIZE_FIELD_SIZE)
            || !BoundedByteArrayReader::hasBytes(buffer, *pos, totalSize)) {
        *pos = NOT_A_DICT_POS;
        return -1;
    }
    *pos = cursor;
    return totalSize - SHORTCUT_LIST_SIZE_FIELD_SIZE;
}

/* static */ int ShortcutListReadingUtils::readShortcutTarget(const ReadOnlyByteArrayView buffer,
        const int maxLength, int *const outWord, int *const pos) {
    return BoundedByteArrayReader::readString(buffer, maxLength, nullptr, outWord, pos);
}

/* static */ bool ShortcutListReadingUtils::skipShortcuts(const ReadOnlyByteArrayView buffer,
        int *const pos) {
    int cursor = *pos;
    const int size = getShortcutListSizeAndForwardPointer(buffer, &cursor);
    if (size < 0) return false;
    *pos = cursor + size;
    return true;
}

} // namespace latinime
