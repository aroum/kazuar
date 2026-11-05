package io.github.aroum.kazuar.inputmethod.keyboard.internal;

import android.util.Xml;
import android.view.inputmethod.EditorInfo;
import io.github.aroum.kazuar.inputmethod.keyboard.Key;
import io.github.aroum.kazuar.inputmethod.latin.common.Constants;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import io.github.aroum.kazuar.inputmethod.keyboard.internal.CustomLayoutParser.CustomKeySpec;

/** Adapts the imported model to the same Key and KeySpecParser used by KeyboardBuilder. */
final class CustomLayoutKeyBuilder {
    static boolean build(KeyboardParams params, CustomLayoutParser.Layout layout) {
        ArrayList<ArrayList<CustomKeySpec>> rows = layout.rows;
        ArrayList<Float> rowDefaultWidths = layout.widths;
        if (rows.isEmpty()) return false;
        params.clearKeys();

        int totalRows = rows.size();
        int baseWidth = params.mBaseWidth;
        int rowHeight = params.mBaseHeight / totalRows;

        for (int r = 0; r < totalRows; r++) {
            ArrayList<CustomKeySpec> rowKeys = rows.get(r);
            float rowDefaultW = (r < rowDefaultWidths.size()) ? rowDefaultWidths.get(r) : 0.10f;
            int y = params.mTopPadding + r * rowHeight;
            int x = params.mLeftPadding;

            for (int i = 0; i < rowKeys.size(); i++) {
                CustomKeySpec spec = rowKeys.get(i);
                float wPercent = rowDefaultW;
                if (spec.keyWidth != null) {
                    wPercent = CustomLayoutParser.parsePercent(spec.keyWidth, rowDefaultW);
                }
                int w = Math.round(wPercent * baseWidth);

                if (spec.isSpacer) {
                    Key spacer = new Key.Spacer(params, x, y, w, rowHeight);
                    params.onAddKey(spacer);
                    x += w;
                    continue;
                }

                params.onAddKey(buildKey(params, spec, x, y, w, rowHeight));

                x += w;
            }
        }

        return true;
    }

    private static Key buildKey(KeyboardParams params, CustomKeySpec spec,
            int x, int y, int w, int rowHeight) {
        String label = spec.label;
        String hint = (spec.hint != null && !spec.hint.trim().isEmpty()) ? spec.hint.trim() : null;
        String specCodeStr = spec.codes;
        String specIconStr = spec.keyIcon;

        CustomKeyStyle style = CustomKeyStyle.resolve(spec.keyStyle);
        boolean isActionKey = false;
        if (spec.actionKey != null) {
            isActionKey = Boolean.parseBoolean(spec.actionKey);
        } else if (style != null) {
            isActionKey = style.action;
        } else if ("action".equalsIgnoreCase(specCodeStr)) {
            isActionKey = true;
        } else if (specCodeStr == null && "enter".equalsIgnoreCase(specIconStr)) {
            isActionKey = true;
        } else if (specCodeStr == null && specIconStr == null && "⏎".equals(label)) {
            isActionKey = true;
        }

        if (style != null) {
            if (specCodeStr == null) specCodeStr = style.code;
            if (specIconStr == null) specIconStr = style.icon;
        }

        int code = resolveCode(specCodeStr);
        int iconId = resolveIcon(specIconStr);
        String outputText = null;

        if (label != null) {
            try {
                int parsedIcon = KeySpecParser.getIconId(label);
                if (parsedIcon != KeyboardIconsSet.ICON_UNDEFINED && iconId == KeyboardIconsSet.ICON_UNDEFINED) {
                    iconId = parsedIcon;
                }
                int parsedCode = KeySpecParser.getCode(label);
                if (code == Constants.CODE_UNSPECIFIED && parsedCode != Constants.CODE_UNSPECIFIED) {
                    code = parsedCode;
                }
                String parsedOutputText = KeySpecParser.getOutputText(label);
                if (parsedOutputText != null) {
                    outputText = parsedOutputText;
                }
                String parsedLabel = KeySpecParser.getLabel(label);
                if (parsedLabel != null) {
                    label = parsedLabel;
                }
            } catch (Exception ignored) {
            }
        }

        // Support composite specs like "!icon/language_switch_key|!code/key_language_switch" if still unresolved
        if (label != null && label.contains("|") && (code == Constants.CODE_UNSPECIFIED || iconId == KeyboardIconsSet.ICON_UNDEFINED)) {
            String[] parts = label.split("\\|");
            for (String part : parts) {
                String p = part.trim();
                if (p.startsWith(KeyboardIconsSet.PREFIX_ICON) || p.contains("icon")) {
                    if (iconId == KeyboardIconsSet.ICON_UNDEFINED) iconId = resolveIcon(p);
                } else if (p.startsWith(KeyboardCodesSet.PREFIX_CODE) || p.contains("code")) {
                    if (code == Constants.CODE_UNSPECIFIED) code = resolveCode(p);
                }
            }
        }

        if (label != null) {
            int nlIndex = label.indexOf("\\n");
            if (nlIndex >= 0) {
                hint = label.substring(0, nlIndex);
                label = label.substring(nlIndex + 2);
            } else {
                nlIndex = label.indexOf("\n");
                if (nlIndex >= 0) {
                    hint = label.substring(0, nlIndex);
                    label = label.substring(nlIndex + 1);
                }
            }
        }

        int labelFlags = 0;
        if (spec.keyLabelFlags != null && spec.keyLabelFlags.contains("preserveCase")) {
            labelFlags |= Key.LABEL_FLAGS_PRESERVE_CASE;
        }

        // Dynamic action/enter key resolution
        if (isActionKey) {
            code = Constants.CODE_ENTER;
            final int imeAction = (params.mId != null) ? params.mId.imeAction() : EditorInfo.IME_ACTION_UNSPECIFIED;
            final boolean isMultiLine = (params.mId != null) && params.mId.isMultiLine();
            final String customActionLabel = (params.mId != null) ? params.mId.mCustomActionLabel : null;

            if (!isMultiLine) {
                if (customActionLabel != null && !customActionLabel.trim().isEmpty()) {
                    label = customActionLabel.trim();
                    iconId = KeyboardIconsSet.ICON_UNDEFINED;
                    labelFlags |= Key.LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL | Key.LABEL_FLAGS_PRESERVE_CASE;
                } else if (imeAction == EditorInfo.IME_ACTION_SEARCH) {
                    iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SEARCH_KEY);
                    label = null;
                } else if (imeAction == EditorInfo.IME_ACTION_SEND) {
                    iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SEND_KEY);
                    label = null;
                } else if (imeAction == EditorInfo.IME_ACTION_GO) {
                    iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_GO_KEY);
                    label = null;
                } else if (imeAction == EditorInfo.IME_ACTION_DONE) {
                    iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_DONE_KEY);
                    label = null;
                } else if (imeAction == EditorInfo.IME_ACTION_NEXT) {
                    iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_NEXT_KEY);
                    label = null;
                } else if (imeAction == EditorInfo.IME_ACTION_PREVIOUS) {
                    iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_PREVIOUS_KEY);
                    label = null;
                } else {
                    if (iconId == KeyboardIconsSet.ICON_UNDEFINED && (label == null || label.isEmpty())) {
                        iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_ENTER_KEY);
                    }
                }
            } else {
                if (iconId == KeyboardIconsSet.ICON_UNDEFINED && (label == null || label.isEmpty())) {
                    iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_ENTER_KEY);
                }
            }
        }

        // Derive code from icon if not explicitly set
        if (code == Constants.CODE_UNSPECIFIED && iconId != KeyboardIconsSet.ICON_UNDEFINED) {
            if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY)) {
                code = Constants.CODE_LANGUAGE_SWITCH;
            } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY)
                    || iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_EMOJI_ACTION_KEY)) {
                code = Constants.CODE_EMOJI;
            } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_CLIPBOARD_NORMAL_KEY)
                    || iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_CLIPBOARD_ACTION_KEY)) {
                code = Constants.CODE_CLIPBOARD;
            } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SETTINGS_KEY)) {
                code = Constants.CODE_SETTINGS;
            } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_DELETE_KEY)) {
                code = Constants.CODE_DELETE;
            } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHIFT_KEY)) {
                code = Constants.CODE_SHIFT;
            } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SPACE_KEY)) {
                code = Constants.CODE_SPACE;
            } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_ENTER_KEY)) {
                code = Constants.CODE_ENTER;
            }
        }

        // Derive code from label if still unspecified
        if (code == Constants.CODE_UNSPECIFIED && label != null && !label.isEmpty()) {
            if ("⌫".equals(label)) {
                code = Constants.CODE_DELETE;
            } else {
                code = label.codePointAt(0);
            }
        }

        // Assign standard icon if code is known and key has no text label or custom icon
        if (iconId == KeyboardIconsSet.ICON_UNDEFINED && (label == null || label.isEmpty())) {
            if (code == Constants.CODE_LANGUAGE_SWITCH) {
                iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY);
            } else if (code == Constants.CODE_EMOJI) {
                iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY);
            } else if (code == Constants.CODE_CLIPBOARD) {
                iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_CLIPBOARD_NORMAL_KEY);
            } else if (code == Constants.CODE_SETTINGS) {
                iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SETTINGS_KEY);
            } else if (code == Constants.CODE_DELETE) {
                iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_DELETE_KEY);
            } else if (code == Constants.CODE_SHIFT) {
                iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHIFT_KEY);
            } else if (code == Constants.CODE_SPACE) {
                iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SPACE_KEY);
            } else if (code == Constants.CODE_ENTER) {
                iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_ENTER_KEY);
            } else if (code == Constants.CODE_SWITCH_TO_ABC) {
                label = "ABC";
            } else if (code == Constants.CODE_SWITCH_TO_EDITING) {
                label = "⌨";
            } else if (code == Constants.CODE_SWITCH_TO_USR0) {
                label = "USR0";
            } else if (code == Constants.CODE_SWITCH_TO_USR1) {
                label = "USR1";
            } else if (code == Constants.CODE_SWITCH_TO_USR2) {
                label = "USR2";
            } else if (code == Constants.CODE_SWITCH_TO_NUM) {
                label = "123";
            }
        }

        int backgroundType = Key.BACKGROUND_TYPE_NORMAL;
        if (spec.backgroundType != null) {
            String bg = spec.backgroundType.trim().toLowerCase(java.util.Locale.ROOT);
            if ("action".equals(bg)) {
                backgroundType = Key.BACKGROUND_TYPE_ACTION;
            } else if ("functional".equals(bg)) {
                backgroundType = Key.BACKGROUND_TYPE_FUNCTIONAL;
            } else if ("spacebar".equals(bg)) {
                backgroundType = Key.BACKGROUND_TYPE_SPACEBAR;
            } else if ("normal".equals(bg)) {
                backgroundType = Key.BACKGROUND_TYPE_NORMAL;
            }
        } else {
            if (isActionKey) {
                backgroundType = Key.BACKGROUND_TYPE_ACTION;
            } else if (code == Constants.CODE_SPACE) {
                backgroundType = Key.BACKGROUND_TYPE_SPACEBAR;
            } else if (code == Constants.CODE_DELETE || code == Constants.CODE_SHIFT
                    || code == Constants.CODE_ENTER || code == Constants.CODE_LANGUAGE_SWITCH
                    || code == Constants.CODE_EMOJI || code == Constants.CODE_CLIPBOARD
                    || code == Constants.CODE_SETTINGS || code == Constants.CODE_SWITCH_ALPHA_SYMBOL
                    || code == Constants.CODE_CAPSLOCK || code == Constants.CODE_SWITCH_TO_EDITING
                    || code == Constants.CODE_SWITCH_TO_USR0 || code == Constants.CODE_SWITCH_TO_USR1
                    || code == Constants.CODE_SWITCH_TO_USR2 || code == Constants.CODE_SWITCH_TO_ABC
                    || code == Constants.CODE_SWITCH_TO_NUM
                    || iconId != KeyboardIconsSet.ICON_UNDEFINED) {
                backgroundType = Key.BACKGROUND_TYPE_FUNCTIONAL;
            }
        }

        String moreKeySpecs = null;
        if (spec.moreKeys != null && !spec.moreKeys.trim().isEmpty()) {
            moreKeySpecs = normalizeMoreKeys(spec.moreKeys);
        } else if (spec.longCode != null && !spec.longCode.trim().isEmpty()) {
            String lc = spec.longCode.trim();
            String prefix = (hint != null && !hint.trim().isEmpty()) ? hint.trim() : (label != null ? label : " ");
            if (",".equals(prefix)) {
                prefix = "\\,";
            } else if ("%".equals(prefix)) {
                prefix = "\\%";
            } else if ("|".equals(prefix)) {
                prefix = "\\|";
            }
            if (lc.contains("|")) {
                moreKeySpecs = "!noPanelAutoMoreKey!," + lc;
            } else {
                moreKeySpecs = "!noPanelAutoMoreKey!," + prefix + "|" + lc;
            }
        } else if (hint != null && !hint.trim().isEmpty()) {
            String h = hint.trim();
            if (",".equals(h)) {
                h = "\\,";
            } else if ("%".equals(h)) {
                h = "\\%";
            } else if ("|".equals(h)) {
                h = "\\|";
            }
            moreKeySpecs = "!noPanelAutoMoreKey!," + h;
        }

        return new Key(label, iconId, code, outputText, hint, moreKeySpecs, labelFlags, backgroundType, x, y, w, rowHeight, params);
    }

    private static int resolveCode(String specCode) {
        if (specCode == null) return Constants.CODE_UNSPECIFIED;
        String trimmed = specCode.trim();
        if (trimmed.isEmpty()) return Constants.CODE_UNSPECIFIED;

        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {}

        if (trimmed.startsWith(KeyboardCodesSet.PREFIX_CODE)) {
            trimmed = trimmed.substring(KeyboardCodesSet.PREFIX_CODE.length());
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        switch (lower) {
            case "language_switch":
            case "language":
            case "lang":
            case "globe":
            case "key_language_switch":
                return Constants.CODE_LANGUAGE_SWITCH;
            case "emoji":
            case "key_emoji":
            case "emoji_normal":
            case "emoji_action":
                return Constants.CODE_EMOJI;
            case "clipboard":
            case "key_clipboard":
            case "clipboard_normal":
            case "clipboard_action":
                return Constants.CODE_CLIPBOARD;
            case "shift":
            case "key_shift":
                return Constants.CODE_SHIFT;
            case "capslock":
            case "key_capslock":
                return Constants.CODE_CAPSLOCK;
            case "switch_alpha_symbol":
            case "key_switch_alpha_symbol":
            case "symbols":
            case "symbol":
                return Constants.CODE_SWITCH_ALPHA_SYMBOL;
            case "delete":
            case "backspace":
            case "key_delete":
                return Constants.CODE_DELETE;
            case "settings":
            case "key_settings":
                return Constants.CODE_SETTINGS;
            case "shortcut":
            case "key_shortcut":
                return Constants.CODE_SHORTCUT;
            case "action_next":
            case "key_action_next":
                return Constants.CODE_ACTION_NEXT;
            case "action_previous":
            case "key_action_previous":
                return Constants.CODE_ACTION_PREVIOUS;
            case "shift_enter":
            case "key_shift_enter":
                return Constants.CODE_SHIFT_ENTER;
            case "alpha_from_emoji":
            case "key_alpha_from_emoji":
                return Constants.CODE_ALPHA_FROM_EMOJI;
            case "alpha_from_clipboard":
            case "key_alpha_from_clipboard":
                return Constants.CODE_ALPHA_FROM_CLIPBOARD;
            case "enter":
            case "return":
            case "key_enter":
                return Constants.CODE_ENTER;
            case "space":
            case "spc":
            case "key_space":
                return Constants.CODE_SPACE;
            case "tab":
            case "key_tab":
                return Constants.CODE_TAB;
            case "copy":
            case "key_copy":
                return Constants.CODE_COPY;
            case "paste":
            case "key_paste":
                return Constants.CODE_PASTE;
            case "cut":
            case "key_cut":
                return Constants.CODE_CUT;
            case "undo":
            case "key_undo":
                return Constants.CODE_UNDO;
            case "redo":
            case "key_redo":
                return Constants.CODE_REDO;
            case "select_all":
            case "key_select_all":
                return Constants.CODE_SELECT_ALL;
            case "select_toggle":
            case "key_select_toggle":
                return Constants.CODE_SELECT_TOGGLE;
            case "delete_word":
            case "key_delete_word":
                return Constants.CODE_DELETE_WORD;
            case "forward_delete":
            case "key_forward_delete":
                return Constants.CODE_FORWARD_DELETE;
            case "switch_to_editing":
            case "key_switch_to_editing":
            case "switch_to_edit":
            case "key_switch_to_edit":
                return Constants.CODE_SWITCH_TO_EDITING;
            case "switch_to_usr0":
            case "key_switch_to_usr0":
            case "switch_to_user0":
            case "key_switch_to_user0":
                return Constants.CODE_SWITCH_TO_USR0;
            case "switch_to_usr1":
            case "key_switch_to_usr1":
            case "switch_to_user1":
            case "key_switch_to_user1":
                return Constants.CODE_SWITCH_TO_USR1;
            case "switch_to_usr2":
            case "key_switch_to_usr2":
            case "switch_to_user2":
            case "key_switch_to_user2":
                return Constants.CODE_SWITCH_TO_USR2;
            case "switch_to_abc":
            case "key_switch_to_abc":
                return Constants.CODE_SWITCH_TO_ABC;
            case "switch_to_num":
            case "key_switch_to_num":
            case "switch_to_numpad":
            case "key_switch_to_numpad":
            case "switch_to_pin":
            case "key_switch_to_pin":
            case "num":
            case "numpad":
            case "pin":
                return Constants.CODE_SWITCH_TO_NUM;
            case "arrow_left":
            case "key_arrow_left":
                return Constants.CODE_ARROW_LEFT;
            case "arrow_right":
            case "key_arrow_right":
                return Constants.CODE_ARROW_RIGHT;
            case "arrow_up":
            case "key_arrow_up":
                return Constants.CODE_ARROW_UP;
            case "arrow_down":
            case "key_arrow_down":
                return Constants.CODE_ARROW_DOWN;
            case "move_home":
            case "key_move_home":
                return Constants.CODE_MOVE_HOME;
            case "move_end":
            case "key_move_end":
                return Constants.CODE_MOVE_END;
            case "page_up":
            case "key_page_up":
                return Constants.CODE_PAGE_UP;
            case "page_down":
            case "key_page_down":
                return Constants.CODE_PAGE_DOWN;
            default:
                try {
                    return KeyboardCodesSet.getCode(lower);
                } catch (Exception ignored) {
                    return Constants.CODE_UNSPECIFIED;
                }
        }
    }

    private static int resolveIcon(String specIcon) {
        if (specIcon == null) return KeyboardIconsSet.ICON_UNDEFINED;
        String trimmed = specIcon.trim();
        if (trimmed.isEmpty()) return KeyboardIconsSet.ICON_UNDEFINED;
        if (trimmed.startsWith(KeyboardIconsSet.PREFIX_ICON)) {
            trimmed = trimmed.substring(KeyboardIconsSet.PREFIX_ICON.length());
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        try {
            return KeyboardIconsSet.getIconId(lower);
        } catch (RuntimeException ignored) {
            // Aliases below are only considered after exact registered names.
        }
        if (lower.equals("space")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SPACE_KEY);
        } else if (lower.equals("return") || lower.equals("enter")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_ENTER_KEY);
        } else if (lower.equals("shift")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHIFT_KEY);
        } else if (lower.equals("delete") || lower.equals("backspace")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_DELETE_KEY);
        } else if (lower.equals("settings")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SETTINGS_KEY);
        } else if (lower.equals("globe") || lower.equals("language")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY);
        } else if (lower.equals("emoji")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY);
        } else if (lower.equals("clipboard")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_CLIPBOARD_NORMAL_KEY);
        } else if (lower.equals("tab")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_TAB_KEY);
        } else if (lower.equals("shortcut")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHORTCUT_KEY);
        } else if (lower.equals("incognito")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_INCOGNITO_KEY);
        }
        try {
            return KeyboardIconsSet.getIconId(lower);
        } catch (Exception ignored) {
            return KeyboardIconsSet.ICON_UNDEFINED;
        }
    }

    private static String normalizeMoreKeys(String moreKeys) {
        if (moreKeys == null) return null;
        String trimmed = moreKeys.trim();
        if (trimmed.isEmpty()) return null;

        if (",".equals(trimmed)) {
            return "\\,";
        }

        if (!trimmed.contains("\\\\")) {
            return trimmed;
        }
        return trimmed
                .replace("\\\\,", "\\,")
                .replace("\\\\%", "\\%")
                .replace("\\\\|", "\\|");
    }


}
