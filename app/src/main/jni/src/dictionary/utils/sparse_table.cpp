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

#include "dictionary/utils/sparse_table.h"
#include <climits>

namespace latinime {

const int SparseTable::NOT_EXIST = -1;
const int SparseTable::INDEX_SIZE = 4;

bool SparseTable::contains(const int id) const {
    const int pos = getPosInIndexTable(id);
    uint32_t index;
    return pos >= 0 && mIndexTableBuffer && mContentTableBuffer
            && mIndexTableBuffer->tryReadUint(INDEX_SIZE, pos, &index)
            && index <= INT_MAX && getPosInContentTable(id, index) >= 0
            && static_cast<int64_t>(getPosInContentTable(0, index))
                    + static_cast<int64_t>(mBlockSize) * mDataSize
                    <= mContentTableBuffer->getTailPosition();
}

uint32_t SparseTable::get(const int id) const {
    if (!contains(id)) return NOT_A_DICT_POS;
    const int index = mIndexTableBuffer->readUint(INDEX_SIZE, getPosInIndexTable(id));
    uint32_t value;
    if (!mContentTableBuffer->tryReadUint(mDataSize, getPosInContentTable(id, index), &value)) {
        return NOT_A_DICT_POS;
    }
    const uint32_t empty = 0xFFFFFFFFu >> ((4 - mDataSize) * 8);
    return value == empty ? static_cast<uint32_t>(NOT_A_DICT_POS) : value;
}

bool SparseTable::set(const int id, const uint32_t value) {
    const int indexPos = getPosInIndexTable(id);
    if (indexPos < 0 || !mIndexTableBuffer || !mContentTableBuffer
            || mIndexTableBuffer->getTailPosition() % INDEX_SIZE != 0) return false;
    if (contains(id)) {
        const int index = mIndexTableBuffer->readUint(INDEX_SIZE, indexPos);
        return mContentTableBuffer->writeUint(value, mDataSize, getPosInContentTable(id, index));
    }
    const int64_t blockBytes = static_cast<int64_t>(mBlockSize) * mDataSize;
    const int contentTail = mContentTableBuffer->getTailPosition();
    if (contentTail % blockBytes || blockBytes > INT_MAX - contentTail) return false;
    // Reserve complete ranges before publishing an index. Huge sparse IDs fail promptly.
    const int oldIndexTail = mIndexTableBuffer->getTailPosition();
    if (indexPos > INT_MAX - INDEX_SIZE) return false;
    if (indexPos + INDEX_SIZE > oldIndexTail
            && !mIndexTableBuffer->extend(indexPos + INDEX_SIZE - oldIndexTail)) return false;
    if (!mContentTableBuffer->extend(static_cast<int>(blockBytes))) return false;
    for (int pos = oldIndexTail; pos <= indexPos; pos += INDEX_SIZE) {
        if (!mIndexTableBuffer->writeUint(NOT_EXIST, INDEX_SIZE, pos)) return false;
    }
    const int index = getIndexFromContentTablePos(contentTail);
    for (int pos = contentTail; pos < contentTail + blockBytes; pos += mDataSize) {
        if (!mContentTableBuffer->writeUint(NOT_EXIST, mDataSize, pos)) return false;
    }
    return mContentTableBuffer->writeUint(value, mDataSize, getPosInContentTable(id, index))
            && mIndexTableBuffer->writeUint(index, INDEX_SIZE, indexPos);
}

int SparseTable::getIndexFromContentTablePos(const int pos) const {
    return mDataSize > 0 && mBlockSize > 0 ? pos / mDataSize / mBlockSize : -1;
}

int SparseTable::getPosInIndexTable(const int id) const {
    if (id < 0 || mBlockSize <= 0 || mDataSize < 1 || mDataSize > 4) return -1;
    const int64_t pos = static_cast<int64_t>(id / mBlockSize) * INDEX_SIZE;
    return pos <= INT_MAX - INDEX_SIZE ? static_cast<int>(pos) : -1;
}

int SparseTable::getPosInContentTable(const int id, const int index) const {
    if (id < 0 || index < 0 || mBlockSize <= 0 || mDataSize < 1 || mDataSize > 4) return -1;
    const int64_t pos = (static_cast<int64_t>(index) * mBlockSize + id % mBlockSize) * mDataSize;
    return pos <= INT_MAX - mDataSize ? static_cast<int>(pos) : -1;
}

} // namespace latinime
