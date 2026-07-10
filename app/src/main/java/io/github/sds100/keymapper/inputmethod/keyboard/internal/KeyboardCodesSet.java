/*
 * Copyright (C) 2012 The Android Open Source Project
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

package io.github.sds100.keymapper.inputmethod.keyboard.internal;

import io.github.sds100.keymapper.inputmethod.latin.common.Constants;

import java.util.HashMap;

public final class KeyboardCodesSet {
    public static final String PREFIX_CODE = "!code/";

    private static final HashMap<String, Integer> sNameToIdMap = new HashMap<>();

    private KeyboardCodesSet() {
        // This utility class is not publicly instantiable.
    }

    public static int getCode(final String name) {
        Integer id = sNameToIdMap.get(name);
        if (id == null) throw new RuntimeException("Unknown key code: " + name);
        return DEFAULT[id];
    }

    private static final String[] ID_TO_NAME = {
        "key_tab",
        "key_enter",
        "key_space",
        "key_shift",
        "key_capslock",
        "key_switch_alpha_symbol",
        "key_output_text",
        "key_delete",
        "key_settings",
        "key_shortcut",
        "key_action_next",
        "key_action_previous",
        "key_shift_enter",
        "key_language_switch",
        "key_emoji",
        "key_alpha_from_emoji",
        "key_unspecified",
        "key_clipboard",
        "key_alpha_from_clipboard",
        "key_start_onehanded",
        "key_stop_onehanded",
        "key_switch_onehanded",
        "key_copy",
        "key_paste",
        "key_cut",
        "key_undo",
        "key_delete_word",
        "key_redo",
        "key_select_toggle",
        "key_select_all",
        "key_clear",
        "key_arrow_left",
        "key_arrow_right",
        "key_arrow_up",
        "key_arrow_down",
        "key_move_home",
        "key_move_end",
        "key_page_up",
        "key_page_down",
        "key_switch_to_editing",
        "key_forward_delete"
    };

    private static final int[] DEFAULT = {
        Constants.CODE_TAB,
        Constants.CODE_ENTER,
        Constants.CODE_SPACE,
        Constants.CODE_SHIFT,
        Constants.CODE_CAPSLOCK,
        Constants.CODE_SWITCH_ALPHA_SYMBOL,
        Constants.CODE_OUTPUT_TEXT,
        Constants.CODE_DELETE,
        Constants.CODE_SETTINGS,
        Constants.CODE_SHORTCUT,
        Constants.CODE_ACTION_NEXT,
        Constants.CODE_ACTION_PREVIOUS,
        Constants.CODE_SHIFT_ENTER,
        Constants.CODE_LANGUAGE_SWITCH,
        Constants.CODE_EMOJI,
        Constants.CODE_ALPHA_FROM_EMOJI,
        Constants.CODE_UNSPECIFIED,
        Constants.CODE_CLIPBOARD,
        Constants.CODE_ALPHA_FROM_CLIPBOARD,
        Constants.CODE_START_ONE_HANDED_MODE,
        Constants.CODE_STOP_ONE_HANDED_MODE,
        Constants.CODE_SWITCH_ONE_HANDED_MODE,
        Constants.CODE_COPY,
        Constants.CODE_PASTE,
        Constants.CODE_CUT,
        Constants.CODE_UNDO,
        Constants.CODE_DELETE_WORD,
        Constants.CODE_REDO,
        Constants.CODE_SELECT_TOGGLE,
        Constants.CODE_SELECT_ALL,
        Constants.CODE_CLEAR,
        Constants.CODE_ARROW_LEFT,
        Constants.CODE_ARROW_RIGHT,
        Constants.CODE_ARROW_UP,
        Constants.CODE_ARROW_DOWN,
        Constants.CODE_MOVE_HOME,
        Constants.CODE_MOVE_END,
        Constants.CODE_PAGE_UP,
        Constants.CODE_PAGE_DOWN,
        Constants.CODE_SWITCH_TO_EDITING,
        Constants.CODE_FORWARD_DELETE
    };

    static {
        for (int i = 0; i < ID_TO_NAME.length; i++) {
            sNameToIdMap.put(ID_TO_NAME[i], i);
        }
    }
}
