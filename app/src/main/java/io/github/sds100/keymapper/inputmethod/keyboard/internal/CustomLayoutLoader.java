package io.github.sds100.keymapper.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.util.Xml;
import android.view.inputmethod.EditorInfo;
import io.github.sds100.keymapper.inputmethod.keyboard.Key;
import io.github.sds100.keymapper.inputmethod.keyboard.KeyboardId;
import io.github.sds100.keymapper.inputmethod.latin.common.Constants;
import org.xmlpull.v1.XmlPullParser;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

public class CustomLayoutLoader {
    private static final String TAG = "CustomLayoutLoader";

    public static class CustomReplaceRule {
        public final String from;
        public final String to;
        public CustomReplaceRule(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    public static boolean tryLoadCustomLayout(Context context, KeyboardParams params, File file) {
        if (file == null || !file.exists()) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, "UTF-8");
            return loadCustomLayout(context, params, parser);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load custom layout from file", e);
            return false;
        }
    }

    public static boolean tryLoadCustomLayoutFromString(Context context, KeyboardParams params, String xmlContent) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new java.io.StringReader(xmlContent));
            return loadCustomLayout(context, params, parser);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load custom layout from string", e);
            return false;
        }
    }

    private static boolean loadCustomLayout(Context context, KeyboardParams params, XmlPullParser parser) {
        try {
            int eventType = parser.getEventType();
            int currentRow = -1;

            int baseWidth = params.mBaseWidth;
            int baseHeight = params.mBaseHeight;

            int totalRows = 4;
            int rowHeight = baseHeight / totalRows;

            float defaultKeyWidthPercent = 0.10f;

            ArrayList<ArrayList<CustomKeySpec>> rows = new ArrayList<>();
            ArrayList<Float> rowDefaultWidths = new ArrayList<>();
            ArrayDeque<SwitchState> switchStack = new ArrayDeque<>();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("switch".equalsIgnoreCase(tagName)) {
                        SwitchState newState = new SwitchState();
                        newState.isBranchActive = false;
                        switchStack.push(newState);
                    } else if ("case".equalsIgnoreCase(tagName)) {
                        SwitchState state = switchStack.peek();
                        if (state != null) {
                            boolean parentActive = isParentActive(switchStack);
                            if (parentActive && !state.conditionMatched && matchesCaseCondition(parser, params, context)) {
                                state.conditionMatched = true;
                                state.isBranchActive = true;
                            } else {
                                state.isBranchActive = false;
                            }
                        }
                    } else if ("default".equalsIgnoreCase(tagName)) {
                        SwitchState state = switchStack.peek();
                        if (state != null) {
                            boolean parentActive = isParentActive(switchStack);
                            if (parentActive && !state.conditionMatched) {
                                state.conditionMatched = true;
                                state.isBranchActive = true;
                            } else {
                                state.isBranchActive = false;
                            }
                        }
                    } else if (isCurrentActive(switchStack)) {
                        if ("Keyboard".equalsIgnoreCase(tagName) || "merge".equalsIgnoreCase(tagName)) {
                            String keyWidthAttr = getAttributeValue(parser, "keyWidth");
                            if (keyWidthAttr != null) {
                                defaultKeyWidthPercent = parsePercent(keyWidthAttr, 0.10f);
                            }
                        } else if ("Row".equalsIgnoreCase(tagName)) {
                            currentRow++;
                            rows.add(new ArrayList<CustomKeySpec>());
                            float rowWidth = defaultKeyWidthPercent;
                            String rowKeyWidthAttr = getAttributeValue(parser, "keyWidth");
                            if (rowKeyWidthAttr != null) {
                                rowWidth = parsePercent(rowKeyWidthAttr, defaultKeyWidthPercent);
                            }
                            rowDefaultWidths.add(rowWidth);
                        } else if ("Key".equalsIgnoreCase(tagName)) {
                            if (currentRow >= 0 && currentRow < rows.size()) {
                                CustomKeySpec spec = new CustomKeySpec();
                                spec.label = getAttributeValue(parser, "keyLabel", "label", "keySpec");
                                spec.codes = getAttributeValue(parser, "codes", "code");
                                spec.keyWidth = getAttributeValue(parser, "keyWidth", "width");
                                spec.keyIcon = getAttributeValue(parser, "keyIcon", "icon");
                                spec.longCode = getAttributeValue(parser, "longCode");
                                spec.moreKeys = getAttributeValue(parser, "moreKeys");
                                spec.backgroundType = getAttributeValue(parser, "backgroundType");
                                spec.hint = getAttributeValue(parser, "keyHintLabel", "hintLabel", "hint");
                                spec.keyStyle = getAttributeValue(parser, "keyStyle", "style");
                                spec.keyLabelFlags = getAttributeValue(parser, "keyLabelFlags", "labelFlags");
                                spec.mode = getAttributeValue(parser, "mode");
                                spec.imeAction = getAttributeValue(parser, "imeAction");
                                spec.actionKey = getAttributeValue(parser, "actionKey", "dynamicAction");
                                if (matchesKeyConditions(spec, params, context)) {
                                    rows.get(currentRow).add(spec);
                                }
                            }
                        } else if ("Spacer".equalsIgnoreCase(tagName)) {
                            if (currentRow >= 0 && currentRow < rows.size()) {
                                CustomKeySpec spec = new CustomKeySpec();
                                spec.isSpacer = true;
                                spec.keyWidth = getAttributeValue(parser, "keyWidth", "width");
                                spec.mode = getAttributeValue(parser, "mode");
                                spec.imeAction = getAttributeValue(parser, "imeAction");
                                if (matchesKeyConditions(spec, params, context)) {
                                    rows.get(currentRow).add(spec);
                                }
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String tagName = parser.getName();
                    if ("switch".equalsIgnoreCase(tagName)) {
                        if (!switchStack.isEmpty()) {
                            switchStack.pop();
                        }
                    } else if ("case".equalsIgnoreCase(tagName) || "default".equalsIgnoreCase(tagName)) {
                        SwitchState state = switchStack.peek();
                        if (state != null) {
                            state.isBranchActive = false;
                        }
                    }
                }
                eventType = parser.next();
            }

            if (rows.isEmpty()) return false;

            params.clearKeys();

            totalRows = rows.size();
            rowHeight = baseHeight / totalRows;

            for (int r = 0; r < totalRows; r++) {
                ArrayList<CustomKeySpec> rowKeys = rows.get(r);
                float rowDefaultW = (r < rowDefaultWidths.size()) ? rowDefaultWidths.get(r) : defaultKeyWidthPercent;
                int y = params.mTopPadding + r * rowHeight;
                int x = params.mLeftPadding;

                for (int i = 0; i < rowKeys.size(); i++) {
                    CustomKeySpec spec = rowKeys.get(i);
                    float wPercent = rowDefaultW;
                    if (spec.keyWidth != null) {
                        wPercent = parsePercent(spec.keyWidth, rowDefaultW);
                    }
                    int w = Math.round(wPercent * baseWidth);

                    if (spec.isSpacer) {
                        Key spacer = new Key.Spacer(params, x, y, w, rowHeight);
                        params.onAddKey(spacer);
                        x += w;
                        continue;
                    }

                    String label = spec.label;
                    String hint = (spec.hint != null && !spec.hint.trim().isEmpty()) ? spec.hint.trim() : null;
                    String specCodeStr = spec.codes;
                    String specIconStr = spec.keyIcon;

                    boolean isActionKey = false;
                    if (spec.actionKey != null) {
                        isActionKey = Boolean.parseBoolean(spec.actionKey);
                    } else if (spec.keyStyle != null) {
                        String ks = spec.keyStyle.toLowerCase(java.util.Locale.ROOT);
                        if (ks.contains("enter") || ks.contains("action")) {
                            isActionKey = true;
                        }
                    } else if ("action".equalsIgnoreCase(specCodeStr)) {
                        isActionKey = true;
                    } else if (specCodeStr == null && "enter".equalsIgnoreCase(specIconStr)) {
                        isActionKey = true;
                    } else if (specCodeStr == null && specIconStr == null && "⏎".equals(label)) {
                        isActionKey = true;
                    }

                    if (spec.keyStyle != null) {
                        String ks = spec.keyStyle.toLowerCase(java.util.Locale.ROOT);
                        if (ks.contains("delete")) {
                            if (specCodeStr == null) specCodeStr = "delete";
                            if (specIconStr == null) specIconStr = "delete";
                        } else if (ks.contains("space")) {
                            if (specCodeStr == null) specCodeStr = "space";
                            if (specIconStr == null) specIconStr = "space";
                        } else if (ks.contains("shift")) {
                            if (specCodeStr == null) specCodeStr = "shift";
                            if (specIconStr == null) specIconStr = "shift";
                        } else if (ks.contains("enter") || ks.contains("return")) {
                            if (specCodeStr == null) specCodeStr = "enter";
                            if (specIconStr == null) specIconStr = "enter";
                        } else if (ks.contains("toalpha") || ks.contains("tomoresymbol") || ks.contains("tosymbol")) {
                            if (specCodeStr == null) specCodeStr = "switch_alpha_symbol";
                        }
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

                    Key key = new Key(label, iconId, code, outputText, hint, moreKeySpecs, labelFlags, backgroundType, x, y, w, rowHeight, params);
                    params.onAddKey(key);

                    x += w;
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse layout XML", e);
            return false;
        }
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
        if (lower.contains("space")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SPACE_KEY);
        } else if (lower.contains("return") || lower.contains("enter")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_ENTER_KEY);
        } else if (lower.contains("shift")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHIFT_KEY);
        } else if (lower.contains("delete") || lower.contains("backspace")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_DELETE_KEY);
        } else if (lower.contains("settings")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SETTINGS_KEY);
        } else if (lower.contains("globe") || lower.contains("language")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY);
        } else if (lower.contains("emoji")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY);
        } else if (lower.contains("clipboard")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_CLIPBOARD_NORMAL_KEY);
        } else if (lower.contains("tab")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_TAB_KEY);
        } else if (lower.contains("shortcut")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHORTCUT_KEY);
        } else if (lower.contains("incognito")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_INCOGNITO_KEY);
        }
        try {
            return KeyboardIconsSet.getIconId(lower);
        } catch (Exception ignored) {
            return KeyboardIconsSet.ICON_UNDEFINED;
        }
    }

    private static String getAttributeValue(XmlPullParser parser, String... attrNames) {
        final int count = parser.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String rawName = parser.getAttributeName(i);
            final String name = rawName.contains(":") ? rawName.substring(rawName.indexOf(':') + 1) : rawName;
            for (String attr : attrNames) {
                if (attr.equalsIgnoreCase(name) || attr.equalsIgnoreCase(rawName)) {
                    return parser.getAttributeValue(i);
                }
            }
        }
        return null;
    }

    private static float parsePercent(String val, float defaultVal) {
        try {
            if (val.endsWith("%p")) {
                return Float.parseFloat(val.substring(0, val.length() - 2)) / 100.0f;
            } else if (val.endsWith("%")) {
                return Float.parseFloat(val.substring(0, val.length() - 1)) / 100.0f;
            }
            return Float.parseFloat(val) / 100.0f;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static class SwitchState {
        boolean conditionMatched = false;
        boolean isBranchActive = false;
    }

    private static boolean isParentActive(ArrayDeque<SwitchState> switchStack) {
        int idx = 0;
        for (SwitchState s : switchStack) {
            if (idx > 0) {
                if (!s.isBranchActive) return false;
            }
            idx++;
        }
        return true;
    }

    private static boolean isCurrentActive(ArrayDeque<SwitchState> switchStack) {
        for (SwitchState s : switchStack) {
            if (!s.isBranchActive) return false;
        }
        return true;
    }

    private static boolean matchesCaseCondition(XmlPullParser parser, KeyboardParams params, Context context) {
        String mode = getAttributeValue(parser, "mode");
        if (mode != null && !matchesMode(mode, params)) {
            return false;
        }

        String imeAction = getAttributeValue(parser, "imeAction");
        if (imeAction != null && !matchesImeAction(imeAction, params)) {
            return false;
        }

        String isMultiLine = getAttributeValue(parser, "isMultiLine");
        if (isMultiLine != null && !matchesMultiLine(isMultiLine, params)) {
            return false;
        }

        String orientation = getAttributeValue(parser, "orientation");
        if (orientation != null && !matchesOrientation(orientation, context)) {
            return false;
        }

        String keyboardLayoutSet = getAttributeValue(parser, "keyboardLayoutSet");
        if (keyboardLayoutSet != null && !matchesLayoutSet(keyboardLayoutSet, params)) {
            return false;
        }

        return true;
    }

    private static boolean matchesKeyConditions(CustomKeySpec spec, KeyboardParams params, Context context) {
        if (spec.mode != null && !matchesMode(spec.mode, params)) {
            return false;
        }
        if (spec.imeAction != null && !matchesImeAction(spec.imeAction, params)) {
            return false;
        }
        return true;
    }

    private static boolean matchesMode(String modeSpec, KeyboardParams params) {
        if (params == null || params.mId == null || modeSpec == null) return true;
        int currentMode = params.mId.mMode;
        String[] parts = modeSpec.split("[,|]");
        boolean hasPositive = false;
        boolean positiveMatch = false;
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.startsWith("!")) {
                int target = parseMode(p.substring(1).trim());
                if (currentMode == target) {
                    return false;
                }
            } else {
                hasPositive = true;
                int target = parseMode(p);
                if (currentMode == target) {
                    positiveMatch = true;
                }
            }
        }
        if (hasPositive) {
            return positiveMatch;
        }
        return true;
    }

    private static int parseMode(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "url": return KeyboardId.MODE_URL;
            case "email": return KeyboardId.MODE_EMAIL;
            case "im": return KeyboardId.MODE_IM;
            case "phone": return KeyboardId.MODE_PHONE;
            case "number": return KeyboardId.MODE_NUMBER;
            case "date": return KeyboardId.MODE_DATE;
            case "time": return KeyboardId.MODE_TIME;
            case "datetime": return KeyboardId.MODE_DATETIME;
            case "text":
            default:
                return KeyboardId.MODE_TEXT;
        }
    }

    private static boolean matchesImeAction(String actionSpec, KeyboardParams params) {
        if (params == null || params.mId == null || actionSpec == null) return true;
        int currentAction = params.mId.imeAction();
        String[] parts = actionSpec.split("[,|]");
        boolean hasPositive = false;
        boolean positiveMatch = false;
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.startsWith("!")) {
                int target = parseImeAction(p.substring(1).trim());
                if (currentAction == target) {
                    return false;
                }
            } else {
                hasPositive = true;
                int target = parseImeAction(p);
                if (currentAction == target) {
                    positiveMatch = true;
                }
            }
        }
        if (hasPositive) {
            return positiveMatch;
        }
        return true;
    }

    private static int parseImeAction(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "actionsearch":
            case "search":
                return EditorInfo.IME_ACTION_SEARCH;
            case "actionsend":
            case "send":
                return EditorInfo.IME_ACTION_SEND;
            case "actiongo":
            case "go":
                return EditorInfo.IME_ACTION_GO;
            case "actiondone":
            case "done":
                return EditorInfo.IME_ACTION_DONE;
            case "actionnext":
            case "next":
                return EditorInfo.IME_ACTION_NEXT;
            case "actionprevious":
            case "previous":
                return EditorInfo.IME_ACTION_PREVIOUS;
            case "actionnone":
            case "none":
                return EditorInfo.IME_ACTION_NONE;
            default:
                return EditorInfo.IME_ACTION_UNSPECIFIED;
        }
    }

    private static boolean matchesMultiLine(String multiLineSpec, KeyboardParams params) {
        if (params == null || params.mId == null) return true;
        boolean isMultiLine = params.mId.isMultiLine();
        return Boolean.parseBoolean(multiLineSpec) == isMultiLine;
    }

    private static boolean matchesOrientation(String orientSpec, Context context) {
        if (context == null || orientSpec == null) return true;
        int orientation = context.getResources().getConfiguration().orientation;
        boolean isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
        if ("landscape".equalsIgnoreCase(orientSpec) || "land".equalsIgnoreCase(orientSpec)) return isLandscape;
        if ("portrait".equalsIgnoreCase(orientSpec) || "port".equalsIgnoreCase(orientSpec)) return !isLandscape;
        return true;
    }

    private static boolean matchesLayoutSet(String layoutSetSpec, KeyboardParams params) {
        if (params == null || params.mId == null || params.mId.mSubtype == null) return true;
        String current = params.mId.mSubtype.getKeyboardLayoutSetName();
        return layoutSetSpec.equalsIgnoreCase(current);
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

    private static class CustomKeySpec {
        String label;
        String codes;
        String keyWidth;
        String keyIcon;
        String longCode;
        String moreKeys;
        String backgroundType;
        String hint;
        String keyStyle;
        String keyLabelFlags;
        String mode;
        String imeAction;
        String actionKey;
        boolean isSpacer;
    }
}
