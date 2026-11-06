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

#include "dictionary/utils/buffer_with_extendable_buffer.h"
#include <cstring>
#include <limits>

namespace latinime {

const size_t BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE = 1024 * 1024;
const int BufferWithExtendableBuffer::NEAR_BUFFER_LIMIT_THRESHOLD_PERCENTILE = 90;
// TODO: Needs to allocate larger memory corresponding to the current vector size.
const size_t BufferWithExtendableBuffer::EXTEND_ADDITIONAL_BUFFER_SIZE_STEP = 128 * 1024;

bool BufferWithExtendableBuffer::tryReadUint(const int size, const int pos, uint32_t *const out) const {
    if (!out || size < 1 || size > 4 || pos < 0 || pos > getTailPosition()
            || size > getTailPosition() - pos) return false;
    uint32_t value = 0;
    for (int i = 0; i < size; ++i) {
        const int current = pos + i;
        const bool additional = isInAdditionalBuffer(current);
        const int local = additional ? current - getOriginalBufferSize() : current;
        value = (value << 8) | getBuffer(additional)[local];
    }
    *out = value;
    return true;
}

uint32_t BufferWithExtendableBuffer::readUint(const int size, const int pos) const {
    uint32_t value = 0;
    tryReadUint(size, pos, &value);
    return value;
}

uint32_t BufferWithExtendableBuffer::readUintAndAdvancePosition(const int size, int *const pos) const {
    uint32_t value = 0;
    if (tryReadUint(size, *pos, &value)) *pos += size;
    else *pos = NOT_A_DICT_POS;
    return value;
}

void BufferWithExtendableBuffer::readCodePointsAndAdvancePosition(const int capacity,
        int *const out, int *count, int *const pos) const {
    *count = 0;
    int cursor = *pos;
    if (capacity < 0 || (capacity > 0 && !out)) { *pos = NOT_A_DICT_POS; return; }
    int length = 0;
    for (;;) {
        uint32_t first, value;
        if (!tryReadUint(1, cursor, &first)) break;
        if (first == 0x1F) { *pos = cursor + 1; *count = length; return; }
        const int size = first < 0x20 ? 3 : 1;
        if (length >= capacity || !tryReadUint(size, cursor, &value)) break;
        out[length++] = value;
        cursor += size;
    }
    *pos = NOT_A_DICT_POS;
}

bool BufferWithExtendableBuffer::extend(const int size) {
    return checkAndPrepareWriting(getTailPosition(), size);
}

bool BufferWithExtendableBuffer::writeUint(const uint32_t data, const int size, const int pos) {
    int writingPos = pos;
    return writeUintAndAdvancePosition(data, size, &writingPos);
}

bool BufferWithExtendableBuffer::writeUintAndAdvancePosition(const uint32_t data, const int size,
        int *const pos) {
    if (!(size >= 1 && size <= 4)) {
        AKLOGI("writeUintAndAdvancePosition() is called with invalid size: %d", size);
        ASSERT(false);
        return false;
    }
    if (!checkAndPrepareWriting(*pos, size)) {
        return false;
    }
    const bool usesAdditionalBuffer = isInAdditionalBuffer(*pos);
    uint8_t *const buffer =
            usesAdditionalBuffer ? mAdditionalBuffer.data() : mOriginalBuffer.data();
    if (usesAdditionalBuffer) {
        *pos -= mOriginalBuffer.size();
    }
    ByteArrayUtils::writeUintAndAdvancePosition(buffer, data, size, pos);
    if (usesAdditionalBuffer) {
        *pos += mOriginalBuffer.size();
    }
    return true;
}

bool BufferWithExtendableBuffer::writeCodePointsAndAdvancePosition(const int *const codePoints,
        const int codePointCount, const bool writesTerminator, int *const pos) {
    if (codePointCount < 0 || (codePointCount > 0 && !codePoints)
            || *pos < 0 || *pos > getTailPosition()) return false;
    const size_t boundary = isInAdditionalBuffer(*pos)
            ? mOriginalBuffer.size() + mMaxAdditionalBufferSize : mOriginalBuffer.size();
    const size_t minimum = static_cast<size_t>(codePointCount) + (writesTerminator ? 1 : 0);
    if (minimum > boundary - static_cast<size_t>(*pos)) return false;
    const size_t size = ByteArrayUtils::calculateRequiredByteCountToStoreCodePoints(
            codePoints, codePointCount, writesTerminator);
    if (size > static_cast<size_t>(std::numeric_limits<int>::max())
            || !checkAndPrepareWriting(*pos, static_cast<int>(size))) {
        return false;
    }
    const bool usesAdditionalBuffer = isInAdditionalBuffer(*pos);
    uint8_t *const buffer =
            usesAdditionalBuffer ? mAdditionalBuffer.data() : mOriginalBuffer.data();
    if (usesAdditionalBuffer) {
        *pos -= mOriginalBuffer.size();
    }
    ByteArrayUtils::writeCodePointsAndAdvancePosition(buffer, codePoints, codePointCount,
            writesTerminator, pos);
    if (usesAdditionalBuffer) {
        *pos += mOriginalBuffer.size();
    }
    return true;
}

bool BufferWithExtendableBuffer::extendBuffer(const size_t size) {
    if (size > mMaxAdditionalBufferSize - mAdditionalBuffer.size()) return false;
    const size_t growth = std::min(std::max(EXTEND_ADDITIONAL_BUFFER_SIZE_STEP, size),
            mMaxAdditionalBufferSize - mAdditionalBuffer.size());
    mAdditionalBuffer.resize(mAdditionalBuffer.size() + growth);
    return true;
}

bool BufferWithExtendableBuffer::checkAndPrepareWriting(const int pos, const int size) {
    if (pos < 0 || size < 0) {
        // Invalid position or size.
        return false;
    }
    const size_t totalRequiredSize = static_cast<size_t>(pos) + static_cast<size_t>(size);
    if (totalRequiredSize > static_cast<size_t>(std::numeric_limits<int>::max())) return false;
    if (!isInAdditionalBuffer(pos)) {
        // Here don't need to care about the additional buffer.
        if (mOriginalBuffer.size() < totalRequiredSize) {
            // Violate the boundary.
            return false;
        }
        // The buffer has sufficient capacity.
        return true;
    }
    // Hereafter, pos is in the additional buffer.
    const size_t tailPosition = static_cast<size_t>(getTailPosition());
    if (totalRequiredSize <= tailPosition) {
        // The buffer has sufficient capacity.
        return true;
    }
    if (static_cast<size_t>(pos) != tailPosition) {
        // The additional buffer must be extended from the tail position.
        return false;
    }
    const size_t extendSize = totalRequiredSize -
            std::min(mAdditionalBuffer.size() + mOriginalBuffer.size(), totalRequiredSize);
    if (extendSize > 0 && !extendBuffer(extendSize)) {
        // Failed to extend the buffer.
        return false;
    }
    mUsedAdditionalBufferSize += size;
    return true;
}

bool BufferWithExtendableBuffer::copy(const BufferWithExtendableBuffer *const source) {
    if (!source) return false;
    if (source == this) return true;
    const int size = source->getTailPosition();
    if (size > getTailPosition() && !extend(size - getTailPosition())) return false;
    int pos = 0;
    while (pos < size) {
        const bool srcAdditional = source->isInAdditionalBuffer(pos);
        const bool dstAdditional = isInAdditionalBuffer(pos);
        const int srcBase = srcAdditional ? source->getOriginalBufferSize() : 0;
        const int dstBase = dstAdditional ? getOriginalBufferSize() : 0;
        const int srcEnd = srcAdditional ? size : source->getOriginalBufferSize();
        const int dstEnd = dstAdditional ? size : getOriginalBufferSize();
        const int bytes = std::min(size, std::min(srcEnd, dstEnd)) - pos;
        uint8_t *destination = dstAdditional ? mAdditionalBuffer.data() : mOriginalBuffer.data();
        memmove(destination + pos - dstBase, source->getBuffer(srcAdditional) + pos - srcBase, bytes);
        pos += bytes;
    }
    return true;
}

} // namespace latinime
