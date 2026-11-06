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

#ifndef LATINIME_SHORTCUT_LIST_POLICY_H
#define LATINIME_SHORTCUT_LIST_POLICY_H

#include <cstdint>

#include "defines.h"
#include "dictionary/interface/dictionary_shortcuts_structure_policy.h"
#include "dictionary/structure/pt_common/shortcut/shortcut_list_reading_utils.h"
#include "utils/byte_array_view.h"

namespace latinime {

class ShortcutListPolicy : public DictionaryShortcutsStructurePolicy {
 public:
    explicit ShortcutListPolicy(const ReadOnlyByteArrayView buffer) : mBuffer(buffer) {}

    ~ShortcutListPolicy() {}

    int getStartPos(const int pos) const {
        if (pos == NOT_A_DICT_POS) {
            return NOT_A_DICT_POS;
        }
        int listPos = pos;
        if (ShortcutListReadingUtils::getShortcutListSizeAndForwardPointer(mBuffer, &listPos) < 0)
            return NOT_A_DICT_POS;
        return listPos;
    }

    void getNextShortcut(const int maxCodePointCount, int *const outCodePoint,
            int *const outCodePointCount, bool *const outIsWhitelist, bool *const outHasNext,
            int *const pos) const {
        const ShortcutListReadingUtils::ShortcutFlags flags =
                ShortcutListReadingUtils::getFlagsAndForwardPointer(mBuffer, pos);
        if (outHasNext) {
            *outHasNext = ShortcutListReadingUtils::hasNext(flags);
        }
        if (outIsWhitelist) {
            *outIsWhitelist = ShortcutListReadingUtils::isWhitelist(flags);
        }
        int scratch[MAX_WORD_LENGTH];
        int count = ShortcutListReadingUtils::readShortcutTarget(mBuffer,
                outCodePoint ? maxCodePointCount : MAX_WORD_LENGTH,
                outCodePoint ? outCodePoint : scratch, pos);
        if (outCodePointCount) *outCodePointCount = count < 0 ? 0 : count;
        if (*pos == NOT_A_DICT_POS || count <= 0) {
            if (outHasNext) *outHasNext = false;
            if (outIsWhitelist) *outIsWhitelist = false;
        }
    }

    bool skipAllShortcuts(int *const pos) const {
        return ShortcutListReadingUtils::skipShortcuts(mBuffer, pos);
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ShortcutListPolicy);

    const ReadOnlyByteArrayView mBuffer;
};
} // namespace latinime
#endif // LATINIME_SHORTCUT_LIST_POLICY_H
