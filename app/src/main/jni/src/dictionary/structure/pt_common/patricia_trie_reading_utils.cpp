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

#include "dictionary/structure/pt_common/patricia_trie_reading_utils.h"

#include "defines.h"
#include <algorithm>
#include "utils/bounded_byte_array_reader.h"
#include "dictionary/interface/dictionary_bigrams_structure_policy.h"
#include "dictionary/interface/dictionary_shortcuts_structure_policy.h"
#include "dictionary/utils/byte_array_utils.h"

namespace latinime {

typedef PatriciaTrieReadingUtils PtReadingUtils;

const PtReadingUtils::NodeFlags PtReadingUtils::MASK_CHILDREN_POSITION_TYPE = 0xC0;
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_CHILDREN_POSITION_TYPE_NOPOSITION = 0x00;
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_CHILDREN_POSITION_TYPE_ONEBYTE = 0x40;
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_CHILDREN_POSITION_TYPE_TWOBYTES = 0x80;
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_CHILDREN_POSITION_TYPE_THREEBYTES = 0xC0;

// Flag for single/multiple char group
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_HAS_MULTIPLE_CHARS = 0x20;
// Flag for terminal PtNodes
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_IS_TERMINAL = 0x10;
// Flag for shortcut targets presence
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_HAS_SHORTCUT_TARGETS = 0x08;
// Flag for bigram presence
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_HAS_BIGRAMS = 0x04;
// Flag for non-words (typically, shortcut only entries)
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_IS_NOT_A_WORD = 0x02;
// Flag for possibly offensive words
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_IS_POSSIBLY_OFFENSIVE = 0x01;

/* static */ int PtReadingUtils::getPtNodeArraySizeAndAdvancePosition(
        const ReadOnlyByteArrayView buffer, int *const pos) {
    uint32_t first;
    if (!BoundedByteArrayReader::readUint(buffer, 1, &first, pos)) return -1;
    if (first < 0x80) return first;
    uint32_t second;
    if (!BoundedByteArrayReader::readUint(buffer, 1, &second, pos)) return -1;
    return ((first & 0x7F) << 8) | second;
}

/* static */ PtReadingUtils::NodeFlags PtReadingUtils::getFlagsAndAdvancePosition(
        const ReadOnlyByteArrayView buffer, int *const pos) {
    uint32_t flags = 0;
    BoundedByteArrayReader::readUint(buffer, 1, &flags, pos);
    return flags;
}

/* static */ int PtReadingUtils::getCodePointAndAdvancePosition(const ReadOnlyByteArrayView buffer,
        const CodePointArrayView codePointTable, int *const pos) {
    int value = NOT_A_CODE_POINT;
    BoundedByteArrayReader::readCodePoint(buffer, codePointTable, &value, pos);
    return value;
}

/* static */ int PtReadingUtils::getCharsAndAdvancePosition(const ReadOnlyByteArrayView buffer,
        const NodeFlags flags, const int maxLength, const CodePointArrayView codePointTable,
        int *const outBuffer, int *const pos) {
    if (hasMultipleChars(flags)) {
        return BoundedByteArrayReader::readString(buffer, maxLength, codePointTable, outBuffer, pos);
    }
    if (maxLength <= 0 || !outBuffer) { *pos = NOT_A_DICT_POS; return -1; }
    const int codePoint = getCodePointAndAdvancePosition(buffer, codePointTable, pos);
    if (*pos == NOT_A_DICT_POS || codePoint == NOT_A_CODE_POINT) {
        *pos = NOT_A_DICT_POS;
        return -1;
    }
    outBuffer[0] = codePoint;
    return 1;
}

/* static */ int PtReadingUtils::skipCharacters(const ReadOnlyByteArrayView buffer,
        const NodeFlags flags, const int maxLength, const CodePointArrayView codePointTable, int *const pos) {
    if (maxLength < 0 || maxLength > MAX_WORD_LENGTH) { *pos = NOT_A_DICT_POS; return -1; }
    int ignored[MAX_WORD_LENGTH];
    return getCharsAndAdvancePosition(buffer, flags, maxLength, codePointTable, ignored, pos);
}

/* static */ int PtReadingUtils::readProbabilityAndAdvancePosition(
        const ReadOnlyByteArrayView buffer, int *const pos) {
    uint32_t value;
    return BoundedByteArrayReader::readUint(buffer, 1, &value, pos) ? value : NOT_A_PROBABILITY;
}

/* static */ int PtReadingUtils::readChildrenPositionAndAdvancePosition(
        const ReadOnlyByteArrayView buffer, const NodeFlags flags, int *const pos) {
    const int size = (MASK_CHILDREN_POSITION_TYPE & flags) >> 6;
    if (!size) return NOT_A_DICT_POS;
    const int base = *pos;
    uint32_t offset;
    if (!BoundedByteArrayReader::readUint(buffer, size, &offset, pos)
            || offset > static_cast<uint32_t>(std::numeric_limits<int>::max() - base)) {
        *pos = NOT_A_DICT_POS;
        return NOT_A_DICT_POS;
    }
    const int child = base + offset;
    if (!BoundedByteArrayReader::hasBytes(buffer, child, 1)) {
        *pos = NOT_A_DICT_POS;
        return NOT_A_DICT_POS;
    }
    return child;
}

/* static */ bool PtReadingUtils::readPtNodeInfo(const ReadOnlyByteArrayView dictBuf,
        const int ptNodePos, const DictionaryShortcutsStructurePolicy *const shortcutPolicy,
        const DictionaryBigramsStructurePolicy *const bigramPolicy, const CodePointArrayView codePointTable,
        NodeFlags *const outFlags, int *const outCodePointCount, int *const outCodePoint,
        int *const outProbability, int *const outChildrenPos, int *const outShortcutPos,
        int *const outBigramPos, int *const outSiblingPos) {
    *outCodePointCount = 0;
    *outSiblingPos = NOT_A_DICT_POS;
    int pos = ptNodePos;
    const NodeFlags flags = getFlagsAndAdvancePosition(dictBuf, &pos);
    if (pos == NOT_A_DICT_POS) return false;
    int codePoints[MAX_WORD_LENGTH];
    const int count = getCharsAndAdvancePosition(dictBuf, flags, MAX_WORD_LENGTH,
            codePointTable, codePoints, &pos);
    if (count <= 0) return false;
    const int probability = isTerminal(flags)
            ? readProbabilityAndAdvancePosition(dictBuf, &pos) : NOT_A_PROBABILITY;
    const int children = hasChildrenInFlags(flags)
            ? readChildrenPositionAndAdvancePosition(dictBuf, flags, &pos) : NOT_A_DICT_POS;
    if (pos == NOT_A_DICT_POS) return false;
    const int shortcut = hasShortcutTargets(flags) ? pos : NOT_A_DICT_POS;
    if (hasShortcutTargets(flags) && (!shortcutPolicy || !shortcutPolicy->skipAllShortcuts(&pos))) {
        return false;
    }
    const int bigram = hasBigrams(flags) ? pos : NOT_A_DICT_POS;
    if (hasBigrams(flags) && (!bigramPolicy || !bigramPolicy->skipAllBigrams(&pos))) return false;
    if (!BoundedByteArrayReader::hasBytes(dictBuf, pos, 0) || pos <= ptNodePos) return false;
    *outFlags = flags;
    *outCodePointCount = count;
    std::copy(codePoints, codePoints + count, outCodePoint);
    *outProbability = probability;
    *outChildrenPos = children;
    *outShortcutPos = shortcut;
    *outBigramPos = bigram;
    *outSiblingPos = pos;
    return true;
}

} // namespace latinime
