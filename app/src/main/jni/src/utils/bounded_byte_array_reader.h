// SPDX-License-Identifier: GPL-3.0-only
#ifndef LATINIME_BOUNDED_BYTE_ARRAY_READER_H
#define LATINIME_BOUNDED_BYTE_ARRAY_READER_H

#include <limits>
#include "defines.h"
#include "utils/byte_array_view.h"
#include "dictionary/utils/byte_array_utils.h"

namespace latinime {
// Positions stay in the signed dictionary domain. A failed primitive poisons the cursor so
// callers cannot mistake a missing field or terminator for a valid absent child.
class BoundedByteArrayReader {
 public:
    static bool hasBytes(ReadOnlyByteArrayView buffer, int pos, size_t count) {
        return pos >= 0 && static_cast<size_t>(pos) <= buffer.size()
                && count <= buffer.size() - static_cast<size_t>(pos)
                && count <= static_cast<size_t>(std::numeric_limits<int>::max() - pos);
    }

    static bool readUint(ReadOnlyByteArrayView buffer, int size, uint32_t *value, int *pos) {
        if (size < 1 || size > 4 || !hasBytes(buffer, *pos, size)) {
            *pos = NOT_A_DICT_POS;
            return false;
        }
        *value = ByteArrayUtils::readUint(buffer.data(), size, *pos);
        *pos += size;
        return true;
    }

    static bool readCodePoint(ReadOnlyByteArrayView buffer, CodePointArrayView table, int *value, int *pos) {
        *value = ByteArrayUtils::readCodePointAndAdvancePosition(buffer, table, pos);
        return *pos != NOT_A_DICT_POS;
    }

    // An exact-length string must still have its terminator; overlong strings are rejected.
    static int readString(ReadOnlyByteArrayView buffer, int capacity, CodePointArrayView table,
            int *out, int *pos) {
        if (capacity < 0 || (capacity > 0 && !out)) { *pos = NOT_A_DICT_POS; return -1; }
        int count = 0;
        for (;;) {
            int codePoint;
            if (!readCodePoint(buffer, table, &codePoint, pos)) return -1;
            if (codePoint == NOT_A_CODE_POINT) return count;
            if (count >= capacity) { *pos = NOT_A_DICT_POS; return -1; }
            out[count++] = codePoint;
        }
    }
};
} // namespace latinime
#endif // LATINIME_BOUNDED_BYTE_ARRAY_READER_H
